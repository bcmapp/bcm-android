package com.bcm.messenger.adhoc.ui.channel.holder

import android.view.View
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.api.IConversationContentAction
import com.bcm.messenger.common.mms.GlideRequests

/**
 *
 * Created by wjh on 2018/11/27
 */
abstract class BaseHolderAction<V : View>() : IConversationContentAction<AdHocMessageDetail> {

    protected var mBaseView: V? = null
    protected var mMessageDetail: AdHocMessageDetail? = null

    override fun hasPop(): Boolean {
        return true
    }

    override fun getDisplayView(): V? {
        return mBaseView
    }

    override fun setSelectMode(isSelectable: Boolean?) {
        if (isSelectable != null) {
            mBaseView?.isFocusable = !isSelectable
            mBaseView?.isClickable = !isSelectable
        }
    }

    override fun getCurrent(): AdHocMessageDetail? {
        return mMessageDetail
    }

    override fun bind(accountContext: AccountContext, message: AdHocMessageDetail, body: View, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?) {
        mMessageDetail = message
        mBaseView = body as V
        bindData(message, body, glideRequests, batchSelected)
    }

    abstract fun bindData(message: AdHocMessageDetail, body: V, glideRequests: GlideRequests, batchSelected: Set<AdHocMessageDetail>?)

    override fun unBind() {

    }
}