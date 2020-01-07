package com.bcm.messenger.me.provider

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.server.IServerConnectForceLogoutListener
import com.bcm.messenger.common.server.KickEvent
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.login.jobs.MultiDeviceProfileKeyUpdateJob
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.logic.FeedbackReport
import com.bcm.messenger.me.ui.keybox.SwitchAccountAdapter
import com.bcm.messenger.me.ui.note.AmeNoteActivity
import com.bcm.messenger.me.ui.note.AmeNoteUnlockActivity
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.logger.AmeLogConfig
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by wjh on 2018/7/3
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_USER_BASE)
class UserModuleImp : IUserModule
        , IServerConnectForceLogoutListener
        , AppForeground.IForegroundEvent {
    private val TAG = "UserProviderImp"

    private var expireDispose: Disposable? = null
    private var kickOutDispose: Disposable? = null

    private var kickEvent: AccountKickedEvent? = null

    private lateinit var accountContext: AccountContext
    override val context: AccountContext
        get() = accountContext

    override fun setContext(context: AccountContext) {
        this.accountContext = context
    }


    override fun initModule() {
        AmeNoteLogic.getInstance().refreshCurrentUser()
        AmeModuleCenter.serverDaemon(accountContext).setForceLogoutListener(this)
        AppForeground.listener.addListener(this)
        AmePinLogic.initLogic()
    }

    override fun uninitModule() {
        AmeModuleCenter.serverDaemon(accountContext).setForceLogoutListener(null)
        AppForeground.listener.removeListener(this)
    }

    override fun onClientForceLogout(accountContext: AccountContext, info: String?, type: KickEvent) {
        if (accountContext.uid != this.accountContext.uid) {
            return
        }
        val event = when (type) {
            KickEvent.OTHER_LOGIN -> {
                val deviceInfo = try {
                    if (info == null) {
                        null
                    } else {
                        String(org.whispersystems.signalservice.internal.util.Base64.decode(info))
                    }
                } catch (ex: Exception) {
                    ALog.e("ForceLogout", "onClientForceLogout fail", ex)
                    null
                }
                AccountKickedEvent(AccountKickedEvent.TYPE_EXCEPTION_LOGIN, deviceInfo)
            }
            KickEvent.ACCOUNT_GONE -> {
                AccountKickedEvent(AccountKickedEvent.TYPE_ACCOUNT_GONE)
            }
        }

        ALog.i(TAG, "onClientAccountDisableEvent: ${event.type}, lastEvent: ${event.type}")

        AmeDispatcher.mainThread.dispatch {
            if (AppForeground.foreground()) {
                forceLogout(event)
            } else {
                if (kickEvent?.type ?: 0 < event.type) {
                    kickEvent = event
                }
            }
        }
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        val kick = kickEvent
        if (isForeground && kick != null) {
            kickEvent = null
            forceLogout(kick)
        }
    }

    override fun checkBackupAccountValid(qrString: String): Pair<String?, String?> {
        return AmeLoginLogic.checkAccountBackupValid(qrString)
    }

    override fun showImportAccountWarning(context: Context, dissmissCallback: (() -> Unit)?) {
        AmeCenterPopup.instance().newBuilder()
                .withCancelable(false)
                .withContent(context.getString(R.string.me_account_import_qr_warning_description))
                .withOkTitle(context.getString(R.string.common_popup_ok))
                .withDismissListener {
                    dissmissCallback?.invoke()
                }
                .show(AmeAppLifecycle.current() as? FragmentActivity)
    }

    @SuppressLint("CheckResult")
    override fun updateNameProfile(recipient: Recipient, name: String, callback: (success: Boolean) -> Unit) {
        if (recipient.isLogin) {
            AmeModuleCenter.contact(accountContext)?.uploadBcmNick(AppContextHolder.APP_CONTEXT, recipient, name) { success ->
                if (success) {
                    saveAccount(recipient, name, null)
                    AmeModuleCenter.accountJobMgr(accountContext)?.add(MultiDeviceProfileKeyUpdateJob(AppContextHolder.APP_CONTEXT))
                }
                callback.invoke(success)
            }

        } else {
            Observable.create<Boolean> {
                val settings = recipient.resolve().settings
                if (settings.localName != name) {
                    Repository.getRecipientRepo(accountContext)?.setLocalProfile(recipient, name, settings.localAvatar)
                    it.onNext(true)
                } else {
                    it.onNext(false)
                }
                it.onComplete()

            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        if (result) {
                            AmeModuleCenter.contact(accountContext)?.handleFriendPropertyChanged(recipient.address.serialize()) {
                                callback(result)
                            }
                        } else {
                            callback(true)
                        }
                    }, {
                        ALog.e(TAG, "updateNameProfile error", it)
                        callback(false)
                    })

        }
    }

    @SuppressLint("CheckResult")
    override fun updateAvatarProfile(recipient: Recipient, avatarBitmap: Bitmap?, callback: (success: Boolean) -> Unit) {
        if (recipient.isLogin) {
            if (avatarBitmap == null) {
                callback(false)
                return
            }
            AmeModuleCenter.contact(accountContext)?.uploadBcmAvatar(AppContextHolder.APP_CONTEXT, recipient, avatarBitmap) { success ->
                if (success) {
                    saveAccount(recipient, null, recipient.privacyAvatar)
                    AmeModuleCenter.accountJobMgr(accountContext)?.add(MultiDeviceProfileKeyUpdateJob(AppContextHolder.APP_CONTEXT))
                }
                callback.invoke(success)
            }
        } else {
            Observable.create(ObservableOnSubscribe<Boolean> {
                val newAvatar = if (avatarBitmap == null) {
                    ""
                } else {
                    MediaStore.Images.Media.insertImage(AppContextHolder.APP_CONTEXT.contentResolver, avatarBitmap, recipient.address.serialize() + ".avatar", null)
                }
                Repository.getRecipientRepo(accountContext)?.setLocalProfile(recipient, recipient.localName, newAvatar)
                it.onNext(true)
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        callback.invoke(it)
                    }, {
                        callback.invoke(false)
                    })
        }
    }

    override fun saveAccount(recipient: Recipient, newName: String?, newAvatar: String?) {
        AmeLoginLogic.getMajorAccount()?.apply {
            if (newName != null) {
                name = newName
            }
            if (newAvatar != null) {
                avatar = newAvatar
            }
            AmeLoginLogic.saveAccount(this)
        }
    }

    override fun saveAccount(recipient: Recipient, newPrivacyProfile: PrivacyProfile) {
        AmeLoginLogic.getMajorAccount()?.apply {
            name = if (!newPrivacyProfile.name.isNullOrEmpty()) {
                newPrivacyProfile.name ?: ""
            } else {
                recipient.profileName ?: ""
            }
            avatar = if (!newPrivacyProfile.avatarHDUri.isNullOrEmpty()) {
                newPrivacyProfile.avatarHDUri ?: ""
            } else if (!newPrivacyProfile.avatarLDUri.isNullOrEmpty()) {
                newPrivacyProfile.avatarLDUri ?: ""
            } else {
                ""
            }
            AmeLoginLogic.saveAccount(this)
        }
    }

    override fun changePinPassword(oldPassword: String, newPassword: String): Boolean {
        val privateKeyArray = checkOldPasswordRight(oldPassword)
        val newPrivateKey = BCMPrivateKeyUtils.encryptPrivateKey(privateKeyArray, newPassword.toByteArray())

        val account = AmeLoginLogic.getMajorAccount()
        if (account == null) {
            ALog.e(TAG, "change pin without login")
            return false
        }

        account.priKey = newPrivateKey
        AmeLoginLogic.saveAccount(account)

        return true
    }

    override fun showClearHistoryConfirm(context: Context, confirmCallback: () -> Unit, cancelCallback: () -> Unit) {
        MeConfirmDialog.showForClearHistory(context, {
            confirmCallback.invoke()
        }, {
            cancelCallback.invoke()
        })
    }

    override fun checkUseDefaultPin(callback: (result: Boolean, defaultPin: String?) -> Unit) {
        var defaultPin: String? = null
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                defaultPin = getDefaultPinPassword()
                it.onNext(try {
                    if (defaultPin != null) {
                        getUserPrivateKey(defaultPin!!) != null
                    } else {
                        false
                    }
                } catch (ex: Exception) {
                    ALog.e(TAG, "checkPasswordBeforeHandle error", ex)
                    false
                })

            } finally {
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it, defaultPin)
                }, {
                    callback.invoke(false, defaultPin)
                })
    }

    override fun getDefaultPinPassword(): String? {
        var phone = AmeLoginLogic.getMajorAccount()?.phone ?: return null
        try {
            if (isPhoneEncrypted(phone)) {
                phone = decryptPhone(phone)
            }
            if (phone.length > 6) {
                return phone.substring(phone.length - 6, phone.length)
            }
        } catch (ex: Exception) {
            ALog.e("UserProviderImp", "getDefaultPinPassword fail", ex)
        }
        return null
    }

    override fun changePinPasswordAsync(activity: AppCompatActivity?, oldPassword: String, newPassword: String, callback: ((result: Boolean, cause: Throwable?) -> Unit)?) {
        val observable = Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                it.onNext(changePinPassword(oldPassword, newPassword))
            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
        observable.subscribe({
            callback?.invoke(it, null)
        }, {
            ALog.e("UserProviderImp", "changePinPasswordAsync error", it)
            callback?.invoke(false, it)
        })
    }

    @Throws(Exception::class)
    private fun checkOldPasswordRight(oldPassword: String): ByteArray {
        val account = AmeLoginLogic.getMajorAccount()
        if (null != account) {
            val result = AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(account, oldPassword)
            if (null != result) {
                return result
            }
        }
        throw Exception("login state failed")
    }

    override fun gotoDataNote(context: Context) {
        if (AmeNoteLogic.getInstance().isLocked()) {
            val intent = Intent(context, AmeNoteUnlockActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(context, AmeNoteActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun gotoBackupTutorial() {
        val locale = Locale.getDefault()
        if (locale == Locale.CHINESE || locale == Locale.CHINA || locale.language.contains("zh")) {
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WEB)
                    .putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_BACKUP_ZH_ADDRESS)
                    .navigation()
        } else {
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WEB)
                    .putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_BACKUP_EN_ADDRESS)
                    .navigation()
        }
    }

    override fun hasBackupAccount(): Boolean {
        return AmeLoginLogic.accountHistory.getBackupTime(accountContext.uid) > 0
    }

    override fun getUserPrivateKey(password: String): ByteArray? {
        try {
            val account = AmeLoginLogic.getMajorAccount() ?: return null
            return AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(account, password)
        } catch (ex: Exception) {
            return null
        }
    }

    override fun doForLogin(address: Address, profileKey: ByteArray?, profileName: String?, profileAvatar: String?) {

        val context = AppContextHolder.APP_CONTEXT
        Recipient.clearCache(context, address)
        val recipient = Recipient.from(address, false)

        var changed = false
        val name = if (!profileName.isNullOrEmpty()) {
            changed = true
            profileName
        } else {
            recipient.profileName
        }
        val avatar = if (!profileAvatar.isNullOrEmpty() && BcmFileUtils.isExist(profileAvatar)) {
            changed = true
            profileAvatar
        } else {
            recipient.profileAvatar
        }
        if (changed) {
            Repository.getRecipientRepo(accountContext)?.setProfile(recipient, profileKey, name, avatar)
        }

        AmeModuleCenter.contact(accountContext)?.fetchProfile(recipient) {}
        AmeNoteLogic.getInstance().updateUser(address.serialize())

    }

    override fun doForLogout() {
        AmeNoteLogic.getInstance().updateUser("")
    }

    override fun feedback(tag: String, description: String, screenshotList: List<String>, callback: ((result: Boolean, cause: Throwable?) -> Unit)?) {

        fun removeFiles(list: List<String>) {
            for (p in list) {
                try {
                    File(p).delete()
                } catch (ex: Exception) {
                }
            }
        }

        val screenList = ArrayList<String>()
        Observable.create(ObservableOnSubscribe<Boolean> {
            val list = File(AmeLogConfig.logDir).listFiles()?.map { f -> f.absolutePath }?.toMutableList()

            for (p in screenshotList) {
                val thumb = BitmapUtils.getImageThumbnailPath(p, 600)
                if (thumb.isNotEmpty()) {
                    screenList.add(thumb)
                }
            }

            if (screenList.isNotEmpty()) {
                list?.addAll(screenList)
            }

            val emitter = it
            val succeed = FeedbackReport.feedback(tag, description, list ?: ArrayList()) {

                removeFiles(screenList)
                emitter.onNext(it)
                emitter.onComplete()
            }

            if (!succeed) {
                removeFiles(screenList)
                it.onNext(false)
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(it, null)
                }, {
                    callback?.invoke(false, it)
                })

    }

    private fun createAESKeyPair(dhPassword: ByteArray): Pair<ByteArray, ByteArray> {
        val sha512Data = EncryptUtils.encryptSHA512(dhPassword)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(sha512Data, 0, aesKey256, 0, 32)
        System.arraycopy(sha512Data, 48, iv, 0, 16)

        return Pair(aesKey256, iv)
    }

    override fun encryptPhonePair(phone: String): Pair<String, String> {
        val keyPair = BCMPrivateKeyUtils.generateKeyPair()
        val otherPrivateKeyArray = keyPair.privateKey.serialize()

        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(accountContext)
        val myPublicKeyArray = (myKeyPair.publicKey.publicKey as DjbECPublicKey).publicKey

        val dhPassword = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(myPublicKeyArray, otherPrivateKeyArray)
        val aesKeyPair = createAESKeyPair(dhPassword)
        return Pair(Base64.encodeBytes(EncryptUtils.encryptAES(phone.toByteArray(), aesKeyPair.first, "AES/CBC/PKCS7Padding", aesKeyPair.second)),
                Base64.encodeBytes((keyPair.publicKey as DjbECPublicKey).publicKey))

    }

    override fun decryptPhone(phoneBunk: String): String {
        val pair = phoneBunk.split("-!-")
        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(accountContext)
        val dhPassword = Curve25519.getInstance(Curve25519.BEST)
                .calculateAgreement(Base64.decode(pair[1]), myKeyPair.privateKey.serialize())

        val aesKeyPair = createAESKeyPair(dhPassword)
        return String(EncryptUtils.decryptAES(Base64.decode(pair[0]), aesKeyPair.first, "AES/CBC/PKCS7Padding", aesKeyPair.second))

    }

    override fun isPhoneEncrypted(phoneBunk: String): Boolean {
        try {
            val parts = phoneBunk.split("-!-")
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                return true
            }
        } catch (ex: Exception) {

        }
        return false
    }

    override fun packEncryptedPhone(encryptedPhone: String, tempPubKey: String): String {
        return "$encryptedPhone-!-$tempPubKey"
    }

    private fun forceLogout(event: AccountKickedEvent) {
        ALog.i(TAG, "handleAccountExceptionLogout ${event.type}")
        val activity = AmeAppLifecycle.current() ?: return
        ALog.i(TAG, "handleAccountExceptionLogout 1 ${event.type}")
        if (AMELogin.isLogin) {
            ALog.i(TAG, "handleAccountExceptionLogout 2 ${event.type}")
            try {
                when (event.type) {
                    AccountKickedEvent.TYPE_EXPIRE -> handleTokenExpire(activity)
                    AccountKickedEvent.TYPE_EXCEPTION_LOGIN -> handleForceLogout(activity, event.data)
                    AccountKickedEvent.TYPE_ACCOUNT_GONE -> handleAccountGone(activity)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "handleAccountExceptionLogout error", ex)
            }
        }
    }

    private fun handleForceLogout(activity: Activity, info: String?) {
        expireDispose?.dispose()
        expireDispose = null

        if (!AMELogin.isLogin) {
            return
        }

        val uid = accountContext.uid
        ALog.i(TAG, "handleForceLogout 1")
        if (kickOutDispose == null) {
            ALog.i(TAG, "handleForceLogout 2")
            kickOutDispose = Observable.create<Recipient> {
                ALog.i(TAG, "handleForceLogout 3")
                AmeModuleCenter.login().quit(accountContext, clearHistory = false, withLogOut = false)
                it.onComplete()
            }.subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        ALog.e(TAG, "handleForceLogout", it)
                        kickOutDispose = null
                    }
                    .doOnComplete { kickOutDispose = null }
                    .subscribe()

            AmePopup.center.dismiss()
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_DESTROY)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, Address.from(accountContext, uid))
                    .putString(ARouterConstants.PARAM.PARAM_CLIENT_INFO, info)
                    .putString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID, uid)
                    .navigation(activity)
        }

    }

    private fun handleTokenExpire(activity: Activity) {
        if (!AMELogin.isLogin) {
            return
        }

        if (expireDispose == null && kickOutDispose == null) {
            expireDispose = Observable.create<Recipient> {
                ALog.i(TAG, "handleTokenExpire 1")
                if (AMELogin.isLogin) {
                    AmeModuleCenter.login().quit(accountContext, clearHistory = false, withLogOut = false)
                } else {
                    throw java.lang.Exception("not login")
                }

                it.onComplete()
            }.delaySubscription(3000, TimeUnit.MILLISECONDS, AmeDispatcher.singleScheduler)
                    .subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        ALog.e(TAG, "handleTokenExpire", it)
                        expireDispose = null
                    }
                    .doOnComplete { expireDispose = null }
                    .subscribe {
                        AmeAppLifecycle.show(activity.getString(R.string.me_logout_with_expire)) {
                            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    .navigation(activity)
                        }
                    }
        }
    }

    private fun handleAccountGone(activity: Activity) {
        val self = try {
            Recipient.login(accountContext)
        } catch (ex: Exception) {
            null
        } ?: return

        AmeDispatcher.io.dispatch {
            AmeModuleCenter.login().quit(accountContext, false)
            AmeLoginLogic.accountHistory.deleteAccount(self.address.serialize())
        }

        MeConfirmDialog.showConfirm(activity, activity.getString(R.string.me_destroy_account_confirm_title),
                activity.getString(R.string.me_destroy_account_warning_notice), activity.getString(R.string.me_destroy_account_confirm_button)) {

            AmeModuleCenter.onLogOutSucceed(accountContext)

            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .navigation(activity)
            activity.finish()
        }
    }

    override fun isPinLocked(): Boolean {
        return AmePinLogic.isLocked()
    }

    override fun showPinLock() {
        if (AppForeground.foreground()) {
            AmePinLogic.showPinLock()
        }
    }

    override fun logoutMenu() {
        val activity = AmeAppLifecycle.current() ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        SwitchAccountAdapter().switchAccount(activity, accountContext.uid, Recipient.login(accountContext))
    }
}