package com.bcm.messenger.chats.components

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.setDrawableLeft
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.chats_dialog_forward.*

/**
 * Forward confirm dialog
 *
 * Created by Kin on 2018/8/21
 */
const val FORWARD_IMAGE = 0
const val FORWARD_VIDEO = 1
const val FORWARD_FILE = 2
const val FORWARD_TEXT = 3
const val FORWARD_CONTACT = 4
const val FORWARD_LOCATION = 5
const val FORWARD_PRIVATE_MULTIPLE = 6
const val FORWARD_GROUP_MULTIPLE = 7


class ChatForwardDialog : DialogFragment() {

    private val TAG = "ChatForwardDialog"

    private var dialogType: Int = -1
    private val recipients = mutableListOf<Recipient>()
    private lateinit var masterSecret: MasterSecret
    private var isGroup = false
    private var isShare = false
    private var forwardHistory = false

    private var forwardText = ""
    private var fileUri: Uri? = null
    private var glideRequests: GlideRequests? = null
    private var size = 0L
    private var duration = 0L
    private var callback: ((Boolean, String) -> Unit)? = null
    private var locationTitle = ""
    private var locationContent = ""
    private val privateMessageList = mutableListOf<MessageRecord>()
    private val groupMessageList = mutableListOf<AmeGroupMessageDetail>()

