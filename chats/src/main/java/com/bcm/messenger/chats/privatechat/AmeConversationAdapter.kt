package com.bcm.messenger.chats.privatechat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ConversationStickNoticeLayout
import com.bcm.messenger.common.api.BindableConversationItem
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.crypto.MasterCipher
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.StickyHeaderDecoration
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Conversions
import com.bcm.messenger.utility.Util
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 *
 */
class AmeConversationAdapter(val context: Context, val masterSecret: MasterSecret,
                             val glideRequests: GlideRequests,
                             val locale: Locale,
                             val recipient: Recipient,
                             val delegate: IConversationDelegate) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaderDecoration.StickyHeaderAdapter<AmeConversationAdapter.HeaderViewHolder> {

    companion object {
        private const val TAG = "AmeConversationAdapter"

        private const val MESSAGE_TYPE_OUTGOING = 0
        private const val MESSAGE_TYPE_INCOMING = 1
        private const val MESSAGE_TYPE_UPDATE = 2
        private const val MESSAGE_TYPE_STICKY = 3
    }

    var selectedItems: Set<MessageRecord>? = null
        set(batchSelected) {
            field = batchSelected
            notifyDataSetChanged()
        }

    private val inflater: LayoutInflater
    private val calendar: Calendar
    private val digest: MessageDigest
    private val masterCipher: MasterCipher

    private val mRecordList = mutableListOf<MessageRecord>()
    private var mStickyNotificationView: ConversationStickNoticeLayout? = null

    interface IConversationDelegate {
        fun onViewClicked(adapter: AmeConversationAdapter, viewHolder: RecyclerView.ViewHolder)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        init {
            itemView.setOnClickListener {
                delegate.onViewClicked(this@AmeConversationAdapter, this)
            }
        }

        fun getItem(): BindableConversationItem? {
            return itemView as? BindableConversationItem
        }

    }

    open class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView

        init {
            textView = itemView.findViewById(R.id.text)
        }

        fun setText(text: CharSequence) {
            textView.text = text
        }
    }


    init {
        try {
            this.masterCipher = MasterCipher(masterSecret)
            this.inflater = LayoutInflater.from(context)
            this.calendar = Calendar.getInstance()
            this.digest = MessageDigest.getInstance("SHA1")
            setHasStableIds(true)

        } catch (nsae: NoSuchAlgorithmException) {
            throw AssertionError("SHA1 isn't supported!")
        }
    }

    fun getRecordList(): List<MessageRecord> {
        return mRecordList
    }

    fun initRecordList(recordList: List<MessageRecord>, notify: Boolean = false) {
        mRecordList.clear()
        mRecordList.addAll(recordList)
        if (notify) {
            notifyDataSetChanged()
        }
    }

    fun clearRecord(notify: Boolean = false) {
        mRecordList.clear()
        if (notify) {
            notifyDataSetChanged()
        }
    }

    fun replaceRecord(position: Int, record: MessageRecord, notify: Boolean = false) {
        if (position in 0 until mRecordList.size) {
            mRecordList[position] = record
            if (notify) {
                notifyItemChanged(position)
            }
        }
    }

    fun removeRecordForId(id: Long, notify: Boolean = false) {
        for ((index, record) in mRecordList.withIndex()) {
            if (record.id == id) {
                removeRecord(index, notify)
                break
            }
        }
    }

    fun removeRecord(data: MessageRecord, notify: Boolean = false) {
        val contains = mRecordList.contains(data)
        if (contains) {
            val index = mRecordList.indexOf(data)
            removeRecord(index, notify)
        }
    }


    fun removeRecord(position: Int, notify: Boolean = false) {
        if (mRecordList.size == 0) {
            return
        }
        if (position in 0 until mRecordList.size) {
            mRecordList.removeAt(position)
            if (notify) {
                notifyItemRemoved(position)
            }
        }
    }


    fun loadRecord(data: MessageRecord, notify: Boolean = true) {
        val index = mRecordList.size
        loadRecord(index, data, notify)
    }


    fun loadRecord(dataList: List<MessageRecord>, notify: Boolean = true) {
        val index = mRecordList.size
        loadRecord(index, dataList, notify)
    }


    fun loadRecord(index: Int, data: MessageRecord, notify: Boolean = true) {
        val count = mRecordList.size
        if (hasStickyNotification()) {
            if (index == count) {
                mRecordList.add(data)
            } else {
                mRecordList.add(index, data)
            }
            if (notify) {
                notifyItemRangeChanged(index, count - index + 2)
            } else {
                notifyItemInserted(index)
            }
        } else {
            if (index == count) {
                mRecordList.add(data)
            } else {
                mRecordList.add(index, data)
            }
            if (notify) {
                notifyItemRangeChanged(index, count - index + 1)
            } else {
                notifyItemInserted(index)
            }
        }
    }

    fun loadRecord(index: Int, dataList: List<MessageRecord>, notify: Boolean = true) {
        val count = mRecordList.size
        if (hasStickyNotification()) {
            if (index == count) {
                mRecordList.addAll(dataList)
            } else {
                mRecordList.addAll(index, dataList)
            }
            if (notify) {
                notifyItemRangeChanged(index, count + 1 - index + dataList.size)
            } else {
                notifyItemRangeInserted(index, dataList.size)
            }
        } else {
            if (index == count) {
                mRecordList.addAll(dataList)
            } else {
                mRecordList.addAll(index, dataList)
            }
            if (notify) {
                notifyItemRangeChanged(index, count - index + dataList.size)
            } else {
                notifyItemRangeInserted(index, dataList.size)
            }
        }
    }


    fun findLastSeenPosition(lastSeen: Long): Int {
        var messageRecord: MessageRecord
        val recordList = mRecordList
        for (i in 0 until recordList.size) {
            messageRecord = recordList[i]
            if (messageRecord.isOutgoing() || messageRecord.dateReceive <= lastSeen) {
                return i
            }
        }
        return -1
    }


    fun addStickNotification(context: Context): ConversationStickNoticeLayout {
        if (mStickyNotificationView == null) {
            mStickyNotificationView = ConversationStickNoticeLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        return mStickyNotificationView!!
    }


    fun hasStickyNotification(): Boolean {
        return mStickyNotificationView != null
    }

    fun showStickyNotificationLoading(loading: Boolean) {
        mStickyNotificationView?.showLoading(loading)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MESSAGE_TYPE_STICKY -> object : RecyclerView.ViewHolder(mStickyNotificationView ?: View(context)) {}
            MESSAGE_TYPE_INCOMING -> ViewHolder(inflater.inflate(R.layout.chats_conversation_item_received, parent, false))
            MESSAGE_TYPE_OUTGOING -> ViewHolder(inflater.inflate(R.layout.chats_conversation_item_sent, parent, false))
            else -> ViewHolder(inflater.inflate(R.layout.chats_conversation_item_status, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val item = mRecordList[position]
            holder.getItem()?.bind(masterSecret, item, glideRequests, locale, selectedItems, recipient, position)
        }
    }

    override fun getItemCount(): Int {
        return if (hasStickyNotification()) {
            mRecordList.size + 1
        } else {
            mRecordList.size
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.getItem()?.unbind()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasStickyNotification()) {
            if (position == mRecordList.size) {
                MESSAGE_TYPE_STICKY
            } else {
                getItemViewType(mRecordList[position])
            }
        } else {
            getItemViewType(mRecordList[position])
        }
    }

    private fun getItemViewType(messageRecord: MessageRecord?): Int {
        return if (messageRecord == null) {
            MESSAGE_TYPE_INCOMING
        } else if (messageRecord.isJoin() ||
                messageRecord.isExpirationTimerUpdate() || messageRecord.isEndSession() ||
                messageRecord.isIdentityUpdate() || messageRecord.isIdentityVerify() ||
                messageRecord.isIdentityDefault()) {
            MESSAGE_TYPE_UPDATE
        } else if (messageRecord.isOutgoing()) {
            MESSAGE_TYPE_OUTGOING
        } else {
            MESSAGE_TYPE_INCOMING
        }
    }

    override fun getItemId(position: Int): Long {
        if (hasStickyNotification()) {
            if (position == mRecordList.size) {
                return 0
            }
        }
        val record = mRecordList[position]
        val fastPreflightId = record.getImageAttachment()?.fastPreflightId
                ?: record.getDocumentAttachment()?.fastPreflightId
                ?: record.getAudioAttachment()?.fastPreflightId
        if (fastPreflightId != null) {
            return fastPreflightId.toLong()
        }
        val uniqueId = if (record.isMediaMessage()) {
            "MMS::" + record.id + "::" + record.dateSent
        } else {
            "SMS::" + record.id + "::" + record.dateSent
        }
        val bytes = digest.digest(uniqueId.toByteArray())
        return Conversions.byteArrayToLong(bytes)
    }

    override fun getHeaderId(position: Int): Long {
        if (hasStickyNotification()) {
            if (position == mRecordList.size) {
                return -1
            }
        }
        return if (position in 0 until mRecordList.size) {
            val record = mRecordList[position]
            calendar.time = Date(record.dateSent)
            Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR)).toLong()
        } else {
            -1
        }
    }

    private fun getSentTimestamp(position: Int): Long {
        if (hasStickyNotification()) {
            if (position == mRecordList.size) {
                return 0
            }
        }
        return if (position in mRecordList.indices) {
            mRecordList[position].dateSent
        } else {
            0
        }
    }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): HeaderViewHolder {
        return HeaderViewHolder(LayoutInflater.from(context).inflate(R.layout.chats_conversation_item_header, parent, false))
    }

    override fun onBindHeaderViewHolder(viewHolder: HeaderViewHolder, position: Int) {
        viewHolder.setText(DateUtils.getConversationTimeSpan(context, getSentTimestamp(position), getSelectedLocale(AppContextHolder.APP_CONTEXT)))
    }

    internal class LastSeenHeader(private val adapter: AmeConversationAdapter) : StickyHeaderDecoration(adapter, false, false) {
        override fun hasHeader(parent: RecyclerView, stickyAdapter: StickyHeaderAdapter<*>, position: Int): Boolean {

            val currentRecordTimestamp = adapter.getSentTimestamp(position)
            val previousRecordTimestamp = adapter.getSentTimestamp(position + 1)

            return currentRecordTimestamp - previousRecordTimestamp > 1000 * 60 * 3
        }

        override fun getHeaderTop(parent: RecyclerView, child: View, header: View, adapterPos: Int, layoutPos: Int): Int {
            return parent.layoutManager?.getDecoratedTop(child) ?: 0
        }

        override fun getHeader(parent: RecyclerView, stickyAdapter: StickyHeaderAdapter<*>, position: Int): HeaderViewHolder {
            val viewHolder = adapter.onCreateHeaderViewHolder(parent)
            adapter.onBindHeaderViewHolder(viewHolder, position)

            val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

            val childWidth = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, viewHolder.itemView.layoutParams.width)
            val childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, viewHolder.itemView.layoutParams.height)

            viewHolder.itemView.measure(childWidth, childHeight)
            viewHolder.itemView.layout(0, 0, viewHolder.itemView.measuredWidth, viewHolder.itemView.measuredHeight)

            return viewHolder
        }
    }
}

