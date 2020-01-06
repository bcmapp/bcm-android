package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageModel
import com.bcm.messenger.adhoc.ui.AdHocSessionSelectionActivity
import com.bcm.messenger.adhoc.ui.channel.holder.AdHocChatViewHolder
import com.bcm.messenger.chats.components.ConversationItemPopWindow
import com.bcm.messenger.chats.components.UnreadMessageBubbleView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonConversationAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.adhoc_activity_conversation.*
import kotlinx.android.synthetic.main.adhoc_fragment_conversation.*
import kotlinx.android.synthetic.main.adhoc_sticky_secure_item.view.*
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * Created by wjh on 2019/7/27
 */
class AdHocConversationFragment : BaseFragment() {

    companion object {
        private const val TAG = "AdHocConversationFragment"
        private const val PAGE_COUNT = 100

        private const val FORWARD_REQUEST = 1001
    }
    private var mAdapter: CommonConversationAdapter<AdHocMessageDetail>? = null
    private var lastIndexAtomic: AtomicLong = AtomicLong(0)
    private var mAdHocModel: AdHocMessageModel? = null
    private var topUnreadBubble: UnreadMessageBubbleView? = null
    private var bottomUnreadBubble: UnreadMessageBubbleView? = null
    private var mSelectItemBatch: MutableSet<AdHocMessageDetail>? = null
    private var mHasMore: Boolean = false
    private var mCurrentForwardSource: String? = null
    private var mCurrentForwardText: String? = null


