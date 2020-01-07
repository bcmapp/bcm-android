package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.GroupMemberPhotoView
import com.bcm.messenger.common.utils.BcmGroupNameUtil

/**
 */
class GroupMemberView : LinearLayout, RecipientModifiedListener {

    private lateinit var avatarView: GroupMemberPhotoView
    private lateinit var actorView: TextView
    private lateinit var nicknameView: TextView
    private lateinit var member: AmeGroupMemberInfo
    private lateinit var recipient: Recipient

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    public override fun onFinishInflate() {
        super.onFinishInflate()

        this.avatarView = findViewById(R.id.group_member_avatar)
        this.actorView = findViewById(R.id.group_member_actor)
        this.nicknameView = findViewById(R.id.group_member_nickname)

    }

    fun isInited(): Boolean {
        return ::nicknameView.isInitialized
    }


    fun unbind() {
        if (isInited()) {
            avatarView.clearAddressListener()
            if(::recipient.isInitialized) {
                recipient.removeListener(this)
            }
        }
    }

    fun bind(accountContext: AccountContext, member: AmeGroupMemberInfo?) {
        this.member = member ?: return
        this.recipient = Recipient.from(accountContext, member.uid, true)
        this.recipient.addListener(this)

        if (isInited()) {
            actorView.visibility = View.VISIBLE
            nicknameView.visibility = View.VISIBLE
            updateActor(member.role)
            updateRecipient(this.recipient)
        }
    }

    private fun updateActor(role: Long) {
        when (role) {
            AmeGroupMemberInfo.OWNER -> {
                actorView.text = context.resources.getString(R.string.chats_group_actor_creator)
                actorView.visibility = View.VISIBLE
                nicknameView.maxLines = 1
                nicknameView.setLines(1)
            }
            else -> {
                actorView.visibility = View.GONE
                nicknameView.maxLines = 2
                nicknameView.setLines(2)
            }
        }

    }

    private fun updateRecipient(recipient: Recipient) {
        if (isInited()) {
            val nickname = BcmGroupNameUtil.getGroupMemberName(recipient, member)
            nicknameView.text = nickname
            avatarView.setAvatar(member.role, recipient.address, member.keyConfig, nickname)
        }
    }

    override fun onModified(recipient: Recipient) {
        post {
            if(this.recipient == recipient) {
                updateRecipient(recipient)
            }
        }
    }
}