package com.bcm.messenger.chats.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.group.viewholder.ChatViewHolder
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_reply_view.view.*
import java.lang.ref.WeakReference

/**
 * Chat reply view
 *
 * Created by wjh on 2018/11/26
 */
class ChatReplyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        LinearLayout(context, attrs, defStyle), RecipientModifiedListener {

    companion object {
        private const val TAG = "ChatReplyView"
    }

    private var mImagePlaceHolder: Int = 0
    private var mImageError: Int = 0
    private var mImageRadius: Int = 0
    private var mGroupMessage: AmeGroupMessageDetail? = null
    private var mReplyRecipient: Recipient? = null

    private var mReplyClickListener: ChatComponentListener? = null

    init {
        ALog.d(TAG, "init")
        View.inflate(context, R.layout.chats_reply_view, this)
        mImagePlaceHolder = R.drawable.common_image_place_img
        mImageError = R.drawable.common_image_broken_img
        mImageRadius = AppUtil.dp2Px(resources, 6)

        reply_image.radius = mImageRadius.toFloat()
        super.setOnClickListener { v ->
            mReplyClickListener?.onClick(v, mGroupMessage ?: return@setOnClickListener)
        }

        orientation = VERTICAL
    }

    override fun onModified(recipient: Recipient) {
        if (mReplyRecipient == recipient) {
            post {
                reply_to.text = recipient.name
            }
        }
    }

    fun setReplyClickListener(listener: ChatComponentListener?) {
        mReplyClickListener = listener
    }

    fun setReply(accountContext: AccountContext, messageRecord: AmeGroupMessageDetail) {
        ALog.d(TAG, "setReply messageRecord gid: ${messageRecord.gid}, mid: ${messageRecord.serverIndex}")
        mGroupMessage = messageRecord
        val content = messageRecord.message.content as? AmeGroupMessage.ReplyContent ?: return
        val recipient = content.getReplyRecipient(accountContext)
        mReplyRecipient = recipient
        recipient?.addListener(this)

        setAppearance(messageRecord.isSendByMe)
        val groupModel = GroupLogic.get(accountContext).getModel(messageRecord.gid)
        reply_to.text = if (recipient == null) {
            null
        }else {
            BcmGroupNameUtil.getGroupMemberName(recipient, groupModel?.getGroupMember(recipient.address.serialize()))
        }
        when {
            content.getReplyMessage().isText() -> {
                reply_source_text.visibility = View.VISIBLE
                reply_source_content.visibility = View.GONE
                ChatViewHolder.interceptTextLink(reply_source_text, messageRecord.isSendByMe, (content.getReplyMessage().content as AmeGroupMessage.TextContent).text)
            }
            content.getReplyMessage().isLink() -> {
                reply_source_text.visibility = View.VISIBLE
                reply_source_content.visibility = View.GONE
                ChatViewHolder.interceptTextLink(reply_source_text, messageRecord.isSendByMe, (content.getReplyMessage().content as AmeGroupMessage.LinkContent).url)
            }
            else -> {
                reply_source_text.visibility = View.GONE
                reply_source_content.visibility = View.VISIBLE
                reply_source_content.text = content.getReplyDescribe(messageRecord.gid, accountContext, messageRecord.isSendByMe)
            }
        }
        setReplaySnapshot(accountContext, messageRecord)
        ChatViewHolder.interceptMessageText(reply_text, messageRecord, content.text)
    }

    fun setAppearance(isOutgoing: Boolean) {
        if (isOutgoing) {
            reply_to.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            reply_text.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            reply_text.setLinkTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            reply_source_text.setTextColor(Color.parseColor("#AAD5FF"))
            reply_source_content.setTextColor(Color.parseColor("#AAD5FF"))

            reply_line.setBackgroundColor(AppUtil.getColor(resources, R.color.common_color_white_30))
        } else {
            reply_to.setTextColor(AppUtil.getColor(resources, R.color.common_color_black))
            reply_text.setTextColor(AppUtil.getColor(resources, R.color.common_color_black))
            reply_text.setLinkTextColor(AppUtil.getColor(resources, R.color.common_app_primary_color))
            reply_source_text.setTextColor(AppUtil.getColor(resources, R.color.common_content_second_color))
            reply_source_content.setTextColor(AppUtil.getColor(resources, R.color.common_content_second_color))

            reply_line.setBackgroundColor(Color.parseColor("#E8E8E8"))
        }
    }

