package com.bcm.messenger.chats.bean

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.AlertView
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.api.BindableConversationListItem
import com.bcm.messenger.common.api.Unbindable
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.GroupNameOrAvatarChanged
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.chats_list_item_view.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*


class MessageListItem @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs),
        RecipientModifiedListener, BindableConversationListItem, Unbindable {

    private val TAG = "MessageListItem"
    private var mLocale: Locale? = null
    private var selectedThreads: Set<Long>? = null

    var recipient: Recipient? = null
        private set

    var threadId: Long = 0L
        private set

    private var glideRequests: GlideRequests? = null

    private lateinit var subjectView: TextView
    private lateinit var fromView: TextView
    private lateinit var dateView: TextView
    private lateinit var alertView: AlertView
    private lateinit var contactPhotoImage: RecipientAvatarView
    private lateinit var ring:View

    var groupId = 0L
        private set

    private lateinit var threadRecord: ThreadRecord
    private var lastThread: ThreadRecord? = null

    var lastSeen: Long = 0
        private set

    var unreadCount: Int = 0
        private set

    var distributionType: Int = 0
        private set

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        this.subjectView = findViewById(R.id.subject)
        this.fromView = findViewById(R.id.from)
        this.dateView = findViewById(R.id.date)
        this.alertView = findViewById(R.id.indicators_parent)
        this.contactPhotoImage = findViewById(R.id.contact_photo_image)
        this.ring = findViewById(R.id.contact_burn_ring)

    }

    override fun bind(masterSecret: MasterSecret, threadRecord: ThreadRecord,
                      glideRequests: GlideRequests, locale: Locale,
                      selectedThreads: Set<Long>, batchMode: Boolean) {

        reset()
        this.mLocale = locale
        this.threadRecord = threadRecord
        this.selectedThreads = selectedThreads
        this.threadId = threadRecord.id
        this.glideRequests = glideRequests
        this.unreadCount = threadRecord.unreadCount
        this.distributionType = threadRecord.distributionType
        this.lastSeen = threadRecord.lastSeenTime
        if (threadRecord.getRecipient(masterSecret.accountContext).isGroupRecipient) {
            setGroup(masterSecret.accountContext, threadRecord, lastThread?.id == threadRecord.id)
        } else {
            setPrivate(masterSecret.accountContext, threadRecord, lastThread?.id == threadRecord.id)
        }
        setBatchState(batchMode)
        setMessageBody(masterSecret.accountContext, threadRecord)
    }

    override fun unbind() {
        this.recipient?.removeListener(this)
        reset()
    }

    fun checkPin(): Boolean {
        return if (::threadRecord.isInitialized) {
            threadRecord.pinTime > 0L
        } else {
            false
        }
    }

    /**
     * 重置UI
     */
    private fun reset() {
        this.groupId = 0
        group_live_icon?.visibility = View.GONE
        this.contactPhotoImage.clear()
    }

    private fun setMessageBody(accountContext: AccountContext, record: ThreadRecord) {

        val unreadDescription = getUnreadCountString(unreadCount)
        val displayBody = getDisplayBody(accountContext, this.threadRecord)

        if (record.timestamp > 0) {
            val date = DateUtils.getThreadMessageTimeSpan(context, record.timestamp, getSelectedLocale(AppContextHolder.APP_CONTEXT))
            if (dateView.text != date) {
                dateView.text = date
            }
        }

        val builder = SpannableStringBuilder().append(unreadDescription)
        if (record.isJoinRequestMessage() && !record.isRead()) {
            // The priority of the group join request is higher than @
            builder.append(createNewJoinRequestDescription())
        } else if (record.isAtMeMessage() && !record.isRead()) {
            val desc = StringAppearanceUtil.applyAppearance(AppContextHolder.APP_CONTEXT.getString(R.string.chats_at_me_description),
                    color = context.getAttrColor(R.attr.common_text_warn_color))
            builder.append(desc)
        }
        builder.append(displayBody)
        if (this.subjectView.text != builder) {
            ALog.d(TAG, "setMessageBody record: ${record.id}, actual body: $builder, after isAtMe")
            this.subjectView.text = builder
        }

    }

    private fun createNewJoinRequestDescription(): CharSequence {
        return StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_new_join_request_description),
                color = context.getAttrColor(R.attr.common_text_warn_color))
    }

    private fun getUnreadCountString(unreadCount: Int): String {
        return when {
            unreadCount > 99 -> context.resources.getString(R.string.chats_unread_full_messages)
            unreadCount == 1 -> context.getString(R.string.chats_unread_message)
            unreadCount > 1 -> context.resources.getString(R.string.chats_unread_messages, unreadCount)
            else -> ""
        }
    }

    fun clearUnreadCount(accountContext: AccountContext) {
        this.unreadCount = 0
        threadRecord.unreadCount = 0
        setMessageBody(accountContext, threadRecord)
        if (recipient?.isGroupRecipient == true) {
            groupId = recipient?.groupId ?: -1L
            setGroup(accountContext, threadRecord, false)
        } else {
            setPrivate(accountContext, threadRecord, false)
        }
    }

    private fun getDisplayBody(accountContext: AccountContext, record: ThreadRecord): CharSequence {
        if (record.messageCount < 0) {
            return ""
        }
        var builder: SpannableStringBuilder? = null
        if (record.isPending()) {
            builder = SpannableStringBuilder()
            val drawable = AppUtil.getDrawable(resources, R.drawable.chats_message_list_sending_icon)
            drawable.setTint(context.getAttrColor(R.attr.common_icon_color_grey))
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            builder.append(StringAppearanceUtil.addImage("  ", drawable, 0))
        } else if (record.isFailed() || record.isPendingInsecureFallback()) {
            builder = SpannableStringBuilder()
            builder.append(StringAppearanceUtil.addImage(context, "  ", R.drawable.chats_message_list_warn_icon, 12.dp2Px(), 0))
        }

        val text: CharSequence = try {
            if (record.isDraftMessage()) {
                if (record.snippetContent.isEmpty()) {
                    ""
                } else {
                    builder = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(context.getString(R.string.common_thread_draft), color = context.getAttrColor(R.attr.common_text_warn_color)))
                    builder.append(" ")
                    record.snippetContent
                }
            } else if (record.isGroup()) {
                val message = record.getGroupMessage()
                if (message != null) {

                    if (message.isLiveMessage()) {
                        val owner = GroupLogic.get(accountContext).getGroupInfo(groupId)?.owner
                        if (owner != null) {
                            val r = Recipient.from(AMELogin.majorContext, owner, true)
                            val des = (message.content as AmeGroupMessage.LiveContent).getDescription(accountContext, r)
                            message.content.setRecipientCallback(accountContext, this)
                            des
                        } else {
                            ""
                        }
                    } else {
                        message.content.setRecipientCallback(accountContext, this)
                        val text = message.content.getDescribe(groupId, accountContext)
                        text
                    }
                } else {
                    ""
                }
            } else {
                if (record.isExpirationTimerUpdate()) {
                    if (record.isOutgoing()) {
                        resources.getString(R.string.chats_read_burn_detail_by_you,
                                ExpirationUtil.getExpirationDisplayValue(context, (record.expiresTime / 1000).toInt()))
                    } else {
                        resources.getString(R.string.chats_read_burn_detail,
                                record.getRecipient(accountContext).name, ExpirationUtil.getExpirationDisplayValue(context, (record.expiresTime / 1000).toInt()))
                    }
                } else if (record.isDecrypting()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET + context.getString(R.string.chats_message_decrypting_description) + ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isLocation()) {
                    val message = record.getGroupMessage()
                    if (message != null) {
                        message.content.setRecipientCallback(accountContext, this)
                        val text = if (message.type == AmeGroupMessage.CONTROL_MESSAGE) {
                            val content = message.content as AmeGroupMessage.ControlContent
                            if (content.actionCode == AmeGroupMessage.ControlContent.ACTION_CLEAR_MESSAGE) {
                                if (record.isFailed() || record.isPendingInsecureFallback()) {
                                    context.getString(R.string.common_chats_you_clear_history_fail_tip)
                                } else if (record.isPending()) {
                                    context.getString(R.string.common_chats_you_clearing_history_tip)
                                } else {
                                    content.getDescribe(groupId, accountContext)
                                }
                            } else {
                                content.getDescribe(groupId, accountContext)
                            }
                        } else {
                            message.content.getDescribe(groupId, accountContext)
                        }
                        text
                    } else {
                        ""
                    }
                } else if (record.isKeyExchange()) {
                    record.snippetContent
                } else if (record.isDecryptFail()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET + context.getString(R.string.chats_message_decrypt_fail_description) + ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isNoSession()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET + context.getString(R.string.chats_message_is_not_remote_description) + ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isEndSession()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET + context.getString(R.string.chats_message_end_session_description) + ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isLegacy()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET +
                            context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported) +
                            ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isMissedCall()) {
                    val text = if (record.isOutgoingMissedCall()) {
                        resources.getString(R.string.chats_call_unanswer_message_description)
                    } else {
                        resources.getString(R.string.chats_call_untook_message_description)
                    }
                    ARouterConstants.CONSTANT_LEFT_BRACKET + text +
                            ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.isCallLog()) {
                    ARouterConstants.CONSTANT_LEFT_BRACKET + context.getString(R.string.chats_message_call_description) +
                            ARouterConstants.CONSTANT_RIGHT_BRACKET
                } else if (record.snippetUri != null && record.snippetUri.toString().isNotEmpty()) {
                    val contentType = record.snippetContent
                    when {
                        MediaUtil.isImageType(contentType) -> context.getString(R.string.common_image_message_description)
                        MediaUtil.isVideoType(contentType) -> context.getString(R.string.common_video_message_description)
                        MediaUtil.isAudioType(contentType) -> context.getString(R.string.common_audio_message_description)
                        contentType.isNotEmpty() -> context.getString(R.string.common_file_message_description)
                        else -> ARouterConstants.CONSTANT_LEFT_BRACKET +
                                context.getString(R.string.chats_message_media_description) +
                                ARouterConstants.CONSTANT_RIGHT_BRACKET
                    }
                } else {
                    record.snippetContent
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "getDisplayBodyText fail", ex)
            ""
        }
        return builder?.append(text) ?: text
    }


    private fun setBatchState(batch: Boolean) {
        isSelected = batch && selectedThreads?.contains(threadId) == true
    }

    private fun setPrivate(accountContext: AccountContext, threadRecord: ThreadRecord, anim: Boolean) {
        this.groupId = 0
        this.lastThread = threadRecord
        this.recipient = threadRecord.getRecipient(accountContext)
        this.recipient?.addListener(this)
        this.fromView.text = recipient?.name ?: ""
        this.fromView.setCompoundDrawables(null, null, null, null)
        this.recipient?.let {
            this.contactPhotoImage.showPrivateAvatar(it)
        }
        ALog.logForSecret(TAG, "setPrivate thread: ${threadRecord.id}, address: ${this.recipient?.address}")
        if (threadRecord.isOutgoing()) {
            alertView.setNone()
        }
        if (this.recipient?.isMuted == true) {
            alertView.setNotificationAlert(unreadCount, true, anim && lastThread?.id == threadRecord.id)
        } else {
            alertView.setNotificationAlert(unreadCount, false, anim && lastThread?.id == threadRecord.id)
        }

        if (this.recipient?.expireMessages?:0 > 0) {
            ring.visibility = View.VISIBLE
        } else {
            ring.visibility = View.GONE
        }

    }

    private fun setGroup(accountContext: AccountContext, threadRecord: ThreadRecord, anim: Boolean) {
        ring.visibility = View.GONE
        this.lastThread = threadRecord
        this.recipient = threadRecord.getRecipient(accountContext)
        this.recipient?.addListener(this)
        this.groupId = this.recipient?.groupId ?: 0L
        ALog.logForSecret(TAG, "setGroup thread: ${threadRecord.id}, groupId: $groupId")
        val groupInfo = GroupLogic.get(accountContext).getGroupInfo(groupId)
        this.contactPhotoImage.showGroupAvatar(accountContext, groupId)
        this.fromView.text = groupInfo?.displayName ?: ""
        if (null != groupInfo) {
            if (groupInfo.legitimateState == AmeGroupInfo.LegitimateState.ILLEGAL) {
                alertView.setNone()
            } else {
                if (groupInfo.mute) {
                    alertView.setNotificationAlert(unreadCount, true, anim)
                } else {
                    alertView.setNotificationAlert(unreadCount, false, anim)
                }
            }
            if (threadRecord.liveState == 1) {
                group_live_icon.visibility = View.VISIBLE
            } else {
                group_live_icon.visibility = View.GONE
            }
        } else {
            ALog.i(TAG, "setGroup thread: ${threadRecord.id}, groupId: $groupId, groupInfo is null")
        }
    }

    override fun onModified(recipient: Recipient) {
        ALog.d(TAG, "onModified address: ${recipient.address}")
        post {
            if (this.recipient == recipient) {
                if (!recipient.isGroupRecipient) {
                    setPrivate(AMELogin.majorContext, threadRecord, false)
                } else {
                    setGroup(AMELogin.majorContext, threadRecord, false)
                }
            }
            setMessageBody(AMELogin.majorContext, threadRecord)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupViewModel.GroupMuteEnableEvent) {
        ALog.d(TAG, "receive group notificationEnableEvent")
        if (distributionType == ThreadRepo.DistributionTypes.NEW_GROUP) {
            setGroup(AMELogin.majorContext, threadRecord, true)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupNameOrAvatarChanged) {
        ALog.d(TAG, "Receive new group name and/or avatar")
        if (event.gid == groupId) {
            if (event.name.isNotBlank()) {
                fromView.text = event.name
            }
            if (event.avatarPath.isNotBlank()) {
                contactPhotoImage.showGroupAvatar(AMELogin.majorContext, event.gid, path = event.avatarPath)
            }
        }
    }
}
