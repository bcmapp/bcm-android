package com.bcm.messenger.adhoc.ui.channel.holder

import com.bcm.messenger.adhoc.component.AdHocJoinView
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.GlideRequests

/**
 * Offline join Holder
 * Created by wjh on 2019/7/27
 */
class AdHocJoinHolderAction() : BaseHolderAction<AdHocJoinView>() {

    override fun bindData(message: AdHocMessageDetail, body: AdHocJoinView, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        val content = message.getMessageBody()?.content as? AmeGroupMessage.AirChatContent ?: return
        body.setData(content, message.sendByMe)
    }

    override fun resend(message: AdHocMessageDetail) {
        AdHocMessageLogic.resend(message)
    }

}