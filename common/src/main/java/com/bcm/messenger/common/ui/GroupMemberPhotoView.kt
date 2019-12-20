package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.R
import com.bcm.messenger.common.database.model.ProfileKeyModel
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.utility.AppContextHolder

/**
 * 群成员头像（可根据角色设置描边）
 * Created by bcm.social.01 on 2018/5/25.
 */
class GroupMemberPhotoView : ConstraintLayout, RecipientModifiedListener {

    private var avatarView: IndividualAvatarView? = null
    private var border: View? = null
    private var innerBorder: View? = null

    private var recipient: Recipient? = null
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
     * 清理地址相关的回调
     */
    fun clearAddressListener() {
        this.recipient?.removeListener(this)
    }

    /**
     * 设置个人联系人的avatar
     */
    fun setRecipient(recipient: Recipient?, nickname: String? = null) {
        this.recipient = recipient
        this.avatarView?.setPhoto(recipient, nickname, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
        setBorderVisible(View.GONE)
    }


    /**
     * 旧的avatar设置入口
     */
    fun setAvatar(role: Long?, address: Address?, keyConfig: AmeGroupMemberInfo.KeyConfig? = null, nickname:String? = null) {
        setAvatar(address, keyConfig, nickname)
        if (role == AmeGroupMemberInfo.OWNER) {
            setBorderVisible(View.VISIBLE)
        } else {
            setBorderVisible(View.GONE)
        }
    }

    /**
     * avatar设置入口
     */
    fun setAvatar(address: Address?, keyConfig: AmeGroupMemberInfo.KeyConfig? = null, nickname: String? = null) {
        if (address == null) {
            return
        }

        if (this.recipient?.address != address) {
            recipient?.removeListener(this)
            this.recipient = Recipient.from(context, address, true)
            recipient?.addListener(this)
        }

        val recipient = this.recipient
        val profileKeyModel = ProfileKeyModel.fromKeyConfig(keyConfig)
        if (null != recipient && null != profileKeyModel) {
            RecipientProfileLogic.updateProfileKey(AppContextHolder.APP_CONTEXT, recipient, profileKeyModel)
        }

        setRecipient(recipient, nickname)
    }

    /**
     * avatar设置入口
     */
    fun setAvatar(address: Address?) {
       setAvatar(address, null, null)
    }

    /**
     * 设置指定的avatar
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
            if (recipient == this.recipient) {
                avatarView?.setPhoto(recipient)
                mUpdateCallback?.invoke(recipient)
            }
        }
    }

}