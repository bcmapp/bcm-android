package com.bcm.messenger.chats.util

import android.content.Context
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.route.api.BcmRouter

/**
 * Created by zjl on 2018/7/2.
 */
class LinkUrlSpan(val context: Context, val url: String, val isOutgoing: Boolean) : ClickableSpan() {

    override fun onClick(widget: View?) {
        val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
        contactProvider.discernLink(context, url)
    }

    override fun updateDrawState(ds: TextPaint?) {
        ds?.let {
            it.color = if (isOutgoing) {
                AppUtil.getColor(context.resources, R.color.common_color_white)
            } else {
                AppUtil.getColor(context.resources, R.color.common_color_379BFF)
            }
            it.isUnderlineText = true
        }
    }
}