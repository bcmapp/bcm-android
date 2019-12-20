package com.bcm.messenger.chats.group.viewholder

import android.view.View
import com.bcm.messenger.common.api.IConversationContentAction
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests

/**
 *
 * Created by wjh on 2018/11/27
 */
abstract class BaseChatHolderAction<V : View>() : IConversationContentAction<AmeGroupMessageDetail> {

    protected var mBaseView: V? = null
    protected var mMessageDetail: AmeGroupMessageDetail? = null

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

    override fun getCurrent(): AmeGroupMessageDetail? {
        return mMessageDetail
    }

    override fun bind(message: AmeGroupMessageDetail, body: View, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        mMessageDetail = message
        mBaseView = body as V
        bindData(message, body, glideRequests, batchSelected)
    }

    abstract fun bindData(message: AmeGroupMessageDetail, body: V, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?)

    override fun unBind() {

    }
}