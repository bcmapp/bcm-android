package com.bcm.messenger.wallet.presenter

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.server.ConnectState
import com.bcm.messenger.common.server.IServerConnectStateListener
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.*
import com.bcm.messenger.wallet.utils.*
import com.bcm.route.api.BcmRouter
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

/**
 * 钱包所有变更监控的视图实体
 * Created by wjh on 2018/6/1
 */
class WalletViewModel : ViewModel(), IServerConnectStateListener {

    companion object {

        private val TAG = "WalletViewModel"

        var walletModelSingle: WalletViewModel? = null
            private set

        /**
         * 获取当前现有的监控视图，如果没有则新建
         * @param activity
         */
        fun of(activity: FragmentActivity): WalletViewModel {
            if (walletModelSingle == null) {
                try {
                    walletModelSingle = ViewModelProviders.of(activity).get(WalletViewModel::class.java)
                }catch (ex: Exception) {
                    ALog.e("WalletViewModel", "of activity error", ex)
                }
            }
            return walletModelSingle!!
        }

        /**
         * 回收
         */
        fun recycle() {
            walletModelSingle = null
        }

    }

    private lateinit var mAccountContext: AccountContext

    init {
        ALog.i(TAG, "init")
        EventBus.getDefault().register(this)
    }

    val eventData: ImportantLiveData by lazy { ImportantLiveData(getManager()) }

    override fun onCleared() {
        super.onCleared()
        ALog.i(TAG, "onCleared")
        EventBus.getDefault().unregister(this)
        AmeModuleCenter.serverDaemon(mAccountContext).removeConnectionListener(this)
        recycle()
    }

    override fun onServerConnectionChanged(accountContext: AccountContext, newState: ConnectState) {
        getManager().isBCMConnected = newState == ConnectState.CONNECTED
    }

    fun setAccountContext(accountContext: AccountContext) {
        mAccountContext = accountContext
        AmeModuleCenter.serverDaemon(mAccountContext).addConnectionListener(this)
    }

    fun getManager(): BCMWalletManagerContainer.BCMWalletManager {
        return BCMWalletManagerContainer.get(mAccountContext)
    }

