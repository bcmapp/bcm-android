package com.bcm.messenger.wallet.utils

import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.btc.WalletEx
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Service
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates
import org.bitcoinj.protocols.channels.StoredPaymentChannelServerStates
import org.bitcoinj.store.BlockStore
import org.bitcoinj.store.BlockStoreException
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletExtension
import org.bitcoinj.wallet.WalletProtobufSerializer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by wjh on 2018/7/23
 */
class BtcWalletSyncHelper(val mParams: NetworkParameters) {

    companion object {

        private const val TAG = "BtcWalletSyncHelper"

        fun getBlockChainPrefix(param: NetworkParameters): String {
            return if (param.id == NetworkParameters.ID_MAINNET) {
                "BCM_chain_production"
            } else {
                "BCM_chain_test"
            } + "_" + AMESelfData.uid
        }

        fun getCheckpointFile(params: NetworkParameters): String {
            return if (params.id == NetworkParameters.ID_MAINNET) {
                "checkpoints_production.txt"
            } else {
                "checkpoints_test.txt"
            }
        }
    }

    init {
        addCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
            ALog.d("BtcWalletSyncHelper", "receive coin, previous: ${MonetaryFormat.BTC.format(prevBalance)}, new: ${MonetaryFormat.BTC.format(newBalance)}")
            WalletViewModel.walletModelSingle?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_BALANCE, WalletDisplay(wallet, newBalance.value.toString())))
        }
        addCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
            ALog.d("BtcWalletSyncHelper", "sent coin, previous: ${MonetaryFormat.BTC.format(prevBalance)}, new: ${MonetaryFormat.BTC.format(newBalance)}")

            WalletViewModel.walletModelSingle?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_BALANCE, WalletDisplay(wallet, newBalance.value.toString())))
        }
        addTransactionConfidenceEventListener { wallet, tx ->

            val sourceWallet = getSourceWallet(wallet)
                    ?: return@addTransactionConfidenceEventListener
            //对于已经是超过指定的确认数的记录，就不需要再广播了，避免造成过度广播
            if (tx.hasConfidence() && tx.confidence.confidenceType == TransactionConfidence.ConfidenceType.BUILDING &&
                    tx.confidence.appearedAtChainHeight > WalletSettings.CONFIDENCE_BTC) {
                return@addTransactionConfidenceEventListener
            }

            WalletViewModel.walletModelSingle?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_TRANSACTION_NEW, BtcWalletUtils.toTransactionDisplay(wallet, sourceWallet, tx)))

        }
    }

    private val mWalletMap: MutableMap<BCMWallet, WalletEx> = mutableMapOf()

    private var mEarliestKeyCreationTime: Long = 0

    private var mInternalService: WalletAppKit? = null

    private var mDownloadListener: DownloadProgressTracker? = null

    private var mSyncDone = false

    fun clearWallet() {
        for ((bcmWallet, walletEx) in mWalletMap) {
            walletEx.removeDiscoveryFlag()
        }
        mWalletMap.clear()
        mDownloadListener = null
    }

    fun clearChainFile(): Boolean {
        val chainFile = getWalletChainFile()
        return chainFile.delete()
    }

    fun getSourceWallet(BCMWallet: BCMWallet): WalletEx? {
        return mWalletMap[BCMWallet]
    }

    private fun getWalletChainFile(): File {
        return File(BtcWalletUtils.getDestinationDirectory(), getBlockChainPrefix(mParams) + ".spvchain")
    }

    private fun setEarliestKeyCreationTime(time: Long) {
        ALog.d("BtcWalletSyncHelper", "setEarliestKeyCreationTime: $time")
        mEarliestKeyCreationTime = time
    }

    private fun createDownloadListener(): DownloadProgressTracker {
        return object : DownloadProgressTracker() {
            override fun doneDownload() {
                ALog.d(TAG, "walletKit doneDownload")
                mSyncDone = true
                //同步完成后，通知更新页面
                WalletViewModel.walletModelSingle?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_BALANCE, null))
                BCMWalletManager.broadcastWalletInitProgress(BCMWalletManager.WalletStage.STAGE_DONE, 100, 100)
            }

            override fun startDownload(blocks: Int) {
                ALog.d("BtcWalletSyncHelper", "walletKit startDownload blocks: $blocks")
                mSyncDone = false
                BCMWalletManager.broadcastWalletInitProgress(BCMWalletManager.WalletStage.STAGE_SYNC, 0, 100)
            }

            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                ALog.d("BtcWalletSyncHelper", "walletKit progress: $pct, soFar: $blocksSoFar")
                if (!mSyncDone) {
                    BCMWalletManager.broadcastWalletInitProgress(BCMWalletManager.WalletStage.STAGE_SYNC, pct.toInt(), 100)
                }
            }
        }
    }

    @Synchronized
    fun start(restart: Boolean = false): Boolean {
        ALog.d(TAG, "start sync service, restart: $restart, wallet count: ${mWalletMap.count()}")
        if (mWalletMap.isEmpty()) {
            return false
        }

        var service = mInternalService
        var rebuild = false
        //首先判断是否存在之前的服务，如果restart为true，且之前的服务在运行中，则停止之前的服务
        if (service != null) {
            if (restart) {
                try {
                    service.stopAsync()
                    service.awaitTerminated()

                } catch (ex: Exception) {
                    ALog.e(TAG, "start restart: $restart, stopAsync", ex)
                }
                service = null
                mInternalService = null

                rebuild = true

            } else if (service.state() != Service.State.NEW && service.state() != Service.State.STARTING &&
                    service.state() != Service.State.RUNNING) {

                rebuild = true
            }

        } else {
            rebuild = true

        }
        if (restart) {//重启需要先把旧的chain file删掉
            val result = getWalletChainFile().delete()
            ALog.d(TAG, "delete chain file result: $result")
        }

        if (rebuild) {
            ALog.d(TAG, "rebuild sync service")
            service = createWalletAppKit()
            mInternalService = service
        }

        if (service?.state() == Service.State.NEW) {

            mDownloadListener = null
            mDownloadListener = createDownloadListener()
            service.setDownloadListener(mDownloadListener)

            try {
                service.startAsync()
                service.awaitRunning()

            } catch (ex: Exception) {
                if (!mSyncDone) {
                    getWalletChainFile().delete()
                }
                throw ex
            }

        } else {
            ALog.d(TAG, "service is running")
        }
        return true
    }

    @Synchronized
    fun stop(await: Boolean = true) {
        val service = mInternalService
        ALog.d(TAG, "stop sync service, await: $await")
        if (service != null && (service.state() == Service.State.STARTING || service.state() == Service.State.RUNNING)) {
            service.stopAsync()
            if (await) {
                service.awaitTerminated()
            }
        } else {
            ALog.d(TAG, "service is stopped")
        }
    }

    @Synchronized
    @Throws(Exception::class)
    fun addWallet(BCMWallet: BCMWallet): WalletEx {
        var sourceWallet = mWalletMap[BCMWallet]
        if (sourceWallet == null) {
            ALog.d(TAG, "addWallet address: ${BCMWallet.address}, accountIndex: ${BCMWallet.accountIndex}")
            //Preconditions.checkState(this.state() == Service.State.NEW, "Cannot call after startup is called")
            val walletFile = BCMWallet.getSourceFile()
            if (!walletFile.exists()) {
                throw Exception("wallet save file is not exist")
            }
            //maybeMoveOldWalletOutOfTheWay(BCMWallet, walletFile)
            val chainFile = getWalletChainFile()
            // 采用mycelium方式，所以这里要该下
//            val shouldReplayWallet = walletFile.exists() && !chainFile.exists()
            val shouldReplayWallet = !walletFile.exists()
            sourceWallet = loadWallet(BCMWallet, shouldReplayWallet)
            mWalletMap[BCMWallet] = sourceWallet
            //对于是默认的钱包，则把它的首次生成key的时间作为起点，这样能加快区块同步时间
            if (WalletSettings.isBCMDefault(BCMWallet.accountIndex)) {
                ALog.d(TAG, "addDefaultWallet create time: ${sourceWallet.earliestKeyCreationTime}")
                setEarliestKeyCreationTime(sourceWallet.earliestKeyCreationTime)
            }
            //添加回调
            sourceWallet.addCoinsReceivedEventListener { _, tx, pre, new ->
                mCoinReceivedListener?.invoke(BCMWallet, tx, pre, new)
            }
            sourceWallet.addCoinsSentEventListener { _, tx, pre, new ->
                mCoinSentListener?.invoke(BCMWallet, tx, pre, new)
            }
            sourceWallet.addTransactionConfidenceEventListener { _, tx ->
                mTransactionListener?.invoke(BCMWallet, tx)
            }

        } else {
            ALog.d(TAG, "no need addWallet, exist")
        }
        return sourceWallet
    }

    fun removeWallet(BCMWallet: BCMWallet) {
        val sourceWallet = mWalletMap[BCMWallet]
        if (sourceWallet != null) {
            ALog.d(TAG, "removeWallet accountIndex: ${BCMWallet.accountIndex}")
            mInternalService?.peerGroup()?.removeWallet(sourceWallet)
        }
        mWalletMap.remove(BCMWallet)
    }

    private fun createWalletAppKit(): WalletAppKit {
        val service = object : WalletAppKit(mParams, BtcWalletUtils.getDestinationDirectory(), getBlockChainPrefix(mParams)) {

            override fun provideBlockStore(file: File?): BlockStore {
                return SPVBlockStore(this.params, file, 10000, true)
//                return MemoryBlockStore(this.params)
            }

            override fun createPeerGroup(): PeerGroup {
                val peerGroup = PeerGroup(this.params, this.vChain)
                // 测试一些配置
                peerGroup.setConnectTimeoutMillis(15000)
                peerGroup.setPeerDiscoveryTimeoutMillis(10000)
                peerGroup.fastCatchupTimeSecs = mEarliestKeyCreationTime
                return peerGroup
            }

            @Throws(Exception::class)
            override fun startUp() {
                // Runs in a separate thread.
                Context.propagate(context)
                if (!directory.exists()) {
                    if (!directory.mkdirs()) {
                        throw IOException("Could not create directory " + directory.absolutePath)
                    }
                }
                try {
                    if (mEarliestKeyCreationTime <= 0) {
                        mEarliestKeyCreationTime = AMESelfData.genTime
                    }
                    ALog.d(TAG, "setEarliestKeyCreationTiem: $mEarliestKeyCreationTime")

                    val chainFile = getWalletChainFile()
                    val chainFileExists = chainFile.exists()
                    ALog.d(TAG, "startUp chainFile exists: $chainFileExists")

                    // Initiate Bitcoin network objects (block store, blockchain and peer group)
                    vStore = provideBlockStore(chainFile)
                    if (!chainFileExists || restoreFromSeed != null || restoreFromKey != null) {
                        ALog.d(TAG, "startUp to set checkPoint")
                        if (checkpoints != null) {
                            // Initialize the chain file with a checkpoint to speed up first-run sync.
                            val time: Long
                            if (restoreFromSeed != null) {
                                time = restoreFromSeed?.creationTimeSeconds ?: 0L
                                if (chainFileExists) {
                                    //log.info("Deleting the chain file in preparation from restore.")
                                    vStore.close()
                                    if (!chainFile.delete())
                                        throw IOException("Failed to delete chain file in preparation for restore.")
                                    vStore = provideBlockStore(chainFile)
                                }
                            } else if (restoreFromKey != null) {
                                time = restoreFromKey?.creationTimeSeconds ?: 0L
                                if (chainFileExists) {
                                    //log.info("Deleting the chain file in preparation from restore.")
                                    vStore.close()
                                    if (!chainFile.delete())
                                        throw IOException("Failed to delete chain file in preparation for restore.")
                                    vStore = provideBlockStore(chainFile)
                                }
                            } else {
                                time = mEarliestKeyCreationTime
                            }
                            if (time > 0) {
                                CheckpointManager.checkpoint(params, checkpoints, vStore, time)
                            } else {
                                ALog.d("BtcWalletSyncHelper", "time is zero, create uncheckpointed block store")
                            }

                        } else if (chainFileExists) {
                            ALog.i("BtcWalletSyncHelper", "Deleting the chain file in preparation from restore.")
                            vStore.close()
                            if (!chainFile.delete())
                                throw IOException("Failed to delete chain file in preparation for restore.")
                            vStore = provideBlockStore(chainFile)
                        }
                    }

                    vChain = BlockChain(params, vStore)
                    vPeerGroup = createPeerGroup()

                    // 测试验证
                    if (this.userAgent != null) {
                        vPeerGroup.setUserAgent(userAgent, version)
                    }

                    // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
                    // before we're actually connected the broadcast waits for an appropriate number of connections.
                    if (peerAddresses != null) {
                        for (addr in peerAddresses) {
                            vPeerGroup.addAddress(addr)
                        }
                        vPeerGroup.maxConnections = peerAddresses.size
                        peerAddresses = null
                    } else if (params.id != NetworkParameters.ID_REGTEST) {
                        vPeerGroup.addPeerDiscovery(if (discovery != null) discovery else DnsDiscovery(params))
                    }

                    //遍历当前所有子钱包，并并入到链和节点组中
                    for ((k, sourceWallet) in mWalletMap) {
                        vChain.addWallet(sourceWallet)
                        vPeerGroup.addWallet(sourceWallet)
                    }

                    onSetupCompleted()

                    if (blockingStartup) {
                        vPeerGroup.start()
                        // Make sure we shut down cleanly.
                        installShutdownHook()

                        for ((k, v) in mWalletMap) {
                            completeExtensionInitiations(v, vPeerGroup)
                        }

                        // TODO: Be able to use the provided download listener when doing a blocking startup.
                        val listener = DownloadProgressTracker()
                        vPeerGroup.startBlockChainDownload(listener)
                        listener.await()

                    } else {
                        Futures.addCallback(vPeerGroup.startAsync(), object : FutureCallback<Any> {
                            override fun onSuccess(result: Any?) {
                                for ((k, v) in mWalletMap) {
                                    completeExtensionInitiations(v, vPeerGroup)
                                }

                                val l = if (downloadListener == null) DownloadProgressTracker() else downloadListener
                                vPeerGroup.startBlockChainDownload(l)
                            }

                            override fun onFailure(t: Throwable) {
                                throw RuntimeException(t)

                            }
                        })
                    }

                } catch (e: BlockStoreException) {
                    ALog.e(TAG, "startUp error", e)
                    throw IOException(e)
                } catch (ex: Exception) {
                    ALog.e(TAG, "startUp error", ex)
                    throw IOException(ex)
                }

            }

            @Throws(Exception::class)
            override fun shutDown() {
                // Runs in a separate thread.
                try {
                    Context.propagate(context)
                    vPeerGroup.stop()

                    for ((k, v) in mWalletMap) {
                        val saveFile = k.getSourceFile()
                        v.saveToFile(saveFile)
                    }
                    vStore.close()

                    vPeerGroup = null
                    vWallet = null
                    vStore = null
                    vChain = null
                    mInternalService = null

                } catch (e: BlockStoreException) {
                    ALog.e("BtcWalletSyncHelper", "shutDown error", e)
                    throw IOException(e)
                }

            }

            override fun onSetupCompleted() {
                for ((k, v) in mWalletMap) {
                    ALog.d("BtcWalletSyncHelper", "onSetupCompleted accountIndex: ${k.accountIndex}")
                    v.allowSpendingUnconfirmedTransactions()
                }
            }

            /*
            * As soon as the transaction broadcaster han been created we will pass it to the
            * payment channel extensions
            */
            private fun completeExtensionInitiations(sourceWallet: Wallet, transactionBroadcaster: TransactionBroadcaster?) {
                transactionBroadcaster ?: return
                ALog.d("BtcWalletSyncHelper", "completeExtensionInitiations extensions: ${sourceWallet.extensions.size}")
                val clientStoredChannels = sourceWallet.extensions[StoredPaymentChannelClientStates::class.java.name] as? StoredPaymentChannelClientStates
                clientStoredChannels?.setTransactionBroadcaster(transactionBroadcaster)
                val serverStoredChannels = sourceWallet.extensions[StoredPaymentChannelServerStates::class.java.name] as? StoredPaymentChannelServerStates
                serverStoredChannels?.setTransactionBroadcaster(transactionBroadcaster)
            }

            private fun installShutdownHook() {
                if (autoStop)
                    Runtime.getRuntime().addShutdownHook(object : Thread() {
                        override fun run() {
                            try {
                                stopAsync()
                                awaitTerminated()
                            } catch (e: Exception) {
                                throw RuntimeException(e)
                            }

                        }
                    })
            }
        }
        service.setAutoSave(true).setBlockingStartup(false).setUserAgent("BCM", WalletSettings.AME_BTC_VERSION)

        try {
            val checkpointFile = getCheckpointFile(mParams)
            ALog.d(TAG, "set checkpointFile name: $checkpointFile")
            service.setCheckpoints(AppContextHolder.APP_CONTEXT.assets.open(checkpointFile))
        } catch (ex: Exception) {
            ALog.e(TAG, "setCheckpoints error", ex)
        }
        return service
    }

    @Throws(Exception::class)
    private fun loadWallet(BCMWallet: BCMWallet, shouldReplayWallet: Boolean): WalletEx {
        val saveFile = BCMWallet.getSourceFile()
        val walletStream = FileInputStream(saveFile)

        val wallet: WalletEx
        try {
            val extensions = ImmutableList.of<WalletExtension>()
            val proto = WalletProtobufSerializer.parseToProto(walletStream)
            val serializer = WalletProtobufSerializer(object : WalletProtobufSerializer.WalletFactory {
                override fun create(p0: NetworkParameters, p1: KeyChainGroup): Wallet {
                    return WalletEx(p0, p1, BtcWalletUtils.getClient())
                }

            })
            wallet = serializer.readWallet(mParams, extensions.toTypedArray(), proto) as WalletEx
            if (shouldReplayWallet) {
                ALog.d(TAG, "recycle wallet data")
                wallet.reset()
            }

            wallet.autosaveToFile(saveFile, 5, TimeUnit.SECONDS, null)

        }
        catch (ex: Exception) {
            ALog.e(TAG, "loadWallet error", ex)
            saveFile.delete()
            throw ex
        }
        finally {
            walletStream.close()
        }

        return wallet
    }

    private var mCoinReceivedListener: ((wallet: BCMWallet, tx: Transaction, preBalance: Coin, newBalance: Coin) -> Unit)? = null

    fun addCoinsReceivedEventListener(listener: ((wallet: BCMWallet, tx: Transaction, preBalance: Coin, newBalance: Coin) -> Unit)?) {
        mCoinReceivedListener = listener
    }

    private var mCoinSentListener: ((wallet: BCMWallet, tx: Transaction, preBalance: Coin, newBalance: Coin) -> Unit)? = null

    fun addCoinsSentEventListener(listener: ((wallet: BCMWallet, tx: Transaction, preBalance: Coin, newBalance: Coin) -> Unit)?) {
        mCoinSentListener = listener
    }

    private var mTransactionListener: ((wallet: BCMWallet, tx: Transaction) -> Unit)? = null

    fun addTransactionConfidenceEventListener(listener: ((wallet: BCMWallet, tx: Transaction) -> Unit)?) {
        mTransactionListener = listener
    }

}
