package com.bcm.messenger.chats.group.viewholder

import android.app.Activity
import android.content.Context
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ShareChannelView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.route.api.BcmRouter

/**
 *
 * Created by wjh on 2018/10/23
 */
class ChatNewChannelHolderAction() : BaseChatHolderAction<ShareChannelView>(), ShareChannelView.ChannelOnClickListener {

    override fun onClick(v: View) {
    }

    override fun bindData(message: AmeGroupMessageDetail, bodyView: ShareChannelView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {

        bodyView.setChannelClickListener(this)
        val data = message.message.content as AmeGroupMessage.NewShareChannelContent
        if (message.isSendByMe) {
            bodyView.setLinkAppearance(R.color.common_color_white, R.color.common_color_white, true)
        } else {
            bodyView.setLinkAppearance(R.color.common_color_black, R.color.common_color_379BFF, false)
        }

        bodyView.setTitleContent(data.name, data.intro)
        if (data.gid > 0) {
            val address = GroupUtil.addressFromGid(data.gid)
            if (address != null) {
                bodyView.setAvater(address)
            }
        }
        bodyView.setLink(data.channel, true)
    }

    private fun routeToGroup(context: Context, gid: Long) {
        (context as? Activity)?.finish()
        AmeDispatcher.mainThread.dispatch( {
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.CHAT_GROUP_CONVERSATION)
                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, -1L)
                    .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, gid)
                    .putBoolean(ARouterConstants.PARAM.PRIVATE_CHAT.IS_ARCHIVED_EXTRA, true)
                    .putInt(ARouterConstants.PARAM.PRIVATE_CHAT.DISTRIBUTION_TYPE_EXTRA, ThreadRepo.DistributionTypes.NEW_GROUP)
                    .navigation(AmeAppLifecycle.current())
        },250)
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        messageRecord.message.type = AmeGroupMessage.TEXT
        GroupMessageLogic.messageSender.resendTextMessage(messageRecord)
    }
}