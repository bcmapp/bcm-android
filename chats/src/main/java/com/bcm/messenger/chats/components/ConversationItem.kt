package com.bcm.messenger.chats.components

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.*
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.forward.ForwardActivity
import com.bcm.messenger.chats.history.ChatHistoryActivity
import com.bcm.messenger.chats.privatechat.jobs.AttachmentDownloadJob
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.chats.util.*
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.api.BindableConversationItem
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.MultiSelectEvent
import com.bcm.messenger.common.expiration.ExpirationManager
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.views.Stub
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.util.*


/**
 * private chat item
 * @author lishuangling
 */
class ConversationItem @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        LinearLayout(context, attrs), RecipientModifiedListener, BindableConversationItem, View.OnLongClickListener {

    companion object {

        private const val TAG = "ConversationItem"

        @Volatile
        private var BODY_MAX_WIDTH = 0

        fun getBodyMaxWidth(): Int {
            if (BODY_MAX_WIDTH == 0) {
                BODY_MAX_WIDTH = AppContextHolder.APP_CONTEXT.getScreenWidth() - 130.dp2Px()
            }
            return BODY_MAX_WIDTH
        }
    }

    private lateinit var messageRecord: MessageRecord
    private lateinit var masterSecret: MasterSecret
    private lateinit var locale: Locale
    private var groupThread: Boolean = false
    private lateinit var glideRequests: GlideRequests

    private var batchSelected: MutableSet<MessageRecord>? = null

    private var bodyBubble: View? = null
    private var paddingTop: View? = null

    private var bodyTextView: EmojiTextView? = null
    private var unSupportMessageView: TextView? = null
    private var groupSender: TextView? = null
    private var groupSenderHolder: View? = null
    private var contactPhoto: IndividualAvatarView? = null
    private var alertView: AlertView? = null
    private var specialNotifyLayout: View? = null
    private var specialNotifyText: TextView? = null
    private var selectedView: ImageView? = null

    private lateinit var mediaThumbnailStub: Stub<ChatThumbnailView>
    private lateinit var audioViewStub: Stub<ChatAudioView>
    private lateinit var documentViewStub: Stub<ChatDocumentView>
    private lateinit var newGroupShareViewWrapperStub: Stub<ShareChannelView>
    private lateinit var contactViewStub: Stub<ContactCardView>
    private lateinit var historyViewStub: Stub<HistoryView>
    private lateinit var mapShareStub: Stub<MapShareView>
    private lateinit var mGroupShareViewStub: Stub<GroupShareCardView>


    private lateinit var conversationRecipient: Recipient

    private lateinit var messageRecipient: Recipient

    private var mBreatheAnim = AlphaAnimation(0.5f, 0.3f)

