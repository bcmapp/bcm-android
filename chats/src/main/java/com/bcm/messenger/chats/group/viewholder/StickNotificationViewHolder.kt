package com.bcm.messenger.chats.group.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import kotlinx.android.synthetic.main.chats_stick_notification_item.view.*

/**
 * ViewHolder for group message pin to top
 * Created by wjh on 2019/5/24
 */
class StickNotificationViewHolder(containerView: View) : RecyclerView.ViewHolder(containerView) {

    fun bindData(messageRecord: AmeGroupMessageDetail) {
        itemView.chats_stick_notification_layout?.setGroupInfo(GroupLogic.getGroupInfo(messageRecord.gid)
                ?: return)
        itemView.chats_stick_notification_layout?.showLoading(messageRecord.isSending)
    }
}