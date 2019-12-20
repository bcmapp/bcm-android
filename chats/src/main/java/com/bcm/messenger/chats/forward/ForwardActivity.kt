package com.bcm.messenger.chats.forward

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatForwardDialog
import com.bcm.messenger.chats.forward.viewmodel.ForwardViewModel
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.OutgoingMediaMessage
import com.bcm.messenger.common.mms.OutgoingSecureMediaMessage
import com.bcm.messenger.common.mms.SlideDeck
import com.bcm.messenger.common.provider.IForwardSelectProvider.ForwardSelectCallback
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

@Route(routePath = ARouterConstants.Activity.FORWARD)
class ForwardActivity : SwipeBaseActivity() {

    companion object {
        private const val TAG = "ForwardActivity"
        private const val DIALOG_TAG = "ForwardDialog"

        private const val URI_MODE = 0
        private const val NORMAL_MODE = 1

        const val GID = "__gid"
        const val INDEX_ID = "__index_id"
        const val MULTI_INDEX_ID = "__multi_index_id"
        const val URI = "__uri"
    }
    
    private var gid = 0L
    private var mode = NORMAL_MODE
    private var path = ""
    private lateinit var viewModel: ForwardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_forward)
        initResources()

        setSwipeBackEnable(false)
    }

    private fun initView() {
        val fragment = ForwardRecentFragment()
        fragment.setCallback(object : ForwardSelectCallback {
            override fun onClickContact(recipient: Recipient) {
                clickContact(recipient)
            }
        })
        fragment.setMasterSecret(getMasterSecret())
        fragment.setContactSelectContainer(R.id.activity_forward_root)
        fragment.setGroupSelectContainer(R.id.activity_forward_root)
        supportFragmentManager.beginTransaction()
                .add(R.id.activity_forward_root, fragment)
                .commit()
    }

    private fun initResources() {
        viewModel = ViewModelProviders.of(this).get(ForwardViewModel::class.java)

        val uri = intent.getStringExtra(URI)
        if (uri != null) {
            this.path = uri
            mode = URI_MODE
        } else {
            initChatRes()
        }
        initView()
    }

    private fun initChatRes() {
        gid = intent.getLongExtra(GID, -1)
        val indexId = intent.getLongExtra(INDEX_ID, -1)
        val indexIdList = intent.getLongArrayExtra(MULTI_INDEX_ID)
        if (indexIdList != null && indexIdList.isNotEmpty()) {
            viewModel.fetchSuccess.observe(this, Observer {
                if (it == true) {

                }
            })
            when (gid) {
                ARouterConstants.PRIVATE_TEXT_CHAT, ARouterConstants.PRIVATE_MEDIA_CHAT -> viewModel.getMultiplePrivateMessages(getMasterSecret(), indexIdList)
                else -> viewModel.getMultipleGroupMessages(gid, indexIdList)
            }
        } else {
            when (gid) {
                -1000L -> {
                    try {
                        viewModel.getPrivateMessage(getMasterSecret(), indexId)
                    } catch (e: Exception) {
                        AlertDialog.Builder(this)
                                .setTitle(R.string.chats_forward_no_such_message_title)
                                .setMessage(R.string.chats_forward_no_such_message_content)
                                .setPositiveButton(R.string.common_popup_ok) { _, _ ->
                                    finish()
                                }
                                .show()
                    }
                }
                -1001L -> viewModel.getPrivateMessage(getMasterSecret(), indexId)
                else -> viewModel.getGroupMessage(indexId, gid)
            }
        }
    }

    fun clickContact(recipient: Recipient) {
        viewModel.selectRecipients.clear()
        viewModel.selectRecipients.add(recipient)
        handleDone()
    }

    private fun showForwardDialog(dialog: DialogFragment) {
        if (isFinishing) return
        try {
            dialog.show(supportFragmentManager, DIALOG_TAG)
        }catch (ex: Exception) {
            ALog.e(TAG, "showForwardDialog error", ex)
        }
    }

    private fun handleDone() {
        when {
            mode == URI_MODE -> {
                AmePopup.center.newBuilder()
                        .withTitle(getString(R.string.chats_forward_confirm_text))
                        .withOkTitle(getString(R.string.chats_item_confirm))
                        .withOkListener {
                            sendMessages(viewModel.selectRecipients[0])
                        }
                        .withCancelTitle(getString(R.string.chats_cancel))
                        .show(this)
            }
            viewModel.groupMessageList.size > 1 ->
                ChatForwardDialog().setMasterSecret(getMasterSecret())
                    .setRecipients(viewModel.selectRecipients)
                    .setForwardGroupMultiple(viewModel.groupMessageList)
                        .setCallback { forwardHistory, commentText ->
                            viewModel.forwardMultipleMessages(gid, getMasterSecret(), forwardHistory, commentText) {
                            AmeDispatcher.mainThread.dispatch {
                                showSentToast(it)
                            }
                        }
                    }.apply {
                            showForwardDialog(this)
                        }

            viewModel.privateMessageList.size > 1 ->
                ChatForwardDialog().setMasterSecret(getMasterSecret())
                        .setRecipients(viewModel.selectRecipients)
                        .setForwardPrivateMultiple(viewModel.privateMessageList)
                        .setCallback { forwardHistory, commentText ->
                            viewModel.forwardMultipleMessages(gid, getMasterSecret(), forwardHistory, commentText) {
                                AmeDispatcher.mainThread.dispatch {
                                    showSentToast(it)
                                }
                            }
                        }.apply {
                            showForwardDialog(this)
                        }

            viewModel.messageType == viewModel.PRIVATE_MESSAGE -> handlePrivateMessage()
            viewModel.messageType == viewModel.GROUP_MESSAGE -> handleGroupMessage()
        }
    }

    private fun handlePrivateMessage() {
        val dialog = ChatForwardDialog()
                .setMasterSecret(getMasterSecret())
                .setRecipients(viewModel.selectRecipients)
                .setCallback { _, commentText ->
                    val res = viewModel.forwardPrivateMessage(getMasterSecret(), commentText)
                    showSentToast(res)
                }

        val message = viewModel.getPrivateMessage() ?: return
        when {
            message.isMediaMessage() && message.getDocumentAttachment() != null -> {
                ALog.d(TAG, "PrivateMessage is a file.")
                val attachment = message.getDocumentAttachment()!!
                dialog.setForwardFileDialog(attachment.fileName.orEmpty())
            }
            message.isMediaMessage() && message.getImageAttachment() != null -> {
                val attachment = message.getImageAttachment()!!
                ALog.d(TAG, "PrivateMessage is an image.")
                val uri = attachment.getThumbnailPartUri() ?: attachment.getPartUri()
                if (uri != null) {
                    dialog.setForwardImageDialog(uri, attachment.dataSize, GlideApp.with(this))
                }
            }
            message.isMediaMessage() && message.getVideoAttachment() != null -> {
                val attachment = message.getVideoAttachment()!!
                ALog.d(TAG, "PrivateMessage is a video.")
                val uri = attachment.getThumbnailPartUri() ?: attachment.getPartUri()
                if (uri != null) {
                    dialog.setForwardVideoDialog(uri, attachment.dataSize, attachment.duration, GlideApp.with(this))
                }
            }
            message.isLocation() -> {
                val newMessage = AmeGroupMessage.messageFromJson(message.body)

                when (newMessage.type) {
                    AmeGroupMessage.CONTACT -> {
                        ALog.d(TAG, "PrivateMessage is a contact.")
                        val content = newMessage.content as AmeGroupMessage.ContactContent
                        dialog.setForwardContactDialog(content.nickName)
                    }
                    AmeGroupMessage.LOCATION -> {
                        ALog.d(TAG, "PrivateMessage is a location.")
                        val content = newMessage.content as AmeGroupMessage.LocationContent
                        dialog.setForwardLocationDialog(content.title, content.address)
                    }
                    AmeGroupMessage.GROUP_SHARE_CARD -> {
                        ALog.d(TAG, "PrivateMessage is a share card")
                        val content = newMessage.content as AmeGroupMessage.GroupShareContent
                        dialog.setForwardTextDialog(content.shareLink ?: "")
                    }
                }
            }
            else -> {
                ALog.d(TAG, "PrivateMessage is a text or unknown message.")
                dialog.setForwardTextDialog(message.body)
            }
        }
        showForwardDialog(dialog)
    }

    private fun handleGroupMessage() {
        val dialog = ChatForwardDialog()
                .setRecipients(viewModel.selectRecipients)
                .setIsGroup(true)
                .setCallback { _, commentText ->
                    val res = viewModel.forwardGroupMessage(getMasterSecret(), commentText)
                    showSentToast(res)
                }

        val message = viewModel.getGroupMessage() ?: return
        when (val content = message.message.content) {
            is AmeGroupMessage.ContactContent -> {
                ALog.d(TAG, "GroupMessage is a contact.")
                dialog.setForwardContactDialog(content.nickName).apply {
                    showForwardDialog(this)
                }
            }
            is AmeGroupMessage.ImageContent -> {
                ALog.d(TAG, "GroupMessage is an image.")
                if (!message.attachmentUri.isNullOrBlank()) {
                    var uri = Uri.parse(message.attachmentUri)
                    if (uri.scheme.isNullOrBlank()) {
                        uri = Uri.fromFile(File(message.attachmentUri))
                    }
                    dialog.setForwardImageDialog(uri, content.size, GlideApp.with(this))
                            .apply {
                                showForwardDialog(this)
                            }
                } else {
                    viewModel.downloadAndDecryptThumbnail { uri ->
                        dialog.setForwardImageDialog(uri, content.size, GlideApp.with(this))
                                .apply {
                                    showForwardDialog(this)
                                }
                    }
                }
            }
            is AmeGroupMessage.VideoContent -> {
                ALog.d(TAG, "GroupMessage is a video.")
                if (!message.attachmentUri.isNullOrBlank()) {
                    var uri = Uri.parse(message.attachmentUri)
                    if (uri.scheme.isNullOrBlank()) {
                        uri = Uri.fromFile(File(message.attachmentUri))
                    }
                    dialog.setForwardVideoDialog(uri, content.size, content.duration, GlideApp.with(this))
                            .apply {
                                showForwardDialog(this)
                            }
                } else {
                    viewModel.downloadAndDecryptThumbnail { uri ->
                        dialog.setForwardVideoDialog(uri, content.size, content.duration, GlideApp.with(this))
                                .apply {
                                    showForwardDialog(this)
                                }
                    }
                }
            }
            is AmeGroupMessage.TextContent -> {
                ALog.d(TAG, "GroupMessage is a text.")
                dialog.setForwardTextDialog(content.text)
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.FileContent -> {
                ALog.d(TAG, "GroupMessage is a file.")
                dialog.setForwardFileDialog(content.fileName ?: "")
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.LocationContent -> {
                ALog.d(TAG, "GroupMessage is a location.")
                dialog.setForwardLocationDialog(content.title, content.address)
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.LinkContent -> {
                ALog.d(TAG, "GroupMessage is a Link.")
                dialog.setForwardTextDialog(content.url)
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.NewShareChannelContent -> {
                ALog.d(TAG, "GroupMessage is a NewShareChannel.")
                dialog.setForwardTextDialog(content.channel)
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.ReplyContent -> {
                ALog.d(TAG, "GroupMessage is a Reply.")
                dialog.setForwardTextDialog(content.text)
                        .apply {
                            showForwardDialog(this)
                        }
            }
            is AmeGroupMessage.GroupShareContent -> {
                ALog.d(TAG, "GroupMessage is a group share card.")
                dialog.setForwardTextDialog(content.shareLink ?: "")
                        .apply {
                            showForwardDialog(this)
                        }
            }
        }
    }

    private fun sendMessages(recipient: Recipient) {
        if (recipient.isGroupRecipient) {
            Observable.create<Unit> {
                val uri = Uri.fromFile(File(path))
                val imageContent = AttachmentUtils.getAttachmentContent(this, uri, path) as? AmeGroupMessage.ImageContent
                        ?: throw Exception("ImageContent is null")
                GroupMessageLogic.messageSender.sendImageMessage(getMasterSecret(), GroupUtil.gidFromAddress(recipient.address), imageContent, uri, path, null)
                it.onComplete()

            }.subscribeOn(Schedulers.io())
                    .doOnComplete { finish() }
                    .subscribe()
        } else {
            ThreadListViewModel.getThreadId(recipient) { threadId ->
                try {
                    val uri = Uri.fromFile(File(path))
                    val slide = AttachmentUtils.getImageSlide(this, uri)?:return@getThreadId
                    val expiresIn = (recipient.expireMessages * 1000).toLong()
                    val deck = SlideDeck()
                    deck.addSlide(slide)

                    val outgoingMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                            -1, expiresIn, ThreadRepo.DistributionTypes.CONVERSATION))

                    Observable.create<Long> {
                        it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT, getMasterSecret(),
                                outgoingMessage, threadId, null))
                        it.onComplete()

                    }.subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({

                            }, {
                                ALog.e(TAG, "sendMessage error", it)
                            })

                    finish()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    private fun showSentToast(succeed: Boolean) {
        if (succeed) {
            AmePopup.result.succeed(this, getString(R.string.chats_group_channel_share_sent)) {
                finish()
            }
        } else {
            AmePopup.result.failure(this, getString(R.string.chats_forward_result_fail))
        }
    }

}