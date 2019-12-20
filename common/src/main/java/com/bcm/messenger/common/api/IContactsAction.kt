package com.bcm.messenger.common.api

import android.content.Context
import android.view.View
import com.bcm.messenger.common.recipients.Recipient

/**
 * 
 * Created by wjh on 2018/4/13
 */
interface IContactsAction {

    /**
     * 
     */
    fun queryContacts(filter: String, callback: QueryResultCallback?)

    /**
     * 
     */
    fun queryContactsFromRemote(address: String, callback: QueryResultCallback)

    /**
     * 
     */
    fun addSearchBar(context: Context)

    /**
     * 
     */
    fun addEmptyShade(context: Context)

    /**
     * 
     */
    fun addHeader(header: View)

    /**
     * 
     */
    fun addFooter(footer: View)

    /**
     * （index1）
     */
    fun showHeader(header: View, show: Boolean)

    /**
     * （index1）
     */
    fun showFooter(footer: View, show: Boolean)

    /**
     * 
     */
    fun setMultiMode(multiSelect: Boolean)

    /**
     * 设置已选中的不可更改的联系人
     */
    fun setFixedSelected(recipientList: List<Recipient>)
    /**
     * 
     */
    fun setSelected(recipient: Recipient, select: Boolean)

    /**
     * 
     */
    fun setContactSelectCallback(callback: IContactsCallback)


    /**
     * 
     */
    interface QueryResultCallback {
        /**
         * 
         */
        fun onQueryResult(recipientList: List<Recipient>)
    }
}