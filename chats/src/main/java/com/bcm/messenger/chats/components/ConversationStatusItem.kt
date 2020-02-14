package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.api.BindableConversationItem
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.groups.GroupUpdateModel
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.ExpirationUtil
import com.bcm.messenger.common.utils.GroupDescription
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.utility.StringAppearanceUtil
import java.util.*

/**
 */
class ConversationStatusItem : LinearLayout, RecipientModifiedListener, BindableConversationItem {

    private val TAG = "ConversationStatusItem"

    private var masterSecret: MasterSecret? = null
    private var batchSelected: Set<MessageRecord>? = null

    private var icon: ImageView? = null
    private var body: TextView? = null
    private var date: TextView? = null
    private var targetRecipient: Recipient? = null
    private var messageRecord: MessageRecord? = null
    private var locale: Locale? = null

    private var mGroupDescription: GroupDescription? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    public override fun onFinishInflate() {
        super.onFinishInflate()

        this.icon = findViewById(R.id.conversation_update_icon)
        this.body = findViewById(R.id.conversation_update_body)
//        this.date = findViewById<TextView>(R.id.conversation_update_date)

        this.setOnClickListener(InternalClickListener(null))
    }

    override fun bind(masterSecret: MasterSecret,
                      messageRecord: MessageRecord,
                      glideRequests: GlideRequests,
                      locale: Locale,
                      batchSelected: Set<MessageRecord>?,
                      conversationRecipient: Recipient,
                      position: Int) {
        unbind()
        this.locale = locale
        this.masterSecret = masterSecret
        this.batchSelected = batchSelected
        this.messageRecord = messageRecord
        this.targetRecipient = messageRecord.getRecipient(masterSecret.accountContext)
        isSelected = batchSelected?.contains(messageRecord) == true

        setBody(messageRecord)

        if (position == 0) {
            layoutParams = (layoutParams as RecyclerView.LayoutParams).apply {
                bottomMargin = AppUtil.dp2Px(resources, 15)
            }
        } else if ((layoutParams as RecyclerView.LayoutParams).bottomMargin != 0) {
            layoutParams = (layoutParams as RecyclerView.LayoutParams).apply {
                bottomMargin = 0
            }
        }
    }

    override fun getMessageRecord(): MessageRecord? {
        return messageRecord
    }


    private fun setShowed(show: Boolean) {
        val lp = layoutParams
        visibility = if (show) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            View.VISIBLE
        } else {
            lp.height = 0
            View.GONE
        }
        layoutParams = lp
    }

    private fun setBody(messageRecord: MessageRecord) {
        if (messageRecord.isJoin()) {
            setJoinedRecord(messageRecord)
        } else if (messageRecord.isExpirationTimerUpdate()) {
            setTimerRecord(messageRecord)
        } else if (messageRecord.isEndSession()) {
            setEndSessionRecord(messageRecord)
        } else if (messageRecord.isIdentityUpdate()) {
            setIdentityRecord(messageRecord)
        } else if (messageRecord.isIdentityVerify()|| messageRecord.isIdentityDefault()) {
            setIdentityVerifyUpdate(messageRecord)
        } else {
            setShowed(false)
        }
    }

    private fun setTimerRecord(messageRecord: MessageRecord) {
        val bodyText = if (messageRecord.isOutgoing()) {
            resources.getString(R.string.chats_read_burn_detail_by_you,
                    ExpirationUtil.getExpirationDisplayValue(context, (messageRecord.expiresTime / 1000).toInt()))
        } else {
            val accountContext = targetRecipient?.address?.context()?:return
            resources.getString(R.string.chats_read_burn_detail, messageRecord.getRecipient(accountContext).name,
                    ExpirationUtil.getExpirationDisplayValue(context, (messageRecord.expiresTime / 1000).toInt()))

        }
        body?.text = bodyText
    }


    private fun setIdentityRecord(messageRecord: MessageRecord) {
        setShowed(true)
        icon?.visibility = View.VISIBLE
        icon?.setImageResource(R.drawable.ic_security_white_24dp)

        val accountContext = targetRecipient?.address?.context()?:return
        body?.text = resources.getString(R.string.chats_message_verification_change_description
                , messageRecord.getRecipient(accountContext).name)

    }


    private fun setIdentityVerifyUpdate(messageRecord: MessageRecord) {
        setShowed(true)
        var bodyText = ""
        if (messageRecord.isIdentityVerify()) {
            bodyText += resources.getString(R.string.chats_message_verified_description, targetRecipient?.name
                    ?: resources.getString(R.string.chats_message_target_recipient_unknown))
        } else {
            bodyText += resources.getString(R.string.chats_message_unverified_description, targetRecipient?.name
                    ?: resources.getString(R.string.chats_message_target_recipient_unknown))
        }
        icon?.visibility = View.GONE
        body?.text = bodyText

    }

    private fun setGroupRecord(messageRecord: MessageRecord) {
        setShowed(true)
        val groupDescription = GroupDescription.getDescription(context, messageRecord.body)
        groupDescription.addListener(this)
        body?.text = toString(groupDescription)
        mGroupDescription = groupDescription
    }

    private fun setJoinedRecord(messageRecord: MessageRecord) {
        icon?.visibility = View.GONE
        body?.text = resources.getString(R.string.chats_message_contact_registration_description, targetRecipient?.name
                ?: resources.getString(R.string.chats_message_target_recipient_unknown))

    }

    private fun setEndSessionRecord(messageRecord: MessageRecord) {
        setShowed(true)
        icon?.visibility = View.VISIBLE
        icon?.setImageResource(R.drawable.common_refresh_icon)
        icon?.drawable?.setTint(context.getAttrColor(R.attr.common_white_color))
        body?.text = messageRecord.body
    }


    private fun toString(groupDescription: GroupDescription): CharSequence {
        val groupModel = groupDescription.groupUpdateModel ?: return ""
        when (groupModel.action) {
            in arrayOf(GroupUpdateModel.GROUP_MEMBER_JOINED, GroupUpdateModel.GROUP_CREATE) -> {
                val targetBuilder = StringBuilder()
                if (groupModel.target != null) {
                    for (nikeName in groupModel.target) {
                        targetBuilder.append(nikeName)
                        targetBuilder.append(",")
                    }
                    if (targetBuilder.isNotEmpty()) {
                        targetBuilder.deleteCharAt(targetBuilder.length - 1)
                    }
                }
                val result = resources.getString(R.string.chats_group_create_description, (groupModel.sender
                        ?: ""), targetBuilder.toString())
                return StringAppearanceUtil.applyFilterAppearance(result, targetBuilder.toString(), color = AppUtil.getColor(resources, R.color.common_app_primary_color))
            }

            GroupUpdateModel.GROUP_AVATAR_CHANGED -> {
                return resources.getString(R.string.chats_group_avatar_changed_description)
            }
            GroupUpdateModel.GROUP_TITLE_CHANGED -> {
                return resources.getString(R.string.chats_group_title_changed_description, groupModel.info)
            }
            else -> return resources.getString(R.string.chats_group_update_description)
        }
    }

    override fun onModified(recipient: Recipient) {
        post {
            if (targetRecipient == recipient) {
                setBody(messageRecord ?: return@post)
            }
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(InternalClickListener(l))
    }

    override fun unbind() {
        targetRecipient?.removeListener(this)
    }

    private inner class InternalClickListener(private val parent: OnClickListener?) : OnClickListener {

        override fun onClick(v: View) {
            parent?.onClick(v)
        }
    }

}
