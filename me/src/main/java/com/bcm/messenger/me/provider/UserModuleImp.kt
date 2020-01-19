package com.bcm.messenger.me.provider

import android.annotation.SuppressLint
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
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.server.IServerConnectForceLogoutListener
import com.bcm.messenger.common.server.KickEvent
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.logic.FeedbackReport
import com.bcm.messenger.me.ui.keybox.SwitchAccount
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
import io.reactivex.schedulers.Schedulers
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.io.File
import java.util.*

/**
 * Created by wjh on 2018/7/3
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_USER_BASE)
class UserModuleImp : IUserModule
        , IServerConnectForceLogoutListener
        , AppForeground.IForegroundEvent {
    private val TAG = "UserProviderImp"

    private var kickEvent: AccountKickedEvent? = null
    private lateinit var noteLogic: AmeNoteLogic

    private lateinit var accountContext: AccountContext
    override val context: AccountContext
        get() = accountContext

    override fun setContext(context: AccountContext) {
        this.accountContext = context
    }

    fun getNote(): AmeNoteLogic {
        return noteLogic
    }

    override fun initModule() {
        noteLogic = AmeNoteLogic(accountContext)
        AmeModuleCenter.serverDaemon(accountContext).setForceLogoutListener(this)
        AppForeground.listener.addListener(this)

        noteLogic.refreshCurrentUser()
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
                AccountKickedEvent(accountContext, AccountKickedEvent.TYPE_EXCEPTION_LOGIN, deviceInfo)
            }
            KickEvent.ACCOUNT_GONE -> {
                AccountKickedEvent(accountContext, AccountKickedEvent.TYPE_ACCOUNT_GONE)
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
                    //todo multi device user profile sync
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
                    //todo multi device user profile sync
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
        AmeLoginLogic.getAccount(recipient.address.serialize())?.apply {
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
        AmeLoginLogic.getAccount(recipient.address.serialize())?.apply {
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

        val account = AmeLoginLogic.getAccount(accountContext.uid)
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
        var phone = AmeLoginLogic.getAccount(accountContext.uid)?.phone ?: return null
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
        val account = AmeLoginLogic.getAccount(accountContext.uid)
        if (null != account) {
            val result = AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(account, oldPassword)
            if (null != result) {
                return result
            }
        }
        throw Exception("login state failed")
    }

    override fun gotoDataNote(context: Context, accountContext: AccountContext) {
        if (noteLogic.isLocked()) {
            val intent = Intent(context, AmeNoteUnlockActivity::class.java)
            context.startBcmActivity(accountContext, intent)
        } else {
            val intent = Intent(context, AmeNoteActivity::class.java)
            context.startBcmActivity(accountContext, intent)
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
            val account = AmeLoginLogic.getAccount(accountContext.uid) ?: return null
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

    }

    override fun doForLogout() {

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
        if (event.accountContext == accountContext) {
            ALog.i(TAG, "handleAccountExceptionLogout 2 ${event.type}")
            try {
                when (event.type) {
                    AccountKickedEvent.TYPE_EXPIRE -> AccountForceLogout.handleTokenExpire(accountContext, activity)
                    AccountKickedEvent.TYPE_EXCEPTION_LOGIN -> AccountForceLogout.handleForceLogout(accountContext, event.data)
                    AccountKickedEvent.TYPE_ACCOUNT_GONE -> AccountForceLogout.handleAccountGone(accountContext, activity)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "handleAccountExceptionLogout error", ex)
            }
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

    override fun clearAccountPin() {
        AmePinLogic.clearAccountPin()
    }

    override fun majorHasPin(): Boolean {
        return AmePinLogic.majorHasPin()
    }

    override fun anyAccountHasPin(): Boolean {
        return AmePinLogic.anyAccountHasPin()
    }

    override fun logoutMenu() {
        val activity = AmeAppLifecycle.current() ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        SwitchAccount.switchAccount(accountContext, activity, Recipient.login(accountContext))
    }
}