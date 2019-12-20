package com.bcm.messenger.wallet.btc

import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.btc.request.BroadcastTransactionRequest
import com.bcm.messenger.wallet.btc.request.GetTransactionsRequest
import com.bcm.messenger.wallet.btc.request.QueryTransactionInventoryRequest
import com.bcm.messenger.wallet.btc.request.QueryUnspentOutputsRequest
import com.bcm.messenger.wallet.btc.response.GetTransactionsResponse
import com.bcm.messenger.wallet.btc.response.QueryUnspentOutputsResponse
import com.bcm.messenger.wallet.btc.util.ByteReader
import com.bcm.messenger.wallet.btc.util.ByteWriter
import com.bcm.messenger.wallet.utils.BCMWalletManager
import com.bcm.messenger.wallet.utils.WalletSettings
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import org.bitcoinj.core.*
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletTransaction
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.LinkedHashSet

/**
 * Created by wjh on 2019/3/22
 */
class WalletEx(params: NetworkParameters, keyChainGroup: KeyChainGroup, val mApi: Wapi?) : Wallet(params, keyChainGroup) {

    companion object {
        private const val TAG = "WalletEx"

        const val EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH = 20
        const val INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH = 20

        private const val EXTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH = 4
        private const val INTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH = 1

    }

    // How many keys on each path have actually been used. This may be fewer than the number that have been deserialized
    // or held in memory, because of the lookahead zone.
    private val issuedExternalKeys: Int
        get() = activeKeyChain.issuedExternalKeys


    private val issuedInternalKeys: Int
        get() = activeKeyChain.issuedInternalKeys


    private var issuedExternalMap = mutableMapOf<Int, LegacyAddress>()
    private var issuedInternalMap = mutableMapOf<Int, LegacyAddress>()


    init {
        setCoinSelector(MyceliumCoinSelector())
    }

    fun getAccountIndex(): Int {
        return activeKeyChain.accountPath.last().num()
    }

    fun addIssuedInternalKeyMap(addressIndex: Int, addressBase58: String) {
        ALog.i(TAG, "addIssuedInternalKeyMap addressIndex: $addressIndex, addressBase58: $addressBase58")
        issuedInternalMap[addressIndex] = LegacyAddress.fromBase58(this.params, addressBase58)
    }

    fun addIssuedExternalKeyMap(addressIndex: Int, addressBase58: String) {
        ALog.i(TAG, "addIssuedExternalKeyMap addressIndex: $addressIndex, addressBase58: $addressBase58")
        issuedExternalMap[addressIndex] = LegacyAddress.fromBase58(this.params, addressBase58)
    }

    override fun getTransactions(includeDead: Boolean): Set<Transaction> {
        this.lock.lock()
        try {
            val result = super.getTransactions(includeDead)
            return result.filter { it.outputs.any { it.isMineOrWatched(this) } || it.inputs.any { it.connectedOutput?.isMineOrWatched(this) == true } }.toSet()
        } finally {
            this.lock.unlock()
        }
    }

    @Throws(Exception::class)
    fun broadcastTransaction(request: SendRequest): String {

        completeTx(request)
        val tran = request.tx
        val response = mApi?.broadcastTransaction(BroadcastTransactionRequest(Wapi.VERSION,
                parseRawTransactionByteArray(tran, true)))?.result
        if (response != null && response.success) {
            ALog.i(TAG, "broadcastTransaction response txId: ${response.txid}, actual tx: ${tran.txId}")
            handleTransactionsUpdate(listOf(tran), true)
            return response.txid
        } else {
            throw Exception("broadcastTransaction fail")
        }
    }

    fun callSynchronization(): Boolean {
        ALog.i(TAG, "callSynchronization")
        try {
            var checkHistory = false
            if (needDiscoverHistory()) {
                checkHistory = true
                if (!doHistoryDiscovery()) {
                    return false
                }
            }
            return doUnspentDiscovery(checkHistory)

        } catch (ex: Exception) {
            ALog.e(TAG, "callSynchronization error", ex)
        }
        return false
    }

