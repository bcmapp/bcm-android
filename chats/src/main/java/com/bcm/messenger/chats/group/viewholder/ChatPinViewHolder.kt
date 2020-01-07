package com.bcm.messenger.chats.group.viewholder

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AppUtil
import kotlinx.android.synthetic.main.chats_conversation_item_pin.view.*

/**
 * 
 * Created by zjl on 2018/4/3.
 */
class ChatPinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), RecipientModifiedListener {

    private val TAG = "ChatPinViewHolder"
    private lateinit var data: AmeGroupMessage.PinContent
    private var pinRecipient: Recipient? = null
    private var pinMessage: AmeGroupMessageDetail? = null

    override fun onModified(recipient: Recipient) {
        if (pinRecipient == recipient) {
            pinMessage?.let { message ->
                showReceivePin(recipient.name, message.message.type, message)
            }
        }
    }

    private fun getAccountContext(): AccountContext {
        return AMELogin.majorContext
    }

    fun unBindData() {
        pinRecipient?.removeListener(this)
    }

    fun bindData(messageRecord: AmeGroupMessageDetail) {
        data = messageRecord.message.content as AmeGroupMessage.PinContent
        if (data.mid == -1L) {
            cancelPin(itemView.context, messageRecord)
        } else {
            setPin(itemView.context, messageRecord)
        }
    }

    @SuppressLint("CheckResult")
    fun setPin(context: Context, messageRecord: AmeGroupMessageDetail) {
        showPinMessage(GroupLogic.get(getAccountContext()).getGroupInfo(messageRecord.gid))
        GroupLogic.get(getAccountContext()).getModel(messageRecord.gid)?.getMessageDetailByMid(data.mid) { result ->
            if (result != null) {
                this.pinMessage = result
                val groupInfo = GroupLogic.get(getAccountContext()).getGroupInfo(messageRecord.gid)
                if (messageRecord.message.content != data)
                    return@getMessageDetailByMid
                val pinType = result.message.type
                if (groupInfo?.role == AmeGroupMemberInfo.OWNER) {

                    itemView.pin_text?.text = when (pinType) {
                        AmeGroupMessage.TEXT -> {
                            val message = result.message.content as AmeGroupMessage.TextContent
                            itemView.resources.getString(R.string.chats_send_pin_text, checkMessageLength(message.text.trim()))
                        }
                        AmeGroupMessage.CHAT_REPLY -> {
                            val message = result.message.content as AmeGroupMessage.ReplyContent
                            itemView.resources.getString(R.string.chats_send_pin_text, checkMessageLength(message.text.trim()))
                        }
                        AmeGroupMessage.AUDIO -> itemView.resources.getString(R.string.chats_send_pin_audio)
                        AmeGroupMessage.IMAGE -> itemView.resources.getString(R.string.chats_send_pin_photo)
                        AmeGroupMessage.VIDEO -> itemView.resources.getString(R.string.chats_send_pin_video)
                        AmeGroupMessage.FILE -> itemView.resources.getString(R.string.chats_send_pin_file)
                        AmeGroupMessage.LOCATION -> itemView.resources.getString(R.string.chats_send_pin_location)
                        AmeGroupMessage.LINK -> itemView.resources.getString(R.string.chats_send_pin_link)
                        AmeGroupMessage.NEWSHARE_CHANNEL -> itemView.resources.getString(R.string.chats_send_pin_channel)
                        AmeGroupMessage.CONTACT -> itemView.resources.getString(R.string.chats_send_pin_contact)
                        AmeGroupMessage.CHAT_HISTORY -> itemView.resources.getString(R.string.chats_send_pin_history)
                        AmeGroupMessage.GROUP_SHARE_CARD -> itemView.resources.getString(R.string.chats_send_pin_share_card)
                        else -> itemView.resources.getString(R.string.chats_send_pin_unknown)
                    }
                } else {

                    val senderName =
                            groupInfo?.let {
                                pinRecipient = Recipient.from(getAccountContext(), it.owner, true)
                                pinRecipient?.addListener(this)
                                pinRecipient?.name
                            }

                    showReceivePin(senderName, pinType, result)
                }

            }
        }
    }

    private fun showPinMessage(groupInfo: AmeGroupInfo?) {
        val senderName =
                groupInfo?.let {
                    pinRecipient = Recipient.from(getAccountContext(), it.owner, true)
                    pinRecipient?.addListener(this)
                    pinRecipient?.name
                }
        itemView.pin_text?.text = itemView.resources.getString(R.string.chats_pin_a_message, senderName)
    }

    private fun showReceivePin(senderName: String?, pinType: Long, record: AmeGroupMessageDetail) {

        itemView.pin_text?.text = when (pinType) {
            AmeGroupMessage.TEXT -> {
                val message = record.message.content as AmeGroupMessage.TextContent
                itemView.resources.getString(R.string.chats_receive_pin_text, senderName, checkMessageLength(message.text.trim()))
            }
            AmeGroupMessage.AUDIO -> itemView.resources.getString(R.string.chats_receive_pin_audio, senderName)
            AmeGroupMessage.IMAGE -> itemView.resources.getString(R.string.chats_receive_pin_photo, senderName)
            AmeGroupMessage.VIDEO -> itemView.resources.getString(R.string.chats_receive_pin_video, senderName)
            AmeGroupMessage.FILE -> itemView.resources.getString(R.string.chats_receive_pin_file, senderName)
            AmeGroupMessage.LOCATION -> itemView.resources.getString(R.string.chats_receive_pin_location, senderName)
            AmeGroupMessage.LINK -> itemView.resources.getString(R.string.chats_receive_pin_link, senderName)
            AmeGroupMessage.NEWSHARE_CHANNEL -> itemView.resources.getString(R.string.chats_receive_pin_channel, senderName)
            AmeGroupMessage.CONTACT -> itemView.resources.getString(R.string.chats_receive_pin_contact, senderName)
            AmeGroupMessage.CHAT_HISTORY -> itemView.resources.getString(R.string.chats_receive_pin_history, senderName)
            AmeGroupMessage.GROUP_SHARE_CARD -> itemView.resources.getString(R.string.chats_receive_pin_share_card, senderName)
            else -> itemView.resources.getString(R.string.chats_receive_pin_unknown)
        }

    }

    private fun cancelPin(context: Context, messageRecord: AmeGroupMessageDetail) {
        val groupInfo = GroupLogic.get(getAccountContext()).getGroupInfo(messageRecord.gid)
        if (groupInfo?.role == AmeGroupMemberInfo.OWNER) {
            itemView.pin_text?.text = AppUtil.getString(context, R.string.chats_send_unpin_message)
        } else {
            val senderName = groupInfo?.let {
                Recipient.from(getAccountContext(), groupInfo.owner, true).name
            }
            itemView.pin_text?.text = context.resources.getString(R.string.chats_receive_unpin_message, senderName)
        }
    }

    private fun checkMessageLength(message: String): String {
        val text = if (message.length > 40) {
            message.substring(0, 40) + "…"
        } else {
            message
        }
        return text
    }

}