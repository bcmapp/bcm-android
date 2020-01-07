package com.bcm.messenger.common.api

import android.view.View
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.mms.GlideRequests

/**
 * 
 * Created by wjh on 2019/7/27
 */
interface IConversationContentAction<T> {

    /**
     * 
     */
    fun hasPop(): Boolean

    /**
     * view
     */
    fun getDisplayView(): View?

    /**
     * 
     */
    fun bind(accountContext: AccountContext, message: T, body: View, glideRequests: GlideRequests, batchSelected: Set<T>?)

    /**
     * 
     */
    fun unBind()

    /**
     * 
     */
    fun resend(accountContext: AccountContext, message: T)

    /**
     * 
     */
    fun setSelectMode(isSelectable: Boolean?)

    /**
     * 
     */
    fun getCurrent(): T?

    fun getAccountContext(): AccountContext?
}