    fun removeDiscoveryFlag() {
        val account = activeKeyChain.accountPath.get(2).num()
        ALog.i(TAG, "removeDiscoveryFlag account: $account")
        BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).edit().remove("${BCMWalletManager.PREF_LAST_DISCOVERY}${WalletSettings.BTC}_$account").apply()
    }

    private fun needDiscoverHistory(): Boolean {
        val last = getLastDiscoveryTime()
        ALog.i(TAG, "needDiscoverHistory last: $last, now: ${System.currentTimeMillis()}")
        return last + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()
    }

    private fun getLastDiscoveryTime(): Long {
        val account = activeKeyChain.accountPath.get(2).num()
        ALog.i(TAG, "getLastDiscoveryTime account: $account")
        return BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).getLong("${BCMWalletManager.PREF_LAST_DISCOVERY}${WalletSettings.BTC}_$account", 0)
    }

    private fun setLastDiscoveryTime() {
        val account = activeKeyChain.accountPath.get(2).num()
        ALog.i(TAG, "setLastDiscoveryTime account: $account")
        BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
                .putLong("${BCMWalletManager.PREF_LAST_DISCOVERY}${WalletSettings.BTC}_$account", System.currentTimeMillis()).apply()
    }

    private fun doUnspentDiscovery(hasDiscoverHistory: Boolean): Boolean {
        ALog.i(TAG, "doUnspentDiscovery hasDiscoveryHistory: $hasDiscoverHistory")
        try {
            val internalCount = if (hasDiscoverHistory) {
                INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
            } else {
                INTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH
            }
            val externalCount = if (hasDiscoverHistory) {
                EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
            } else {
                EXTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH
            }
            ensureAddressIndexes(true, internalCount)
            ensureAddressIndexes(false, externalCount)
            var internalAddressList = getAddressRange(true, internalCount)
            var externalAddressList = getAddressRange(false, externalCount)

            val newUtxo = handleUnspentDiscovery(internalAddressList + externalAddressList)
            ALog.i(TAG, "doUnspentDiscovery newUtxo: $newUtxo")
            if (newUtxo == -1) {
                return false
            }
            if (newUtxo > 0 && !hasDiscoverHistory) {
                ensureAddressIndexes(true, INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
                ensureAddressIndexes(false, EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)

                internalAddressList = getAddressRange(true, issuedInternalKeys, issuedInternalKeys + INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
                externalAddressList = getAddressRange(false, issuedExternalKeys, issuedExternalKeys + EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)

                return handleUnspentDiscovery(internalAddressList + externalAddressList) != -1
            }
            return true

        } catch (ex: Exception) {
            ALog.e(TAG, "doUnspentDiscovery error", ex)
        }

        return false
    }

    private fun doHistoryDiscovery(): Boolean {
        try {
            var loop = false
            do {
                ALog.i(TAG, "doHistoryDiscovery")
                loop = handleHistoryDiscovery()

            } while (loop)

        } catch (ex: Exception) {
            ALog.e(TAG, "doHistoryDiscovery error", ex)
            return false
        }
        setLastDiscoveryTime()
        return true
    }

    private fun handleUnspentDiscovery(addressList: Collection<Address>): Int {

        fun toMap(unspentList: Collection<QueryUnspentOutputsResponse.UnspentOutput>): Map<Sha256Hash, QueryUnspentOutputsResponse.UnspentOutput> {
            val result = mutableMapOf<Sha256Hash, QueryUnspentOutputsResponse.UnspentOutput>()
            unspentList.forEach {
                result[Sha256Hash.wrap(it.txHash)] = it
            }
            return result
        }

        val queryUnspentResponse = mApi?.queryUnspentOutputs(QueryUnspentOutputsRequest(Wapi.VERSION, addressList))?.result
        if (queryUnspentResponse != null) {
            var newUtxo = 0

            val queryUnspent = toMap(queryUnspentResponse.unspent)
            val updateSet = mutableSetOf<Sha256Hash>()

            val unspent = getTransactionPool(WalletTransaction.Pool.UNSPENT)
            for ((txId, transaction) in unspent) {
                if (!queryUnspent.containsKey(txId)) {
                    updateSet.add(txId)
                }
            }

            val pending = getTransactionPool(WalletTransaction.Pool.PENDING)
            for ((txId, transaction) in pending) {
                updateSet.add(txId)
            }

            val spent = getTransactionPool(WalletTransaction.Pool.SPENT)
            for ((txId, transaction) in spent) {
                if (transaction.confidence.depthInBlocks < WalletSettings.CONFIDENCE_BTC) {
                    updateSet.add(txId)
                }
            }

            for ((txId, unspentOutput) in queryUnspent) {
                val exit = this.transactions[txId]
                if (exit == null) {
                    newUtxo++
                    updateSet.add(txId)
                } else {
                    if (unspentOutput.height < 0 || unspentOutput.height != exit.confidence.depthInBlocks) {
                        updateSet.add(txId)
                    }
                }
            }

            val getTransactionsResponse = mApi?.getTransactions(GetTransactionsRequest(Wapi.VERSION, updateSet))?.result
            if (getTransactionsResponse != null) {
                val newTransactionList = transform(getTransactionsResponse.transactions)
                tryFetchValidTransactionOutPoint(newTransactionList)
                handleTransactionsUpdate(newTransactionList)
                return newUtxo
            }
        }
        return -1
    }

    private fun handleHistoryDiscovery(): Boolean {
        ALog.i(TAG, "handleHistoryDiscovery")
        ensureAddressIndexes(true, INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
        ensureAddressIndexes(false, EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
        val internalAddress = getAddressRange(true, INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
        val externalAddress = getAddressRange(false, EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH)
        val getHistoryResponse = mApi?.queryTransactionInventory(QueryTransactionInventoryRequest(Wapi.VERSION, internalAddress + externalAddress))?.result
        if (getHistoryResponse != null) {
            val txIds = getHistoryResponse.txHistory.map { Sha256Hash.wrap(it.txHash) }
            val getTransactionResponse = mApi?.getTransactions(GetTransactionsRequest(Wapi.VERSION, txIds))?.result
            if (getTransactionResponse != null) {
                val internalKeysBefore = issuedInternalKeys
                val externalKeysBefore = issuedExternalKeys
                ALog.i(TAG, "handleHistoryDiscovery internalBefore: $internalKeysBefore, externalBefore: $externalKeysBefore")
                val newTransactionList = transform(getTransactionResponse.transactions)
                tryFetchValidTransactionOutPoint(newTransactionList)
                handleTransactionsUpdate(newTransactionList)
                ALog.i(TAG, "handleHistoryDiscovery internalAfter: $issuedInternalKeys, externalAfter: $issuedExternalKeys")
                return issuedInternalKeys != internalKeysBefore || issuedExternalKeys != externalKeysBefore
            }
        }
        return false
    }

    private fun tryFetchValidTransactionOutPoint(newTransactionList: List<Transaction>): Boolean {

        try {
            val fetchList = mutableListOf<Sha256Hash>()
            for (tx in newTransactionList) {
                for (input in tx.inputs) {
                    if (input.isCoinBase) {
                        continue
                    }

                    ALog.i(TAG, "tryFetchValidTransactionOutPoint tx: ${input.outpoint.hash}")
                    if (!this.transactions.containsKey(input.outpoint.hash)) {
                        fetchList.add(input.outpoint.hash)
                    }
                }
            }
            ALog.i(TAG, "tryFetchValidTransactionOutPoint fetch: ${fetchList.joinToString { it.toString() }}")
            if (fetchList.isNotEmpty()) {
                val getTransactionResponse = mApi?.getTransactions(GetTransactionsRequest(Wapi.VERSION, fetchList))?.result
                if (getTransactionResponse != null) {
                    handleTransactionsUpdate(transform(getTransactionResponse.transactions), false)
                }
                return true
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "tryFetchValidTransactionOutPoint error", ex)
        }
        return false
    }

    private fun handleTransactionsUpdate(newTransactionList: List<Transaction>, needVerify: Boolean = true): Boolean {

        this.lock.lock()
        var txHash: Sha256Hash
        var bestChain: Boolean
        val clazz = this.javaClass
        var method: Method
        try {
            for (tx in newTransactionList) {
                try {
                    txHash = tx.txId
                    bestChain = tx.confidence.confidenceType == TransactionConfidence.ConfidenceType.BUILDING

                    val pending = getTransactionPool(WalletTransaction.Pool.PENDING)
                    val dead = getTransactionPool(WalletTransaction.Pool.DEAD)
                    val unspent = getTransactionPool(WalletTransaction.Pool.UNSPENT)
                    val spent = getTransactionPool(WalletTransaction.Pool.SPENT)

                    method = getMethod(clazz, "markKeysAsUsed", Transaction::class.java)
                    method.invoke(this, tx)

                    var tmp = this.transactions[txHash]
                    if (tmp == null) {
                        tmp = tx
                    } else {
                        tmp.confidence?.depthInBlocks = tx.confidence.depthInBlocks
                        tmp.confidence?.confidenceType = tx.confidence.confidenceType
                    }

                    val wasPending = pending.remove(txHash) != null
                    if (wasPending) {
                        ALog.i(TAG, "  <-pending")
                    }

                    if (bestChain) {
                        val wasDead = dead.remove(txHash) != null
                        if (wasDead)
                            ALog.i(TAG, "  <-dead")
                        if (wasPending) {
                            // Was pending and is now confirmed. Disconnect the outputs in case we spent any already: they will be
                            // re-connected by processTxFromBestChain below.
                            for (output in tx.outputs) {
                                val spentBy = output.spentBy
                                if (spentBy != null) {
                                    spentBy.disconnect()
                                }
                            }
                        }
                        method = getMethod(clazz, "processTxFromBestChain", Transaction::class.java, Boolean::class.java)
                        method.invoke(this, tmp, wasPending || wasDead || !needVerify)

                    } else {

                        // Transactions that appear in a side chain will have that appearance recorded below - we assume that
                        // some miners are also trying to include the transaction into the current best chain too, so let's treat
                        // it as pending, except we don't need to do any risk analysis on it.
                        if (wasPending) {
                            // Just put it back in without touching the connections or confidence.
                            addWalletTransaction(WalletTransaction(WalletTransaction.Pool.PENDING, tmp))
                            ALog.i(TAG, "  ->pending")
                        } else {
                            // Ignore the case where a tx appears on a side chain at the same time as the best chain (this is
                            // quite normal and expected).
                            if (!unspent.containsKey(txHash) && !spent.containsKey(txHash) && !dead.containsKey(txHash)) {
                                // Otherwise put it (possibly back) into pending.
                                // Committing it updates the spent flags and inserts into the pool as well.
                                commitTx(tx)
                            }
                        }
                    }

                } catch (ex: Exception) {
                    //ALog.e(TAG, "handleTransactionsUpdate tx: ${tx.txId} error", ex)
                }
            }
        } finally {
            saveNow()
            this.lock.unlock()
        }
        return true
    }

    private fun transform(transactionXList: Collection<GetTransactionsResponse.TransactionX>): List<Transaction> {

        fun peekByte(reader: ByteReader): Byte {
            val b = reader.get()
            reader.position = reader.position - 1
            return b
        }

        try {

            return transactionXList.mapNotNull { tx ->
                var targetTx: Transaction? = Transaction(this.params)
                try {
                    val reader = ByteReader(parseHexOrBinaryFromTransactionX(tx.hex))
                    val size = reader.available()
                    val version = reader.intLE
                    var useSegwit = false
                    val marker = peekByte(reader)
                    if (marker.toInt() == 0) {
                        //segwit possible
                        reader.get()
                        val flag = peekByte(reader)
                        if (flag.toInt() == 1) {
                            //it's segwit
                            reader.get()
                            useSegwit = true
                        } else {
                            throw Exception("Unable to parse segwit transaction. Flag must be 0x01")
                        }
                    }

                    val inputs = parseTransactionInputs(reader)
                    val outputs = parseTransactionOutputs(reader)
                    ALog.i(TAG, "transform tx: ${tx.txid} inputs: ${inputs.size} outputs: ${outputs.size}")

                    if (useSegwit) {
                        parseWitness(reader, inputs)
                    }
                    targetTx?.setVersion(version)
                    targetTx?.lockTime = reader.intLE * 1000L

                    inputs.forEach {
                        targetTx?.addInput(it)
                    }
                    outputs.forEach {
                        targetTx?.addOutput(it)
                    }

                    if (tx.confirmations <= 0) {
                        targetTx?.getConfidence(this.context)?.confidenceType = TransactionConfidence.ConfidenceType.PENDING
                    } else {
                        targetTx?.confidence?.appearedAtChainHeight = tx.height
                        targetTx?.getConfidence(this.context)?.confidenceType = TransactionConfidence.ConfidenceType.BUILDING
                    }
                    targetTx?.getConfidence(this.context)?.depthInBlocks = tx.confirmations
                    targetTx?.updateTime = Date(tx.time * 1000L)


                } catch (ex: Exception) {
                    ALog.e(TAG, "transform transaction:${tx.txid} error", ex)
                    targetTx = null
                }
                targetTx
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "transform GetTransactionsResponse to actual Transaction error", ex)
        }
        return emptyList<Transaction>()
    }

    private fun parseTransactionInputs(reader: ByteReader): List<TransactionInput> {
        val numInputs = reader.compactInt.toInt()
        val inputs = mutableListOf<TransactionInput>()
        for (i in 0 until numInputs) {
            try {
                val outPointHash = Sha256Hash.wrapReversed(reader.sha256Hash.bytes)
                val outPointIndex = reader.intLE.toLong()
                val scriptSize = reader.compactInt.toInt()
                val script = reader.getBytes(scriptSize)
                val sequence = reader.intLE.toLong()
                val input = if (outPointHash.equals(Sha256Hash.ZERO_HASH)) {
                    // Coinbase scripts are special as they can contain anything that
                    // does not parse
                    TransactionInput(this.params, null, script)
                } else {
                    val outPoint = TransactionOutPoint(this.params, outPointIndex, outPointHash)
                    TransactionInput(this.params, null, script, outPoint)
                }
                input.sequenceNumber = sequence
                inputs.add(input)

            } catch (e: Exception) {
                throw Exception("Unable to parse transaction input at index: $i", e)
            }
        }
        return inputs
    }

    private fun parseTransactionOutputs(reader: ByteReader): List<TransactionOutput> {
        val numOutputs = reader.compactInt.toInt()
        val outputs = mutableListOf<TransactionOutput>()
        for (i in 0 until numOutputs) {
            try {
                val value = reader.longLE
                val scriptSize = reader.compactInt.toInt()
                val scriptBytes = reader.getBytes(scriptSize)
                outputs.add(TransactionOutput(this.params, null, Coin.valueOf(value), scriptBytes))

            } catch (e: Exception) {
                throw Exception("Unable to parse transaction output at index: $i")
            }
        }
        return outputs
    }


    private fun parseWitness(reader: ByteReader, inputs: List<TransactionInput>) {
        for (input in inputs) {
            val stackSize = reader.compactInt
            val witness = TransactionWitness(stackSize.toInt())
            input.witness = witness
            for (y in 0 until stackSize.toInt()) {
                val pushSize = reader.compactInt
                val push = reader.getBytes(pushSize.toInt())
                witness.setPush(y, push)
            }
        }
    }

    private fun parseRawTransactionByteArray(tx: Transaction, asSegwit: Boolean): ByteArray {

        /**
         * @return true if transaction is SegWit, else false
         */
        fun isSegwit(): Boolean {
            return Iterables.any(tx.inputs, Predicate<TransactionInput> { transactionInput -> transactionInput != null && transactionInput.hasWitness() })
        }

        fun toByteWriter(writer: ByteWriter, input: TransactionInput) {
            writer.putSha256Hash(input.outpoint.hash, true)
            writer.putIntLE(input.outpoint.index.toInt())
            val script = input.scriptBytes
            writer.putCompactInt(script.size.toLong())
            writer.putBytes(script)
            writer.putIntLE(input.sequenceNumber.toInt())
        }

        fun toByteWriter(writer: ByteWriter, output: TransactionOutput) {
            writer.putLongLE(output.value.value)
            val scriptBytes = output.scriptBytes
            writer.putCompactInt(scriptBytes.size.toLong())
            writer.putBytes(scriptBytes)
        }

        fun toByteWriter(writer: ByteWriter, witness: TransactionWitness) {
            writer.putCompactInt(witness.pushCount.toLong())
            for (i in 0 until witness.pushCount) {
                writer.putCompactInt(witness.getPush(i).size.toLong())
                writer.putBytes(witness.getPush(i))
            }
        }

        val writer = ByteWriter(1024)
        writer.putIntLE(tx.version.toInt())
        val isSegwit = isSegwit()
        val isSegWitMode = asSegwit && isSegwit
        if (isSegWitMode) {
            writer.putCompactInt(0) //marker
            writer.putCompactInt(1) //flag
        }
        writer.putCompactInt(tx.inputs.size.toLong())
        for (input in tx.inputs) {
            toByteWriter(writer, input)
        }
        writer.putCompactInt(tx.outputs.size.toLong())
        for (output in tx.outputs) {
            toByteWriter(writer, output)
        }
        if (isSegWitMode) {
            for (input in tx.inputs) {
                toByteWriter(writer, input.witness)
            }
        }
        writer.putIntLE((tx.lockTime / 1000).toInt())
        return writer.toBytes()
    }

    private fun ensureAddressIndexes(isChange: Boolean, lookHead: Int) {
        val map = if (isChange) issuedInternalMap else issuedExternalMap
        val chain = activeKeyChain
        var index = if (isChange) chain.issuedInternalKeys else chain.issuedExternalKeys
        if (index > 0) {
            index -= 1
        }
        ALog.i(TAG, "ensureAddressIndexes isChange: $isChange index: $index lookHead: $lookHead")
        index += lookHead
        val path = ImmutableList.builder<ChildNumber>().addAll(chain.accountPath).add(if (isChange) ChildNumber.ONE else ChildNumber.ZERO).build()
        while (index >= 0) {
            if (!map.containsKey(index)) {
                var key = chain.getKeyByPath(path, false)
                key = HDKeyDerivation.deriveChildKey(key, index)
                map[index] = LegacyAddress.fromKey(this.params, key)
            }
            index--
        }

    }

    private fun getAddressRange(isChange: Boolean, count: Int): Collection<Address> {
        var fromIndex = 0
        var toIndex = 0
        if (isChange) {
            fromIndex = issuedInternalKeys - 1
            toIndex = issuedInternalKeys + count
        } else {
            fromIndex = issuedExternalKeys - 4
            toIndex = issuedExternalKeys + count
        }
        return getAddressRange(isChange, fromIndex, toIndex)
    }

    private fun getAddressRange(isChange: Boolean, fromIndex: Int, toIndex: Int): Collection<Address> {
        ALog.i(TAG, "getAddressRange isChange: $isChange fromIndex: $fromIndex toIndex: $toIndex")
        val map = if (isChange) issuedInternalMap else issuedExternalMap
        val clippedFromIndex = Math.max(0, fromIndex) // clip at zero
        val ret = LinkedHashSet<Address>(toIndex - clippedFromIndex + 1)
        for (i in clippedFromIndex..toIndex) {
            ret.add(map[i] ?: continue)
        }
        return ret
    }

    private fun parseHexOrBinaryFromTransactionX(data: String): ByteArray {
        return try {
            toBytes(data)
        } catch (ex: Exception) {
            Base64.decode(data)
        }
    }

    /**
     * Get the byte representation of an ASCII-HEX string.
     *
     * @param hexStr
     * The string to convert to bytes
     * @return The byte representation of the ASCII-HEX string.
     */
    private fun toBytes(hexStr: String?): ByteArray {
        var hexString = hexStr
        if (hexString != null) {
            hexString = hexString.replace(" ", "")
        }
        if (hexString == null || hexString.length % 2 != 0) {
            throw RuntimeException("Input string must contain an even number of characters")
        }
        val hex = hexString.toCharArray()
        val length = hex.size / 2
        val raw = ByteArray(length)
        for (i in 0 until length) {
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            if (high < 0 || low < 0) {
                throw RuntimeException("Invalid hex digit " + hex[i * 2] + hex[i * 2 + 1])
            }
            var value = high shl 4 or low
            if (value > 127)
                value -= 256
            raw[i] = value.toByte()
        }
        return raw
    }

    @Throws(Exception::class)
    private fun getMethod(clazz: Class<*>, methodName: String, vararg params: Class<*>): Method {
        return try {
            val method = clazz.getDeclaredMethod(methodName, *params)
            method.isAccessible = true
            method
        } catch (e: Exception) {
            val superClass = clazz.superclass
            superClass?.let { getMethod(it, methodName, *params) } ?: throw e
        }
    }
}