    private val isListAtBottom: Boolean
        get() {
            if (chats_conversation_list?.childCount ?: 0 == 0) {
                return true
            }
            return (chats_conversation_list?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0 == 0
        }


    override fun onNewIntent() {
        super.onNewIntent()
        initAdapter()
        initResource()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FORWARD_REQUEST && resultCode == Activity.RESULT_OK) {
            val targetSession = data?.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION)
            if (targetSession != null && !mCurrentForwardText.isNullOrEmpty()) {
                try {
                    val message = AdHocMessageDetail(0, targetSession, AdHocMessageLogic.myAdHocId() ?: return).apply {
                        sendByMe = true
                        nickname = Recipient.major().name
                        setMessageBodyJson(mCurrentForwardText ?: "")
                        val content = getMessageBody()?.content
                        if (content is AmeGroupMessage.AttachmentContent) {
                            attachmentUri = content.url // attachmentContent url save path
                            thumbnailUri = content.url // attachmentContent url save path
                        }
                    }
                    AdHocMessageLogic.send(targetSession, message.nickname, message)
                }catch (ex: Exception) {
                    ALog.e(TAG, "forward text fail", ex)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.adhoc_fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.i(TAG, "onViewCreated")
        topUnreadBubble = view.findViewById(R.id.message_unread_up_bubble)
        bottomUnreadBubble = view.findViewById(R.id.message_unread_down_bubble)

        val layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, true)
        layoutManager.stackFromEnd = true
        chats_conversation_list.setHasFixedSize(false)
        chats_conversation_list.layoutManager = layoutManager
        chats_conversation_list.itemAnimator = null
        chats_conversation_list.overScrollMode = View.OVER_SCROLL_NEVER
        chats_conversation_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            private var isSlidingToLast = false  //slide to up
            private var isSlidingToFirst = false //slide to bottom

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                //dx>0:slide to right,dx<0:slide to left
                //dy>0:slide to down,dy<0:slide to top
                isSlidingToLast = dy < 0
                isSlidingToFirst = dy > 0
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount

                    if (isSlidingToLast && mHasMore && lastVisibleItem >= totalItemCount - 1) {
                        mAdHocModel?.let {
                            loadMore(it)
                        }
                    }

                    if (isSlidingToLast && topUnreadBubble?.visibility == View.VISIBLE && lastVisibleItem >= topUnreadBubble?.unreadCount ?: 0) {
                        topUnreadBubble?.visibility = View.GONE
                    }

                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                    if (isSlidingToFirst && bottomUnreadBubble?.visibility == View.VISIBLE && firstVisibleItem <= (bottomUnreadBubble?.unreadCount ?: 0) - 1) {
                        bottomUnreadBubble?.visibility = View.GONE
                    }

                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val c = activity
                    if (c is AdHocConversationActivity) {
                        c.hideInput()
                    }
                }
            }
        })
        chats_conversation_list.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            }

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                val v = rv.findChildViewUnder(e.x, e.y)
                if(v == null) {
                    activity?.let {
                        if (it is AdHocConversationActivity) {
                            it.hideInput()
                        }
                    }
                }
                return false
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        initAdapter()
        initResource()
    }


    fun getMessageView(indexId: Long): View? {
        val layoutManager = chats_conversation_list?.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val lastPos = layoutManager.findLastVisibleItemPosition()
            for (i in firstPos..lastPos) {
                val view = layoutManager.findViewByPosition(i)
                if (view != null) {
                    val holder = chats_conversation_list?.getChildViewHolder(view)
                    if (holder is AdHocChatViewHolder) {
                        val bodyView = holder.getMessageView(indexId)
                        if (bodyView != null) return bodyView
                    }
                }
            }
        }
        return null
    }

    fun scrollToBottom(isSmooth: Boolean) {
        scrollToPosition(isSmooth, 0)
    }

    fun scrollToPosition(isSmooth: Boolean, position: Int) {
        chats_conversation_list?.post {
            if (isSmooth) {
                chats_conversation_list?.smoothScrollToPosition(position)
            }else {
                chats_conversation_list?.scrollToPosition(position)
            }
        }
    }

    private fun initResource() {
        lastIndexAtomic.set(0)
        mHasMore = false
        bottomUnreadBubble?.visibility = View.GONE
        topUnreadBubble?.visibility = View.GONE
        mCurrentForwardSource = null
        mCurrentForwardText = null
        mSelectItemBatch = null
        mAdHocModel = AdHocMessageLogic.getModel()
        mAdHocModel?.addOnMessageListener(object : AdHocMessageModel.DefaultOnMessageListener(TAG) {

            override fun onForward(source: String, text: String) {
                mCurrentForwardSource = source
                mCurrentForwardText = text
                val intent = Intent(context, AdHocSessionSelectionActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, mAdHocModel?.getSessionId())
                }
                startActivityForResult(intent, FORWARD_REQUEST)
            }

            override fun onClearHistory() {
                ALog.i(TAG, "onClearHistory")
                mAdapter?.removeAllNotRefresh()
                mAdapter?.loadData(createStickySecureMessage(mAdHocModel?.getSessionId() ?: ""))

            }

            override fun onAddMessage(messageList: List<AdHocMessageDetail>) {
                ALog.i(TAG, "onAddMessage messageList: ${messageList.size}")
                if (messageList.isEmpty()) {
                    return
                }
                val isSendByMe = messageList.first().sendByMe
                val notFixed = (isListAtBottom || isSendByMe) && !ConversationItemPopWindow.isConversationPopShowing()
                mAdapter?.loadData(0, messageList, notFixed)
                chats_conversation_list?.post {
                    if (notFixed) {
                        scrollToBottom(true)
                    }else {
                        var unread = 0
                        messageList.forEach {
                            if (!it.sendByMe) {
                                unread++
                            }
                        }
                        if (unread > 0) {
                            bottomUnreadBubble?.visibility = View.VISIBLE
                            bottomUnreadBubble?.setUnreadMessageCount((bottomUnreadBubble?.unreadCount
                                    ?: 0) + unread)
                            bottomUnreadBubble?.setOnClickListener {
                                it.visibility = View.GONE
                                scrollToBottom(true)
                            }
                        }
                    }
                }
            }

            override fun onUpdateMessage(messageList: List<AdHocMessageDetail>) {
                ALog.i(TAG, "onUpdateMessage messageList: ${messageList.size}")
                messageList.forEach {
                    mAdapter?.replaceData(it)
                }
            }

            override fun onDeleteMessage(messageList: List<AdHocMessageDetail>) {
                ALog.i(TAG, "onDeleteMessage messageList: ${messageList.size}")
                messageList.forEach {
                    mAdapter?.removeData(it)
                }
            }

            override fun onAddAt(atId: String, atNick: String) {
                val a = activity
                if (a is AdHocConversationActivity) {
                    a.bottom_panel?.addAt(atId, atNick)
                }
            }
        })

        mAdHocModel?.let {
            fetchMessage(it)
        }
    }

    private fun initAdapter() {
        activity?.let {activity ->
            mAdapter = CommonConversationAdapter(activity, object : CommonConversationAdapter.IConversationDelegate<AdHocMessageDetail> {

                private val calendar = Calendar.getInstance()
                override fun getViewHolderType(adapter: CommonConversationAdapter<AdHocMessageDetail>, position: Int, data: AdHocMessageDetail): Int {
                    return when {
                        data.getMessageBody()?.type == AmeGroupMessage.MESSAGE_SECURE_NOTICE -> R.layout.adhoc_sticky_secure_item
                        data.sendByMe -> R.layout.adhoc_conversation_sent_item
                        else -> R.layout.adhoc_conversation_received_item
                    }
                }

                override fun createViewHolder(adapter: CommonConversationAdapter<AdHocMessageDetail>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    return if (viewType == R.layout.adhoc_sticky_secure_item) {
                        object : RecyclerView.ViewHolder(inflater.inflate(viewType, parent, false)) {
                            init {
                                itemView.adhoc_sticky_secure_layout?.setNormal(getString(R.string.adhoc_channel_message_sticky_secure))
                            }
                        }
                    }else {
                        val holder = AdHocChatViewHolder(inflater.inflate(viewType, parent, false))
                        holder.itemView.setOnClickListener {
                            if (activity is AdHocConversationActivity) {
                                activity.hideInput()
                            }
                        }
                        holder
                    }
                }

                override fun bindViewHolder(adapter: CommonConversationAdapter<AdHocMessageDetail>, viewHolder: RecyclerView.ViewHolder, position: Int, data: AdHocMessageDetail) {
                    if (viewHolder is AdHocChatViewHolder) {
                        viewHolder.bind(data, GlideApp.with(this@AdHocConversationFragment), mSelectItemBatch)
                    }
                    if (position == 0) {
                        val lp = viewHolder.itemView.layoutParams as RecyclerView.LayoutParams
                        lp.bottomMargin = 25.dp2Px()
                        viewHolder.itemView.layoutParams = lp

                    } else {
                        val lp = viewHolder.itemView.layoutParams as RecyclerView.LayoutParams
                        if (lp.bottomMargin > 0) {
                            lp.bottomMargin = 0
                            viewHolder.itemView.layoutParams = lp
                        }
                    }
                }

                override fun unbindViewHolder(adapter: CommonConversationAdapter<AdHocMessageDetail>, viewHolder: RecyclerView.ViewHolder) {
                    if (viewHolder is AdHocChatViewHolder) {
                        viewHolder.unbind()
                    }
                }

                override fun getItemId(position: Int, data: AdHocMessageDetail): Long {
                    return data.indexId
                }

                override fun getLastSeenHeaderId(position: Int, data: AdHocMessageDetail): Long {
                    calendar.time = Date(data.time)
                    return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR)).toLong()
                }

                override fun getReceiveTime(position: Int, data: AdHocMessageDetail): Long {
                    return data.time
                }

            })

            chats_conversation_list.addItemDecoration(CommonConversationAdapter.LastSeenHeader(mAdapter!!, 1000 * 60 * 3))
            chats_conversation_list.adapter = mAdapter

        }
    }


    private fun fetchMessage(model: AdHocMessageModel) {
        model.fetchMessageWithUnRead(-1L, PAGE_COUNT) { result, unread ->
            ALog.i(TAG, "fetchMessage session: ${model.getSessionId()}, result: ${result.size}, unread: $unread")
            if (result.isNotEmpty()) {
                lastIndexAtomic.set(result.last().indexId)
                mHasMore = result.size == PAGE_COUNT
                mAdapter?.removeAll()
                mAdapter?.loadData(result + createStickySecureMessage(model.getSessionId()))
                scrollToBottom(false)
                chats_conversation_list?.post {
                    val visibleCount = chats_conversation_list?.layoutManager?.childCount ?: 0
                    if (unread > visibleCount) {
                        topUnreadBubble?.visibility = View.VISIBLE
                        topUnreadBubble?.setUnreadMessageCount(unread - visibleCount)
                        topUnreadBubble?.setOnClickListener {
                            it.visibility = View.GONE
                            scrollToPosition(true, unread)
                        }
                    }
                }

            }else {
                mAdapter?.loadData(createStickySecureMessage(model.getSessionId()))
            }
            AdHocMessageLogic.updateSessionUnread(model.getSessionId(), unread, true)
        }
    }


    private fun loadMore(model: AdHocMessageModel) {
        model.fetchMessage(lastIndexAtomic.get(), PAGE_COUNT) { result ->
            if (result.isNotEmpty()) {
                lastIndexAtomic.set(result.last().indexId)
                var index = (mAdapter?.getDataCount() ?: 0) - 1
                if (index < 0) {
                    index = 0
                }
                mAdapter?.loadData(index, result)
                mHasMore = result.size == PAGE_COUNT
            }

        }
    }


    private fun createStickySecureMessage(session: String): AdHocMessageDetail {
        return AdHocMessageDetail(0, session, AdHocMessageLogic.myAdHocId() ?: "").apply {
            setMessageBody(AmeGroupMessage(AmeGroupMessage.MESSAGE_SECURE_NOTICE, AmeGroupMessage.SecureContent()))
            time = -1L
        }
    }

}