    private val downloadClickListener = object : ChatComponentListener {
        override fun onClick(v: View, data: Any) {
            if (data is MessageRecord) {
                data.attachments.forEach {
                    Repository.getAttachmentRepo(getAccountContext())?.setTransferStateAsync(it, AttachmentDbModel.TransferState.STARTED)
                    AmeModuleCenter.accountJobMgr(getAccountContext())?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, getAccountContext(), messageRecord.id,
                            it.id, it.uniqueId, true))
                }
            }
        }
    }

    private val previewClickListener = object : ChatPreviewClickListener(getAccountContext()) {
        override fun onClick(v: View, data: Any) {
            when {
                messageRecord.isMediaFailed() -> {
                    downloadClickListener.onClick(v, data)
                }
                messageRecord.isMediaDeleted() -> {
                    (context as? FragmentActivity)?.let {
                        if (messageRecord.getVideoAttachment() != null) {
                            AmePopup.result.failure(it, context.getString(R.string.chats_media_view_video_expire), true)
                        } else {
                            AmePopup.result.failure(it, context.getString(R.string.chats_media_view_image_expire), true)
                        }
                    }
                }
                !shouldInterceptClicks(messageRecord) -> {
                    super.onClick(v, data)
                }
            }
        }
    }

    private val multiClickListener = MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
        override fun onMultiClick(view: View?, count: Int) {
            view?.hideKeyboard()
            BigContentRecycleFragment.showBigContent(context as AccountSwipeBaseActivity, messageRecord.threadId, messageRecord.id, masterSecret)
        }
    })

    private val isInMultiSelectMode: Boolean
        get() = selectedView?.visibility == View.VISIBLE

    fun getAccountContext(): AccountContext {
        return AMELogin.majorContext
    }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(ClickListener(l))
    }

    override fun onLongClick(v: View?): Boolean {
        if (v == null) {
            return true
        }
        when (v) {
            is TextView -> {
                showPopWindowForTextItem(messageRecord, v, v.text.toString(), true)
            }
            is ContactCardView -> {
                showPopWindowForTextItem(messageRecord, v, forwardable = false)
            }
            is ShareChannelView -> {
                showPopWindowForTextItem(messageRecord, v)
            }
            else -> {
                showPopWindowForMediaItem(messageRecord, v)
            }
        }
        return true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        this.paddingTop = findViewById(R.id.item_padding_top)
        this.bodyTextView = findViewById(R.id.conversation_item_body)
        this.unSupportMessageView = findViewById(R.id.conversation_item_un_support_message)
        this.groupSender = findViewById(R.id.conversation_recipient_name)
        this.alertView = findViewById(R.id.indicators_parent)
        this.contactPhoto = findViewById(R.id.conversation_recipient_photo)
        this.bodyBubble = findViewById(R.id.body_bubble)
        this.groupSenderHolder = findViewById(R.id.group_sender_holder)
        this.specialNotifyLayout = findViewById(R.id.special_notify_layout)
        this.specialNotifyText = findViewById(R.id.special_notify_text)
        this.contactViewStub = Stub(findViewById(R.id.contact_view_stub))
        this.mGroupShareViewStub = Stub(findViewById(R.id.group_share_view_stub))
        this.selectedView = findViewById(R.id.conversation_selected)

        this.mediaThumbnailStub = Stub(findViewById(R.id.image_view_stub))
        this.audioViewStub = Stub(findViewById(R.id.audio_view_stub))
        this.documentViewStub = Stub(findViewById(R.id.document_view_stub))
        this.newGroupShareViewWrapperStub = Stub(findViewById(R.id.group_share_wrapper_view_stub))
        this.mapShareStub = Stub(findViewById(R.id.map_view_stub))
        this.historyViewStub = Stub(findViewById(R.id.history_view_stub))

        setOnClickListener(ClickListener(null))
    }

    override fun bind(masterSecret: MasterSecret,
                      messageRecord: MessageRecord,
                      glideRequests: GlideRequests,
                      locale: Locale,
                      batchSelected: MutableSet<MessageRecord>?,
                      conversationRecipient: Recipient,
                      position: Int) {

        recycleData()
        this.masterSecret = masterSecret
        this.messageRecord = messageRecord
        this.locale = locale
        this.glideRequests = glideRequests
        this.batchSelected = batchSelected
        this.conversationRecipient = conversationRecipient
        this.groupThread = conversationRecipient.isGroupRecipient
        this.messageRecipient = messageRecord.getRecipient(getAccountContext())
        this.messageRecipient.addListener(this)

        resetItemState()
        updateMessageStatus(messageRecord)
        updateMessageContent(messageRecord)
        updateMessageText(messageRecord)
        setContactPhoto(messageRecipient)
        checkGroupMessage(messageRecord, messageRecipient)
        setExpiration(messageRecord)
        setInteractionState(messageRecord)

        if (position == 0) {
            layoutParams = (layoutParams as RecyclerView.LayoutParams).apply {
                bottomMargin = 15.dp2Px()
            }
        } else {
            layoutParams = (layoutParams as RecyclerView.LayoutParams).apply {
                bottomMargin = 0
            }
        }
    }

    override fun unbind() {
        recycleData()
        alertView?.stopExpiration()
        mBreatheAnim.cancel()
    }

    override fun onDetachedFromWindow() {
        alertView?.stopExpiration()
        super.onDetachedFromWindow()
    }

    private fun recycleData() {
        if (::messageRecipient.isInitialized) {
            messageRecipient.removeListener(this)
        }
    }

    override fun getMessageRecord(): MessageRecord? {
        return messageRecord
    }

    private fun setInteractionState(messageRecord: MessageRecord) {

        val isInMultiSelectMode = batchSelected != null && specialNotifyLayout?.visibility != View.VISIBLE
        selectedView?.visibility = if (isInMultiSelectMode) {
            if (messageRecord.isLocation()) {
                val content = AmeGroupMessage.messageFromJson(messageRecord.body)
                if (content.isExchangeProfile()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            } else {
                if (isInMultiSelectMode) View.VISIBLE else View.GONE
            }
        } else View.GONE

        if (isInMultiSelectMode) {
            selectedView?.isSelected = batchSelected?.contains(messageRecord) ?: false
        }

        if (mediaThumbnailStub.resolved()) {
            mediaThumbnailStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            mediaThumbnailStub.get().isClickable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            mediaThumbnailStub.get().isLongClickable = !isInMultiSelectMode
        }

        if (audioViewStub.resolved()) {
            audioViewStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            audioViewStub.get().isClickable = !isInMultiSelectMode
            audioViewStub.get().isEnabled = !isInMultiSelectMode
            audioViewStub.get().isLongClickable = !isInMultiSelectMode
        }

        if (documentViewStub.resolved()) {
            documentViewStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            documentViewStub.get().isClickable = !isInMultiSelectMode
            documentViewStub.get().isLongClickable = !isInMultiSelectMode
        }

        if (mapShareStub.resolved()) {
            mapShareStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            mapShareStub.get().isClickable = !isInMultiSelectMode
            mapShareStub.get().isLongClickable = !isInMultiSelectMode
        }

        if (newGroupShareViewWrapperStub.resolved()) {
            newGroupShareViewWrapperStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            newGroupShareViewWrapperStub.get().isClickable = !isInMultiSelectMode
            newGroupShareViewWrapperStub.get().isLongClickable = !isInMultiSelectMode
        }

        if (contactViewStub.resolved()) {
            contactViewStub.get().isFocusable = !shouldInterceptClicks(messageRecord) && !isInMultiSelectMode
            contactViewStub.get().isClickable = !isInMultiSelectMode
            contactViewStub.get().isLongClickable = !isInMultiSelectMode
        }

        bodyTextView?.isFocusable = !isInMultiSelectMode
        bodyTextView?.isClickable = !isInMultiSelectMode
        bodyTextView?.isLongClickable = !isInMultiSelectMode
    }

    private fun isCaptionlessMms(messageRecord: MessageRecord): Boolean {
        return messageRecord.getDisplayBody().isEmpty() && messageRecord.isMediaMessage()
    }

    private fun hasAudio(messageRecord: MessageRecord): Boolean {
        return messageRecord.isMediaMessage() && messageRecord.hasAudios()
    }

    private fun hasThumbnail(messageRecord: MessageRecord): Boolean {
        return messageRecord.isMediaMessage() && messageRecord.hasImages()
    }

    private fun hasVideo(messageRecord: MessageRecord): Boolean {
        return messageRecord.isMediaMessage() && messageRecord.hasVideos()
    }

    private fun hasDocument(messageRecord: MessageRecord): Boolean {
        return messageRecord.isMediaMessage() && messageRecord.hasDocuments()
    }

    private fun updateMessageText(messageRecord: MessageRecord) {
        ALog.d(TAG, "Conversation messageRecord duration: ${messageRecord.callDuration}, type: ${messageRecord.type}")
        val callContent = resources.getString(R.string.chats_call_finish_message_holder, DateUtils.convertMinuteAndSecond(messageRecord.callDuration.toLong()))
        val d: Drawable
        val drawableSize = 20.dp2Px()
        if (messageRecord.isMissedCall()) {
            var text: CharSequence
            if (messageRecord.isOutgoingMissedCall()) {
                d = AppUtil.getDrawable(resources, R.drawable.chats_message_call_sent_icon)
                d.setBounds(0, 0, drawableSize, drawableSize)
                text = resources.getString(R.string.chats_call_unanswer_message_description)
                text = "$text  "
                bodyTextView?.text = StringAppearanceUtil.addImage(text, d, text.length - 1)
            } else {
                d = AppUtil.getDrawable(resources, R.drawable.chats_message_call_received_icon)
                d.setBounds(0, 0, drawableSize, drawableSize)
                text = resources.getString(R.string.chats_call_untook_message_description)
                text = "  $text"
                bodyTextView?.text = StringAppearanceUtil.addImage(text, d, 0)
            }

        } else if (messageRecord.isOutgoingCall()) {
            d = AppUtil.getDrawable(resources, R.drawable.chats_message_call_sent_icon)
            d.setBounds(0, 0, drawableSize, drawableSize)
            val t = "$callContent  "
            bodyTextView?.text = StringAppearanceUtil.addImage(t, d, t.length - 1)

        } else if (messageRecord.isIncomingCall()) {
            d = AppUtil.getDrawable(resources, R.drawable.chats_message_call_received_icon)
            d.setBounds(0, 0, drawableSize, drawableSize)
            bodyTextView?.text = StringAppearanceUtil.addImage("  $callContent", d, 0)

        } else if (isCaptionlessMms(messageRecord)) {
            bodyTextView?.visibility = View.GONE
        } else if (messageRecord.isLocation()) {
            bodyTextView?.visibility = View.GONE
        } else {
            val spannableString = messageRecord.getDisplayBody()
            if (spannableString.toString().isNotEmpty()) {
                bodyTextView?.text = spannableString
                bodyTextView?.visibility = View.VISIBLE

                bodyTextView?.setOnClickListener(multiClickListener)
                bodyTextView?.setOnLongClickListener(this)
                bodyTextView?.setOnTouchListener(ClickSpanTouchHandler.getInstance(context))

            } else {
                bodyTextView?.visibility = View.GONE
                paddingTop?.visibility = View.GONE
                return
            }
        }

        bodyTextView?.let {
            interceptLink(it, messageRecord.isOutgoing())
            val text = it.text ?: ""
            val staticLayout = StaticLayout(text, 0, text.length, it.paint, getBodyMaxWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false,
                    TextUtils.TruncateAt.END, getBodyMaxWidth())
            val w = requestFitWidth(staticLayout)
            if (w > 0) {
                val lp = it.layoutParams
                if (lp != null) {
                    lp.width = w + it.paddingStart + it.paddingEnd
                    it.layoutParams = lp
                }
            }
        }

    }

    private fun updateMessageContent(messageRecord: MessageRecord) {
        when {
            hasAudio(messageRecord) -> handleAudioMessage(messageRecord)
            hasDocument(messageRecord) -> handleDocumentMessage(messageRecord)
            hasThumbnail(messageRecord) || hasVideo(messageRecord) -> handleThumbnailMessage(messageRecord)
            messageRecord.isLocation() -> {
                val message = AmeGroupMessage.messageFromJson(messageRecord.body)
                when (message.type) {
                    AmeGroupMessage.LOCATION -> handleLocationMessage(message)
                    AmeGroupMessage.SHARE_CHANNEL -> handleChannelMessage(message)
                    AmeGroupMessage.NEWSHARE_CHANNEL -> handleNewChannelMessage(message)
                    AmeGroupMessage.CONTROL_MESSAGE -> handleControlMessage(messageRecord, message)
                    AmeGroupMessage.SCREEN_SHOT_MESSAGE -> handleScreenShotMessage(message)
                    AmeGroupMessage.CONTACT -> handleContactMessage(message)
                    AmeGroupMessage.GROUP_SHARE_CARD -> handleGroupShareMessage(message)
                    AmeGroupMessage.FRIEND -> handleFriendMessage(message)
                    AmeGroupMessage.SYSTEM_INFO -> handleSystemMessage(message)
                    AmeGroupMessage.CHAT_HISTORY -> handleHistoryMessage(message)
                    AmeGroupMessage.EXCHANGE_PROFILE, AmeGroupMessage.RECEIPT -> handleNotShowMessage()
                    else -> handleUnSupportMessage(message)
                }
            }
            else -> {
                updateAlpha(messageRecord, bodyTextView, null)
            }
        }
    }


    private fun updateAlpha(messageRecord: MessageRecord, bodyView: View?, slide: AttachmentDbModel?) {
        if (bodyView == null) {
            return
        }
        if (!messageRecord.isOutgoing()) {
            bodyView.animation?.cancel()
            bodyView.clearAnimation()
            bodyView.alpha = 1.0f
        } else if (messageRecord.isFailed() || messageRecord.isPendingInsecureFallback()) {
            ALog.d(TAG, "updateAlpha fail id: ${messageRecord.id}")
            bodyView.animation?.cancel()
            bodyView.clearAnimation()
            bodyView.alpha = 0.5f
        } else if (messageRecord.isPending() || slide?.transferState == AttachmentDbModel.TransferState.STARTED.state) {
            bodyView.alpha = 0.5f
            mBreatheAnim.duration = 1000
            mBreatheAnim.repeatCount = Animation.INFINITE
            mBreatheAnim.repeatMode = Animation.REVERSE
            bodyView.startAnimation(mBreatheAnim)

        } else {
            ALog.d(TAG, "updateAlpha complete id: ${messageRecord.id}")
            bodyView.animation?.cancel()
            bodyView.clearAnimation()
            bodyView.alpha = 1.0f
        }
    }

    private fun handleUnSupportMessage(message: AmeGroupMessage<*>) {
        if (message.type == AmeGroupMessage.NONSUPPORT) {
            unSupportMessageView?.visibility = View.VISIBLE
            val content = message.content as AmeGroupMessage.TextContent
            unSupportMessageView?.text = content.text
        }
    }

    private fun handleAudioMessage(messageRecord: MessageRecord) {
        val audioAttachment = messageRecord.getAudioAttachment()
        if (audioAttachment?.isPendingDownload() == true) {
            AmeModuleCenter.accountJobMgr(getAccountContext())?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, getAccountContext(), messageRecord.id,
                    audioAttachment.id, audioAttachment.uniqueId, false))
        }

        audioViewStub.get().visibility = View.VISIBLE
        audioViewStub.get().setAudio(masterSecret, messageRecord)
        audioViewStub.get().setOnLongClickListener(this)
        audioViewStub.get().setDownloadClickListener(downloadClickListener)

        if (messageRecord.isOutgoing()) {
            audioViewStub.get().setProgressDrawableResource(R.drawable.chats_audio_send_top_progress_bg)

            audioViewStub.get().setAudioAppearance(R.drawable.chats_audio_send_play_icon, R.drawable.chats_audio_send_pause_icon,
                    context.getColorCompat(R.color.chats_audio_send_decoration_color),
                    context.getColorCompat(R.color.common_color_white))

        } else {
            audioViewStub.get().setProgressDrawableResource(R.drawable.chats_audio_receive_top_progress_bg)
            audioViewStub.get().setAudioAppearance(R.drawable.chats_audio_receive_play_icon, R.drawable.chats_audio_receive_pause_icon,
                    context.getColorCompat(R.color.chats_audio_receive_decoration_color),
                    context.getColorCompat(R.color.common_color_black))
        }

        updateAlpha(messageRecord, audioViewStub.get(), audioAttachment)
    }

    private fun handleDocumentMessage(messageRecord: MessageRecord) {
        val docAttachment = messageRecord.getDocumentAttachment()
        if (docAttachment?.isPendingDownload() == true) {
            AmeModuleCenter.accountJobMgr(getAccountContext())?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, getAccountContext(), messageRecord.id,
                    docAttachment.id, docAttachment.uniqueId, false))
        }

        documentViewStub.get().visibility = View.VISIBLE
        documentViewStub.get().setDocument(messageRecord)
        documentViewStub.get().setDownloadClickListener(downloadClickListener)
        documentViewStub.get().setDocumentClickListener(previewClickListener)
        documentViewStub.get().setOnLongClickListener(this)

        updateAlpha(messageRecord, documentViewStub.get(), docAttachment)
    }

    private fun handleThumbnailMessage(messageRecord: MessageRecord) {
        val slide = messageRecord.getImageAttachment() ?: messageRecord.getVideoAttachment() ?: return
        if (slide.isPendingDownload()) {
            AmeModuleCenter.accountJobMgr(getAccountContext())?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, getAccountContext(), messageRecord.id,
                    slide.id, slide.uniqueId, false))
        }

        mediaThumbnailStub.get().visibility = View.VISIBLE
        val radius = resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius)
        if (MediaUtil.isVideo(slide.contentType)) {
            mediaThumbnailStub.get().setThumbnailAppearance(R.drawable.common_video_place_img, R.drawable.common_video_place_img, radius)
        } else {
            mediaThumbnailStub.get().setThumbnailAppearance(R.drawable.common_image_place_img, R.drawable.common_image_broken_img, radius)
        }

        mediaThumbnailStub.get().setImage(masterSecret, glideRequests, messageRecord)
        mediaThumbnailStub.get().setThumbnailClickListener(previewClickListener)
        mediaThumbnailStub.get().setDownloadClickListener(downloadClickListener)
        mediaThumbnailStub.get().setOnLongClickListener(this)

        updateAlpha(messageRecord, mediaThumbnailStub.get(), slide)
    }

    private fun handleLocationMessage(message: AmeGroupMessage<*>) {

        val content = message.content as AmeGroupMessage.LocationContent
        val mapShareView = mapShareStub.get()
        mapShareView.visibility = View.VISIBLE
        if (messageRecord.isOutgoing()) {
            mapShareView.setAppearance(R.color.common_color_white, true)
        } else {
            mapShareView.setAppearance(R.color.common_color_black, false)
        }
        mapShareView.setMap(glideRequests, content)
        mapShareView.setOnClickListener { v ->
            if (isInMultiSelectMode) {
                performMultiSelect()
            } else {
                val compat = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as AppCompatActivity, v, ShareElements.Activity.PREVIEW_MAP).toBundle()
                BcmRouter.getInstance().get(ARouterConstants.Activity.MAP_PREVIEW)
                        .putDouble(ARouterConstants.PARAM.MAP.LATITUDE, content.latitude)
                        .putDouble(ARouterConstants.PARAM.MAP.LONGTITUDE, content.longtitude)
                        .putInt(ARouterConstants.PARAM.MAP.MAPE_TYPE, content.mapType)
                        .putString(ARouterConstants.PARAM.MAP.TITLE, content.title)
                        .putString(ARouterConstants.PARAM.MAP.ADDRESS, content.address)
                        .setActivityOptionsCompat(compat)
                        .startBcmActivity(getAccountContext(), v.context)
            }
        }
        mapShareView.setOnLongClickListener {
            showPopWindowForTextItem(messageRecord, mapShareView)
            true
        }

        updateAlpha(messageRecord, mapShareStub.get(), null)
    }

    private fun handleChannelMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.ShareChannelContent
        val shareChannelView = newGroupShareViewWrapperStub.get()
        shareChannelView.visibility = View.VISIBLE
        if (messageRecord.isOutgoing()) {
            shareChannelView.setBackgroundResource(R.drawable.chats_conversation_send_bubble_bg)
            shareChannelView.setLinkAppearance(R.color.common_color_white, R.color.common_color_white, true)
        } else {
            shareChannelView.setBackgroundResource(R.drawable.chats_conversation_received_bubble_bg)
            shareChannelView.setLinkAppearance(R.color.common_color_black, R.color.common_color_379BFF, false)
        }
        shareChannelView.setTitleContent(content.name, content.intro)
        shareChannelView.setAvater(GroupUtil.addressFromGid(getAccountContext(), content.gid))
        shareChannelView.setLink(context.getString(R.string.chats_channel_share_describe), false)
        updateAlpha(messageRecord, newGroupShareViewWrapperStub.get(), null)
    }

    private fun handleNewChannelMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.NewShareChannelContent
        val shareChannelView = newGroupShareViewWrapperStub.get()
        shareChannelView.visibility = View.VISIBLE
        if (messageRecord.isOutgoing()) {
            shareChannelView.setBackgroundResource(R.drawable.chats_conversation_send_bubble_bg)
            shareChannelView.setLinkAppearance(R.color.common_color_white, R.color.common_color_white, true)
        } else {
            shareChannelView.setBackgroundResource(R.drawable.chats_conversation_received_bubble_bg)
            shareChannelView.setLinkAppearance(R.color.common_color_black, R.color.common_color_379BFF, false)
        }
        shareChannelView.setTitleContent(content.name, content.intro)
        shareChannelView.setAvater(GroupUtil.addressFromGid(getAccountContext(), content.gid))
        shareChannelView.setLink(content.channel, true)
        shareChannelView.setChannelClickListener(object : ShareChannelView.ChannelOnClickListener {
            override fun onClick(v: View) {
                if (batchSelected != null) {
                    performClick()
                }
            }

        })
        shareChannelView.setOnLongClickListener(this)

        updateAlpha(messageRecord, newGroupShareViewWrapperStub.get(), null)
    }

    private fun handleControlMessage(record: MessageRecord, message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.ControlContent
        content.setRecipientCallback(getAccountContext(), this)
        specialNotifyLayout?.visibility = View.VISIBLE
        if (content.actionCode == AmeGroupMessage.ControlContent.ACTION_CLEAR_MESSAGE) {
            if (record.isFailed() || record.isPendingInsecureFallback()) {
                specialNotifyText?.text = context.getString(R.string.common_chats_you_clear_history_fail_tip)
            } else if (record.isPending()) {
                specialNotifyText?.text = context.getString(R.string.common_chats_you_clearing_history_tip)
            } else {
                specialNotifyText?.text = content.getDescribe(0, getAccountContext())
            }
        } else {
            specialNotifyText?.text = content.getDescribe(0, getAccountContext())
        }
        alertView?.setNone()
    }

    private fun handleContactMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.ContactContent
        val contactView = contactViewStub.get()
        contactView.visibility = View.VISIBLE
        contactView.setContact(content, messageRecord.isOutgoing())
        contactView.setOnLongClickListener(this)
        updateAlpha(messageRecord, contactViewStub.get(), null)
    }

    private fun handleGroupShareMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.GroupShareContent
        val groupShareView = mGroupShareViewStub.get()
        groupShareView.visibility = View.VISIBLE
        groupShareView.bind(content, messageRecord.isOutgoing())
        groupShareView.setOnLongClickListener {
            showPopWindowForTextItem(messageRecord, groupShareView, content.shareLink ?: "", true)
            true
        }
        updateAlpha(messageRecord, mGroupShareViewStub.get(), null)
    }

    private fun handleScreenShotMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.ScreenshotContent
        specialNotifyLayout?.visibility = View.VISIBLE
        val tip = if (messageRecord.isOutgoing()) {
            context.getString(R.string.chats_you_screenshot_tip)
        } else {
            context.getString(R.string.chats_partner_screenshot_tip, content.name)
        }
        specialNotifyText?.text = tip
        alertView?.setNone()
    }

    private fun handleFriendMessage(message: AmeGroupMessage<*>) {
        specialNotifyLayout?.visibility = View.VISIBLE
        alertView?.setNone()
        val content = message.content as AmeGroupMessage.FriendContent
        content.setRecipientCallback(getAccountContext(), this)
        specialNotifyText?.text = content.getDescribe(0, getAccountContext())
    }


    private fun handleSystemMessage(message: AmeGroupMessage<*>) {
        specialNotifyLayout?.visibility = View.VISIBLE
        alertView?.setNone()
        val content = message.content as AmeGroupMessage.SystemContent
        content.setRecipientCallback(getAccountContext(), this)
        specialNotifyText?.text = content.getDescribe(0, getAccountContext())
        if (content.tipType == AmeGroupMessage.SystemContent.TIP_CHAT_STRANGER_RESTRICTION && !messageRecipient.isFriend) {
            val span = SpannableStringBuilder(content.getDescribe(0, getAccountContext()))
            var addSpan = StringAppearanceUtil.applyAppearance(context.getString(R.string.chats_relation_add_friend_action), color = getColor(R.color.common_color_black))
            addSpan = StringAppearanceUtil.applyAppearance(context, addSpan, true)
            span.append(addSpan)
            specialNotifyText?.text = span
            specialNotifyText?.setOnClickListener {
                BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND)
                        .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, messageRecipient.address).startBcmActivity(getAccountContext(), context)
            }
        }
    }

    private fun handleHistoryMessage(message: AmeGroupMessage<*>) {
        val content = message.content as AmeGroupMessage.HistoryContent
        val historyView = historyViewStub.get()
        historyView.visibility = View.VISIBLE
        if (messageRecord.isOutgoing()) {
            historyView.setStyle(IN_VIEW_CHAT_SEND)
        } else {
            historyView.setStyle(IN_VIEW_CHAT_RECEIVE)
        }
        historyView.bindData(getAccountContext(), content.messageList)
        historyView.setOnClickListener {
            val intent = Intent(context, ChatHistoryActivity::class.java)
            intent.putExtra(ChatHistoryActivity.CHAT_HISTORY_GID, ARouterConstants.PRIVATE_TEXT_CHAT)
            intent.putExtra(ChatHistoryActivity.MESSAGE_INDEX_ID, messageRecord.id)
            context.startBcmActivity(getAccountContext(), intent)
        }
        historyView.setOnLongClickListener {
            showPopWindowForTextItem(messageRecord, historyView, forwardable = false)
            return@setOnLongClickListener true
        }

        updateAlpha(messageRecord, historyViewStub.get(), null)
    }

    private fun handleNotShowMessage() {
        bodyTextView?.text = null
        alertView?.setNone()
        layoutParams = layoutParams.apply {
            setPadding(paddingStart, 0, paddingEnd, 0)
        }
        updateAlpha(messageRecord, bodyTextView, null)
    }

    private fun updateMessageStatus(messageRecord: MessageRecord) {
        bodyTextView?.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (messageRecord.isKeyExchange()) R.drawable.ic_menu_login else 0, 0)
        when {
            messageRecord.isFailed() -> setFailedStatusIcons()
            messageRecord.isMediaFailed() -> setFailedStatusIcons()
            messageRecord.isPendingInsecureFallback()-> setFailedStatusIcons()
            else -> alertView?.setNone()
        }
    }

    private fun resetItemState() {
        layoutParams = layoutParams.apply {
            setPadding(paddingStart, 5.dp2Px(), paddingEnd, 5.dp2Px())
        }

        if (mediaThumbnailStub.resolved()) {
            mediaThumbnailStub.get().visibility = View.GONE
        }
        if (audioViewStub.resolved()) {
            audioViewStub.get().visibility = View.GONE
        }
        if (documentViewStub.resolved()) {
            documentViewStub.get().visibility = View.GONE
        }

        if (newGroupShareViewWrapperStub.resolved()) {
            newGroupShareViewWrapperStub.get().visibility = View.GONE
        }
        if (mapShareStub.resolved()) {
            mapShareStub.get().visibility = View.GONE
        }
        if (contactViewStub.resolved()) {
            contactViewStub.get().visibility = View.GONE
        }
        if (historyViewStub.resolved()) {
            historyViewStub.get().visibility = View.GONE
        }
        if (mGroupShareViewStub.resolved()) {
            mGroupShareViewStub.get().visibility = View.GONE
        }
        specialNotifyLayout?.visibility = View.GONE
        specialNotifyText?.setOnClickListener(null)

        unSupportMessageView?.visibility = View.GONE
        paddingTop?.visibility = View.VISIBLE
    }

    private fun setContactPhoto(recipient: Recipient) {
        if (contactPhoto == null) {
            return
        }
        when {
            messageRecord.isOutgoing() -> contactPhoto?.visibility = View.GONE
            conversationRecipient.isGroupRecipient -> {
                contactPhoto?.setPhoto(recipient)
                contactPhoto?.visibility = View.VISIBLE
            }
            else -> contactPhoto?.visibility = View.GONE
        }
    }

    private fun setExpiration(messageRecord: MessageRecord) {
        if (messageRecord.isFailed()) return
        var shouldExpiration = true
        if (messageRecord.isLocation()) {
            val message = AmeGroupMessage.messageFromJson(messageRecord.body)
            if (message.type == AmeGroupMessage.CONTROL_MESSAGE) {
                shouldExpiration = false
            }
        }
        if (messageRecord.expiresTime > 0 && shouldExpiration) {
            if (messageRecord.expiresStartTime > 0) {
                alertView?.setExpirationTimer(messageRecord.expiresStartTime,
                        messageRecord.expiresTime)
                alertView?.setExpirationVisible(true)
                val color = if (messageRecord.getRecipient(getAccountContext()).expireMessages > 0) {
                    R.color.common_color_white_50
                } else {
                    R.color.common_color_88c0c0c0
                }
                alertView?.setExpirationColor(color)
            } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {

                Observable.create<Boolean> {
                    val id = messageRecord.id
                    Repository.getChatRepo(getAccountContext())?.setMessageExpiresStart(messageRecord.id)
                    ExpirationManager.scheduler(getAccountContext()).scheduleDeletion(id, messageRecord.isMediaMessage(), messageRecord.expiresTime)
                    it.onNext(true)
                    it.onComplete()

                }.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({

                        }, {
                            ALog.e(TAG, "setExpiration error", it)
                        })
            }
        } else {
            alertView?.setExpirationVisible(false)
        }
    }

    private fun setFailedStatusIcons() {
        alertView?.setFailed()
    }

    private fun shouldInterceptClicks(messageRecord: MessageRecord): Boolean {
        return messageRecord.isFailed() ||
                messageRecord.isPendingInsecureFallback() ||
                messageRecord.isBundleKeyExchange()
    }

    private fun checkGroupMessage(messageRecord: MessageRecord, recipient: Recipient) {
        if (groupThread && !messageRecord.isOutgoing()) {
            this.groupSender?.text = recipient.name
            this.groupSenderHolder?.visibility = View.VISIBLE
        } else {
            this.groupSenderHolder?.visibility = View.GONE
        }
    }

    override fun onModified(recipient: Recipient) {
        post {
            if (this.messageRecipient === recipient) {
                checkGroupMessage(messageRecord, recipient)
                setContactPhoto(recipient)
            }
            updateMessageContent(messageRecord)
        }
    }

    private fun interceptLink(tv: TextView, isOutgoing: Boolean) {
        if (tv.text is Spannable) {
            val end = tv.text.length
            val urlSpans = (tv.text as Spannable).getSpans(0, end, URLSpan::class.java)
            if (urlSpans.isEmpty()) {
                return
            }

            val builder = SpannableStringBuilder(tv.text)
            for (uri in urlSpans) {
                val url = uri.url
                if (url.indexOf("http://") == 0 || url.indexOf("https://") == 0) {
                    val linkSpan = LinkUrlSpan(context, url, isOutgoing)
                    builder.setSpan(linkSpan, (tv.text as Spannable).getSpanStart(uri), (tv.text as Spannable).getSpanEnd(uri), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                    builder.removeSpan(uri)
                } else if (url.startsWith("tel:")) {
                    val telUrlSpan = TelUrlSpan(Uri.parse(url))
                    builder.setSpan(telUrlSpan, (tv.text as Spannable).getSpanStart(uri), (tv.text as Spannable).getSpanEnd(uri), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }
            tv.text = builder
        }
    }

    private fun showPopWindowForMediaItem(messageRecord: MessageRecord, anchorView: View) {

        val slide = when {
            hasThumbnail(messageRecord) -> messageRecord.getImageAttachment()
            hasVideo(messageRecord) -> messageRecord.getVideoAttachment()
            hasDocument(messageRecord) -> messageRecord.getDocumentAttachment()
            else -> null
        }

        ConversationItemPopWindow.ItemPopWindowBuilder(context)
                .withAnchorView(anchorView)
                .withRecallVisible(!messageRecord.isFailed() && !messageRecord.isPending() && messageRecord.isOutgoing() && conversationRecipient.address.toString() != getAccountContext().uid)
                .withCopyVisible(false)
                .withForwardable(slide != null && slide.transferState == AttachmentDbModel.TransferState.DONE.state && !slide.isAudio()) // Only attachment downloaded and is not an audio can be forwarded
                .withReplyable(false)
                .withOutgoing(messageRecord.isOutgoing())
                .withClickListener(object : ConversationItemPopWindow.PopWindowClickListener {
                    override fun onRecall() {
                        AmeModuleCenter.chat(getAccountContext())?.recallMessage(context, false, messageRecord, null)
                    }

                    override fun onDelete() {
                        ThreadListViewModel.getThreadId(conversationRecipient) { threadId ->
                            AmeModuleCenter.chat(getAccountContext())?.deleteMessage(context, false, threadId, setOf(messageRecord), null)
                        }
                    }

                    override fun onCopy() {}

                    override fun onForward() {

                        BcmRouter.getInstance().get(ARouterConstants.Activity.FORWARD)
                                .putLong(ForwardActivity.INDEX_ID, messageRecord.id)
                                .putLong(ForwardActivity.GID, ARouterConstants.PRIVATE_MEDIA_CHAT)
                                .startBcmActivity(getAccountContext(), context)
                    }

                    override fun onSelect() {
                        val set = LinkedHashSet<MessageRecord>()
                        set.add(messageRecord)
                        EventBus.getDefault().post(MultiSelectEvent(false, set))
                    }

                    override fun onPin() {}

                    override fun onReply() {}
                }).build()
    }

    private fun showPopWindowForTextItem(messageRecord: MessageRecord, anchorView: View, text: String = "", copyVisible: Boolean = false, forwardable: Boolean = true) {

        ConversationItemPopWindow.ItemPopWindowBuilder(context)
                .withAnchorView(anchorView)
                .withForwardable(forwardable)
                .withRecallVisible(!messageRecord.isFailed() && !messageRecord.isPending() && messageRecord.isOutgoing() && conversationRecipient.address.toString() != getAccountContext().uid)
                .withCopyVisible(copyVisible)
                .withReplyable(false)
                .withOutgoing(messageRecord.isOutgoing())
                .withClickListener(object : ConversationItemPopWindow.PopWindowClickListener {
                    override fun onRecall() {
                        AmeModuleCenter.chat(getAccountContext())?.recallMessage(context, false, messageRecord, null)
                    }

                    override fun onDelete() {
                        ThreadListViewModel.getThreadId(conversationRecipient) { threadId ->
                            AmeModuleCenter.chat(getAccountContext())?.deleteMessage(context, false, threadId, setOf(messageRecord), null)
                        }
                    }

                    override fun onCopy() {
                        if (TextUtils.isEmpty(text)) {
                            return
                        }
                        AppUtil.saveCodeToBoard(context, text)
                        AmeAppLifecycle.succeed(getString(R.string.common_copied), true)
                    }

                    override fun onForward() {
                        BcmRouter.getInstance().get(ARouterConstants.Activity.FORWARD)
                                .putLong(ForwardActivity.INDEX_ID, messageRecord.id)
                                .putLong(ForwardActivity.GID, ARouterConstants.PRIVATE_TEXT_CHAT)
                                .startBcmActivity(getAccountContext(), context)
                    }

                    override fun onSelect() {
                        val set = LinkedHashSet<MessageRecord>()
                        set.add(messageRecord)
                        EventBus.getDefault().post(MultiSelectEvent(false, set))
                    }

                    override fun onPin() {}

                    override fun onReply() {}
                }).build()
    }

    fun getView(indexId: Long): View? {
        if (indexId == messageRecord.id) {
            if (mediaThumbnailStub.resolved() && mediaThumbnailStub.get().visibility == View.VISIBLE) {
                return mediaThumbnailStub.get()
            }
        }
        return null
    }

    private fun performMultiSelect() {
        val batch = batchSelected
        if (null != batch) {
            selectedView?.let {
                it.isSelected = !it.isSelected
                if (it.isSelected) {
                    batch.add(messageRecord)
                } else {
                    batch.remove(messageRecord)
                }
            }
        }
    }

    private fun requestFitWidth(layout: StaticLayout): Int {
        val line = layout.lineCount
        ALog.i(TAG, "lineCount is: $line")
        var max = 0.0f
        if (line > 0) {

            var w = 0.0f
            for (i in 0 until line) {
                w = layout.getLineMax(i)
                if (w > max) {
                    max = w
                }
            }
        }
        return max.toInt()
    }

    private inner class ClickListener(private val parent: OnClickListener?) : OnClickListener {

        override fun onClick(v: View) {
            if (isInMultiSelectMode) {
                performMultiSelect()
                return
            }
            when {
                messageRecord.isFailed() || messageRecord.isPendingInsecureFallback() -> {
                    AmePopup.bottom.newBuilder()
                            .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_resend)) {
                                if (!groupThread && (messageRecord.isFailed() || messageRecord.isPendingInsecureFallback())) {
                                    Observable.create<Unit> {
                                        if (messageRecipient.isPushGroupRecipient) {
                                        } else {
                                            MessageSender.resend(context, masterSecret.accountContext, messageRecord)
                                        }
                                        it.onComplete()

                                    }.subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({}, {})
                                }
                            })
                            .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_delete)) {
                                Observable.create<Unit> {

                                    Repository.getChatRepo(getAccountContext())?.deleteMessage(messageRecord)
                                    it.onComplete()

                                }.subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe({}, {})

                            })
                            .withDoneTitle(getString(R.string.common_cancel))
                            .show(context as? FragmentActivity)
                }
            }
            parent?.onClick(v)
        }
    }
}
