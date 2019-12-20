package com.bcm.messenger.common.provider

import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.recipients.Recipient

/**
 * 个人相关对外接口
 * Created by ling on 2018/3/14.
 */
interface IUserModule : IAmeModule {

    /**
     * 检测二维码是否有效的账号数据
     */
    fun checkBackupAccountValid(qrString: String): Pair<String?, String?>

    /**
     * 展示导入账号密钥的警告
     */
    fun showImportAccountWarning(context: Context, dissmissCallback: (() -> Unit)? = null)

    /**
     * 展示清除历史的确认弹窗（临时）
     */
    fun showClearHistoryConfirm(context: Context, confirmCallback: () -> Unit, cancelCallback: () -> Unit)

    /**
     * 更新昵称
     */
    fun updateNameProfile(recipient: Recipient, name: String, callback: (success: Boolean) -> Unit)

    /**
     * 更新头像
     */
    fun updateAvatarProfile(recipient: Recipient, avatarBitmap: Bitmap?, callback: (success: Boolean) -> Unit)

    /**
     * 保存当前账号信息（同步）
     */
    fun saveAccount(recipient: Recipient, newName: String?, newAvatar: String?)
    /**
     * 保存当前账号信息（同步）
     */
    fun saveAccount(recipient: Recipient, newPrivacyProfile: PrivacyProfile)

    /**
     * 登录时候设置昵称和头像信息
     */
    fun doForLogin(uid: String, profileKey: ByteArray?, profileName: String?, profileAvatar: String?)

    /**
     * 注销
     */
    fun doForLogout()

    /**
     * 跳转备份说明
     */
    fun gotoBackupTutorial()

    /**
     * 是否备份过账号钥匙
     */
    fun hasBackupAccount(): Boolean

    /**
     * 检查是否使用默认pin
     */
    fun checkUseDefaultPin(callback: (result: Boolean, defaultPin: String?) -> Unit)
    /**
     * 获取用户的私钥
     */
    fun getUserPrivateKey(password: String): ByteArray?

    /**
     * 获取用户默认密码
     */
    fun getDefaultPinPassword(): String?

    /**
     * 修改用户密码(异步)
     */
    fun changePinPasswordAsync(activity: AppCompatActivity?, oldPassword: String, newPassword: String, callback: ((result: Boolean, cause: Throwable?) -> Unit)?)

    /**
     * 修改用户密码（同步）
     */
    @Throws(Exception::class)
    fun changePinPassword(oldPassword: String, newPassword: String): Boolean

    /**
     * 反馈
     */
    fun feedback(tag: String, description: String, screenshotList: List<String>, callback: ((result: Boolean, cause: Throwable?) -> Unit)? = null)

    /**
     * 加密手机号码(会抛异常)
     * Pair first: 加密的手机号， second: 临时公钥的base64
     */
    fun encryptPhonePair(phone: String): Pair<String, String>

    /**
     * 解密手机号码（会抛异常）
     */
    fun decryptPhone(phoneBunk: String): String

    /**
     * 号码是否被加密过（不会抛异常）
     */
    fun isPhoneEncrypted(phoneBunk: String): Boolean

    /**
     * 打包加密手机号到一个字段
     */
    fun packEncryptedPhone(encryptedPhone: String, tempPubKey: String): String


}