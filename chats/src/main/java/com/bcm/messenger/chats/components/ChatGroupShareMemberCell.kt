package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.ui.GroupMemberPhotoView
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by bcm.social.01 on 2018/6/6.
 */
class ChatGroupShareMemberCell : LinearLayout {
    private lateinit var avatarView: GroupMemberPhotoView
    private lateinit var selectRadio: ImageView
    private lateinit var nameView: TextView
    private var member: Address? = null

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onFinishInflate() {
        super.onFinishInflate()
        this.avatarView = findViewById(R.id.share_member_avatar)
        this.selectRadio = findViewById(R.id.share_member_select)
        this.nameView = findViewById(R.id.share_member_name)

        this.avatarView.setUpdateListener {
            if (this.member == it.address) {
                nameView.text = it.name
            }
        }
    }

    fun unbind() {
        if (::avatarView.isInitialized) {
            avatarView.clearAddressListener()
        }
    }

    fun bind(member: Address?, checked: Boolean) {
        unbind()
        if (this.member != member) {
            this.member = member

            val recipient = if(null != member){
                Recipient.from(context, member, true)
            } else {
                null
            }

            nameView.text = recipient?.name?:""
            avatarView.setAvatar(member)
        }
        changeSelectView(checked)
    }

    private fun changeSelectView(isChecked: Boolean) {
        if (isChecked) {
            selectRadio.visibility = View.VISIBLE
        } else {
            selectRadio.visibility = View.GONE
        }
    }

}