    private lateinit var accountContext:AccountContext

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(context, R.style.ForwardDialogStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_dialog_forward, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.setLayout(AppContextHolder.APP_CONTEXT.getScreenWidth() - 60.dp2Px(), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(R.color.common_color_transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        when (dialogType) {
            FORWARD_IMAGE -> {
                forward_image_layout.visibility = View.VISIBLE
                forward_image_size.text = parseSize(size)
                val uri = fileUri
                if (isShare) {
                    glideRequests?.asBitmap()
                            ?.load(uri)
                            ?.error(R.drawable.common_image_broken_img)
                            ?.diskCacheStrategy(DiskCacheStrategy.NONE)
                            ?.into(forward_image_preview)
                } else {
                    val ms = masterSecret
                    if (ms != null && uri != null) {
                        glideRequests?.asBitmap()
                                ?.load(DecryptableStreamUriLoader.DecryptableUri(ms, uri))
                                ?.error(R.drawable.common_image_broken_img)
                                ?.diskCacheStrategy(DiskCacheStrategy.NONE)
                                ?.into(forward_image_preview)
                    }
                }
            }
            FORWARD_VIDEO -> {
                forward_video_layout.visibility = View.VISIBLE
                forward_video_size.text = parseSize(size)
                forward_video_time.text = parseTime(duration)
                val uri = fileUri
                if (isShare) {
                    if (uri != null) {
                        if (uri.scheme == "http" || uri.scheme == Uri.EMPTY.scheme) {
                            BcmFileUtils.getRemoteVideoFrameInfo(uri.toString()) { _, previewPath ->
                                glideRequests?.asBitmap()
                                        ?.load(previewPath)
                                        ?.error(R.drawable.common_video_place_square_img)
                                        ?.diskCacheStrategy(DiskCacheStrategy.NONE)
                                        ?.into(forward_video_preview)
                            }
                        } else {
                            glideRequests?.asBitmap()
                                    ?.load(uri)
                                    ?.error(R.drawable.common_video_place_square_img)
                                    ?.diskCacheStrategy(DiskCacheStrategy.NONE)
                                    ?.into(forward_video_preview)
                        }
                    }
                } else {
                    val ms = masterSecret
                    if (ms != null && uri != null) {
                        glideRequests?.asBitmap()
                                ?.load(DecryptableStreamUriLoader.DecryptableUri(ms, uri))
                                ?.error(R.drawable.common_video_place_square_img)
                                ?.diskCacheStrategy(DiskCacheStrategy.NONE)
                                ?.into(forward_video_preview)
                    }
                }
            }
            FORWARD_FILE -> {
                forward_file_layout.visibility = View.VISIBLE
                forward_file_name.text = forwardText
                forward_file_icon.text = AmeGroupMessage.FileContent.getTypeName(forwardText, "")
            }
            FORWARD_CONTACT -> {
                forward_contact_layout.visibility = View.VISIBLE
                forward_contact_name.text = forwardText
            }
            FORWARD_TEXT -> {
                forward_text_layout.visibility = View.VISIBLE
                forward_text.text = forwardText
                if (forwardText.startsWith("http")) {
                    forward_text.setDrawableLeft(R.drawable.chats_forward_dialog_link_icon)
                }
            }
            FORWARD_LOCATION -> {
                forward_location_layout.visibility = View.VISIBLE
                forward_location_title.text = locationTitle
                forward_location_content.text = locationContent
            }
            FORWARD_PRIVATE_MULTIPLE -> {
                forward_history_layout.setParentWidth(265.dp2Px())
                forward_history_layout.visibility = View.VISIBLE
                forward_history_layout.setHistoryData(masterSecret.accountContext, privateMessageList)
                forward_mode_text.visibility = View.VISIBLE
                forward_mode_text.setOnClickListener {
                    switchForwardHistoryMode()
                }
            }
            FORWARD_GROUP_MULTIPLE -> {
                forward_history_layout.setParentWidth(265.dp2Px())
                forward_history_layout.visibility = View.VISIBLE
                forward_history_layout.setHistoryData(masterSecret.accountContext, groupMessageList)
                forward_mode_text.visibility = View.VISIBLE
                forward_mode_text.setOnClickListener {
                    switchForwardHistoryMode()
                }
            }
        }
        when (recipients.size) {
            0 -> {}
            1 -> {
                if (recipients[0].isGroupRecipient) {
                    val gid = recipients[0].groupId
                    forward_single_avatar.showGroupAvatar(masterSecret.accountContext, gid)
                    val groupInfo = GroupLogic.get(accountContext).getGroupInfo(gid)
                    forward_single_name.text = groupInfo?.displayName ?: ""
                } else {
                    forward_single_avatar.showPrivateAvatar(masterSecret.accountContext, recipients[0])
                    forward_single_name.text = recipients[0].name
                }
            }
            else -> {
                forward_single_avatar.visibility = View.GONE
                forward_single_name.visibility = View.GONE
                forward_multi_avatar_list.visibility = View.VISIBLE
                forward_multi_avatar_list.adapter = AvatarAdapter()
                forward_multi_avatar_list.layoutManager = GridLayoutManager(context, 6, GridLayoutManager.VERTICAL, false)
            }
        }
        forward_ok.setOnClickListener {
            dismiss()
            forward()
        }
        forward_cancel.setOnClickListener {
            dismiss()
        }
        forward_comment_text.imeOptions = EditorInfo.IME_ACTION_SEND
        forward_comment_text.setOnEditorActionListener {_, actionId, _ ->
            return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_SEND && forward_comment_text.text?.isNotEmpty() == true) {
                forward()
                true
            } else {
                context?.let {
                    ToastUtil.show(it, getString(R.string.chats_no_comment_toast))
                }
                false
            }
        }
    }

    fun forward() {
        val commentText = if (forward_comment_text.text.isNullOrEmpty()) {
            ""
        } else {
            forward_comment_text.text.toString()
        }
        callback?.invoke(forwardHistory, commentText)
    }

    private fun switchForwardHistoryMode() {
        if (!forward_mode_text.isActivated) {
            forward_mode_text.setDrawableLeft(R.drawable.chats_forward_checked)
            forwardHistory = true
            forward_mode_text.isActivated = true
            if (dialogType == FORWARD_GROUP_MULTIPLE) {
                forward_history_layout.setHistoryData(masterSecret.accountContext, groupMessageList, true)
            } else {
                forward_history_layout.setHistoryData(masterSecret.accountContext, privateMessageList, true)
            }
        } else {
            forward_mode_text.setDrawableLeft(R.drawable.chats_forward_unchecked)
            forwardHistory = false
            forward_mode_text.isActivated = false
            if (dialogType == FORWARD_GROUP_MULTIPLE) {
                forward_history_layout.setHistoryData(masterSecret.accountContext, groupMessageList, false)
            } else {
                forward_history_layout.setHistoryData(masterSecret.accountContext, privateMessageList, false)
            }
        }
    }

    private fun parseSize(size: Long): String {
        return when {
            size >= 1048576L -> String.format("%.2fMB", size.toDouble() / 1048576L)
            size >= 1024L -> "${size / 1024L}KB"
            size == 0L -> ""
            else -> "${size}B"
        }
    }

    private fun parseTime(duration: Long): String {
        return when {
            duration >= 60L -> String.format("%d:%02d", duration / 60L, duration % 60L)
            duration > 0L -> String.format("0:%02d", duration)
            else -> "0:00"
        }
    }

    private inner class AvatarAdapter : RecyclerView.Adapter<MultiAvatarViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MultiAvatarViewHolder {
            val view = layoutInflater.inflate(R.layout.chats_forward_multi_avatar, parent, false)
            return MultiAvatarViewHolder(masterSecret.accountContext, view)
        }

        override fun getItemCount() = recipients.size

        override fun onBindViewHolder(holder: MultiAvatarViewHolder, position: Int) {
            holder.setAvatar(recipients[position])
        }
    }

