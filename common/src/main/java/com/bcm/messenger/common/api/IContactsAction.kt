package com.bcm.messenger.common.api

import android.content.Context
import android.view.View
import com.bcm.messenger.common.recipients.Recipient

/**
 * 联系人执行接口
 * Created by wjh on 2018/4/13
 */
interface IContactsAction {

    /**
     * 根据条件查询当前联系人
     */
    fun queryContacts(filter: String, callback: QueryResultCallback?)

    /**
     * 根据号码查询平台联系人
     */
    fun queryContactsFromRemote(address: String, callback: QueryResultCallback)

    /**
     * 添加搜索栏
     */
    fun addSearchBar(context: Context)

    /**
     * 添加空白页
     */
    fun addEmptyShade(context: Context)

    /**
     * 添加自定义头部
     */
    fun addHeader(header: View)

    /**
     * 添加自定义尾部
     */
    fun addFooter(footer: View)

    /**
     * 展示或隐藏指定位置的头部（index从1开始）
     */
    fun showHeader(index: Int, show: Boolean)

    /**
     * 展示或隐藏指定位置的尾部（index从1开始）
     */
    fun showFooter(index: Int, show: Boolean)

    /**
     * 设置当前选择模式
     */
    fun setMultiMode(multiSelect: Boolean)
    /**
     * 设置指定的联系人是否选中
     */
    fun setSelected(recipient: Recipient, select: Boolean)

    /**
     * 设置联系人处理回调
     */
    fun setContactSelectCallback(callback: IContactsCallback)

    /**
     * 联系人查询回调
     */
    interface QueryResultCallback {
        /**
         * 根据条件查询结果
         */
        fun onQueryResult(recipientList: List<Recipient>)
    }
}