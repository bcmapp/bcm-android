package com.bcm.messenger.common.provider.accountmodule

import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.recipients.Recipient

/**
 *
 * Created by ling on 2018/3/14.
 */
interface IUserModule : IAmeAccountModule {

    fun checkBackupAccountValid(qrString: String): Pair<String?, String?>

    fun showImportAccountWarning(context: Context, dissmissCallback: (() -> Unit)? = null)

    fun showClearHistoryConfirm(context: Context, confirmCallback: () -> Unit, cancelCallback: () -> Unit)

    fun updateNameProfile(recipient: Recipient, name: String, callback: (success: Boolean) -> Unit)

    fun updateAvatarProfile(recipient: Recipient, avatarBitmap: Bitmap?, callback: (success: Boolean) -> Unit)

    fun saveAccount(recipient: Recipient, newName: String?, newAvatar: String?)

    fun saveAccount(recipient: Recipient, newPrivacyProfile: PrivacyProfile)

    fun doForLogin(address: Address, profileKey: ByteArray?, profileName: String?, profileAvatar: String?)

    fun doForLogout()

    fun gotoDataNote(context: Context)

    fun gotoBackupTutorial()

    fun hasBackupAccount(): Boolean

    fun checkUseDefaultPin(callback: (result: Boolean, defaultPin: String?) -> Unit)

    fun getUserPrivateKey(password: String): ByteArray?

    fun getDefaultPinPassword(): String?

    fun changePinPasswordAsync(activity: AppCompatActivity?, oldPassword: String, newPassword: String, callback: ((result: Boolean, cause: Throwable?) -> Unit)?)

    @Throws(Exception::class)
    fun changePinPassword(oldPassword: String, newPassword: String): Boolean

    fun feedback(tag: String, description: String, screenshotList: List<String>, callback: ((result: Boolean, cause: Throwable?) -> Unit)? = null)

    /**
     * Pair first: ， second: base64
     */
    fun encryptPhonePair(phone: String): Pair<String, String>

    fun decryptPhone(phoneBunk: String): String

    fun isPhoneEncrypted(phoneBunk: String): Boolean

    /**
     *
     */
    fun packEncryptedPhone(encryptedPhone: String, tempPubKey: String): String

    /**
     * @return true app pin locked, false app unlock
     */
    fun isPinLocked(): Boolean

    /**
     *show Pin Lock
     */
    fun showPinLock()

    /**
     *  show login out menu
     */
    fun logoutMenu()
}