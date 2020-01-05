package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.R
import com.bcm.messenger.common.database.model.ProfileKeyModel
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by bcm.social.01 on 2018/5/25.
 */
class GroupMemberPhotoView : ConstraintLayout, RecipientModifiedListener {

    private var avatarView: IndividualAvatarView? = null
    private var border: View? = null
    private var innerBorder: View? = null

    private var recipient: Recipient? = null
    private var accountContext: AccountContext? = null
    private var mUpdateCallback: ((recipient: Recipient) -> Unit)? = null

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}

    constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style) {
        View.inflate(context, R.layout.common_group_member_avatar_view, this)
        this.avatarView = findViewById(R.id.group_portrait_view)
        this.border = findViewById(R.id.group_portrait_border)
        this.innerBorder = findViewById(R.id.group_portrait_border_inner)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearAddressListener()
    }

    fun setUpdateListener(callback: ((member: Recipient) -> Unit)?) {
        mUpdateCallback = callback
    }

    /**
     *
     */
    fun clearAddressListener() {
        this.recipient?.removeListener(this)
    }

    /**
     *
     */
    fun setRecipient(accountContext: AccountContext, recipient: Recipient?, nickname: String? = null) {
        this.recipient = recipient
        this.accountContext = accountContext
        this.avatarView?.setPhoto(accountContext, recipient, nickname, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
        setBorderVisible(View.GONE)
    }


    /**
     *
     */
    fun setAvatar(accountContext: AccountContext, role: Long?, address: Address?, keyConfig: AmeGroupMemberInfo.KeyConfig? = null, nickname: String? = null) {
        setAvatar(accountContext, address, keyConfig, nickname)
        if (role == AmeGroupMemberInfo.OWNER) {
            setBorderVisible(View.VISIBLE)
        } else {
            setBorderVisible(View.GONE)
        }
    }

    /**
     *
     */
    fun setAvatar(accountContext: AccountContext, address: Address?, keyConfig: AmeGroupMemberInfo.KeyConfig? = null, nickname: String? = null) {
        if (address == null) {
            return
        }

        if (this.recipient?.address != address) {
            recipient?.removeListener(this)
            this.recipient = Recipient.from(accountContext, address, true)
            recipient?.addListener(this)
        }

        this.accountContext = accountContext

        val recipient = this.recipient
        val profileKeyModel = ProfileKeyModel.fromKeyConfig(keyConfig)
        if (null != recipient && null != profileKeyModel) {
            AmeModuleCenter.contact(accountContext)?.updateProfileKey(AppContextHolder.APP_CONTEXT, recipient, profileKeyModel)
        }

        setRecipient(accountContext, recipient, nickname)
    }

    /**
     *
     */
    fun setAvatar(accountContext: AccountContext, address: Address?) {
        setAvatar(accountContext, address, null, null)
    }

    /**
     *
     */
    fun setAvatar(resId: Int) {

        avatarView?.setPhoto(resId)
        setBorderVisible(View.GONE)
    }

    private fun setBorderVisible(visible: Int) {
        border?.visibility = visible
        innerBorder?.visibility = visible
    }

    override fun onModified(recipient: Recipient) {
        post {
            val context = accountContext
            if (recipient == this.recipient && context != null) {
                avatarView?.setPhoto(context, recipient)
                mUpdateCallback?.invoke(recipient)
            }
        }
    }

}