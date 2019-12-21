package com.bcm.messenger.common.provider

import android.content.Context
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.recipients.Recipient

/**
 * 
 * Created by ling on 2018/3/14.
 */
interface IContactModule : IAmeModule {

    companion object {
        const val TAG = "IContactProvider"
    }

    /**
     * 
     */
    fun clear()

    /**
     * 
     */
    fun openSearch(context: Context)

    /**
     * 
     */
    fun openContactDataActivity(context: Context, address: Address, nick: String? = null)

    /**
     * 
     */
    fun openContactDataActivity(context: Context, address: Address, fromGroup: Long)

    /**
     * 
     */
    fun openContactDataActivity(context: Context, address: Address, nick: String?, fromGroup: Long)

    /**
     * (callback result: truebcm，falsebcm)
     */
    fun discernScanData(context: Context, qrCode: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * （callback result: truebcm，falsebcm）
     */
    fun discernLink(context: Context, link: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * / （，）
     */
    fun blockContact(address: Address, block: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * / （，）
     */
    fun blockContact(addressList: List<Address>, block: Boolean, callback: ((successList: List<Address>) -> Unit)? = null)

    /**
     * 
     */
    fun doForLogin()
    /**
     * 
     */
    fun doForLogOut()

    /**
     * 
     */
    fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 
     */
    fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    fun handleFriendPropertyChanged(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    /**
     * 
     */
    fun checkNeedRequestAddFriend(context: Context, recipient: Recipient)

    /**
     * 
     */
    fun updateThreadRecipientSource(threadRecipientList: List<Recipient>)
}