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
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.logger.ALog

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
    private var parentWidth = 195.dp2Px()

    private var mCurrentMessageList: List<Any>? = null
    private var mShowName = false
    private var mRecipientList = mutableMapOf<String, Recipient>()

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
        mRecipientList.values.forEach {
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
                textColor = getColor(R.color.common_color_black)
                nameColor = getColor(R.color.common_color_black)
                bold = true
            }
            2 -> {
                textColor = getColor(R.color.chats_mine_tips_text_color)
                nameColor = getColor(R.color.common_color_EBF5FF)
                bold = false
            }
            3 -> {
                textColor = getColor(R.color.common_content_second_color)
                nameColor = getColor(R.color.common_color_616161)
                bold = false
            }
        }
    }


    fun setHistoryData(messageList: List<Any>, showName: Boolean = false) {
        setHistoryData(messageList, showName, true)
    }

    private fun setHistoryData(messageList: List<Any>, showName: Boolean = false, newData: Boolean = false) {
        if (newData) {
            mCurrentMessageList = messageList
            mShowName = showName
            mRecipientList.clear()
        }

        refresh()

        ALog.i(TAG, "setHistoryData messageList: ${messageList.size}")
        var currentLines = 0
        for ((index, it) in messageList.withIndex()) {
            var recipient: Recipient? = null
            var bodyText: CharSequence = ""

            when (it) {
                is MessageRecord -> {
                    recipient = if (it.isOutgoing()) {
                        Recipient.major()
                    } else {
                        it.getRecipient()
                    }
                    if (newData) {
                        mRecipientList[recipient.address.serialize()] = recipient
                        recipient.addListener(this)
                    } else {
                        recipient = mRecipientList[recipient.address.serialize()]
                    }
                    bodyText = getHistoryItemBodyText(it)
                }
                is AmeGroupMessageDetail -> {
                    recipient = if (newData) {
                        val r = Recipient.from(AMELogin.majorContext, Address.fromSerialized(it.senderId), true)
                        mRecipientList[r.address.serialize()] = r
                        r.addListener(this)
                        r
                    } else {
                        mRecipientList[it.senderId]
                    }
                    bodyText = getHistoryItemBodyText(it.message)
                }
                is HistoryMessageDetail -> {
                    recipient = if (newData) {
                        val r = Recipient.from(AMELogin.majorContext, Address.fromSerialized(it.sender ?: ""), true)
                        mRecipientList[r.address.serialize()] = r
                        r.addListener(this)
                        r
                    } else {
                        mRecipientList[it.sender ?: ""]
                    }
                    bodyText = getHistoryItemBodyText(AmeGroupMessage.messageFromJson(it.messagePayload ?: ""))
                }
            }

            val ssb = SpannableStringBuilder()
            if (showName && recipient != null) {
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
                when (newMessage.type) {
                    AmeGroupMessage.LOCATION -> getString(R.string.chats_history_message_location)
                    AmeGroupMessage.CONTACT -> getString(R.string.chats_history_message_contact)
                    AmeGroupMessage.LINK -> getString(R.string.chats_history_message_link)
                    AmeGroupMessage.NEWSHARE_CHANNEL -> getString(R.string.chats_history_message_channel)
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
            AmeGroupMessage.IMAGE -> getString(R.string.chats_history_message_image)
            AmeGroupMessage.VIDEO -> getString(R.string.chats_history_message_video)
            AmeGroupMessage.FILE -> getString(R.string.chats_history_message_file)
            AmeGroupMessage.CONTACT -> getString(R.string.chats_history_message_contact)
            AmeGroupMessage.LOCATION -> getString(R.string.chats_history_message_location)
            AmeGroupMessage.LINK -> getString(R.string.chats_history_message_link)
            AmeGroupMessage.NEWSHARE_CHANNEL -> getString(R.string.chats_history_message_channel)
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
        val r = mRecipientList[recipient.address.serialize()]
        if (r != null) {
            mRecipientList[recipient.address.serialize()] = recipient
            setHistoryData(mCurrentMessageList ?: return, mShowName, false)
        }
    }

    fun setParentWidth(width: Int) {
        this.parentWidth = width
    }
}