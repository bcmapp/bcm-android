package com.bcm.messenger.chats.group.viewholder

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
import kotlinx.android.synthetic.main.chats_conversation_item_live.view.*

/**
 * Created by zjl on 2018/4/3.
 */
class ChatLiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val TAG = "ChatLiveViewHolder"
    private lateinit var data: AmeGroupMessage.LiveContent
    private var liveRecipient: Recipient? = null
    private var groupInfo: AmeGroupInfo? = null

    fun getAccountContext(): AccountContext {
        return AMELogin.majorContext
    }

    fun unBindData() {

    }

    fun bindData(messageRecord: AmeGroupMessageDetail) {
        data = messageRecord.message.content as AmeGroupMessage.LiveContent

        groupInfo = GroupLogic.get(getAccountContext()).getGroupInfo(messageRecord.gid)
        groupInfo?.let {
            liveRecipient = Recipient.from(getAccountContext(), it.owner, true)
            liveRecipient?.addListener(liveListener)
            setPlayTip(liveRecipient?.name)
        }
    }

    private val liveListener = RecipientModifiedListener {
        itemView.live_text?.post {
            setPlayTip(it.name)
        }

    }

    private fun setPlayTip(liveOwnerName: String?) {

        if (!liveOwnerName.isNullOrEmpty()) {
            if (groupInfo?.role == AmeGroupMemberInfo.OWNER) {
                when {
                    data.isStartLive() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_start_live_tip_by_you)
                    data.isRemoveLive() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_stop_live_tip_by_you)
                    data.isRemovePlayback() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_remove_live_tip_by_you)
                }
            } else {
                when {
                    data.isStartLive() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_start_live_tip, liveOwnerName)
                    data.isRemoveLive() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_stop_live_tip, liveOwnerName)
                    data.isRemovePlayback() -> itemView.live_text?.text = itemView.context.getString(R.string.chats_live_remove_live_tip, liveOwnerName)
                }
            }
        }
    }
}