package com.bcm.messenger.contacts.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.logger.ALog
import okhttp3.Call
import java.io.File

/**
 * Created by wjh on 2019/5/8
 */
class PrivacyProfileUpgrader(val logic: BcmProfileLogic) {

    private val TAG = "PrivacyProfileUpgrader"
    private val PREF_NICK_UPGRADE = "pref_nick_upgrade_done"
    private val PREF_AVATAR_UPGRADE = "pref_avatar_upgrade_done"

    /**
     * 
     */
    fun checkNeedUpgrade(recipient: Recipient) {
        ALog.i(TAG, "checkNeedUpgrade")
        var avatarUpgrade = false
        var nickUpgrade = false
        var nameUpload = ""
        var avatarUpload = ""
        val privacyProfile = recipient.privacyProfile
        if (privacyProfile.encryptedName.isNullOrEmpty()) {
            nameUpload = recipient.profileName ?: ""
            if (nameUpload.isNotEmpty()) {
                nickUpgrade = true
            }
        }
        if (privacyProfile.encryptedAvatarLD.isNullOrEmpty() && privacyProfile.encryptedAvatarHD.isNullOrEmpty()) {
            avatarUpload = recipient.profileAvatar ?: ""
            if (avatarUpload.isNotEmpty()) {//，
                avatarUpgrade = true
            }
        }
        if (nickUpgrade && !isNickUpgradeDone()) {
            setNickUpgradeState(true)
            logic.uploadNickName(AppContextHolder.APP_CONTEXT, recipient, nameUpload) {
                ALog.i(TAG, "checkNeedUpgrade uploadNickName result: $it")
                setNickUpgradeState(it)
            }
        }
        if (avatarUpgrade && !isAvatarUpgradeDone()) {
            ALog.i(TAG, "begin avatarUpgrade")
            setAvatarUpgradeState(true)
            val url = BcmHttpApiHelper.getDownloadApi("/avatar/$avatarUpload")
            val path = getTempUpgradePath(recipient)
            downloadAvatarOld(AppContextHolder.APP_CONTEXT, url, path) { bitmap ->
                if (bitmap != null) {
                    logic.uploadAvatar(AppContextHolder.APP_CONTEXT, recipient, bitmap) {
                        ALog.i(TAG, "checkNeedUpgrade uploadAvatar result: $it")
                        setAvatarUpgradeState(it)
                    }
                }else {
                    setAvatarUpgradeState(false)
                }
            }
        }
    }

    private fun isNickUpgradeDone(): Boolean {
        return TextSecurePreferences.getBooleanPreference(logic.mAccountContext, PREF_NICK_UPGRADE, false)
    }

    private fun setNickUpgradeState(done: Boolean) {
        ALog.i(TAG, "setNickUpgradeState: $done")
        TextSecurePreferences.setBooleanPreference(logic.mAccountContext, PREF_NICK_UPGRADE, done)
    }

    private fun isAvatarUpgradeDone(): Boolean {
        return TextSecurePreferences.getBooleanPreference(logic.mAccountContext, PREF_AVATAR_UPGRADE, false)
    }

    private fun setAvatarUpgradeState(done: Boolean) {
        ALog.i(TAG, "setAvatarUpgradeState: $done")
        TextSecurePreferences.setBooleanPreference(logic.mAccountContext, PREF_AVATAR_UPGRADE, done)
    }


    /**
     * 
     */
    private fun getTempUpgradePath(recipient: Recipient): String {
        return "${recipient.address.serialize()}_${System.currentTimeMillis()}_avatarOld.jpg"
    }

    /**
     * 
     */
    private fun downloadAvatarOld(context: Context, url: String, path: String, callback: (result: Bitmap?) -> Unit) {

        AmeFileUploader.get(logic.mAccountContext).downloadFile(context, url, object : FileDownCallback(AmeFileUploader.get(logic.mAccountContext).TEMP_DIRECTORY, path) {

            override fun onError(call: Call?, e: Exception?, id: Long) {
                callback(null)
                File(AmeFileUploader.get(logic.mAccountContext).TEMP_DIRECTORY, path).delete()
            }

            override fun onResponse(response: File?, id: Long) {
                if (response == null) {
                    callback(null)
                } else {
                    val result = BitmapFactory.decodeFile(response.absolutePath)
                    callback(result)
                }
                File(AmeFileUploader.get(logic.mAccountContext).TEMP_DIRECTORY, path).delete()
            }

        })
    }
}