    private fun setReplaySnapshot(accountContext: AccountContext, messageRecord: AmeGroupMessageDetail) {
        val content = messageRecord.message.content as? AmeGroupMessage.ReplyContent
        val replyContent = content?.getReplyMessage()?.content ?: return
        when {
            content.getReplyMessage().type == AmeGroupMessage.LOCATION -> {
                reply_file.visibility = View.GONE
                reply_photo.visibility = View.GONE
                reply_image.visibility = View.VISIBLE
                reply_image.setPhoto(R.drawable.chats_reply_location_snapshot)
            }
            replyContent is AmeGroupMessage.FileContent -> {
                reply_image.visibility = View.GONE
                reply_photo.visibility = View.GONE
                reply_file.visibility = View.VISIBLE
                if (messageRecord.isSendByMe) {
                    reply_file.setBackgroundResource(R.drawable.chats_message_file_icon)
                } else {
                    reply_file.setBackgroundResource(R.drawable.chats_message_file_icon_grey)
                }
                ALog.d(TAG, "setReplySnapshot url: ${replyContent.url}")
                reply_file.text = AmeGroupMessage.FileContent.getTypeName(replyContent.fileName
                        ?: replyContent.url, replyContent.mimeType)
                reply_file.setTextColor(AmeGroupMessage.FileContent.getTypeColor(reply_file.text.toString()))
            }
            replyContent is AmeGroupMessage.ContactContent -> {
                reply_file.visibility = View.GONE
                reply_image.visibility = View.GONE
                reply_photo.visibility = View.VISIBLE
                val r = Recipient.from(AMELogin.majorContext, replyContent.uid, true)
                reply_photo.setPhoto(r)
            }
            replyContent is AmeGroupMessage.ThumbnailContent -> {
                reply_file.visibility = View.GONE
                reply_image.visibility = View.VISIBLE
                reply_photo.visibility = View.GONE
                val weakActivity = WeakReference(context as Activity)

                MessageFileHandler.downloadThumbnail(messageRecord.gid, messageRecord.indexId, replyContent, messageRecord.keyVersion, object : MessageFileHandler.MessageFileCallback {
                    override fun onResult(success: Boolean, uri: Uri?) {
                        if (weakActivity.get()?.isFinishing == true || weakActivity.get()?.isDestroyed == true) {
                            return
                        }
                        if (mGroupMessage == messageRecord) {
                            if (success) {
                                val masterSecret = BCMEncryptUtils.getMasterSecret(accountContext) ?: return
                                buildThumbnailRequest(masterSecret, uri) {
                                    ALog.d(TAG, "buildThumbnailRequest uri: $uri fail, try again")
                                    Observable.create(ObservableOnSubscribe<AmeGroupMessage<*>> {
                                        val lastMsg = MessageDataManager.fetchOneMessageByGidAndMid(accountContext, messageRecord.gid, content.mid)
                                        if (lastMsg != null) {
                                            val newMessage = AmeGroupMessage(AmeGroupMessage.CHAT_REPLY, AmeGroupMessage.ReplyContent(content.mid, content.uid, lastMsg.message.toString(), content.text))
                                            it.onNext(newMessage)
                                        }
                                        it.onComplete()
                                    }).subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                if (mGroupMessage == messageRecord) {
                                                    messageRecord.message = it
                                                    try {
                                                        setReplaySnapshot(accountContext, messageRecord)
                                                    } catch (ex: Exception) {
                                                        ALog.e(TAG, "buildThumbnailRequest failback error", ex)
                                                    }
                                                }

                                            }, {
                                                ALog.e(TAG, "setReplaySnapshot downloadThumbnail error", it)
                                            })
                                }
                            }
                        }
                    }
                })
            }
            else -> {
                reply_file.visibility = View.GONE
                reply_image.visibility = View.GONE
                reply_photo.visibility = View.GONE
            }
        }
    }


    private fun buildThumbnailRequest(masterSecret: MasterSecret, uri: Uri?, failCallback: (() -> Unit)? = null) {
        try {
            ALog.i(TAG, "buildThumbnailRequest: $uri")
            reply_image.setCallback(object : IndividualAvatarView.RecipientPhotoCallback {
                override fun onLoaded(recipient: Recipient?, bitmap: Bitmap?, success: Boolean) {
                    if (!success) {
                        failCallback?.invoke()
                    }
                }
            })

            val loadObj = if (uri == null) null else DecryptableStreamUriLoader.DecryptableUri(masterSecret, uri)
            reply_image.requestPhoto(loadObj, mImagePlaceHolder, mImageError)

        } catch (ex: Exception) {
            ALog.e(TAG, "buildThumbnailRequest error", ex)
        }
    }

}