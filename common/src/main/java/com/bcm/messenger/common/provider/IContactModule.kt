package com.bcm.messenger.common.provider

import android.content.Context
import android.graphics.Bitmap
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.model.ProfileKeyModel
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.recipients.Recipient
import io.reactivex.disposables.Disposable

/**
 * 
 * Created by ling on 2018/3/14.
 */
interface IContactModule : IAmeModule {

    companion object {
        const val TAG = "IContactProvider"
    }

    interface IProfileCallback {
        fun onDone(recipient: Recipient, viaJob: Boolean)
    }

    fun clear()

    fun doForLogin()

    fun doForLogOut()

    fun openSearch(context: Context)

    fun openContactDataActivity(context: Context, address: Address, nick: String? = null)

    fun openContactDataActivity(context: Context, address: Address, fromGroup: Long)

    fun openContactDataActivity(context: Context, address: Address, nick: String?, fromGroup: Long)

    fun discernScanData(context: Context, qrCode: String, callback: ((result: Boolean) -> Unit)? = null)

    fun discernLink(context: Context, link: String, callback: ((result: Boolean) -> Unit)? = null)

    fun blockContact(address: Address, block: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    fun blockContact(addressList: List<Address>, block: Boolean, callback: ((successList: List<Address>) -> Unit)? = null)

    fun replyFriend(targetUid: String, approve: Boolean, request: BcmFriendRequest, callback: ((result: Boolean) -> Unit)? = null)

    fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)? = null)

    fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    fun handleFriendPropertyChanged(targetUid: String, callback: ((result: Boolean) -> Unit)? = null)

    fun checkNeedRequestAddFriend(context: Context, recipient: Recipient)

    fun getContactListWithWait(): List<Recipient>

    fun updateThreadRecipientSource(threadRecipientList: List<Recipient>)

    fun updatePrivacyProfile(context: Context, recipient: Recipient,
                             newEncryptName: String?, newEncryptAvatarLD: String?, newEncryptAvatarHD: String?, allowStranger: Boolean)

    fun updateProfileKey(context: Context, recipient: Recipient, profileKeyModel: ProfileKeyModel)

    fun fetchProfile(recipient: Recipient, callback: (success: Boolean) -> Unit): Disposable

    fun checkNeedFetchProfile(vararg recipients: Recipient, callback: IProfileCallback?)

    fun checkNeedFetchProfileAndIdentity(vararg recipients: Recipient, callback: IProfileCallback?)

    fun checkNeedDownloadAvatar(isHd: Boolean, vararg recipients: Recipient)

    fun checkNeedDownloadAvatarWithAll(vararg recipients: Recipient)

    fun updateNickFromOtherWay(recipient: Recipient, nick: String)

    fun uploadBcmNick(context: Context, recipient: Recipient, nick: String, callback: (success: Boolean) -> Unit)

    fun uploadBcmAvatar(context: Context, recipient: Recipient, avatarBitmap: Bitmap, callback: (success: Boolean) -> Unit)

    fun updateShareLink(context: Context, handledRecipient: Recipient, callback: (success: Boolean) -> Unit)
}