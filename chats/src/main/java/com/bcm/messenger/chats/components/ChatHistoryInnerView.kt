package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener

/**
 * Created by Kin on 2018/10/23
 */
const val IN_VIEW_FORWARD = 1
const val IN_VIEW_CHAT_SEND = 2
const val IN_VIEW_CHAT_RECEIVE = 3

class ChatHistoryInnerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr), RecipientModifiedListener {

    private val TAG = "ChatHistoryInnerView"
    private val rootView: LinearLayout
    private var inView = 1
    private var textSize = 0
    private var textColor = 0
    private var nameColor = 0
    private var bold = false
    private var parentWidth = AppUtil.dp2Px(resources, 195)

    private var mCurrentMessageList: List<Any>? = null
    private var mShowName = false
    private var mRecipientList = mutableListOf<Recipient>()

    init {
        inflate(context, R.layout.chats_history_inner_view, this)
        rootView = getChildAt(0) as LinearLayout

        val array = context.obtainStyledAttributes(attrs, R.styleable.chats_historyInnerViewStyle)
        textSize = array.getDimensionPixelSize(R.styleable.chats_historyInnerViewStyle_chats_textSize, 36)
        inView = array.getInt(R.styleable.chats_historyInnerViewStyle_chats_inView, 1)
        array.recycle()

        setStyle()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mRecipientList.forEach {
            it.removeListener(this)
        }
    }

    fun setViewStyle(inView: Int) {
        this.inView = inView
        setStyle()
    }

    private fun setStyle() {
        when (inView) {
            1 -> {
                textColor = AppUtil.getColor(resources, R.color.common_color_black)
                nameColor = AppUtil.getColor(resources, R.color.common_color_black)
                bold = true
            }
            2 -> {
                textColor = AppUtil.getColor(resources, R.color.chats_mine_tips_text_color)
                nameColor = AppUtil.getColor(resources, R.color.common_color_EBF5FF)
                bold = false
            }
            3 -> {
                textColor = AppUtil.getColor(resources, R.color.common_content_second_color)
                nameColor = AppUtil.getColor(resources, R.color.common_color_616161)
                bold = false
            }
        }
    }


    fun setHistoryData(messageList: List<Any>, showName: Boolean = false) {
        mCurrentMessageList = messageList
        mShowName = showName
        mRecipientList.clear()

        refresh()

        ALog.i(TAG, "setHistoryData messageList: ${messageList.size}")
        var currentLines = 0
        for ((index, it) in messageList.withIndex()) {
            var recipient: Recipient? = null
            var bodyText: CharSequence = ""

            when (it) {
                is MessageRecord -> {
                    recipient = if (it.isOutgoing()) {
                        Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true)
                    } else {
                        it.getRecipient()
                    }
                    bodyText = getHistoryItemBodyText(it)

                }
                is AmeGroupMessageDetail -> {
                    recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(it.senderId), true)
                    bodyText = getHistoryItemBodyText(it.message)

                }
                is HistoryMessageDetail -> {
                    recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(it.sender ?: ""), true)
                    bodyText = getHistoryItemBodyText(AmeGroupMessage.messageFromJson(it.messagePayload ?: ""))
                }
            }

            val ssb = SpannableStringBuilder()
            if (showName && recipient != null) {
                this.mRecipientList.add(recipient)
                recipient.addListener(this)

                ssb.append(InputLengthFilter.filterString(recipient.name, 10), ForegroundColorSpan(nameColor), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                ssb.append(": ", ForegroundColorSpan(nameColor), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                if (bold) {
                    ssb.setSpan(StyleSpan(Typeface.BOLD), 0, ssb.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }
            ssb.append(bodyText)

            val textView = rootView.getChildAt(index) as TextView
            textView.setTextColor(textColor)
            textView.text = ssb
            textView.visibility = View.VISIBLE

            if (currentLines == 3) {
                if (index != messageList.lastIndex) {
                    textView.maxLines = 1
                    val nextView = rootView.getChildAt(index + 1) as TextView
                    nextView.setTextColor(textColor)
                    nextView.text = "…"
                    nextView.visibility = View.VISIBLE
                    break
                }
            } else if (currentLines == 4) {
                textView.setTextColor(textColor)
                textView.text = "…"
                break
            } else if (currentLines == 5 || index == 4) {
                break
            }
            val newLines = getTextViewLines(textView)
            currentLines += newLines

            ALog.i(TAG, "setHistoryData newLines: $newLines, currentLines: $currentLines")
        }
    }

    private fun getHistoryItemBodyText(message: MessageRecord): CharSequence {
        return when {
            message.isMediaMessage() && message.getDocumentAttachment() != null -> getString(R.string.chats_history_message_file)
            message.isMediaMessage() && message.getImageAttachment() != null -> getString(R.string.chats_history_message_image)
            message.isMediaMessage() && message.getVideoAttachment() != null -> getString(R.string.chats_history_message_video)
            message.isLocation() -> {
                val newMessage = AmeGroupMessage.messageFromJson(message.body)
                when {
                    newMessage.type == AmeGroupMessage.LOCATION -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_location)
                    newMessage.type == AmeGroupMessage.CONTACT -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_contact)
                    newMessage.type == AmeGroupMessage.LINK -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_link)
                    newMessage.type == AmeGroupMessage.NEWSHARE_CHANNEL -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_channel)
                    else -> ""
                }
            }
            else -> message.getDisplayBody().toString()
        }
    }

    private fun getHistoryItemBodyText(message: AmeGroupMessage<*>): CharSequence {
        return when (message.type) {
            AmeGroupMessage.TEXT -> {
                val content = message.content as AmeGroupMessage.TextContent
                content.text
            }
            AmeGroupMessage.IMAGE -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_image)
            AmeGroupMessage.VIDEO -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_video)
            AmeGroupMessage.FILE -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_file)
            AmeGroupMessage.CONTACT -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_contact)
            AmeGroupMessage.LOCATION -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_location)
            AmeGroupMessage.LINK -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_link)
            AmeGroupMessage.NEWSHARE_CHANNEL -> AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_history_message_channel)
            AmeGroupMessage.CHAT_REPLY -> {
                val content = message.content as AmeGroupMessage.ReplyContent
                content.text
            }
            else -> ""
        }
    }

    fun refresh() {
        for (i in 0 until rootView.childCount) {
            val textView = rootView.getChildAt(i) as TextView
            textView.text = ""
            textView.maxLines = 2
            textView.visibility = View.GONE
        }
    }

    private fun getTextViewLines(textView: TextView): Int {
        val paint = textView.paint
        val textWidth = paint.measureText(textView.text.toString()).toInt()
        return if (textWidth > parentWidth) 2 else 1
    }

    override fun onModified(recipient: Recipient) {
        if (mRecipientList.find { it == recipient } != null) {
            post {
                setHistoryData(mCurrentMessageList ?: return@post, mShowName)
            }
        }
    }

    fun setParentWidth(width: Int) {
        this.parentWidth = width
    }
}