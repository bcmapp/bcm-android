package com.bcm.messenger.wallet.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.*
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.ui.WalletConfirmDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.wallet.DeterministicSeed
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random


/**
 * Created by wjh on 2018/05/24
 */
object BCMWalletManagerContainer : AccountContextMap<BCMWalletManagerContainer.BCMWalletManager>({
    BCMWalletManager(it)
}) {

    private const val TAG = "BCMWalletManager"

    // 表示当前的钱包版本，以后基于这个判断
    private const val BCM_WALLET_VERSION = 4//当前钱包版本

    const val PREF_LAST_DISCOVERY = "pref_last_discovery_"

    const val TABLE_WALLET_ACCOUNT = "wallet_account_pref_"
    const val TABLE_WALLET_SETTING = "wallet_setting_table"

    private const val KEY_WALLET_PIN = "wallet_pin_key"
    private const val KEY_WALLET_VERSION = "wallet_version_key"

    private var mCurrentHttpClient: OkHttpClient? = null

    @Synchronized
    fun provideHttpClient(): OkHttpClient {
        var client = mCurrentHttpClient
        if (client == null) {
            client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS).build()
            mCurrentHttpClient = client
            return client
        }
        return client
    }

    fun getSuperPreferences(context: Context): SharedPreferences {
        return SuperPreferences.getSuperPreferences(context, TABLE_WALLET_SETTING)
    }

    enum class WalletStage {
        STAGE_UNKNOWN, //未知
        STAGE_INIT, //初始化账号阶段
        STAGE_SYNC, //同步区块阶段
        STAGE_DONE, //同步完毕
        STAGE_ERROR //同步失败
    }

    class BCMWalletManager(private val mAccountContext: AccountContext) {

        val btcController = BtcWalletController(this)
        val ethController = EthWalletController(this)

        private var mCurStage = WalletStage.STAGE_UNKNOWN

        private var bcmConnectedTask: Disposable? = null

        var isBCMConnected: Boolean = true
            set(value) {
                if (field != value) {
                    field = value
                    if (value) {
                        bcmConnectedTask?.dispose()
                        bcmConnectedTask = AmeDispatcher.io.dispatch({
                            if (value) {
                                btcController.checkMyceliumConfig()
                            }
                        }, 1500)
                    }
                }
            }

        private val mSyncFlag = AtomicReference(CountDownLatch(0)) //钱包账号同步标记

        private var mWalletAccounts = AtomicReference<BCMWalletAccounts>() //当前钱包账号

        private var mLastInitResult: Boolean? = null

        private var mPin: String? = null

        init {
            mWalletAccounts.set(loadWalletAccounts()) //初始化钱包账号
        }

        private fun getWalletAccountsSaveKey(): String {
            return "wallets_map_new" + if (AppUtil.useDevBlockChain()) "_test.data" else ".dat"
        }

        fun getAccountPreferences(context: Context): SharedPreferences {
            ALog.d(TAG, "getAccountPreferences table: wallet_preferences_${mAccountContext.uid}")
            return context.getSharedPreferences("wallet_preferences_${mAccountContext.uid}", Context.MODE_PRIVATE)
        }


        @Synchronized
        internal fun getWalletPin(): String {
            var pin = mPin
            if (pin.isNullOrEmpty()) {
                pin = getAccountPreferences(AppContextHolder.APP_CONTEXT).getString(KEY_WALLET_PIN, "")
                if (pin.isNullOrEmpty()) {
                    pin = (System.currentTimeMillis() / 1000).toString() + Random.Default.nextInt(0, 100)
                    getAccountPreferences(AppContextHolder.APP_CONTEXT).edit().putString(KEY_WALLET_PIN, pin).apply()
                }
                mPin = pin
            }
            return pin
        }

        @Synchronized
        fun reset() {
            try {
                mSyncFlag.get().await()
            } catch (ex: Exception) {
            }
            mWalletAccounts.get().clear()
            btcController.clearPeerSyncHelper()
            mPin = null
            mLastInitResult = null
            mCurStage = WalletStage.STAGE_UNKNOWN

        }

        @Synchronized
        fun getCurrentStage(): WalletStage {
            return mCurStage
        }

        @Synchronized
        fun clear() {
            fun delete(file: File) {
                try {
                    if (file.isFile) {
                        file.delete()
                    } else {
                        val directory = file.listFiles()
                        if (directory != null && directory.isNotEmpty()) {
                            directory.forEach {
                                delete(it)
                            }
                        } else {
                            file.delete()
                        }
                    }
                } catch (ex: Exception) {

                }
            }

            var destination = File(AppContextHolder.APP_CONTEXT.filesDir, WalletSettings.BTC)
            delete(destination)

            destination = File(AppContextHolder.APP_CONTEXT.filesDir, WalletSettings.ETH)
            delete(destination)

            getAccountPreferences(AppContextHolder.APP_CONTEXT).edit().clear().commit()

        }


        fun startInitService(context: Context, privateKeyArray: ByteArray, callback: ((success: Boolean) -> Unit)? = null) {
            ALog.i(TAG, "startInitService")
            Observable.create<Boolean> {

                it.onNext(initWallet(privateKeyArray, getWalletPin()))
                it.onComplete()

            }.subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        if (it) {
                            broadcastWalletInitProgress(WalletStage.STAGE_SYNC, 90, 100)
                        } else {
                            EventBus.getDefault().post(WalletProgressEvent(WalletStage.STAGE_ERROR, 100))
                        }
                        callback?.invoke(it)
                    }, {
                        ALog.e(TAG, "startInitService error", it)
                        EventBus.getDefault().post(WalletProgressEvent(WalletStage.STAGE_ERROR, 100))
                        callback?.invoke(false)
                    })
        }

        fun startCreateService(context: Context, coinType: String, privateKeyArray: ByteArray?, name: String? = null) {
            Observable.create<BCMWallet> {
                try {
                    privateKeyArray
                            ?: throw Exception("create new wallet fail, privateKeyArray is null")
                    val new = createWallet(coinType, getWalletPin(), privateKeyArray, name)
                            ?: throw Exception("create new wallet fail")
                    it.onNext(new)
                } catch (ex: Exception) {
                    it.onError(ex)
                } finally {
                    it.onComplete()
                }
            }.subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        EventBus.getDefault().post(WalletNewEvent(it, context.getString(R.string.wallet_service_create_with_name_success_title, it.name)))

                    }, {
                        ALog.e(TAG, "startCreateService error", it)
                        EventBus.getDefault().post(WalletNewEvent(null, context.getString(R.string.wallet_service_create_with_name_fail_title, name
                                ?: "")))

                    })
        }

        fun startTransferService(context: Context, fromWallet: WalletDisplay, toAddress: String, amount: String, extra: Bundle) {
            Observable.create<Pair<Boolean, String>> {
                val result = getWalletUtils(fromWallet.baseWallet.coinType)
                        .broadcastTransaction(fromWallet.baseWallet, toAddress, fromWallet.getCoinAmount().toString(), amount, getWalletPin(), extra)
                it.onNext(result)
                it.onComplete()

            }.subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        if (it.first) {
                            EventBus.getDefault().post(WalletTransferEvent(it.second, fromWallet.baseWallet, context.getString(R.string.wallet_service_transfer_success_title, fromWallet.displayName())))

                        } else {
                            EventBus.getDefault().post(WalletTransferEvent(null, fromWallet.baseWallet, context.getString(R.string.wallet_service_transfer_fail_title, fromWallet.displayName())))
                        }

                    }, {
                        ALog.e(TAG, "startTransferService error", it)
                        EventBus.getDefault().post(WalletTransferEvent(null, fromWallet.baseWallet, context.getString(R.string.wallet_service_transfer_fail_title, fromWallet.displayName())))

                    })
        }

        fun getWalletUtils(coinType: String): IBaseWalletController {
            return if (coinType == WalletSettings.BTC) {
                btcController
            } else {
                ethController
            }
        }

        fun getLastFeePlanTime(coinBase: String): Long {
            val prefs = getAccountPreferences(AppContextHolder.APP_CONTEXT)
            return prefs.getLong(WalletSettings.PREF_FEE_PLAN + coinBase, 0)
        }

        fun saveFeePlanString(coinBase: String, time: Long) {
            val edit = getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
            edit.putLong(WalletSettings.PREF_FEE_PLAN + coinBase, time)
            edit.apply()
        }

        fun getCurrentCurrency(): String {
            val prefs = getAccountPreferences(AppContextHolder.APP_CONTEXT)
            return prefs.getString(WalletSettings.PREF_COIN_CURRENCY, WalletSettings.USD) ?: ""
        }

        fun saveCurrencyCode(currencyCode: String) {
            val edit = getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
            edit.putString(WalletSettings.PREF_COIN_CURRENCY, currencyCode)
            edit.apply()
        }

        fun getWallet(address: String): BCMWallet? {
            return mWalletAccounts.get().findBCMWallet(address)
        }

        fun changeWalletName(address: String, name: String) {
            val utilWallet = getWallet(address)
            if (utilWallet != null) {
                ALog.d("BCMWallet", "change wallet name to storage")
                utilWallet.name = name
                saveWalletAccounts(mWalletAccounts.get())
            }
        }

        fun getWalletList(coinType: String): List<BCMWallet> {
            return mWalletAccounts.get().getAccount(coinType)?.coinList ?: listOf()
        }


        fun getAccount(coinType: String): BCMWalletAccount? {
            return mWalletAccounts.get().getAccount(coinType)
        }

        fun verifyPassword(password: String): Boolean {
            return try {
                AmeModuleCenter.user(mAccountContext)?.getUserPrivateKey(password) != null
            } catch (ex: Exception) {
                false
            }
        }

        fun getCurrentAccountIndex(coinType: String): Int {
            return getWalletUtils(coinType).getCurrentAccountIndex()
        }

        private fun setCurrentAccountIndex(coinType: String, accountIndex: Int) {
            ALog.i(TAG, "setCurrentAccountIndex, coinType: $coinType, $accountIndex")
            getWalletUtils(coinType).setCurrentAccountIndex(accountIndex)
        }

        private fun loadWalletAccounts(): BCMWalletAccounts {
            ALog.i(TAG, "loadWalletAccounts")
            val walletAccounts: BCMWalletAccounts = BCMWalletAccounts()
            try {
                val prefs = getAccountPreferences(AppContextHolder.APP_CONTEXT)
                val key = getWalletAccountsSaveKey()
                ALog.d(TAG, "loadWalletAccounts key: $key")
                if (prefs.contains(key)) {
                    val exist: BCMWalletAccounts = GsonUtils.fromJson(prefs.getString(key, "")
                            ?: "", object : TypeToken<BCMWalletAccounts>() {}.type)
                    if (exist.BTC.coinType != null && exist.BTC.coinList != null) {
                        walletAccounts.BTC.coinList.clear()
                        walletAccounts.BTC.coinList.addAll(exist.BTC.coinList)
                    }
                    if (exist.ETH.coinType != null && exist.ETH.coinList != null) {
                        walletAccounts.ETH.coinList.clear()
                        walletAccounts.ETH.coinList.addAll(exist.ETH.coinList)
                    }
                }
                walletAccounts.BTC.coinList.forEach {
                    it.setManager(this)
                }
                walletAccounts.ETH.coinList.forEach {
                    it.setManager(this)
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "loadWalletAccounts error", ex)

            }
            return walletAccounts
        }

        internal fun broadcastWalletInitProgress(stage: WalletStage, progress: Int, current: Int) {

            val actualProgress = when (stage) {
                WalletStage.STAGE_INIT -> {
                    progress * 0.5f / current
                }
                WalletStage.STAGE_SYNC -> {
                    0.5f + progress * 0.5f / current
                }
                WalletStage.STAGE_DONE -> {
                    1.0f
                }
                else -> {
                    return
                }
            }
            mCurStage = stage
            ALog.i(TAG, "broadcastWalletInit stage: $stage, progress: $actualProgress")
            EventBus.getDefault().post(WalletProgressEvent(stage, (actualProgress * 100).toInt()))
        }

        fun checkAccountsComplete(initIfNeed: Boolean = false, callback: (success: Boolean) -> Unit) {

            fun checkVersionNew(): Boolean {
                val pref = getAccountPreferences(AppContextHolder.APP_CONTEXT)
                val version = pref.getInt(KEY_WALLET_VERSION, BCM_WALLET_VERSION)
                return version == BCM_WALLET_VERSION
            }

            if (!checkVersionNew()) {
                ALog.i(TAG, "start, version not new, clear")
                //版本不对，首先清理旧的数据（所有账号相关的）
                clear()
                reset()
                getAccountPreferences(AppContextHolder.APP_CONTEXT).edit().putInt(KEY_WALLET_VERSION, BCM_WALLET_VERSION).apply()
            }

            mWalletAccounts.get()?.refresh()

            if (initIfNeed) {
                mLastInitResult = null
            }

            if (mLastInitResult == null) {
                val accounts = mWalletAccounts.get()
                if (!accounts.isAccountExist(WalletSettings.BTC) || !accounts.isAccountExist(WalletSettings.ETH)) {
                    ALog.i(TAG, "checkAccountsCompleteOrWait, account is not complete")
                    val keyPair = IdentityKeyUtil.getIdentityKeyPair(mAccountContext)
                    startInitService(AppContextHolder.APP_CONTEXT, keyPair.privateKey.serialize()) {
                        callback(it)
                    }
                } else {
                    callback(true)
                }
            } else if (mLastInitResult == true) {
                EventBus.getDefault().post(WalletProgressEvent(WalletStage.STAGE_DONE, 100))
                callback(true)
            } else if (mLastInitResult == false) {
                EventBus.getDefault().post(WalletProgressEvent(WalletStage.STAGE_ERROR, 100))
                callback(false)
            }
        }

        fun saveWalletAccounts(newAccounts: BCMWalletAccounts) {
            try {
                ALog.i(TAG, "saveWalletAccounts")
                mWalletAccounts.set(newAccounts)
                val backup = Gson().toJson(mWalletAccounts.get(), object : TypeToken<BCMWalletAccounts>() {}.type)
                val edit = getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
                edit.putString(getWalletAccountsSaveKey(), backup)
                edit.apply()

            } catch (ex: Exception) {
                ALog.e(TAG, "saveWalletAccounts error", ex)
            }
        }

        private fun initWallet(privateKeyArray: ByteArray, password: String): Boolean {
            ALog.i(TAG, "initAllWallet")
            try {
                mSyncFlag.get().await()
            } catch (ex: Exception) {
            }

            mSyncFlag.set(CountDownLatch(1))
            val newAccounts = BCMWalletAccounts()
            return try {
                //key: base58地址， value: triple: first: 钱包账号基类， second: isInternal, third: addressIndex
                val walletMap = mutableMapOf<String, Triple<BCMWallet, Boolean, Int>>()
                //默认判断子钱包最多100个
                val accountMax = 20//测试， 100
                val addressMax = 2
                var currentIndex = 0
                var defaultWallet: Triple<BCMWallet, Boolean, Int>? = null
                val seed = DeterministicSeed(privateKeyArray, "", mAccountContext.genTime)
                val hierarchy = KeyChainUtils.buildHierarchy(seed)
                val coinTypeList = arrayOf(WalletSettings.BTC, WalletSettings.ETH)
                var progress = 0
                val total = coinTypeList.size * accountMax
                for (coinType in coinTypeList) {
                    currentIndex = 0
                    walletMap.clear()
                    val utils = getWalletUtils(coinType)
                    val destination = utils.getDestinationDirectory()
                    while (currentIndex <= accountMax) {
                        broadcastWalletInitProgress(WalletStage.STAGE_INIT, progress++, total)

                        var address: String
                        var bcmWallet: BCMWallet? = null
                        //btc与eth不同，需要那external和internal的地址来请求，这样才比较精准
                        if (coinType == WalletSettings.BTC) {
                            for (i in 0 until addressMax) {

                                address = LegacyAddress.fromKey(BtcWalletController.NETWORK_PARAMETERS, KeyChainUtils.computeChildKey(coinType, currentIndex, hierarchy, false, i)).toBase58()
                                if (bcmWallet == null) {
                                    bcmWallet = BCMWallet(address, destination.absolutePath, coinType, currentIndex, System.currentTimeMillis()).apply {
                                        setManager(this@BCMWalletManager)
                                    }
                                }
                                ALog.i(TAG, "initWallet coin: $coinType, account: $currentIndex, address: $address")
                                walletMap[address] = Triple(bcmWallet, false, i)
                                if (WalletSettings.isBCMDefault(currentIndex) && i == 0) {
                                    defaultWallet = walletMap[address]
                                }

                                address = LegacyAddress.fromKey(BtcWalletController.NETWORK_PARAMETERS, KeyChainUtils.computeChildKey(coinType, currentIndex, hierarchy, true, i)).toBase58()
                                walletMap[address] = Triple(bcmWallet, true, i)
                                ALog.i(TAG, "initWallet coin: $coinType, account: $currentIndex, address: $address")

                            }
                        } else {
                            address = KeyChainUtils.computeMainChildAddress(coinType, currentIndex, hierarchy)
                            bcmWallet = BCMWallet(address, destination.absolutePath, coinType, currentIndex, System.currentTimeMillis()).apply {
                                setManager(this@BCMWalletManager)
                            }
                            walletMap[address] = Triple(bcmWallet, false, 0)

                            ALog.i(TAG, "initWallet coin: $coinType, account: $currentIndex, address: $address")
                            if (WalletSettings.isBCMDefault(currentIndex)) {
                                defaultWallet = walletMap[address]
                            }
                        }
                        currentIndex++
                    }

                    ALog.i(TAG, "before checkValid coinType: $coinType, count: ${walletMap.size}")
                    currentIndex = utils.checkValid(walletMap)
                    ALog.i(TAG, "after checkValid coinType: $coinType, count: ${walletMap.size}, index:$currentIndex")
                    if (currentIndex != -1) {//如果checkValid成功，则记录最新到达的账号索引

                        if (defaultWallet != null) {//如果checkValid查询不到余额，是会清空walletMap的，所以这里需要把default加回walletMap
                            walletMap[defaultWallet.first.address] = defaultWallet
                        }
                        setCurrentAccountIndex(coinType, currentIndex + 1)

                    } else {
                        //表示判断某个具体类型钱包是否有效的时候发生异常，则直接中断同步流程，反馈给UI展示
                        throw Exception("checkValid error, coinType is: $coinType")
                    }

                    //当前已经同步的账号index
                    val syncAccount = mutableListOf<Int>()
                    for ((address, bcmWalletTriple) in walletMap) {

                        if (syncAccount.indexOf(bcmWalletTriple.first.accountIndex) == -1) {//因为可能多个address对应同一个account，所以这里要做判断，只有没处理过的account才生成钱包
                            syncAccount.add(bcmWalletTriple.first.accountIndex)
                            if (!synchronizeWallList(newAccounts, bcmWalletTriple.first)) {//只有账号没有对应的钱包数据或者钱包备份file不存在才需要build
                                utils.buildWallet(bcmWalletTriple.first, seed, password)
                            }
                        }

                        if (utils is BtcWalletController) {
                            utils.getCurrentSyncHelper().addWallet(bcmWalletTriple.first).apply {
                                if (bcmWalletTriple.second) {
                                    addIssuedInternalKeyMap(bcmWalletTriple.third, address)
                                } else {
                                    addIssuedExternalKeyMap(bcmWalletTriple.third, address)
                                }
                            }
                        }
                    }

                }
                saveWalletAccounts(newAccounts)
                mLastInitResult = true
                true
            } catch (ex: Exception) {
                ALog.e(TAG, "initWallet error", ex)
                mLastInitResult = false
                false
            } finally {
                mSyncFlag.get().countDown()
            }
        }

        private fun createWallet(coinType: String, password: String, privateKeyArray: ByteArray, name: String? = null): BCMWallet? {
            ALog.i(TAG, "createWallet")
            return try {
                val accountIndex = getCurrentAccountIndex(coinType)
                val utils = getWalletUtils(coinType)
                val seed = DeterministicSeed(privateKeyArray, "", mAccountContext.genTime)
                val hierarchy = KeyChainUtils.buildHierarchy(seed)
                val address = KeyChainUtils.computeMainChildAddress(coinType, accountIndex, hierarchy)
                val w = BCMWallet(address, utils.getDestinationDirectory().absolutePath, coinType, accountIndex, System.currentTimeMillis()).apply {
                    setManager(this@BCMWalletManager)
                }
                if (!synchronizeWallList(mWalletAccounts.get(), w, name)) {
                    utils.buildWallet(w, seed, password)
                }
                setCurrentAccountIndex(coinType, accountIndex + 1)
                if (utils is BtcWalletController) {
                    utils.getCurrentSyncHelper().addWallet(w)
                }
                saveWalletAccounts(mWalletAccounts.get())
                w
            } catch (ex: Exception) {
                ALog.e(TAG, "createWallet error", ex)
                null
            }
        }

        private fun synchronizeWallList(accounts: BCMWalletAccounts, wallet: BCMWallet, name: String? = null): Boolean {
            val account = accounts.getAccount(wallet.coinType) ?: return false

            return if (account.coinList.find { it == wallet } == null) {
                ALog.i(TAG, "synchronize coinType: ${wallet.coinType}, address: ${wallet.address}, name: $name")

                account.coinList.add(wallet)

                if (name != null) {
                    wallet.name = name
                }

                false

            } else wallet.getSourceFile().exists()

        }

        private fun deleteWallet(wallet: BCMWallet) {
            try {
                val nextAccountIndex = getCurrentAccountIndex(wallet.coinType)
                if (nextAccountIndex - 1 == wallet.accountIndex) {
                    setCurrentAccountIndex(wallet.coinType, nextAccountIndex - 1)
                }
                // 实现多账号，跟账号挂钩，所以不删除钱包文件
                if (wallet.coinType == WalletSettings.BTC) {
                    btcController.getCurrentSyncHelper().removeWallet(wallet)
                }

                mWalletAccounts.get().removeBCMWallet(wallet.coinType, wallet)

                saveWalletAccounts(mWalletAccounts.get())

                WalletViewModel.walletModelSingle?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_DELETE, wallet))

            } catch (ex: Exception) {
                ALog.e("BCMWalletManager", "deleteWallet fail")
            }

        }

        fun formatDefaultName(coinType: String, accountIndex: Int = -1): String {
            val index = if(accountIndex == -1) {
                getCurrentAccountIndex(coinType)
            }else {
                accountIndex
            }
            val isMain = WalletSettings.isBCMDefault(index)
            return if (isMain) {
                AppContextHolder.APP_CONTEXT.getString(R.string.wallet_name_main)
            } else {
                AppContextHolder.APP_CONTEXT.getString(R.string.wallet_name_child, index.toString())
            }
        }

        fun goForCreateWallet(activity: AppCompatActivity?, coinType: String, notice: String = "", confirmCallback: (() -> Unit)? = null) {
            val activityRef = WeakReference(activity)
            var privateKeyArray: ByteArray? = null
            WalletConfirmDialog.showForPassword(activity, activity?.getString(R.string.wallet_confirm_password_title), notice,
                    confirmListener = { password ->
                        val a = activityRef.get() ?: return@showForPassword
                        WalletConfirmDialog.showForEdit(a, a.getString(R.string.wallet_name_edit_confirm_title),
                                previous = formatDefaultName(coinType),
                                hint = a.getString(R.string.wallet_name_hint), confirmListener = { name ->

                            startCreateService(a, coinType, privateKeyArray, name)

                            confirmCallback?.invoke()
                        })

                    }, passwordChecker = { password ->

                try {
                    privateKeyArray = AmeModuleCenter.user(mAccountContext)?.getUserPrivateKey(password)
                    privateKeyArray != null
                } catch (ex: Exception) {
                    false
                }
            })
        }

        fun goForInitWallet(activity: AppCompatActivity?, notice: String = "", confirmCallback: (() -> Unit)? = null) {
            var privateKeyArray: ByteArray? = null
            WalletConfirmDialog.showForPassword(activity, activity?.getString(R.string.wallet_confirm_password_title), notice,
                    confirmListener = { password ->
                        AmeModuleCenter.wallet(mAccountContext)?.initWallet(privateKeyArray
                                ?: return@showForPassword, password)
                        confirmCallback?.invoke()

                    }, passwordChecker = { password ->

                try {
                    privateKeyArray = AmeModuleCenter.user(mAccountContext)?.getUserPrivateKey(password)
                    privateKeyArray != null
                } catch (ex: Exception) {
                    false
                }
            })
        }

        fun goForDeleteWallet(activity: AppCompatActivity?, wallet: BCMWallet) {
            val activityRef = WeakReference(activity)
            WalletConfirmDialog.showForPassword(activity, activity?.getString(R.string.wallet_confirm_password_title),
                    confirmListener = { _ ->
                        AmePopup.loading.show(activity)
                        Observable.create(ObservableOnSubscribe<Boolean> {
                            deleteWallet(wallet)
                            it.onNext(true)
                            it.onComplete()
                        }).subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ _ ->
                                    AmePopup.loading.dismiss()
                                    val a = activityRef.get() ?: return@subscribe
                                    AmePopup.result.succeed(a, a.getString(R.string.wallet_delete_success_text), true)

                                }, {
                                    AmePopup.loading.dismiss()
                                    Logger.e(it, "goForExportWallet error")
                                    val a = activityRef.get() ?: return@subscribe
                                    AmePopup.result.failure(a, a.getString(R.string.wallet_delete_fail_text), true)
                                })


                    }, passwordChecker = { password ->
                verifyPassword(password)
            })
        }
    }


}
