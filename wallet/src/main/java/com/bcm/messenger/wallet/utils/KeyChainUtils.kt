package com.bcm.messenger.wallet.utils


import com.bcm.messenger.common.utils.AppUtil
import com.google.common.collect.ImmutableList
import com.bcm.messenger.utility.logger.ALog
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.*
import org.bitcoinj.wallet.DeterministicSeed
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys

/**
 * Created by wjh on 2018/7/25
 */
object KeyChainUtils {

    private const val TAG = "KeyChainUtils"

    fun buildAccountPath(coinType: String, accountIndex: Int): ImmutableList<ChildNumber> {
        return ImmutableList.of(ChildNumber(44, true),
                if (!AppUtil.useDevBlockChain()) {
                    //比特币公链采用0， 以太币采用60
                    if (coinType == WalletSettings.BTC) {
                        ChildNumber.ZERO_HARDENED
                    }else {
                        ChildNumber(60, true)
                    }
                } else {
                    if (coinType == WalletSettings.BTC) {
                        //比特币测试链采用1， 以太币采用60
                        ChildNumber(1, true)
                    }else {
                        ChildNumber(60, true)
                    }
                },
                ChildNumber(accountIndex, true))
    }

    fun buildHierarchy(seed: DeterministicSeed): DeterministicHierarchy {
        val rootKey = HDKeyDerivation.createMasterPrivateKey(seed.seedBytes ?: throw NullPointerException())
        rootKey.creationTimeSeconds = seed.creationTimeSeconds
        return DeterministicHierarchy(rootKey)
    }

    fun computeMainChildAddress(coinType: String, accountIndex: Int, hierarchy: DeterministicHierarchy): String {

        val childKey = computeChildKey(coinType, accountIndex, hierarchy, false, 0)

        if (coinType == WalletSettings.BTC) {
            val address = LegacyAddress.fromKey(BtcWalletUtils.NETWORK_PARAMETERS, childKey).toBase58()
            ALog.d(TAG, "computeMainChildAddress: $address, accountIndex: $accountIndex")
            return address
        }else {
            val kp = ECKeyPair.create(childKey.privKeyBytes)
            return Keys.getAddress(kp)
        }
    }

    fun computeMainChildKey(coinType: String, accountIndex: Int, seed: DeterministicSeed): DeterministicKey {
        val rootKey = HDKeyDerivation.createMasterPrivateKey(seed.seedBytes ?: throw NullPointerException())
        rootKey?.creationTimeSeconds = seed.creationTimeSeconds
        val hierarchy = DeterministicHierarchy(rootKey)

        return computeChildKey(coinType, accountIndex, hierarchy, false, 0)
    }

    fun computeChildKey(coinType: String, accountIndex: Int, hierarchy: DeterministicHierarchy, isInternal: Boolean, addressIndex: Int): DeterministicKey {
        val accountPath = buildAccountPath(coinType, accountIndex)
        //external m/44'/coin'/account'/0
        //internal m/44'/coin'/account'/1

        val childKey = hierarchy.deriveChild(accountPath, false, true, if (isInternal) ChildNumber.ONE else ChildNumber.ZERO)
        return HDKeyDerivation.deriveChildKey(childKey, addressIndex)

    }

//    /**
//     *
//     */
//    fun findKeyFromPubHash(coinType: String, accountIndex: Int, addressString: String): Boolean {
//        val addressActual = Numeric.cleanHexPrefix(addressString)
//        val address = LegacyAddress.fromBase58(BtcWalletUtils.NETWORK_PARAMETERS, addressActual)
//        val keyPair = IdentityKeyUtil.getIdentityKeyPair(AppContextHolder.APP_CONTEXT)
//        val seed = DeterministicSeed(keyPair.privateKey.serialize(), "", AMESelfData.genTime)
//        val keyChain = DeterministicKeyChain.builder().seed(seed).accountPath(KeyChainUtils.buildAccountPath(coinType, accountIndex))
//                .outputScriptType(Script.ScriptType.P2PKH).build()
//
//        return keyChain.findKeyFromPubHash(address.hash) != null
//    }
}