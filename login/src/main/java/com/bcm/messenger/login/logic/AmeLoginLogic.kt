package com.bcm.messenger.login.logic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.crypto.PreKeyUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.repositories.IdentityRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.deprecated.DatabaseFactory
import com.bcm.messenger.common.gcm.FcmUtil
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IUmengModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.R
import com.bcm.messenger.login.bean.*
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.JsonSyntaxException
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECKeyPair
import org.whispersystems.libsignal.ecc.ECPrivateKey
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.util.ByteUtil
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.internal.util.Base58
import java.io.File
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Created by bcm.social.01 on 2018/9/3.
 */
object AmeLoginLogic {

    private const val TAG = "AmeLoginLogic"

    private const val RESPONSE_NOT_FOUND = 404

    private var mChallengeResult: ChallengeResult? = null
    private var mPrefixUid: String? = null
    private var mMask: Long = 0L

    private var tmpAccountContext = AccountContext("", "", "")
    private var loginTempData: Triple<String, ECKeyPair, String>? = null

    val accountHistory: AmeAccountHistory = AmeAccountHistory()

    private val support = BcmFeatureSupport(64)
    private var mRegisterNick: String? = null

    private var gcmToken = ""

    init {
        accountHistory.init()

        val functions = arrayOf(BcmFeatureSupport.FEATURE_ENABLE,
                BcmFeatureSupport.FEATURE_BIDIRECTIONAL_CONTACT,
                BcmFeatureSupport.FEATURE_AWS, BcmFeatureSupport.GROUP_SECURE_V3)

        for (func in functions) {
            support.enableFunction(func)
        }

    }

    private fun getDeviceName(): String {
        val brand = Build.BRAND
        val model = Build.MODEL
        if (brand.isNullOrEmpty() && model.isNullOrEmpty()) {
            return "Android"
        }
        return "$brand - $model"
    }

    fun isAccountHistoryReachLimit(): Boolean {
        return accountHistory.getCurrentAccountSize() >= AmeAccountHistory.LIMIT_SIZE
    }

    /**
     * @return true login, false not login
     */
    fun isLogin(uid: String): Boolean {
        if (accountHistory.isLogin(uid)){
            return true
        }

        if (tmpAccountContext.uid == uid) {
            return tmpAccountContext.password.isNotEmpty()
        }
        return false
    }

    /**
     * @return current login account state
     */
    fun getMajorAccount(): AmeAccountData? {
        return accountHistory.majorAccountData()
    }

    /**
     * @return account state of uid
     */
    fun getAccount(uid: String): AmeAccountData? {
        return accountHistory.getAccount(uid)
    }

    fun getAccountContext(uid: String): AccountContext {
        val context = accountHistory.getAccountContext(uid)
        if (null != context) {
            return context
        }

        if (uid == tmpAccountContext.uid && tmpAccountContext.password.isNotEmpty()) {
            return tmpAccountContext
        }

        return AccountContext(uid, "", "")
    }

    /**
     * save account state
     */
    fun saveAccount(account: AmeAccountData) {
        accountHistory.saveAccount(account)
    }

    /**
     * save backup time
     */
    fun saveCurrentBackup(time: Long) {
        val account = accountHistory.majorAccountData()
        if (account != null) {
            var newTime = min(time, AmeTimeUtil.localTimeSecond())
            if (newTime <= 0) {
                newTime = AmeTimeUtil.localTimeSecond()
            }
            if (account.backupTime != newTime) {
                account.backupTime = newTime
                accountHistory.saveAccount(account)
            }
        }
    }

    private fun handleLocalLogout(accountContext: AccountContext, context: Context) {
        AmeModuleCenter.contact(accountContext)?.doForLogOut()
        AmeModuleCenter.user(accountContext)?.doForLogout()

        AmePushProcess.clearNotificationCenter()
        AmePushProcess.updateAppBadge(AppContextHolder.APP_CONTEXT, 0)
        AmeModuleCenter.wallet(accountContext)?.logoutWallet()
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onAccountLogout(AppContextHolder.APP_CONTEXT, "")

        AmeModuleCenter.onLogOutSucceed(accountContext)
        BCMEncryptUtils.clearMasterSecret()
        Recipient.clearCache(context)

    }

