package com.bcm.messenger.common.api

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder

/**
 *
 * Created by wjh on 2019-09-21
 */
class BcmJSInterface : NotGuard {

    interface JSActionListener {
        fun onRoute(api: String, json: String): Boolean
    }

    private val TAG = "BcmJSInterface"

    private var mListener: JSActionListener? = null

    fun setListener(listener: JSActionListener) {
        mListener = listener
    }

    /**
     * 
     */
    @JavascriptInterface
    fun versionName(): String {
        return AppUtil.getVersionName(AppContextHolder.APP_CONTEXT)
    }

    /**
     * 
     */
    fun versionCode(): Int {
        return AppUtil.getVersionCode(AppContextHolder.APP_CONTEXT)
    }

    /**
     * 
     */
    @JavascriptInterface
    fun route(api: String, json: String): Boolean {
        ALog.d(TAG, "route api: $api, json: $json")
        try {
            if (mListener != null) {
                return mListener?.onRoute(api, json) ?: false
            }
            when(api) {
                "joingroup" -> {
                    val shareContent = AmeGroupMessage.GroupShareContent.fromClipboard(json)
                    if (shareContent != null) {
                        val schemeUri = shareContent.toBcmSchemeUrl()
                        if (schemeUri.isNotEmpty()) {
                            AppContextHolder.APP_CONTEXT.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(schemeUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
                "addfriend" -> {
                    val qrData = Recipient.RecipientQR.fromJson(json)
                    if (qrData != null) {
                        val schemeUri = qrData.toSchemeUri()
                        if (schemeUri.isNotEmpty()) {
                            AppContextHolder.APP_CONTEXT.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(schemeUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
            }
            return true
        }catch (ex: Exception) {
            ALog.e(TAG, "route api: $api fail", ex)
        }
        return false
    }


}