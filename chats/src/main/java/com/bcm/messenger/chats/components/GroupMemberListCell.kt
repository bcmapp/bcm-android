package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.GroupMemberPhotoView
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.utility.AppContextHolder

/**
 * bcm.social.01 2018/5/28.
 */
class GroupMemberListCell : LinearLayout {

    private lateinit var avatarView: GroupMemberPhotoView
    private lateinit var selectRadio: ImageView
    private lateinit var actorView: TextView
    private lateinit var nameView: TextView
    private lateinit var member: AmeGroupMemberInfo

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onFinishInflate() {
        super.onFinishInflate()
        this.avatarView = findViewById(R.id.group_member_avatar)
        this.selectRadio = findViewById(R.id.group_member_select)
        this.actorView = findViewById(R.id.member_actor_text)
        this.nameView = findViewById(R.id.member_name_text)

        avatarView.setUpdateListener {
            if (isInited() && it.address == member.uid) {
                updateNickname(it)
            }
        }
    }

    fun unbind() {
        if (::avatarView.isInitialized) {
            avatarView.clearAddressListener()
        }
    }

    fun bind(member: AmeGroupMemberInfo?, editing: Boolean, checked: Boolean) {
        unbind()
        if (null == member) {
            return
        }

        this.member = member
        if (bindAction()) {
            return
        }

        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, member.uid, true)
        updateNickname(recipient)
        changedEditState(editing, checked)
    }

    private fun isInited(): Boolean {
        return ::member.isInitialized
    }

    private fun updateNickname(recipient: Recipient) {
        if (!isInited()) {
            return
        }

        val nickname = BcmGroupNameUtil.getGroupMemberName(recipient, member)

        if (member.role == AmeGroupMemberInfo.OWNER) {
            actorView.text = context.resources.getString(R.string.chats_group_actor_creator)

            val name = "- $nickname"
            nameView.text = name
        } else {
            actorView.text = ""
            nameView.text = nickname
        }

        avatarView.setAvatar(member.role, member.uid, member.keyConfig, nickname)
    }

    private fun changedEditState(editing: Boolean, checked: Boolean) {
        if (member.role == AmeGroupMemberInfo.OWNER) {
            selectRadio.visibility = View.GONE
            return
        }

        if (editing) {
            selectRadio.visibility = View.VISIBLE
            changeSelectView(checked)
        } else {
            selectRadio.visibility = View.GONE
        }
    }

    private fun bindAction(): Boolean {
        selectRadio.visibility = View.GONE
        when (member) {
            AmeGroupMemberInfo.MEMBER_ADD_MEMBER -> {
                avatarView.setAvatar(R.drawable.chats_40_add_people)
                actorView.text = context.resources.getString(R.string.chats_group_add_member)
                nameView.text = ""
                return true
            }
        }
        return false
    }

    private fun changeSelectView(isChecked: Boolean) {
        if (selectRadio.visibility == View.VISIBLE) {
            if (isChecked) {
                selectRadio.setImageResource(R.drawable.common_checkbox_selected)
            } else {
                selectRadio.setImageResource(R.drawable.common_checkbox_unselected)
            }
        }
    }
}