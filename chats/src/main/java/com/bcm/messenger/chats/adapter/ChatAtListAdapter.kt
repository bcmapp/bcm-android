package com.bcm.messenger.chats.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by wjh on 2018/8/23
 */
class ChatAtListAdapter(private val accountContext: AccountContext, context: Context, private val listener: AtActionListener? = null) : LinearBaseAdapter<Recipient>() {


    private var mKeyWord: String? = null
    private var mAtList: List<Recipient>? = null
    private var mLayoutInflater = LayoutInflater.from(context)
    private var mGroupModel: GroupViewModel? = null

    fun setAtList(atList: List<Recipient>, keyword: String) {
        ALog.d("ChatAtListAdapter", "setAtList keyword: $keyword")
        mAtList = atList
        mKeyWord = keyword
        setDataList(atList)
    }

    fun setGroupId(groupId: Long) {
        mGroupModel = GroupLogic.get(accountContext).getModel(groupId)
    }

    override fun onBindContentHolder(holder: ViewHolder<Recipient>, trueData: Recipient?) {
        if(holder is AtHolder) {
            holder.bind(trueData ?: return)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<Recipient> {
        return AtHolder(mLayoutInflater.inflate(R.layout.chats_at_item, parent, false))
    }

    override fun onViewRecycled(holder: ViewHolder<Recipient>) {
        if (holder is AtHolder) {
            holder.unbind()
        }
    }

    inner class AtHolder(item: View) : ViewHolder<Recipient>(item), RecipientModifiedListener {

        val photoView: IndividualAvatarView
        val nameView: TextView

        lateinit var recipient: Recipient

        init {
            photoView = item.findViewById(R.id.at_photo)
            nameView = item.findViewById(R.id.at_name)
            item.setOnClickListener {
                listener?.onSelect(this.recipient)
            }
        }

        override fun onModified(recipient: Recipient) {
            itemView.post {
                if (this.recipient == recipient) {
                    bind(recipient)
                }
            }
        }

        fun bind(recipient: Recipient) {
            this.recipient = recipient
            this.recipient.addListener(this)
            this.photoView.setPhoto(accountContext, recipient)
            val name = BcmGroupNameUtil.getGroupMemberName(recipient, mGroupModel?.getGroupMember(recipient.address.serialize()))
            this.nameView.text =
                    if (mKeyWord.isNullOrEmpty()) {
                        name
                    } else {
                        StringAppearanceUtil.applyFilterAppearanceIgnoreCase(name, mKeyWord
                                ?: "", color = AppUtil.getColor(itemView.resources, R.color.common_app_primary_color))
                    }
        }

        fun unbind() {
            if (::recipient.isInitialized) {
                this.recipient.removeListener(this)
            }
        }
    }

    interface AtActionListener {

        fun onSelect(recipient: Recipient)
    }
}