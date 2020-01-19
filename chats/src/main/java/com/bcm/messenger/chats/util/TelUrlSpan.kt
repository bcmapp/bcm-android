package com.bcm.messenger.chats.util

import android.content.Intent
import android.net.Uri
import android.text.style.ClickableSpan
import android.view.View
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.AppContextHolder

/**
 *
 * Created by Kin on 2018/8/28
 */
class TelUrlSpan(private val longClickCheck: LongClickCheck, private val uri: Uri) : ClickableSpan() {

    override fun onClick(widget: View?) {
        if (longClickCheck.isLongClick) {
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_DIAL, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppContextHolder.APP_CONTEXT.startActivity(intent)
        }catch (ex: Exception) {
            ALog.e("TelUrlSpan", "goto dial fail", ex)
        }
    }
}