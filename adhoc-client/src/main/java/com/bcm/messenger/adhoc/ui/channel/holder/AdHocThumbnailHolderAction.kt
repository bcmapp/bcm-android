package com.bcm.messenger.adhoc.ui.channel.holder

import com.bcm.messenger.adhoc.component.AdHocThumbnailView
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.util.AdHocPreviewClickListener
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.mms.GlideRequests

/**
 *
 * Created by wjh on 2019/08/27
 */
class AdHocThumbnailHolderAction() : BaseHolderAction<AdHocThumbnailView>() {

    private var mPreviewClickListener = AdHocPreviewClickListener()

    override fun bindData(message: AdHocMessageDetail, body: AdHocThumbnailView, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        body.setThumbnailClickListener(mPreviewClickListener)
        body.setThumbnailAppearance(R.drawable.common_image_place_img, R.drawable.common_image_broken_img, body.resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius))
        body.setImage(glideRequests, message)
    }

    override fun resend(accountContext: AccountContext, message: AdHocMessageDetail) {
        AdHocMessageLogic.resend(message)
    }

}