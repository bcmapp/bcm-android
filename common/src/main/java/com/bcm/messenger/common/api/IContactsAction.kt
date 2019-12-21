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
    fun showHeader(index: Int, show: Boolean)

    /**
     * （index1）
     */
    fun showFooter(index: Int, show: Boolean)

    /**
     * 
     */
    fun setMultiMode(multiSelect: Boolean)
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