    /**
     * 加载钱包账号展示数据
     */
    fun queryAccountsDisplay(initIfNeed: Boolean, callback: (result: List<BCMWalletAccountDisplay>) -> Unit) {

        //完成请求的个数
        val completeNum = AtomicInteger(0)
        val btcDisplay = BCMWalletAccountDisplay(WalletSettings.BTC, mutableListOf())
        val ethDisplay = BCMWalletAccountDisplay(WalletSettings.ETH, mutableListOf())

        /**
         * 通知结果
         */
        fun notifyResult(success: Boolean, coinType: String, list: List<WalletDisplay>) {
            ALog.i(TAG, "loadWalletData notifyResult: current: $completeNum, success: $success, coinType:$coinType")
            if (coinType == WalletSettings.BTC) {
                btcDisplay.coinList.addAll(list)
            } else if (coinType == WalletSettings.ETH) {
                ethDisplay.coinList.addAll(list)
            }
            if (completeNum.addAndGet(1) >= 2) {
                if (getManager().getCurrentStage() == BCMWalletManagerContainer.WalletStage.STAGE_SYNC) {
                    getManager().broadcastWalletInitProgress(BCMWalletManagerContainer.WalletStage.STAGE_DONE, 100, 100)
                }
                callback.invoke(listOf(btcDisplay, ethDisplay))
            }
        }

        ALog.i(TAG, "queryAccountDisplay begin, initIfNeed: $initIfNeed")
        Observable.create(ObservableOnSubscribe<BCMWalletAccount> {

            getManager().checkAccountsComplete(initIfNeed) { success ->
                if (success) {
                    arrayOf(WalletSettings.BTC, WalletSettings.ETH).forEach { coinType ->
                        it.onNext(getManager().getAccount(coinType)
                                ?: BCMWalletAccount(coinType))
                    }
                } else {
                    it.onError(Exception("walletAccounts is not complete"))
                }
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    ALog.i(TAG, "queryAccountsDisplay begin query balance coinType: ${result.coinType}, coinList: ${result.coinList.size}")
                    queryBalance(result.coinType, result.coinList) {
                        notifyResult(it != null, result.coinType, it ?: result.coinList.map { bw ->
                            WalletDisplay(bw, BigDecimal.ZERO.toString()).apply {
                                setManager(getManager())
                            }
                        })
                    }

                }, { ex ->
                    ALog.e(TAG, "queryBalance error", ex)
                    callback.invoke(listOf())
                })

    }

    /**
     * 查询余额
     */
    private fun queryBalance(coinType: String, walletList: List<BCMWallet>, callback: (result: List<WalletDisplay>?) -> Unit) {
        var walletUtil: IBaseWalletController? = null
        var calculator: BaseExchangeCalculator? = null
        when(coinType) {
            WalletSettings.BTC -> {
                walletUtil = getManager().btcController
                calculator = BtcExchangeCalculator
            }
            WalletSettings.ETH -> {
                walletUtil = getManager().ethController
                calculator = EthExchangeCalculator
            }
            else -> {

            }
        }
        if (walletUtil != null && calculator != null) {
            //目标要完成的请求数
            val targetNum = if (walletList.isEmpty()) 1 else 2
            //完成请求的个数
            val completeNum = AtomicInteger(0)
            //结果
            var resultList: MutableList<WalletDisplay>? = null

            /**
             * 通知结果
             */
            fun notifyResult(success: Boolean) {
                if (completeNum.addAndGet(1) >= targetNum) {
                    callback.invoke(resultList)
                }

            }

            if (walletList.isEmpty()) {
                notifyResult(false)
                return
            }

            //一般查询数字货币余额的同时，还要查询汇率以及对应各种合法币种的价值
            calculator.updateExchangeRates(getManager().getCurrentCurrency()) {
                notifyResult(it)
            }

            Observable.create(ObservableOnSubscribe<List<WalletDisplay>> {
                it.onNext(walletUtil.queryBalance(walletList))
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        resultList = mutableListOf<WalletDisplay>()
                        resultList?.addAll(result)
                        notifyResult(true)

                    }, { ex ->
                        ALog.e(TAG, "queryBalance error", ex)
                        notifyResult(false)
                    })

        }else {
            callback.invoke(null)
        }
    }

    /**
     * 查询余额
     */
    fun queryBalance(wallet: BCMWallet, callback: (result: WalletDisplay?) -> Unit) {
        var walletUtil: IBaseWalletController? = null
        var calculator: BaseExchangeCalculator? = null
        when(wallet.coinType) {
            WalletSettings.BTC -> {
                walletUtil = getManager().btcController
                calculator = BtcExchangeCalculator
            }
            WalletSettings.ETH -> {
                walletUtil = getManager().ethController
                calculator = EthExchangeCalculator
            }
            else -> {

            }
        }
        if (walletUtil != null && calculator != null) {

            //目标要完成的请求数
            val targetNum = 2
            //完成请求的个数
            val completeNum = AtomicInteger(0)
            //已经成功的个数
            val successNum = AtomicInteger(0)
            //结果
            var result: WalletDisplay? = null

            /**
             * 通知结果
             */
            fun notifyResult(success: Boolean) {

                if (success) {
                    successNum.addAndGet(1)
                }
                if (completeNum.addAndGet(1) >= targetNum) {

                    if (successNum.get() > 0) {

                        val r = result ?: WalletDisplay(wallet, BigDecimal.ZERO.toString()).apply {
                            setManager(getManager())
                        }
                        callback.invoke(r)
                        eventData.notice(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_BALANCE, r))

                    } else {
                        callback.invoke(null)
                    }
                }

            }

            //查询余额的同时还需要查询当前汇率
            calculator.updateExchangeRates(getManager().getCurrentCurrency()) {
                notifyResult(it)
            }

            Observable.create(ObservableOnSubscribe<WalletDisplay> {
                it.onNext(walletUtil.queryBalance(wallet))
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ r ->
                        result = r
                        notifyResult(true)
                    }, { ex ->
                        ALog.e(TAG, "queryBalance error", ex)
                        notifyResult(false)
                    })

        }else {
            callback.invoke(null)
        }
    }

    /**
     * 查询交易记录
     */
    fun queryTransactions(wallet: BCMWallet, callback: (result: List<TransactionDisplay>?) -> Unit) {
        val controller = when(wallet.coinType) {
            WalletSettings.BTC -> getManager().btcController
            WalletSettings.ETH -> getManager().ethController
            else -> null
        }
        if (controller == null) {
            callback.invoke(null)
        }else {
            Observable.create(ObservableOnSubscribe<List<TransactionDisplay>> {

                it.onNext(controller.queryTransaction(wallet))
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        callback.invoke(result)
                    }, { ex ->
                        ALog.e(TAG, "queryTransactions error", ex)
                        callback.invoke(null)
                    })
        }
    }

