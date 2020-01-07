package com.bcm.messenger.chats.group.viewholder

import android.app.Activity
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.MapShareView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.route.api.BcmRouter

/**
 *
 * Created by wjh on 2018/10/23
 */
class ChatLocationHolderAction(accountContext: AccountContext) : BaseChatHolderAction<MapShareView>(accountContext), View.OnClickListener {

    override fun onClick(v: View) {
        val data = mMessageDetail?.message?.content as? AmeGroupMessage.LocationContent ?: return
        val pMap = Pair.create(v, ShareElements.Activity.PREVIEW_MAP)
        val compat = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as Activity, pMap).toBundle()
        BcmRouter.getInstance().get(ARouterConstants.Activity.MAP_PREVIEW)
                .putDouble(ARouterConstants.PARAM.MAP.LATITUDE, data.latitude)
                .putDouble(ARouterConstants.PARAM.MAP.LONGTITUDE, data.longtitude)
                .putInt(ARouterConstants.PARAM.MAP.MAPE_TYPE, data.mapType)
                .putString(ARouterConstants.PARAM.MAP.TITLE, data.title)
                .putString(ARouterConstants.PARAM.MAP.ADDRESS, data.address)
                .setActivityOptionsCompat(compat)
                .navigation(v.context)
    }

    override fun bindData(message: AmeGroupMessageDetail, body: MapShareView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {

        body.setOnClickListener(this)
        val data = message.message?.content as AmeGroupMessage.LocationContent
        if (message.isSendByMe) {
            body.setAppearance(R.color.common_color_white, true)
        } else {
            body.setAppearance(R.color.common_color_black, false)
        }
        body.setMap(glideRequests, data)
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendLocationMessage(messageRecord)
    }
}