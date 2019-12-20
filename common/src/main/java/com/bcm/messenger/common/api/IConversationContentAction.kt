package com.bcm.messenger.common.api

import android.view.View
import com.bcm.messenger.common.mms.GlideRequests

/**
 * 公共的会话消息处理接口
 * Created by wjh on 2019/7/27
 */
interface IConversationContentAction<T> {

    /**
     * 是否可长按弹菜单
     */
    fun hasPop(): Boolean

    /**
     * 当前展示view
     */
    fun getDisplayView(): View?

    /**
     * 绑定消息数据
     */
    fun bind(message: T, body: View, glideRequests: GlideRequests, batchSelected: Set<T>?)

    /**
     * 解绑定数据
     */
    fun unBind()

    /**
     * 重发消息操作
     */
    fun resend(message: T)

    /**
     * 设置当前选择模式
     */
    fun setSelectMode(isSelectable: Boolean?)

    /**
     * 获取当前数据
     */
    fun getCurrent(): T?
}