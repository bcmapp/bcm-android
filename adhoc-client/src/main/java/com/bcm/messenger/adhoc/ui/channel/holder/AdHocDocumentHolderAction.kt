package com.bcm.messenger.adhoc.ui.channel.holder

import com.bcm.messenger.adhoc.component.AdHocDocumentView
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.util.AdHocPreviewClickListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.mms.GlideRequests

/**
 *
 * Created by wjh on 2019-08-27
 */
class AdHocDocumentHolderAction(private val accountContext: AccountContext) : BaseHolderAction<AdHocDocumentView>() {

    private var mPreviewClickListener = AdHocPreviewClickListener(accountContext)

    override fun bindData(message: AdHocMessageDetail, body: AdHocDocumentView, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        body.setDocumentClickListener(mPreviewClickListener)
        body.setDocument(accountContext, message)
    }

    override fun resend(message: AdHocMessageDetail) {
        AdHocMessageLogic.get(accountContext).resend(message)

    }

    override fun setSelectMode(isSelectable: Boolean?) {
    }

}