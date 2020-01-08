package com.bcm.messenger.adhoc.ui.channel.holder

import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocAudioView
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.AppUtil

/**
 *
 * Created by wjh on 2019-08-27
 */
class AdHocAudioHolderAction(private val accountContext: AccountContext) : BaseHolderAction<AdHocAudioView>() {

    override fun bindData(message: AdHocMessageDetail, body: AdHocAudioView, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        body.setAudio(accountContext, message)
        if (message.sendByMe) {
            body.setProgressDrawableResource(R.drawable.chats_audio_send_top_progress_bg)

            body.setAudioAppearance(R.drawable.chats_audio_send_play_icon, R.drawable.chats_audio_send_pause_icon,
                    AppUtil.getColor(body.resources, R.color.chats_audio_send_decoration_color),
                    AppUtil.getColor(body.resources, R.color.common_color_white))
        }else {
            body.setProgressDrawableResource(R.drawable.chats_audio_receive_top_progress_bg)
            body.setAudioAppearance(R.drawable.chats_audio_receive_play_icon, R.drawable.chats_audio_receive_pause_icon,
                    AppUtil.getColor(body.resources, R.color.chats_audio_receive_decoration_color),
                    AppUtil.getColor(body.resources, R.color.common_color_black))
        }
    }

    override fun unBind() {
        mBaseView?.cleanup()
    }


    override fun resend(message: AdHocMessageDetail) {
        AdHocMessageLogic.get(accountContext).resend(message)

    }


}