    private inner class MultiAvatarViewHolder(private val accountContext: AccountContext, itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar = itemView.findViewById<IndividualAvatarView>(R.id.forward_multi_avatar)

        fun setAvatar(recipient: Recipient) {
            avatar.setPhoto(recipient)
        }
    }

    fun setRecipients(recipients: List<Recipient>): ChatForwardDialog {
        this.recipients.addAll(recipients)
        return this
    }

    fun setForwardTextDialog(forwardText: String): ChatForwardDialog {
        dialogType = FORWARD_TEXT
        this.forwardText = forwardText
        return this
    }

    fun setForwardImageDialog(imageUri: Uri, size: Long, glideRequests: GlideRequests): ChatForwardDialog {
        dialogType = FORWARD_IMAGE
        fileUri = imageUri
        this.glideRequests = glideRequests
        this.size = size
        return this
    }

    fun setForwardVideoDialog(videoUri: Uri, size: Long, duration: Long, glideRequests: GlideRequests): ChatForwardDialog {
        dialogType = FORWARD_VIDEO
        fileUri = videoUri
        this.size = size
        this.duration = duration
        this.glideRequests = glideRequests
        return this
    }

    fun setForwardFileDialog(fileName: String): ChatForwardDialog {
        dialogType = FORWARD_FILE
        this.forwardText = fileName
        return this
    }

    fun setForwardContactDialog(contactName: String): ChatForwardDialog {
        dialogType = FORWARD_CONTACT
        this.forwardText = contactName
        return this
    }

    fun setForwardLocationDialog(title: String, content: String): ChatForwardDialog {
        dialogType = FORWARD_LOCATION
        locationTitle = title
        locationContent = content
        return this
    }

    fun setForwardPrivateMultiple(messageList: List<MessageRecord>): ChatForwardDialog {
        dialogType = FORWARD_PRIVATE_MULTIPLE
        privateMessageList.addAll(messageList)
        return this
    }

    fun setForwardGroupMultiple(messageList: List<AmeGroupMessageDetail>): ChatForwardDialog {
        dialogType = FORWARD_GROUP_MULTIPLE
        groupMessageList.addAll(messageList)
        return this
    }

    fun setCallback(callback: ((forwardHistory: Boolean, commentText: String) -> Unit)? = null): ChatForwardDialog {
        this.callback = callback
        return this
    }

    fun setMasterSecret(masterSecret: MasterSecret): ChatForwardDialog {
        this.masterSecret = masterSecret
        return this
    }

    fun setIsGroup(isGroup: Boolean): ChatForwardDialog {
        this.isGroup = isGroup
        return this
    }

    fun setIsShare(isShare: Boolean): ChatForwardDialog {
        this.isShare = isShare
        return this
    }
}