package com.bcm.messenger.adhoc.ui.channel.holder

import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.CheckBox
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.chats.components.AlertView
import com.bcm.messenger.chats.components.ConversationItemPopWindow
import com.bcm.messenger.chats.util.LinkUrlSpan
import com.bcm.messenger.chats.util.TelUrlSpan
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.api.IConversationContentAction
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.ConversationContentViewHolder
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.AppUtil.getString
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 *
 * Created by wjh on 2019/7/27
 */
class AdHocChatViewHolder(layout: View) : ConversationContentViewHolder<AdHocMessageDetail>(layout), View.OnLongClickListener {

    companion object {

        private const val TAG = "AdHocChatViewHolder"

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

            }else {
                bodyView.text = bodyText
            }
            return bodyText
        }
    }

    private var mActionArray = LongSparseArray<IConversationContentAction<AdHocMessageDetail>>(15)
    private var mCellClickView: View? = null
    private var mSelectView: CheckBox? = null
    private var mAlertView: AlertView? = null
    private var mNickView: TextView? = null
    private var mPhotoView: IndividualAvatarView? = null
    private var mBodyContainer: ViewGroup? = null

    private val mBreatheAnim: Animation = AlphaAnimation(0.5f, 0.3f)

    init {
        mSelectView = itemView.findViewById(R.id.conversation_selected)
        mCellClickView = itemView.findViewById<View>(R.id.conversation_cell_click_view)
        mCellClickView?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mSelectView?.isChecked = !(mSelectView?.isChecked ?: false)
            if (mSelectView?.isChecked == true) {
                mSelectedBatch?.add(mMessageSubject ?: return@setOnClickListener)
            } else {
                mSelectedBatch?.remove(mMessageSubject ?: return@setOnClickListener)
            }
        }

        mAlertView = itemView.findViewById(R.id.indicators_parent)
        mAlertView?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val messageRecord = mMessageSubject ?: return@setOnClickListener
            if (!messageRecord.success) {
                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_resend)) {
                            mAction?.resend(, messageRecord)
                        })
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_delete)) {
                            Observable.create(ObservableOnSubscribe<Boolean> { emitter ->
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
            }
        }

        mNickView = itemView.findViewById(R.id.conversation_recipient_name)
        mPhotoView = itemView.findViewById(R.id.conversation_recipient_layout)
        mPhotoView?.setOnLongClickListener {
            val messageSubject = mMessageSubject
            if (messageSubject != null) {
                AdHocMessageLogic.getModel()?.addAt(messageSubject.fromId, messageSubject.nickname)
            }
            true
        }
        mPhotoView?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val message = mMessageSubject ?: return@setOnClickListener
            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigation() as IContactModule
            provider.openContactDataActivity(it.context, Address.fromSerialized(message.fromId), message.nickname)

        }
        mBodyContainer = itemView.findViewById(R.id.conversation_container)
    }

    fun getMessageView(index: Long): View? {
        val m = mMessageSubject
        if (m?.indexId == index) {
            val action = mActionArray[m.getMessageBodyType()]
            return action?.getDisplayView()
        }
        return null
    }

    override fun bindViewAction(message: AdHocMessageDetail, glideRequests: GlideRequests, batchSelected: MutableSet<AdHocMessageDetail>?): IConversationContentAction<AdHocMessageDetail>? {
        var child: View? = null
        for (i in 0 until (mBodyContainer?.childCount ?: 0)) {
            child = mBodyContainer?.getChildAt(i)
            if (child is ViewStub) {
            }else {
                child?.visibility = View.GONE
            }
        }
        return bindAction(message, glideRequests, batchSelected)
    }

    private fun bindAction(message: AdHocMessageDetail, glideRequests: GlideRequests, batchSelected: MutableSet<AdHocMessageDetail>?): IConversationContentAction<AdHocMessageDetail>? {

        fun <T>getInflateView(resId: Int) : T {
            return when (val view = itemView.findViewById<View>(resId)){
                is ViewStub -> view.inflate() as T
                else -> view as T
            }
        }
        val type = message.getMessageBodyType()
        var v: View? = null
        var action = mActionArray[type]
        when(type) {
            AmeGroupMessage.TEXT -> {
                v = getInflateView(R.id.conversation_message)
                if (action == null) {
                    action = AdHocTextHolderAction()
                }
            }
            AmeGroupMessage.ADHOC_INVITE -> {
                v = getInflateView(R.id.conversation_join)
                if (action == null) {
                    action = AdHocJoinHolderAction()
                }
            }
            AmeGroupMessage.IMAGE -> {
                v = getInflateView(R.id.conversation_thumbnail)
                if (action == null) {
                    action = AdHocThumbnailHolderAction()
                }
            }
            AmeGroupMessage.VIDEO -> {
                v = getInflateView(R.id.conversation_video)
                if (action == null) {
                    action = AdHocThumbnailHolderAction()
                }
            }
            AmeGroupMessage.FILE -> {
                v = getInflateView(R.id.conversation_document)
                if (action == null) {
                    action = AdHocDocumentHolderAction()
                }
            }
            AmeGroupMessage.AUDIO -> {
                v = getInflateView(R.id.conversation_audio)
                if (action == null) {
                    action = AdHocAudioHolderAction()
                }
            }
            else -> {
                v = getInflateView(R.id.conversation_not_support)
                if (action == null) {
                    action = AdHocJoinHolderAction()
                }
            }
        }
        v?.setOnLongClickListener(this)
        mActionArray.put(type, action)
        if (v != null) {
            action.bind(, message, v, glideRequests, batchSelected)
        }
        v?.visibility = View.VISIBLE
        return action
    }

    override fun updateSender(message: AdHocMessageDetail) {
        if (message.sendByMe) {
            mPhotoView?.visibility = View.GONE
            mNickView?.visibility = View.GONE
        }  else {
            val session = AdHocSessionLogic.getSession(message.sessionId)
            if (session != null && session.isChannel()) {
                mPhotoView?.visibility = View.VISIBLE
                mNickView?.visibility = View.VISIBLE
                mNickView?.text = message.nickname
                mPhotoView?.setPhoto(AMELogin.majorContext, Recipient.from(AMELogin.majorContext, Address.fromSerialized(message.fromId), true), message.nickname, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
            }else {
                mPhotoView?.visibility = View.GONE
                mNickView?.visibility = View.GONE
            }
        }
    }

    override fun updateBackground(message: AdHocMessageDetail, action: IConversationContentAction<AdHocMessageDetail>?) {
        if(action == null) {
            return
        }
        val mainView = action.getDisplayView() ?: return
        when {
            message.isSending || message.isAttachmentDownloading -> {
                if(mainView.animation?.isInitialized != true) {
                    mBreatheAnim.duration = 1000
                    mBreatheAnim.repeatCount = Animation.INFINITE
                    mBreatheAnim.repeatMode = Animation.REVERSE
                    mainView.startAnimation(mBreatheAnim)
                    mainView.alpha = 0.5f
                }
            }
            !message.success || (message.getMessageBody()?.content is AmeGroupMessage.AttachmentContent && message.toAttachmentUri() == null) -> {
                mainView.animation?.reset()
                mainView.clearAnimation()
                mainView.alpha = 0.5f
            }
            else -> {
                mainView.animation?.reset()
                mainView.clearAnimation()
                mainView.alpha = 1.0f
            }
        }
    }

    override fun updateAlert(message: AdHocMessageDetail, action: IConversationContentAction<AdHocMessageDetail>?) {
        if (message.isSending || message.success) {
            mAlertView?.setNone()
        }else {
            mAlertView?.setFailed()
        }
    }

    override fun updateSelectionMode(isSelect: Boolean?) {
        if (isSelect == null) {
            mSelectView?.visibility = View.GONE
            mAlertView?.isClickable = true
            mCellClickView?.visibility = View.GONE
        }else {
            mSelectView?.visibility = View.VISIBLE
            mAlertView?.isClickable = false
            mSelectView?.isChecked = isSelect
            mCellClickView?.visibility = View.VISIBLE
        }
    }

    override fun onLongClick(v: View): Boolean {
        mAction?.let {
            onLongClickAction(v, it)
        }
        return true
    }

    private fun onLongClickAction(view: View, action: IConversationContentAction<AdHocMessageDetail>) {

        val messageDetail = action.getCurrent() ?: return
        ConversationItemPopWindow.ItemPopWindowBuilder(view.context)
                .withAnchorView(view)
                .withForwardable(messageDetail.isForwardable())
                .withRecallVisible(false)
                .withCopyVisible(messageDetail.getMessageBody()?.isText() == true)
                .withPinVisible(false, false)
                .withReplyable(false)
                .withOutgoing(messageDetail.sendByMe)
                .withMultiSelect(false)
                .withClickListener(object : ConversationItemPopWindow.PopWindowClickListener {
                    override fun onCopy() {
                        when {
                            messageDetail.getMessageBody()?.isText() == true -> AppUtil.saveCodeToBoard(view.context, messageDetail.getMessageBody()?.content?.getDescribe(0, ).toString())
                            messageDetail.getMessageBody()?.isLink() == true-> AppUtil.saveCodeToBoard(view.context, messageDetail.getMessageBody()?.content?.getDescribe(0, ).toString())
                            else -> return
                        }
                        AmeAppLifecycle.succeed(getString(R.string.common_copied), true)
                    }

                    override fun onDelete() {
                        AdHocMessageLogic.getModel()?.deleteMessage(listOf(messageDetail)) {
                            ALog.i(TAG, "delete Message : ${messageDetail.indexId} result: $it")
                        }
                    }

                    override fun onForward() {
                        val messageSubject = mMessageSubject
                        if (messageSubject != null) {
                            AdHocMessageLogic.getModel()?.forward(messageSubject)
                        }
                    }

                    override fun onRecall() {
                    }

                    override fun onSelect() {
                    }

                    override fun onPin() {
                    }

                    override fun onReply() {
                    }

                }).build()

    }
}