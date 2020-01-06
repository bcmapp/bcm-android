package com.bcm.messenger.common.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.api.IConversationContentAction
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.logger.ALog

/**
 
 * Created by wjh on 2019/7/27
 */
abstract class ConversationContentViewHolder<T>(layout: View) : RecyclerView.ViewHolder(layout) {

    protected var mMessageSubject: T? = null
    private var mCanLongClick = true 
    protected var mSelectedBatch: MutableSet<T>? = null
    protected var mAction: IConversationContentAction<T>? = null

    /**
     * 
     */
    fun setCanLongClick(canLongClick: Boolean) {
        mCanLongClick = canLongClick
    }

    fun canLongClick(): Boolean {
        return mCanLongClick
    }

    fun getAccountContext(): AccountContext {
        return AMELogin.majorContext
    }

    /**
     * 
     */
    open fun bind(message: T, glideRequests: GlideRequests, batchSelected: MutableSet<T>?) {
        ALog.d("ConversationContentViewHoler", "bind")
        mMessageSubject = message
        mSelectedBatch = batchSelected
        updateSender(message)
        val action = bindViewAction(message, glideRequests, batchSelected)
        updateAlert(message, action)
        if (batchSelected != null) {
            val selected = batchSelected.contains(message)
            action?.setSelectMode(selected)
            updateSelectionMode(selected)
        } else {
            action?.setSelectMode(null)
            updateSelectionMode(null)
        }
        updateBackground(message, action)

        //
        if (mCanLongClick && action?.hasPop() == true && batchSelected == null) {
            action?.getDisplayView()?.isLongClickable = true
        } else {
            action?.getDisplayView()?.isLongClickable = false
        }
        mAction = action
    }

    /**
     * 
     */
    fun unbind() {
        mAction?.unBind()
    }

    /**
     * 
     * @param message
     */
    protected abstract fun bindViewAction(message: T, glideRequests: GlideRequests, batchSelected: MutableSet<T>?): IConversationContentAction<T>?

    protected abstract fun updateSender(message: T)

    protected abstract fun updateBackground(message: T, action: IConversationContentAction<T>?)

    protected abstract fun updateAlert(message: T, action: IConversationContentAction<T>?)

    protected abstract fun updateSelectionMode(isSelect: Boolean?)

}