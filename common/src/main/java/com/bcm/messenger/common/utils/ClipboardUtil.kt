package com.bcm.messenger.common.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.provider.accountmodule.IGroupModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter

/**
 * 
 * Created by Kin on 2019/7/18
 */

object ClipboardUtil {

    private const val TAG = "ClipboardUtil"

    private lateinit var clipboardManager: ClipboardManager
    var clipboardChanged = true
        private set

    private val uidRegex = Regex("(?<=•).*?(?=•)")
    private val groupRegex = Regex("(?<=©️).*?(?=©️)")
    private val individualRegex = Regex("(?<=⊙).*?(?=⊙)")

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardChanged = true
    }

    /**
     * 
     */
    fun initClipboardUtil() {
        ALog.i(TAG, "Init clipboard util")
        clipboardManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        clipboardChanged = true
    }

    /**
     *
     */
    fun unInitClipboardUtil() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
    }

    /**
     * 
     */
    fun checkClipboard(activity: Activity) {
        ALog.i(TAG, "Start to check clipboard data")
        if (!clipboardChanged) {
            ALog.i(TAG, "Clipboard has not updated, cancel check")
            return
        }
        clipboardChanged = false
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val clipText = primaryClip.getItemAt(0).text
            if (!clipText.isNullOrBlank()) {
                var match = checkIndividual(activity, clipText)
                if (!match) {
                    match = checkGroup(activity, clipText)
                }
                if (match) {
                    clipboardManager.primaryClip = ClipData.newPlainText("text", "")
                }
            }
        }
    }

    /**
     * 
     */
    fun shareInviteText() {
        val text = AppContextHolder.APP_CONTEXT.getString(R.string.common_invite_user_message, AMESelfData.uid)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        AppContextHolder.APP_CONTEXT.startActivity(intent)
    }

    /**
     * 
     */
    private fun checkIndividual(activity: Activity, clipText: CharSequence): Boolean {
        ALog.d(TAG, "checkIndividual clipText: $clipText")
        var result: MatchResult? = null
        if (clipText.contains("bcm", true)) {
            result = uidRegex.find(clipText)
            if (result != null) {
                ALog.i(TAG, "Find BCM invite message!!")
                val id = result.value
                if (id.isNotBlank()) {
                    if (id != AMESelfData.uid) {
                        val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
                        contactProvider.openContactDataActivity(activity, Address.fromSerialized(id))
                    }
                    return true
                }
            }
        }
        result = individualRegex.find(clipText)
        if (result != null) {
            val json = result.value
            if (json.isNotBlank()) {
                val qrData = Recipient.RecipientQR.fromJson(json)
                if (qrData != null) {
                    if (qrData.uid != AMESelfData.uid) {
                        val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
                        contactProvider.openContactDataActivity(activity, Address.fromSerialized(qrData.uid), qrData.name)
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * 
     */
    private fun checkGroup(activity: Activity, clipText: CharSequence): Boolean {
        ALog.d(TAG, "checkGroup clipText: $clipText")
        val result = groupRegex.find(clipText)
        if (result != null) {
            ALog.i(TAG, "Find BCM group join message!!")
            val json = result.value
            if (json.isNotBlank()) {
                val groupShareContent = AmeGroupMessage.GroupShareContent.fromClipboard(json)
                if (groupShareContent != null && groupShareContent.groupId != 0L && !groupShareContent.shareCode.isNullOrBlank() && !groupShareContent.shareSignature.isNullOrBlank()) {
                    val groupProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_GROUP_BASE).navigationWithCast<IGroupModule>()
                    ALog.i(TAG, "checkGroup gid: ${groupShareContent.groupId}")
                    val eKey = groupShareContent.ekey
                    val eKeyByteArray = if (!eKey.isNullOrEmpty()) {
                        try {
                            eKey.base64Decode()
                        } catch(e:Throwable) {
                            null
                        }
                    } else {
                        null
                    }
                    groupProvider.doGroupJoin(activity, groupShareContent.groupId, groupShareContent.groupName, groupShareContent.groupIcon, groupShareContent.shareCode,
                            groupShareContent.shareSignature, groupShareContent.timestamp, eKeyByteArray) { success ->
                        if (!success) {
                            BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH).navigation(activity)
                        }
                    }
                    return true
                }
            }
        }
        return false
    }
}