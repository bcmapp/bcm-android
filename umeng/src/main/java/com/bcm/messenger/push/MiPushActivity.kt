package com.bcm.messenger.push

import android.content.Intent
import android.os.Parcelable
import android.text.TextUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.push.AmeNotificationService
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.Gson
import com.umeng.message.UmengNotifyClickActivity
import com.umeng.message.entity.UMessage
import org.android.agoo.common.AgooConstants
import org.json.JSONObject

/**
 * Created by bcm.social.01 on 2018/6/28.
 */
class MiPushActivity: UmengNotifyClickActivity() {
    private val TAG = "MiPushActivity"
    override fun onMessage(p0: Intent?) {
        val body = p0?.getStringExtra(AgooConstants.MESSAGE_BODY)
        if (!TextUtils.isEmpty(body)) {
            try {
                val msg = UMessage(JSONObject(body))
                if (msg.extra != null) {
                    ALog.d(TAG, "recv push ${msg.text}")
                    val bcmData = String.format("{\"bcmdata\":%s}", msg.extra["bcmdata"])
                    val notify = Gson().fromJson(bcmData, AmePushProcess.BcmData::class.java)?.bcmdata
                    if (notify != null && (notify.contactChat != null || notify.groupChat != null || notify.friendMsg != null)) {
                        val accountContext = AmePushProcess.findAccountContext(notify.targetHash)
                        if (accountContext == null) {
                            ALog.w(TAG, "find account context fail, onMesssage return")
                            return
                        }
                        var action: Int = AmeNotificationService.ACTION_HOME
                        var parcelable: Parcelable? = null

                        when {
                            notify.contactChat != null -> {
                                notify.contactChat?.uid?.let {
                                    try {
                                        val decryptSource = BCMEncryptUtils.decryptSource(accountContext, it.toByteArray())
                                        notify.contactChat?.uid = decryptSource
                                    } catch (e: Exception) {
                                        ALog.e(TAG, "Uid decrypted failed!")
                                        return
                                    }
                                }

                                action = AmeNotificationService.ACTION_CHAT
                                parcelable = notify.contactChat
                            }
                            notify.groupChat != null -> {
                                action = AmeNotificationService.ACTION_GROUP
                                parcelable = notify.groupChat
                            }
                            notify.friendMsg != null -> {
                                action = AmeNotificationService.ACTION_FRIEND_REQ
                                parcelable = notify.friendMsg
                            }
                        }

                        val intent = Intent(this@MiPushActivity, AmeNotificationService::class.java)
                        intent.putExtra(AmeNotificationService.ACTION, action)
                        intent.putExtra(AmeNotificationService.ACTION_DATA, parcelable)
                        intent.putExtra(AmeNotificationService.ACTION_CONTEXT, accountContext)
                        startService(intent)
                        runOnUiThread { finish() }
                        return
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                ALog.e(TAG, e)
            }

            val intent = Intent(this@MiPushActivity, AmeNotificationService::class.java)
            intent.putExtra(AmeNotificationService.ACTION, AmeNotificationService.ACTION_HOME)
            startService(intent)
            ALog.e(TAG, "default message")
        }
        runOnUiThread { finish() }
    }
}