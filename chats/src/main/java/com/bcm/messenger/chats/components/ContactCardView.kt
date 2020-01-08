package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.common.utils.sp2Px
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_contact_card_view.view.*

/**
 * Created by Kin on 2018/8/15
 */
class ContactCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr),
        RecipientModifiedListener {

    private val TAG = "ContactCardView"
    private var mRecipient: Recipient? = null
    private var mOutgoing = false
    private var mContactContent: AmeGroupMessage.ContactContent? = null
    private var mGroupId: Long = 0L

    init {
        inflate(context, R.layout.chats_contact_card_view, this)
        setOnClickListener {
            mRecipient?.let {
                newChat(it)
            }
        }
        contact_action.setOnClickListener {
            mRecipient?.let {
                if (!it.isLogin && (it.relationship == RecipientRepo.Relationship.STRANGER || it.relationship == RecipientRepo.Relationship.REQUEST)) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND)
                            .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, it.address)
                            .putString(ARouterConstants.PARAM.PARAM_NICK, mContactContent?.nickName)
                            .navigation(context)
                }else {
                    AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(AMELogin.majorContext, HomeTopEvent(true,
                            HomeTopEvent.ConversationEvent.fromPrivateConversation(it.address.serialize(),false)))
                }
            }

        }
    }

    fun setContact(content: AmeGroupMessage.ContactContent, isOutgoing: Boolean) {
        setContact(0L, content, isOutgoing)
    }

    fun setContact(groupId: Long, content: AmeGroupMessage.ContactContent, isOutgoing: Boolean) {
        val recipient = Recipient.from(AMELogin.majorContext, content.uid, true)
        if (mRecipient == recipient) {
            return
        }
        this.mGroupId = groupId
        this.mContactContent = content
        this.mOutgoing = isOutgoing
        mRecipient = recipient
        mRecipient?.addListener(this)
        val name = if (isOutgoing) recipient.name else content.nickName
        if (isOutgoing) {
            setBackgroundResource(R.drawable.chats_share_card_outgoing_bg)
            val spanBuilder = SpannableStringBuilder()
            spanBuilder.append(StringAppearanceUtil.applyAppearance(getString(R.string.chats_contact_card_title), 11.sp2Px(), Color.parseColor("#80FFFFFF")))
            spanBuilder.append("\n")
            spanBuilder.append(StringAppearanceUtil.applyAppearance(name, 16.sp2Px(), getColor(R.color.common_color_white)))
            contact_name.text = spanBuilder

            contact_action.setBackgroundResource(R.drawable.chats_contact_action_outgoing_selector)
            contact_action.setTextColor(getColor(R.color.common_color_white))
            contact_more.setImageResource(R.drawable.common_right_arrow_white_icon)
        } else {
            setBackgroundResource(R.drawable.chats_share_card_incoming_bg)
            val spanBuilder = SpannableStringBuilder()
            spanBuilder.append(StringAppearanceUtil.applyAppearance(getString(R.string.chats_contact_card_title), 11.sp2Px(), Color.parseColor("#4D000000")))
            spanBuilder.append("\n")
            spanBuilder.append(StringAppearanceUtil.applyAppearance(name, 16.sp2Px(), getColor(R.color.common_color_black)))
            contact_name.text = spanBuilder

            contact_action.setBackgroundResource(R.drawable.chats_contact_action_incoming_selector)
            contact_action.setTextColor(getColor(R.color.common_app_primary_color))
            contact_more.setImageResource(R.drawable.common_right_arrow_icon)
        }
        contact_portrait.setPhoto(recipient, name, IndividualAvatarView.NAME_CARD_TYPE)

        if (!recipient.isLogin && (recipient.relationship == RecipientRepo.Relationship.STRANGER || recipient.relationship == RecipientRepo.Relationship.REQUEST)) {
            contact_action.text = getString(R.string.chats_contact_card_add_action)
        }else {
            contact_action.text = getString(R.string.chats_contact_card_chat_action)
        }
    }

    private fun newChat(recipient: Recipient) {
        AmeModuleCenter.contact(recipient.address.context())?.openContactDataActivity(context, recipient.address, mContactContent?.nickName, mGroupId)
    }

    override fun onModified(recipient: Recipient) {
        contact_portrait.post {
            if (this.mRecipient == recipient) {
                mContactContent?.let {
                    setContact(mGroupId, it, mOutgoing)
                }
            }
        }

    }
}