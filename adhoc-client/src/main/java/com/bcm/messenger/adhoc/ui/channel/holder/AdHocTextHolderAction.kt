package com.bcm.messenger.adhoc.ui.channel.holder

import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.logger.ALog

/**
 *
 * Created by wjh on 2019/7/27
 */
class AdHocTextHolderAction(private val accountContext: AccountContext) : BaseHolderAction<EmojiTextView>() {

    override fun bindData(message: AdHocMessageDetail, body: EmojiTextView, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        val text = textFromMessage(message)
        ALog.i("AdHoc", "bindData text: $text")
        if (!message.sendByMe) {
            if (message.getMessageBody()?.type == AmeGroupMessage.NONSUPPORT){
                body.setTextColor(body.context.getColorCompat(R.color.common_color_A8A8A8))
            } else {
                body.setTextColor(body.context.getColorCompat(R.color.common_color_black))
            }
        } else {
            body.setTextColor(body.context.getColorCompat(R.color.common_color_white))
        }

        AdHocChatViewHolder.interceptTextLink(body, message.sendByMe, text)
    }

    override fun resend(message: AdHocMessageDetail) {
        AdHocMessageLogic.get(accountContext).resend(message)
    }

    private fun textFromMessage(message: AdHocMessageDetail): String {
        val data = message.getMessageBody()?.content as? AmeGroupMessage.TextContent
        return data?.text ?: ((message.getMessageBody()?.content as? AmeGroupMessage.LinkContent)?.url ?: "")
    }
}