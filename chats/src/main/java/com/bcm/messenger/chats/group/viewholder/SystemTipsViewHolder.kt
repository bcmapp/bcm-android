package com.bcm.messenger.chats.group.viewholder

import android.content.Context
import android.text.SpannableStringBuilder
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_tip_message_item.view.*
import org.json.JSONObject

/**
 * 
 * Created by zjl on 2018/4/3.
 */
class SystemTipsViewHolder(containerView: View) : RecyclerView.ViewHolder(containerView), RecipientModifiedListener {

    private val TAG = "SystemTipsViewHolder"
    private var messageRecord: AmeGroupMessageDetail? = null
    private lateinit var data: AmeGroupMessage.SystemContent

    init {

    }

    fun unBindData() {
        if(::data.isInitialized) {
            data.setRecipientCallback(, null)
        }
    }

    fun bindData(messageRecord: AmeGroupMessageDetail) {
        this.messageRecord = messageRecord
        this.data = messageRecord.message.content as AmeGroupMessage.SystemContent
        this.data.setRecipientCallback(, this)
        refreshTip(itemView.context)
    }

    override fun onModified(recipient: Recipient) {
        itemView.post {
            refreshTip(itemView.context)
        }
    }

    private fun refreshTip(context: Context) {
        val messageRecord = this.messageRecord ?: return
        val text = data.getDescribe(messageRecord.gid, )
        ALog.d(TAG, "bindData describe: $text, extra: ${data.extra}")
        if (data.tipType == AmeGroupMessage.SystemContent.TIP_JOIN_GROUP_REQUEST) {
            var isHandled = if (data.extra == null) {
                false
            }else {
                try {
                    val json = JSONObject(data.extra)
                    json.optBoolean("handled", false)
                }catch (ex: Exception) {
                    false
                }
            }
            if (!isHandled) {
                val groupModel = GroupLogic.getModel(messageRecord.gid)
                val list = groupModel?.getJoinRequest(messageRecord.indexId)?: listOf()
                var have = false
                if (list.isEmpty()) {
                    have = true
                }else {
                    for (it in list) {
                        if (it.status == BcmGroupJoinStatus.WAIT_OWNER_REVIEW) {
                            have = true
                            break
                        }
                    }
                }
                isHandled = !have
                ALog.d(TAG, "isHandled: $isHandled after gertJoinRequest")
                if (isHandled) {
                    updateGroupJoinRequestTip(messageRecord, data, true)
                }
            }
            val part = if (isHandled) {
                context.getString(R.string.common_chats_group_request_handled_description)
            }else {
                context.getString(R.string.common_chats_group_request_detail_description)
            }
            val span = SpannableStringBuilder(text)
            span.append(StringAppearanceUtil.applyAppearance(context, StringAppearanceUtil.applyAppearance(part, color = getColor(R.color.common_color_black)), true))
            itemView.chats_tips?.text = span
            itemView.chats_tips?.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick) {
                    return@setOnClickListener
                }
                BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_JOIN_CHECK)
                        .putLong(ARouterConstants.PARAM.PARAM_INDEX_ID, messageRecord.indexId)
                        .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, messageRecord.gid).navigation(context)
            }

        }else {
            itemView.chats_tips?.text = text
            itemView.chats_tips?.setOnClickListener(null)
        }
    }

    private fun updateGroupJoinRequestTip(messageRecord: AmeGroupMessageDetail?, systemContent: AmeGroupMessage.SystemContent, isHandled: Boolean) {
        try {
            if (messageRecord == null) {
                return
            }
            AmeDispatcher.io.dispatch {
                val extra = JSONObject()
                extra.put("handled", isHandled)
                val content = AmeGroupMessage.SystemContent(systemContent.tipType, systemContent.sender, systemContent.theOperator, extra.toString())
                messageRecord.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content)
                MessageDataManager.insertReceiveMessage(GroupMessageTransform.transformToEntity(messageRecord))
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "handleGroupJoinRequestTip error", ex)
        }
    }
}