    /**
     * 查询推荐小费列表
     */
    fun querySuggestFee(wallet: BCMWallet, callback: (result: List<FeePlan>?) -> Unit) {
        val lastTime = getManager().getLastFeePlanTime(wallet.coinType)
        //每隔1分钟才执行一次推荐小费的查询
        if ((System.currentTimeMillis() - lastTime) <= 60 * 1000) {
            return
        }
        Observable.create(ObservableOnSubscribe<List<FeePlan>> {

            if (wallet.coinType == WalletSettings.BTC) {
                val request = Request.Builder()
                request.url("https://wallet.schildbach.de/fees")
                val response = BCMWalletManagerContainer.provideHttpClient().newCall(request.build()).execute()
                if (response.isSuccessful) {
                    val context = AppContextHolder.APP_CONTEXT
                    val resultList = ArrayList<FeePlan>()
                    BufferedReader(InputStreamReader(response.body()?.byteStream())).use { reader ->
                        while (true) {
                            var line = reader.readLine() ?: break
                            line = line.trim()
                            if (line.isEmpty() || line[0] == '#') {
                                continue
                            }
                            Logger.d("findSuggestBtcFee line: $line")
                            val fields = line.split("=".toRegex())
                            when {
                                fields[0] == "ECONOMIC" -> resultList.add(FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_low_description),
                                        fields[1]))
                                fields[0] == "NORMAL" -> resultList.add(FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_middle_description),
                                        fields[1]))
                                fields[0] == "PRIORITY" -> resultList.add(FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_high_description),
                                        fields[1]))
                            }
                        }
                    }
                    it.onNext(resultList)

                } else {
                    it.onError(Exception("response fail: ${response.code()}"))
                }
            }else if (wallet.coinType == WalletSettings.ETH) {
                it.onNext(getManager().ethController?.findSuggestGasPrice() ?: listOf())
            }
            else {
                it.onError(Exception("walletType is not support"))
            }
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    getManager().saveFeePlanString(wallet.coinType, System.currentTimeMillis())
                    callback.invoke(result)
                }, { ex ->
                    ALog.e(TAG, "querySuggestFee error", ex)
                    callback.invoke(null)
                })
    }

    /**
     * 查询所有货币对应的法币单位的汇率
     */
    fun queryAllExchangeRate(currency: String, callback: (result: Int?) -> Unit) {

        val target = 2
        //完成请求的个数
        val completeNum = AtomicInteger(0)
        //成功的个数
        val successNum = AtomicInteger(0)

        /**
         * 通知结果
         */
        fun notifyResult(success: Boolean) {
            if (success) {
                successNum.addAndGet(1)
            }
            if (completeNum.addAndGet(1) >= target) {
                if (successNum.get() > 0) {
                    callback.invoke(successNum.get())

                    //所有汇率更新完毕，要发出通知页面更新信息
                    eventData.notice(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_RATE_UPDATE))

                } else {
                    callback.invoke(null)
                }
            }

        }

        listOf(BtcExchangeCalculator, EthExchangeCalculator).forEach {
            it.updateExchangeRates(currency ) {
                notifyResult(it)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventTransfer(result: WalletTransferEvent) {
        ALog.i(TAG, "onEventTransfer result: ${result.tx != null}")
        if (result.tx != null) {
            AmeAppLifecycle.succeed(result.message ?: "", true)
            eventData.notice(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_TRANSACTION_RESULT, result))

            val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
            val noticeBackup = !userProvider.hasBackupAccount()
            eventData.notice(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_ACCOUNT_BACKUP, noticeBackup))


        } else {
            AmeAppLifecycle.failure(result.message ?: "", true)

        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventNewWallet(result: WalletNewEvent) {
        ALog.i(TAG, "onEventNewWallet result: ${result.new != null}")
        if (result.new != null) {
            eventData.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_NEW, result.new))
            AmeAppLifecycle.succeed(result.message ?: "", true)
        } else {
            AmeAppLifecycle.failure(result.message ?: "", true)

        }
    }

    /**
     * 接收初始化和区块同步的进度事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventSyncProgress(progress: WalletProgressEvent) {
        ALog.i(TAG, "onEventSyncProgress stage: ${progress.stage}, progress: ${progress.progress}")
        val event = ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_SYNC, progress)
        eventData.notice(event)
    }

}