    private fun handleLocalLogin(accountContext: AccountContext, context: Context) {

        val currentAccount = getMajorAccount() ?: return

        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onAccountLogin(AppContextHolder.APP_CONTEXT, currentAccount.uid)

        AmeModuleCenter.user(accountContext)?.doForLogin(Address.from(accountContext, currentAccount.uid), null, currentAccount.name, currentAccount.avatar)

        AmeModuleCenter.group(accountContext)?.doOnLogin()

        refreshMySupportFeature(accountContext)

        AmeModuleCenter.metric(accountContext)?.loginEnd(true)
    }

    /**
     * quit account
     */
    fun quit(accountContext: AccountContext, clearHistory: Boolean, withLogOut: Boolean = true) {

        if (accountContext.isLogin) {
            ALog.i(TAG, "quit clearHistory: $clearHistory, withLogout: $withLogOut")

            try {
                handleLocalLogout(accountContext, AppContextHolder.APP_CONTEXT)

                if (withLogOut) {
                    PushUtil.unregisterPush(accountContext)
                }

                if (clearHistory) {
                    ALog.i(TAG, "clear history")
                    try {
                        if (DatabaseFactory.isDatabaseExist(accountContext, AppContextHolder.APP_CONTEXT)) {
                            DatabaseFactory.deleteDatabase(accountContext, AppContextHolder.APP_CONTEXT)
                        }
                        Repository.clearDatabase(accountContext)

                        TextSecurePreferences.clear(accountContext)
                        MasterSecretUtil.clearMasterSecretPassphrase(accountContext)
                        AmeModuleCenter.wallet(accountContext)?.destroyWallet()
                        AmeModuleCenter.contact(accountContext)?.clear()

                    } catch (e: Throwable) {
                        ALog.logForSecret(TAG, "clear account history error", e)
                    }
                    ALog.i(TAG, "clear history finish")
                }

            } catch (e: Throwable) {
                ALog.logForSecret(TAG, "quit error", e)
            }

        } else {
            ALog.i(TAG, "just only clear current login uid")
        }

        accountHistory.removeLoginAccountUid(accountContext.uid)
        loginTempData = null
    }

    /**
     * get account history
     */
    fun getAccountList(): List<AmeAccountData> {
        return accountHistory.getAccountList().sortedWith(kotlin.Comparator { o1, o2 ->
            if (accountHistory.isLogin(o1.uid) || accountHistory.isLogin(o2.uid)) {
                o2.lastLoginTime.compareTo(o1.lastLoginTime)
            } else {
                val o1t = max(o1.lastLoginTime, o1.backupTime)
                val o2t = max(o2.lastLoginTime, o2.backupTime)
                o2t.compareTo(o1t)
            }
        })
    }

    private fun setTmpToken(uid: String, password: String) {
        tmpAccountContext = if (uid.isNotEmpty() && password.isNotEmpty()) {
            val token = accountHistory.genToken(uid, password)
            AccountContext(uid, token, password)
        } else {
            AccountContext(uid, "", "")
        }
    }

    /**
     * do login
     */
    fun login(uid: String, password: String, result: (succeed: Boolean, exception: Throwable?, error: String) -> Unit) {
        val data = accountHistory.getAccount(uid)
        if (data == null) {
            result(false, AmeLoginWrongAccountException(), AppContextHolder.APP_CONTEXT.getString(R.string.login_account_not_exist))
        } else {
            return login(data, password, result)
        }
    }

