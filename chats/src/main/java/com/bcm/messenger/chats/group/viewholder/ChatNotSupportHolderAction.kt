package com.bcm.messenger.chats.group.viewholder

import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.getColorCompat

/**
 * 文本消息行为处理
 * Created by wjh on 2018/10/23
 */
open class ChatNotSupportHolderAction : BaseChatHolderAction<EmojiTextView>() {

    override fun bindData(message: AmeGroupMessageDetail, body: EmojiTextView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {

        val text = textFromMessage(message)
        if (text.isEmpty()) {
            return
        }

        if (!message.isSendByMe) {
            if (message.message.type == AmeGroupMessage.NONSUPPORT) {
                body.setTextColor(body.context.getColorCompat(R.color.common_color_A8A8A8))
            } else {
                body.setTextColor(body.context.getColorCompat(R.color.common_color_black))
            }
        } else {
            body.setTextColor(body.context.getColorCompat(R.color.common_color_white))
        }

        ChatViewHolder.interceptMessageText(body, message, text)

    }

    private fun textFromMessage(record: AmeGroupMessageDetail): String {
        val data = record.message?.content as? AmeGroupMessage.TextContent
        return if (null != data) {
            data.text
        } else {
            (record.message?.content as? AmeGroupMessage.LinkContent)?.url ?: ""
        }
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {

    }

}