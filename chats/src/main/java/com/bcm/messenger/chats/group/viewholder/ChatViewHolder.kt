package com.bcm.messenger.chats.group.viewholder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.style.URLSpan
import android.util.LongSparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.ReplyMessageEvent
import com.bcm.messenger.chats.components.*
import com.bcm.messenger.chats.forward.ForwardActivity
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.chats.util.ClickSpanTouchHandler
import com.bcm.messenger.chats.util.LinkUrlSpan
import com.bcm.messenger.chats.util.TelUrlSpan
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.api.IConversationContentAction
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.model.ProfileKeyModel
import com.bcm.messenger.common.event.MultiSelectEvent
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.ConversationContentViewHolder
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference

/**
 * group chat view holder
 * Created by wjh on 2018/10/22
 */
open class ChatViewHolder(accountContext: AccountContext, containerView: View) : ConversationContentViewHolder<AmeGroupMessageDetail>(accountContext, containerView),
        RecipientModifiedListener, View.OnLongClickListener {

    companion object {
        const val TAG = "ChatViewHolder"
        const val TYPE_AT = 1

        fun interceptMessageText(bodyView: TextView, messageRecord: AmeGroupMessageDetail, bodyText: CharSequence): CharSequence {
            bodyView.text = bodyText
            var resultText = interceptTextLink(bodyView, messageRecord.isSendByMe, bodyView.text)
            resultText = interceptRecipientAt(bodyView, messageRecord.isSendByMe, resultText, messageRecord.getAtRecipientList(AMELogin.majorContext))
            return resultText
        }


        fun interceptRecipientAt(bodyView: TextView, isOutgoing: Boolean, bodyText: CharSequence, atList: List<Recipient>?): CharSequence {
            return bodyText
        }


        fun interceptTextLink(bodyView: TextView, isOutgoing: Boolean, bodyText: CharSequence): CharSequence {

            if (bodyText is Spannable) {
                val end = bodyText.length
                val urlSpans = bodyText.getSpans(0, end, URLSpan::class.java) as Array<URLSpan>

                if (urlSpans.isEmpty()) {
                    bodyView.text = bodyText
                    return bodyText
                }

                val spannableStringBuilder = SpannableStringBuilder(bodyText)
                for (uri: URLSpan in urlSpans) {
                    val url = uri.url
                    when {
                        url.indexOf("http://") == 0 || url.indexOf("https://") == 0 -> {
                            val linkSpan = LinkUrlSpan(bodyView.context, url, isOutgoing)
                            spannableStringBuilder.setSpan(linkSpan, bodyText.getSpanStart(uri), bodyText.getSpanEnd(uri), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                            spannableStringBuilder.removeSpan(uri)
                        }
                        url.startsWith("tel:") -> {
                            val telUrlSpan = TelUrlSpan(Uri.parse(url))
                            spannableStringBuilder.setSpan(telUrlSpan, bodyText.getSpanStart(uri), bodyText.getSpanEnd(uri), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                        }
                    }
                }

                bodyView.text = spannableStringBuilder
                return spannableStringBuilder
            } else {
                bodyView.text = bodyText
            }

            return bodyText
        }

        fun requestFitWidth(layout: StaticLayout): Int {
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
    }

    protected var mActionArray = LongSparseArray<IConversationContentAction<AmeGroupMessageDetail>>(20)
    protected var mSelectView: CheckBox? = null
    protected var mCellClickView: View? = null
    protected var mAlertView: AlertView? = null
    protected var mPhotoView: IndividualAvatarView? = null
    protected var mNameView: TextView? = null
    protected var mBodyContainer: ViewGroup? = null
    protected lateinit var recipient: Recipient

    private val mBreatheAnim: Animation = AlphaAnimation(0.5f, 0.3f)

    init {
        ALog.i(TAG, "init")
        mSelectView = containerView.findViewById(R.id.conversation_selected)
        mCellClickView = containerView.findViewById(R.id.conversation_cell_click_view)

        mCellClickView?.setOnClickListener {
            val messageRecord = mMessageSubject ?: return@setOnClickListener
            mSelectView?.isChecked = !(mSelectView?.isChecked ?: true)
            if (mSelectView?.isChecked == true) {
                mSelectedBatch?.add(messageRecord)
            } else {
                mSelectedBatch?.remove(messageRecord)
            }
        }

        mAlertView = containerView.findViewById(R.id.indicators_parent)
        mAlertView?.setOnClickListener {
            val messageRecord = mMessageSubject ?: return@setOnClickListener
            if (messageRecord.sendState == AmeGroupMessageDetail.SendState.SEND_FAILED) {
                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_resend)) {
                            mAction?.resend(messageRecord)
                        })
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_delete)) {
                            Observable.create(ObservableOnSubscribe<Boolean> { emitter ->
                                MessageDataManager.deleteOneMessageByIndexId(accountContext, messageRecord.gid, messageRecord.indexId)
                                emitter.onNext(true)
                                emitter.onComplete()
                            }).subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                    }, {
                                        ALog.e(TAG, it.toString())
                                    })
                        })
                        .withDoneTitle(getString(R.string.chats_cancel))
                        .show(it.context as? FragmentActivity)
            } else if (messageRecord.sendState == AmeGroupMessageDetail.SendState.THUMB_DOWNLOAD_FAIL) {
                (mAction?.getDisplayView() as? ChatThumbnailView)?.downloadGroupThumbnail(accountContext, messageRecord)
            }
        }

        mPhotoView = containerView.findViewById(R.id.conversation_recipient_photo)
        mNameView = containerView.findViewById(R.id.conversation_recipient_name)

        mPhotoView?.setOnClickListener {
            mMessageSubject?.let {
                AmeModuleCenter.contact(recipient.address.context())?.openContactDataActivity(itemView.context, recipient.address, it.gid)
            }

        }
        mPhotoView?.setOnLongClickListener {
            mMessageSubject?.let {
                notifyEvent(TYPE_AT, it.gid, this.recipient)
            }
            true
        }
        mBodyContainer = containerView.findViewById(R.id.conversation_container)
    }

    override fun updateSender(message: AmeGroupMessageDetail) {
        if (!message.isSendByMe) {
            mNameView?.visibility = View.VISIBLE
            mPhotoView?.visibility = View.VISIBLE
            recipient = Recipient.from(accountContext, message.senderId ?: return, true)
            recipient.addListener(this)
            val sender = getGroupMemberInfo(message)
            updateUserInfo(recipient, sender)

        } else {
            mNameView?.visibility = View.GONE
            mPhotoView?.visibility = View.GONE
        }
    }

    override fun updateBackground(message: AmeGroupMessageDetail, action: IConversationContentAction<AmeGroupMessageDetail>?) {
        updateBackground(message, itemView)
        updateAlpha(message, action?.getDisplayView())
    }

    override fun updateAlert(message: AmeGroupMessageDetail, action: IConversationContentAction<AmeGroupMessageDetail>?) {
        when (message.sendState) {
            AmeGroupMessageDetail.SendState.SEND_FAILED,
            AmeGroupMessageDetail.SendState.THUMB_DOWNLOAD_FAIL -> mAlertView?.setFailed()
            else -> mAlertView?.setNone()
        }
    }

    override fun updateSelectionMode(isSelect: Boolean?) {
        mSelectView?.visibility = if (isSelect != null) View.VISIBLE else View.GONE
        mCellClickView?.visibility = mSelectView?.visibility ?: View.GONE
        if (isSelect != null) {
            mSelectView?.isChecked = isSelect
            mAlertView?.isClickable = false
        } else {
            mAlertView?.isClickable = true
        }
    }

    override fun bindViewAction(message: AmeGroupMessageDetail, glideRequests: GlideRequests, batchSelected: MutableSet<AmeGroupMessageDetail>?): IConversationContentAction<AmeGroupMessageDetail>? {
        var child: View? = null
        for (i in 0 until (mBodyContainer?.childCount ?: 0)) {
            child = mBodyContainer?.getChildAt(i)
            if (child is ViewStub) {

            } else {
                child?.visibility = View.GONE
            }
        }
        return bindAction(message, glideRequests, batchSelected)
    }

    private fun bindAction(messageRecord: AmeGroupMessageDetail, glideRequests: GlideRequests, batchSelected: MutableSet<AmeGroupMessageDetail>?): IConversationContentAction<AmeGroupMessageDetail>? {

        fun <T> getInflateView(resId: Int): T {
            return when (val view = itemView.findViewById<View>(resId)) {
                is ViewStub -> {
                    ALog.i(TAG, "getInflateView viewStub")
                    view.inflate() as T
                }
                else -> {
                    ALog.i(TAG, "getInflateView already view")
                    view as T
                }
            }
        }

        val type = messageRecord.message.type
        ALog.i(TAG, "createViewAction type: $type")
        var action: IConversationContentAction<AmeGroupMessageDetail>? = mActionArray[type]
        val v = when (type) {
            AmeGroupMessage.TEXT -> {
                val v = getInflateView<EmojiTextView>(R.id.conversation_message)
                if (action == null) {
                    action = ChatMessageHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.LINK -> {
                val v = getInflateView<EmojiTextView>(R.id.conversation_link)
                if (action == null) {
                    action = ChatMessageHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.NONSUPPORT -> {
                val v = getInflateView<EmojiTextView>(R.id.conversation_not_support)
                if (action == null) {
                    action = ChatNotSupportHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.AUDIO -> {
                val v = getInflateView<ChatAudioView>(R.id.conversation_audio)
                if (action == null) {
                    action = ChatAudioHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.IMAGE -> {
                val v = getInflateView<ChatThumbnailView>(R.id.conversation_thumbnail)
                if (action == null) {
                    action = ChatThumbnailHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.FILE -> {
                val v = getInflateView<ChatDocumentView>(R.id.conversation_document)
                if (action == null) {
                    action = ChatDocumentHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.NEWSHARE_CHANNEL -> {
                val v = getInflateView<ShareChannelView>(R.id.conversation_channel)
                if (action == null) {
                    action = ChatNewChannelHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.VIDEO -> {
                val v = getInflateView<ChatThumbnailView>(R.id.conversation_video)
                if (action == null) {
                    action = ChatVideoHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.LOCATION -> {
                val v = getInflateView<MapShareView>(R.id.conversation_location)
                if (action == null) {
                    action = ChatLocationHolderAction(accountContext)
                }
                v
            }
            AmeGroupMessage.CONTACT -> {
                val v = getInflateView<ContactCardView>(R.id.conversation_contact)
                if (action == null) {
                    action = ChatContactHolderAction(accountContext)
                }
                v

            }
            AmeGroupMessage.CHAT_HISTORY -> {
                val v = getInflateView<HistoryView>(R.id.conversation_history)
                if (action == null) {
                    action = ChatHistoryHolderAction(accountContext)
                }
                v

            }
            AmeGroupMessage.CHAT_REPLY -> {
                val v = getInflateView<ChatReplyView>(R.id.conversation_reply)
                if (action == null) {
                    action = ChatReplyHolderAction(accountContext)
                }
                v

            }
            AmeGroupMessage.GROUP_SHARE_CARD -> {
                val v = getInflateView<GroupShareCardView>(R.id.conversation_group_share)
                if (action == null) {
                    action = GroupShareHolderAction(accountContext)
                }
                v
            }
            else -> null
        }
        mActionArray.put(type, action)
        v?.setOnLongClickListener(this)
        if (v is EmojiTextView) {
            v.setOnTouchListener(ClickSpanTouchHandler.getInstance(v.context))
        }
        if (v != null) {
            action?.bind(messageRecord, v, glideRequests, batchSelected)
        }
        v?.visibility = View.VISIBLE
        return action
    }

    private fun getGroupMemberInfo(messageRecord: AmeGroupMessageDetail): AmeGroupMemberInfo? {
        if (messageRecord.gid > 0 && GroupLogic.get(accountContext).isCurrentModel(messageRecord.gid)) {
            val model = GroupLogic.get(accountContext).getModel(messageRecord.gid)
            val sender = model?.getGroupMember(messageRecord.senderId)
            if (model != null && sender == null) {
                val weakThis = WeakReference(this)
                model.queryGroupMember(messageRecord.senderId) { member ->
                    if (null != member && weakThis.get()?.recipient?.address?.serialize() == member.uid) {
                        updateUserInfo(recipient, sender)
                    }
                }
            } else {
                return sender
            }
        }
        return null
    }

    private fun updateUserInfo(recipient: Recipient, member: AmeGroupMemberInfo?) {
        val nickname = BcmGroupNameUtil.getGroupMemberName(recipient, member)
        if (mNameView?.text != nickname) {
            mNameView?.text = nickname
        }

        val profileKey = ProfileKeyModel.fromKeyConfig(member?.keyConfig)
        if (null != profileKey) {
            AmeModuleCenter.contact(accountContext)?.updateProfileKey(AppContextHolder.APP_CONTEXT, recipient, profileKey)
        }
        mPhotoView?.setPhoto(recipient, nickname, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
    }

    open fun unBindData() {
        recycleData()
        mBreatheAnim.cancel()
    }

    private fun recycleData() {
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
        mAction?.unBind()
        mAction = null
    }

    override fun onLongClick(v: View): Boolean {
        ALog.i(TAG, "onLongClick")
        mAction?.let {
            it.getCurrent()?.let {
                showItemPopWindow(itemView.context, v, it)
            }
        }
        return true
    }

    override fun onModified(recipient: Recipient) {
        itemView.post {
            val messageRecord = mMessageSubject
            if (this.recipient == recipient && messageRecord != null) {
                val sender = GroupLogic.get(accountContext).getModel(messageRecord.gid)?.getGroupMember(messageRecord.senderId)
                updateUserInfo(recipient, sender)
            }
        }
    }

    private fun notifyEvent(type: Int, groupId: Long, recipient: Recipient) {
        when (type) {
            TYPE_AT -> {
                EventBus.getDefault().post(GroupViewModel.GroupChatAtEvent(recipient))
            }
        }
    }

    private fun showItemPopWindow(context: Context, anchorView: View, messageRecord: AmeGroupMessageDetail) {
        val groupModel = GroupLogic.get(accountContext).getModel(messageRecord.gid) ?: return
        if (groupModel.myRole() == AmeGroupMemberInfo.VISITOR) {
            ALog.i(TAG, "visitor can't showItemPopWindow")
            return
        }
        val pinVisible = (messageRecord.isSendSuccess || !messageRecord.isSendByMe) && groupModel.myRole() == AmeGroupMemberInfo.OWNER
        val isHistory = messageRecord is AmeHistoryMessageDetail
        val hasPin = pinVisible && groupModel.getGroupInfo().pinMid == messageRecord.serverIndex
        ConversationItemPopWindow.ItemPopWindowBuilder(context)
                .withAnchorView(anchorView)
                .withForwardable(messageRecord.isForwardable && !isHistory)
                .withRecallVisible(messageRecord.isRecallable && !isHistory)
                .withCopyVisible(messageRecord.isCopyable)
                .withPinVisible(pinVisible, hasPin)
                .withReplyable((!messageRecord.isSendByMe || messageRecord.isSendSuccess) && !isHistory)
                .withDeletable(!isHistory)
                .withMultiSelect(!isHistory)
                .withOutgoing(messageRecord.isSendByMe)
                .withClickListener(object : ConversationItemPopWindow.PopWindowClickListener {
                    override fun onCopy() {
                        when {
                            messageRecord.message.isText() -> AppUtil.saveCodeToBoard(context, messageRecord.message.content.getDescribe(messageRecord.gid, accountContext).toString())
                            messageRecord.message.isLink() -> AppUtil.saveCodeToBoard(context, messageRecord.message.content.getDescribe(messageRecord.gid, accountContext).toString())
                            messageRecord.message.isGroupShare() -> {
                                val groupShareContent = messageRecord.message.content as AmeGroupMessage.GroupShareContent
                                AppUtil.saveCodeToBoard(context, groupShareContent.shareLink ?: "")
                            }
                            messageRecord.message.isReplyMessage() -> {
                                val replyContent = messageRecord.message.content as AmeGroupMessage.ReplyContent
                                AppUtil.saveCodeToBoard(context, replyContent.text ?: "")
                            }
                            else -> return
                        }
                        AmeAppLifecycle.succeed(getString(R.string.common_copied), true)
                    }

                    override fun onDelete() {
                        AmeModuleCenter.chat(accountContext)?.deleteMessage(itemView.context, true, messageRecord.gid, setOf(messageRecord))
                    }

                    override fun onForward() {
                        onDataForward(context, messageRecord)
                    }

                    override fun onRecall() {
                        AmeModuleCenter.chat(accountContext)?.recallMessage(context, true, messageRecord)
                    }

                    override fun onSelect() {
                        EventBus.getDefault().post(MultiSelectEvent(true, mutableSetOf(messageRecord)))
                    }

                    override fun onPin() {
                        var option = AppContextHolder.APP_CONTEXT.getString(R.string.chats_unpin)
                        var title = AppContextHolder.APP_CONTEXT.getString(R.string.chats_unpin_check_title)
                        if (!hasPin) {
                            option = AppContextHolder.APP_CONTEXT.getString(R.string.chats_pin)
                            title = AppContextHolder.APP_CONTEXT.getString(R.string.chats_pin_check_title)
                        }

                        AmePopup.bottom.newBuilder()
                                .withTitle(title)
                                .withPopItem(AmeBottomPopup.PopupItem(option, AmeBottomPopup.PopupItem.CLR_RED) {
                                    AmeDispatcher.io.dispatch {
                                        GroupMessageLogic.get(accountContext).messageSender.sendPinMessage(messageRecord.gid, if (hasPin) -1 else messageRecord.serverIndex, object : MessageSender.SenderCallback {
                                            override fun call(ameMessageDetail: AmeGroupMessageDetail?, index: Long, isSuccess: Boolean) {

                                            }
                                        })
                                    }
                                })
                                .withDoneTitle(AppContextHolder.APP_CONTEXT.getString(R.string.common_cancel))
                                .show(AmeAppLifecycle.current() as? FragmentActivity)
                    }

                    override fun onReply() {
                        EventBus.getDefault().post(ReplyMessageEvent(messageRecord, ReplyMessageEvent.ACTION_REPLY))
                    }
                }).build()
    }

    protected fun onDataForward(context: Context, messageRecord: AmeGroupMessageDetail) {

        val intent = Intent(context, ForwardActivity::class.java).apply {
            putExtra(ForwardActivity.INDEX_ID, messageRecord.indexId)
            putExtra(ForwardActivity.GID, messageRecord.gid)
        }
        context.startActivity(intent)
    }

    fun getView(indexId: Long): View? {
        val messageRecord = mMessageSubject
        if (messageRecord?.indexId == indexId) {
            val action = mActionArray[messageRecord.message.type]
            return action?.getDisplayView()
        }
        return null
    }

    private fun updateBackground(messageRecord: AmeGroupMessageDetail, mainView: View?) {
        if (mainView == null) {
            return
        }
        if (this.mMessageSubject == messageRecord) {
            when (messageRecord.isLabel) {
                 AmeGroupMessageDetail.LABEL_REPLY -> {
                    mainView.setBackgroundResource(R.color.common_color_black_transparent_10)
                    mainView.postDelayed({
                        messageRecord.isLabel = AmeGroupMessageDetail.LABEL_NONE
                        updateBackground(messageRecord, mainView)
                    }, 1000)
                }
                AmeGroupMessageDetail.LABEL_PIN -> {
                    mainView.setBackgroundResource(R.color.common_color_black_transparent_10)
                    mainView.postDelayed({
                        messageRecord.isLabel = AmeGroupMessageDetail.LABEL_NONE
                        updateBackground(messageRecord, mainView)
                    }, 500)
                }
                else -> mainView.setBackgroundResource(R.color.common_color_transparent)
            }
        }
    }

    private fun updateAlpha(messageRecord: AmeGroupMessageDetail, mainView: View?) {
        if (mainView == null) {
            ALog.d(TAG, "updateAlpha  indexId: ${messageRecord.indexId}, gid: ${messageRecord.gid}, return")
            return
        }
        if (messageRecord !is AmeHistoryMessageDetail) {
            if (!messageRecord.isSendByMe) {
                mainView.animation?.cancel()
                mainView.clearAnimation()
                mainView.alpha = 1.0f
            } else if (messageRecord.isSendFail) {
                mainView.animation?.cancel()
                mainView.clearAnimation()
                mainView.alpha = 0.5f
            } else if (messageRecord.isSending) {
                mainView.alpha = 0.5f
                mBreatheAnim.duration = 1000
                mBreatheAnim.repeatCount = Animation.INFINITE
                mBreatheAnim.repeatMode = Animation.REVERSE
                mainView.startAnimation(mBreatheAnim)
            } else {
                mainView.animation?.cancel()
                mainView.clearAnimation()
                mainView.alpha = 1.0f
            }
        } else {
            mainView.animation?.cancel()
            mainView.clearAnimation()
            mainView.alpha = 1.0f
        }
    }
}