    /**
     * do login
     */
    @SuppressLint("CheckResult")
    private fun login(data: AmeAccountData, password: String, result: (succeed: Boolean, exception: Throwable?, error: String) -> Unit) {
        AmeDispatcher.io.dispatch {
            val error: String
            try {
                ALog.logForSecret(TAG, "login ${data.uid}")
                val priKeyArray = accountHistory.getPrivateKeyWithPassword(data, password)
                        ?: throw BCMPrivateKeyUtils.ErrorPinException()
                val priKey: ECPrivateKey = Curve.decodePrivatePoint(priKeyArray)
                val pubKeyArray = BCMPrivateKeyUtils.generatePublicKeyWithDJB(priKey.serialize())
                        ?: throw BCMPrivateKeyUtils.ErrorPinException()
                if (data.uid.isEmpty()) {
                    data.uid = BCMPrivateKeyUtils.provideUid(pubKeyArray)
                }

                val pubKey: ECPublicKey = Curve.decodePoint(pubKeyArray, 0)

                val keyPair = ECKeyPair(pubKey, priKey)
                val publicKey = Base64.encodeBytes(keyPair.publicKey.serialize())

                val signalingKey = BCMPrivateKeyUtils.getSecret(52)
                val signalPassword = BCMPrivateKeyUtils.getSecret(18)

                val registrationId = KeyHelper.generateRegistrationId(false)

                val sign = BCMPrivateKeyUtils.sign(priKey, signalPassword)
                val accountParams = AmeAccountParams(signalingKey,
                        publicKey,
                        true,
                        registrationId,
                        "test", org.whispersystems.signalservice.internal.util.Base64.encodeBytes(getDeviceName().toByteArray()), true, true)
                val loginParams = AmeLoginParams(sign, accountParams)

                setTmpToken(data.uid, signalPassword)
                mRegisterNick = data.name

                val accountContext = getAccountContext(data.uid)
                AmeModuleCenter.metric(accountContext)?.loginStart()

                AmeLoginCore.login(accountContext, loginParams)
                        .subscribeOn(AmeDispatcher.singleScheduler)
                        .observeOn(AmeDispatcher.singleScheduler)
                        .map {
                            RxIMHttp.remove(accountContext)
                            IMHttp.remove(accountContext)
                            loginSucceed(data.getAccountID(), registrationId, data.uid, keyPair, signalingKey, signalPassword, password, "")
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            result(true, null, "")
                        }, {
                            ALog.e(TAG, "login error", it)
                            setTmpToken("", "")

                            RxIMHttp.remove(accountContext)
                            IMHttp.remove(accountContext)
                            AmeModuleCenter.metric(accountContext)?.loginEnd(false)

                            when (ServerCodeUtil.getNetStatusCode(it)) {
                                ServerCodeUtil.CODE_LOW_VERSION -> result(false, it, it.message
                                        ?: AppContextHolder.APP_CONTEXT.getString(R.string.login_login_failed))
                                ServerCodeUtil.CODE_SERVICE_404 -> result(false, it, AppContextHolder.APP_CONTEXT.getString(R.string.login_account_not_exist))
                                ServerCodeUtil.CODE_CONN_ERROR -> result(false, it, AppContextHolder.APP_CONTEXT.getString(R.string.login_login_failed))
                                else -> result(false, AmeLoginUnknownException(), AppContextHolder.APP_CONTEXT.getString(R.string.login_login_failed))
                            }
                        })

                return@dispatch

            } catch (e: Exception) {
                ALog.logForSecret(TAG, "login error", e)
                error = AppContextHolder.APP_CONTEXT.getString(R.string.login_password_error)
            }

            if (error.isNotEmpty()) {
                AmeDispatcher.mainThread.dispatch {
                    ALog.d(TAG, "login fail, $error")
                    result(false, AmeLoginErrorPasswordException(), error)
                }
            }
        }
    }

    /**
     * destroy account
     */
    fun unregister(uid: String, password: String, result: (succeed: Boolean, error: String) -> Unit) {
        val accountData = accountHistory.getAccount(uid)
        if (accountData == null) {
            result.invoke(false, AppContextHolder.APP_CONTEXT.getString(R.string.login_account_not_exist))
            return
        } else {
            AmeDispatcher.io.dispatch {
                try {
                    val priKeyArray = accountHistory.getPrivateKeyWithPassword(accountData, password)
                            ?: throw BCMPrivateKeyUtils.ErrorPinException()
                    val priKey = Curve.decodePrivatePoint(priKeyArray)

                    val sign = BCMPrivateKeyUtils.sign(priKey, uid)
                    ALog.logForSecret(TAG, "unregister uid: $uid, sign: $sign")

                    AmeLoginCore.unregister(getAccountContext(uid), URLEncoder.encode(sign))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnNext {
                                accountHistory.deleteAccount(uid)
                            }
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                result(true, AppContextHolder.APP_CONTEXT.getString(R.string.login_destroy_other_client_response_fail))

                            }, {
                                ALog.logForSecret(TAG, "unregister error", it)
                                when (ServerCodeUtil.getNetStatusCode(it)) {
                                    ServerCodeUtil.CODE_LOW_VERSION -> {
                                        result(false, it.message
                                                ?: AppContextHolder.APP_CONTEXT.getString(R.string.login_destroy_other_client_response_fail))
                                    }
                                    RESPONSE_NOT_FOUND -> {
                                        result(false, AppContextHolder.APP_CONTEXT.getString(R.string.login_account_not_exist))
                                    }
                                    else -> {
                                        result(false, AppContextHolder.APP_CONTEXT.getString(R.string.login_destroy_other_client_response_fail))
                                    }
                                }
                            })

                } catch (e: Exception) {
                    ALog.logForSecret(TAG, "unregister error", e)
                    result(false, AppContextHolder.APP_CONTEXT.getString(R.string.login_password_error))
                }
            }
        }
    }

    /**
     * query nonce for account register
     */
    fun queryChallengeTarget(keypair: ECKeyPair, result: (target: String?) -> Unit) {
        requestChallengeTarget(keypair) {
            AmeDispatcher.mainThread.dispatch {
                result(it)
            }
        }
    }

    /**
     * calc challenge or register
     */
    fun calcChallengeRegister(keypair: ECKeyPair, result: (target: Long, hash: String, clientNotice: Long, isCalculate: Boolean) -> Unit) {

        AmeDispatcher.io.dispatch {
            registerChallenge(keypair) { target, hash, clientNotice, isCalculate ->
                AmeDispatcher.mainThread.dispatch {
                    result(target, hash, clientNotice, isCalculate)
                }

            }
        }

    }

    /**
     * register account
     */
    fun register(nonce: Long, keyPair: ECKeyPair, password: String, passwordHint: String, name: String, result: (succeed: Boolean) -> Unit) {

        AmeDispatcher.io.dispatch {
            ALog.i(TAG, "register start")
            val uid = BCMPrivateKeyUtils.provideUid(keyPair.publicKey.serialize())
            val signalingKey = BCMPrivateKeyUtils.getSecret(52)
            val signalPassword = BCMPrivateKeyUtils.getSecret(18)

            val registrationId = KeyHelper.generateRegistrationId(false)

            val sign = BCMPrivateKeyUtils.sign(keyPair.privateKey, signalPassword)
            val publicKey = Base64.encodeBytes(keyPair.publicKey.serialize())

            setTmpToken(uid, signalPassword)
            mRegisterNick = name

            val accountParams = AmeAccountParams(signalingKey,
                    publicKey,
                    true,
                    registrationId,
                    "", org.whispersystems.signalservice.internal.util.Base64.encodeBytes(getDeviceName().toByteArray()), true, true)
            val regParams = AmeRegisterParams(sign, nonce, accountParams)

            val accountContext = getAccountContext(uid)
            AmeLoginCore.register(accountContext, regParams)
                    .subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AmeDispatcher.singleScheduler)
                    .map {
                        RxIMHttp.remove(accountContext)
                        IMHttp.remove(accountContext)
                        registerSucceed(registrationId, uid, keyPair, signalingKey, signalPassword, password, passwordHint)
                    }
                    .doOnError {
                        quit(getAccountContext(uid), false)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        result(true)
                    }, {
                        RxIMHttp.remove(accountContext)
                        IMHttp.remove(accountContext)
                        ALog.logForSecret(TAG, "register error", it)
                        setTmpToken("", "")
                        result(false)
                    })
        }

    }

    private fun registerSucceed(registrationId: Int, uid: String, ecKeyPair: ECKeyPair, signalKey: String, signalPassword: String, password: String, passwordHint: String) {
        loginSucceed(uid, registrationId, uid, ecKeyPair, signalKey, signalPassword, password, passwordHint, true)
    }

    private fun loginSucceed(accountId: String, registrationId: Int, uid: String, ecKeyPair: ECKeyPair, signalKey: String,
                             signalPassword: String, password: String, passwordHint: String,
                             isRegister: Boolean = false) {

        setTmpToken("", "")
        AmePushProcess.reset()

        saveAccountData(accountId, registrationId, uid, ecKeyPair, signalKey, signalPassword, password, passwordHint)

        val accountContext = getAccountContext(uid)
        initCreatePhrase(accountContext, ecKeyPair)

        AmeModuleCenter.onLoginSucceed(accountContext)

        if (!DatabaseFactory.isDatabaseExist(accountContext, AppContextHolder.APP_CONTEXT) || isRegister || (TextSecurePreferences.isDatabaseMigrated(accountContext) && TextSecurePreferences.getMigrateFailedCount(accountContext) < 3)) {
            TextSecurePreferences.setHasDatabaseMigrated(accountContext)
            initAfterLoginSuccess(getAccountContext(uid), ecKeyPair, password)
        } else {
            loginTempData = Triple(uid, ecKeyPair, password)
        }
    }

    fun initAfterLoginSuccess(accountContext: AccountContext) {
        val tempData = loginTempData ?: throw RuntimeException("Login temp data is null!")
        initAfterLoginSuccess(accountContext, tempData.second, tempData.third)
    }

    private fun initAfterLoginSuccess(accountContext: AccountContext, ecKeyPair: ECKeyPair, password: String) {
        ALog.logForSecret(TAG, "initAfterLoginSuccess uid: ${accountContext.uid}")

        initIdentityKey(accountContext, ecKeyPair)

        PushUtil.registerPush(accountContext)

        handleLocalLogin(accountContext, AppContextHolder.APP_CONTEXT)

        loginTempData = null
    }

    @Throws(Exception::class)
    private fun initIdentityKey(accountContext: AccountContext, ecKeyPair: ECKeyPair) {
        try {
            val identityKeyPair = IdentityKeyPair(IdentityKey(ecKeyPair.publicKey), ecKeyPair.privateKey)
            val records = PreKeyUtil.generatePreKeys(AppContextHolder.APP_CONTEXT, accountContext)
            val signedPreKey = PreKeyUtil.generateSignedPreKey(AppContextHolder.APP_CONTEXT, accountContext, identityKeyPair, true)
            if (!AmeLoginCore.refreshPreKeys(accountContext, identityKeyPair.publicKey, signedPreKey, records)) {
                throw Exception("pre key upload failed, uid: ${accountContext.uid}")
            }

            Repository.getIdentityRepo(accountContext)?.saveIdentity(accountContext.uid, identityKeyPair.publicKey, IdentityRepo.VerifiedStatus.VERIFIED,
                    true, System.currentTimeMillis(), true)
        } catch (e: Exception) {
            ALog.logForSecret(TAG, "loginSucceed generate prekey or prekey upload failed, ${e.message}", e)
            quit(accountContext, false)
            throw e
        }
    }

    @SuppressLint("CheckResult")
    fun refreshMySupportFeature(accountContext: AccountContext) {
        AmeLoginCore.uploadMyFunctionSupport(accountContext, support)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnError {
                    ALog.e(TAG, "uploadMyFunctionSupport failed", it)
                }
                .subscribe {
                    ALog.i(TAG, "uploadMyFunctionSupport succeed")
                }
    }

    /**
     * save login account state
     */
    private fun saveAccountData(accountId: String, registrationId: Int, uid: String,
                                ecKeyPair: ECKeyPair,
                                signalKey: String,
                                signalPassword: String,
                                password: String,
                                passwordHint: String) {

        val gcmToken = if (PlayServicesUtil.getPlayServicesStatus(AppContextHolder.APP_CONTEXT) == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
            FcmUtil.getToken()
        } else {
            Optional.absent()
        }

        this.gcmToken = if(gcmToken.isPresent) {
            gcmToken.get()
        } else {
            ""
        }

        val encryptedPrivateKeyString = BCMPrivateKeyUtils.encryptPrivateKey(ecKeyPair.privateKey.serialize(), password.toByteArray())
        val pubKey = Base64.encodeBytes(ecKeyPair.publicKey.serialize())

        var accountData = accountHistory.getAccount(accountId)
        if (null == accountData) {
            accountData = AmeAccountData()
            accountData.uid = uid
            accountData.version = AmeAccountData.V4
            accountData.genKeyTime = System.currentTimeMillis() / 1000
            accountData.name = mRegisterNick ?: ""
        }

        if (passwordHint.isNotBlank()) {
            accountData.passwordHint = passwordHint
        }
        accountData.uid = uid
        accountData.lastLoginTime = System.currentTimeMillis() / 1000
        accountData.mode = AmeAccountData.ACCOUNT_MODE_NORMAL
        accountData.priKey = encryptedPrivateKeyString
        accountData.pubKey = pubKey
        accountData.registrationId = registrationId
        accountData.gcmDisabled = !gcmToken.isPresent
        accountData.signalPassword = signalPassword
        accountData.signalingKey = signalKey
        accountData.signedPreKeyRegistered = true

        accountHistory.saveAccount(accountData)
        accountHistory.setMajorLoginAccountUid(accountData.uid)
    }

    private fun initCreatePhrase(accountContext: AccountContext, ecKeyPair: ECKeyPair) {
        IdentityKeyUtil.rebuildIdentityKeys(accountContext, ecKeyPair.privateKey.serialize())

        if (MasterSecretUtil.isPassphraseInitialized(accountContext)) {
            return
        }
        try {
            val passphrase = MasterSecretUtil.UNENCRYPTED_PASSPHRASE
            val masterSecret = MasterSecretUtil.generateMasterSecret(accountContext, passphrase)
            MasterSecretUtil.generateAsymmetricMasterSecret(accountContext, masterSecret)
            TextSecurePreferences.setReadReceiptsEnabled(accountContext, true)

        } catch (e: Exception) {
            ALog.logForSecret(TAG, "initCreatePhrase", e)
        }
    }

    fun checkAccountBackupValid(qrString: String): Pair<String?, String?> {
        var uid: String? = null
        var error: String? = null
        try {
            val backupData = QRExport.parseAccountDataFromString(qrString)
            if (backupData is QRExport.ExportModelV4) {
                val accountData = backupData.toAccountData()
                uid = accountData.uid
            }

        } catch (jse: JsonSyntaxException) {
            ALog.logForSecret(TAG, "checkAccountBackupValid error", jse)
        } catch (ie: IllegalArgumentException) {
            ALog.logForSecret(TAG, "checkAccountBackupValid error", ie)
        } catch (e: Throwable) {
            ALog.logForSecret(TAG, "checkAccountBackupValid error", e)
            error = e.message ?: error
        }
        if (uid.isNullOrEmpty()) {
            error = AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description)
        }
        return Pair(uid, error)
    }

    /**
     * parse account qr code（first: uid, second: error）
     */
    @SuppressLint("CheckResult")
    private fun saveBackupFromExportModel(qrString: String, replace: Boolean = false, callback: (accountId: String?, isExist: Boolean, error: String?) -> Unit) {
        ALog.d(TAG, "saveBackupFromExportModel qrString: $qrString, replace: $replace")

        class ExportResult(val accountId: String?, val isExist: Boolean, var error: String?)

        Observable.create<ExportResult> {

            var uid: String? = null
            var error: String? = null
            var exist = false
            try {
                val backupData = QRExport.parseAccountDataFromString(qrString)
                if (backupData is QRExport.ExportModelV4) {
                    val accountData = backupData.toAccountData()
                    accountData.version = AmeAccountData.V4
                    uid = accountData.uid
                    if (!accountHistory.saveAccount(accountData, replace) && !replace) {
                        exist = true
                    }
                }
            } catch (jse: JsonSyntaxException) {
                ALog.logForSecret(TAG, "saveBackupFromExportModelWithWarning error", jse)
            } catch (ie: IllegalArgumentException) {
                ALog.logForSecret(TAG, "saveBackupFromExportModelWithWarning error", ie)
            } catch (e: Throwable) {
                ALog.logForSecret(TAG, "saveBackupFromExportModelWithWarning error", e)
                error = e.message ?: error
            }
            if (uid.isNullOrEmpty()) {
                error = AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description)
            }
            it.onNext(ExportResult(uid, exist, error))
            it.onComplete()

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    callback(it.accountId, it.isExist, it.error)
                }, {
                    ALog.e(TAG, "saveBackupFromExportModel error", it)
                    callback(null, false, AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description))
                })

    }

    /**
     * parse and save account qr code
     * qrString
     * return uid
     */
    fun saveBackupFromExportModelWithWarning(qrString: String, replace: Boolean = false, callback: (accountId: String?) -> Unit) {

        saveBackupFromExportModel(qrString, replace) { accountId, isExist, error ->
            if (accountId.isNullOrEmpty()) {
                AmeCenterPopup.instance().newBuilder()
                        .withCancelable(false)
                        .withTitle(getString(R.string.login_account_unsupported_qr_warning_title))
                        .withContent(getString(R.string.login_account_unsupported_qr_warning_description))
                        .withOkTitle(getString(R.string.common_popup_ok)).show(AmeAppLifecycle.current() as? FragmentActivity)
            } else {
                if (isExist) {
                    ToastUtil.show(AppContextHolder.APP_CONTEXT, AppContextHolder.APP_CONTEXT.getString(R.string.login_account_qr_exist_notice))
                }
            }
            callback(accountId)
        }
    }

    fun accountDir(uid: String): String {
        var path = AppContextHolder.APP_CONTEXT.filesDir.absolutePath
        if (uid.isNotEmpty()) {
            path += "/$uid"

            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        return path
    }

    /**
     * query nonce
     */
    private fun requestChallenge(uid: String): Observable<Pair<String, ChallengeResult>> {
        return ProxyRetryChallenge.request(uid)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .map {
                    Pair(uid, it)
                }
    }

    @SuppressLint("CheckResult")
    private fun requestChallengeTarget(keypair: ECKeyPair, result: (target: String?) -> Unit) {

        Observable.create(ObservableOnSubscribe<String> {
            it.onNext(BCMPrivateKeyUtils.provideUid(keypair.publicKey.serialize()))
            it.onComplete()
        })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap {
                    setTmpToken(it, "")
                    requestChallenge(it)
                }.observeOn(Schedulers.io()).subscribe({

                    val difficulty = it.second.difficulty
                    if (difficulty > 32 || difficulty < 0) {
                        requestChallengeTarget(keypair, result)
                    } else {
                        mPrefixUid = it.first
                        mChallengeResult = it.second

                        mMask = (Math.pow(2.0, 32.0 - difficulty) - 1).toLong().xor(0xffffffff)
                        val target = Base58.encode(ByteArray(4).apply {
                            ByteUtil.longTo4ByteArray(this, 0, it.second.nonce and mMask)
                        })
                        result(target)
                    }
                }, {
                    result(null)
                    ALog.e(TAG, "requestChallengeTarget fail", it)
                })
    }

    private fun registerChallenge(keypair: ECKeyPair, result: (target: Long, hash: String, clientNotice: Long, isCalculate: Boolean) -> Unit) {

        val prefixUid = mPrefixUid
        val challengeResult = mChallengeResult
        if (prefixUid != null && challengeResult != null) {
            val serviceNonce = challengeResult.nonce
            val difficulty = challengeResult.difficulty
            var prefix: ByteArray = "BCM".toByteArray()
            prefix += prefixUid.toByteArray()
            prefix += ByteArray(4).apply {
                ByteUtil.longTo4ByteArray(this, 0, serviceNonce)
            }
            prefix += ByteUtil.intToByteArray(difficulty)

            var hit = false
            var clientNotice = 0L

            val p2 = 0xffffffffL
            val target = 0x80000000L.shr(difficulty - 1)
            val tempArray = ByteArray(8)
            val clientNoticeBytes = ByteArray(4)
            var showLotteryResult = true
            val mTimer = Timer()
            while (clientNotice < p2) {
                ByteUtil.longTo4ByteArray(clientNoticeBytes, 0, clientNotice)
                val hash = EncryptUtils.computeSHA256(EncryptUtils.computeSHA256(prefix + clientNoticeBytes))

                System.arraycopy(hash.sliceArray(IntRange(0, 3)), 0, tempArray, 4, 4)

                val buffer = ByteBuffer.wrap(tempArray).long

                if (buffer in 0 until target) {
                    val bytes = ByteArray(4)
                    ByteUtil.longTo4ByteArray(bytes, 0, (buffer xor serviceNonce) and mMask)
                    result(target, Base58.encode(bytes) + Base58.encode(hash), clientNotice, false)
                    ALog.d(TAG, "registerChallenge success hash = $hash")
                    hit = true
                    break
                }
                if (showLotteryResult) {
                    result(target, Base58.encode(hash), clientNotice, true)
                    showLotteryResult = false
                    mTimer.schedule(object : TimerTask() {
                        override fun run() {
                            showLotteryResult = true
                        }
                    }, 100)
                }

                clientNotice += 1
            }

            if (!hit) {
                ALog.d(TAG, "registerChallenge fail,retry")
                requestChallengeTarget(keypair) {
                    registerChallenge(keypair, result)
                }
            }
        }
    }

    fun mySupport(): BcmFeatureSupport {
        return support
    }

    fun checkPassword(accountContext: AccountContext, password: String, result: (right: Boolean) -> Unit) {
        AmeDispatcher.io.dispatch {
            var right = false
            try {
                val account = getAccount(accountContext.uid)
                if (null != account) {
                    right = accountHistory.getPrivateKeyWithPassword(account, password) != null
                }
            } catch (e: Exception) {
                ALog.e(TAG, "CheckPassword error", e)
            }

            AmeDispatcher.mainThread.dispatch {
                result(right)
            }
        }
    }

    fun refreshPrekeys(accountContext: AccountContext) {
        AmeModuleCenter.accountJobMgr(accountContext)?.add(RefreshPreKeysJob(AppContextHolder.APP_CONTEXT, accountContext))
    }

    fun rotateSignedPreKey(accountContext: AccountContext) {
        AmeModuleCenter.accountJobMgr(accountContext)?.add(RotateSignedPreKeyJob(AppContextHolder.APP_CONTEXT, accountContext))
    }

    @SuppressLint("CheckResult")
    fun updateAllowReceiveStrangers(accountContext: AccountContext, allow: Boolean, callback: ((succeed: Boolean) -> Unit)?) {
        AmeLoginCore.updateAllowReceiveStrangers(accountContext, allow)
                .observeOn(AmeDispatcher.mainScheduler)
                .doOnError {
                    callback?.invoke(false)
                }
                .subscribe {
                    callback?.invoke(true)
                }
    }

    fun setGcmToken(token: String) {
        this.gcmToken = token
    }

    fun getGcmToken(): String {
        return gcmToken
    }

    fun refreshOfflineToken() {
        AmeDispatcher.io.dispatch {
            accountHistory.getAllLoginContext().forEach {
                PushUtil.registerPush(it)
            }
        }
    }
}