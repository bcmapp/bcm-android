package com.bcm.messenger.chats.privatechat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ConversationItem
import com.bcm.messenger.chats.components.ConversationItemPopWindow
import com.bcm.messenger.chats.components.ConversationStickNoticeLayout
import com.bcm.messenger.chats.components.UnreadMessageBubbleView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_conversation_fragment.*
import java.util.*

/**
 * 
 */
class AmeConversationFragment : Fragment(), RecipientModifiedListener {

    interface ChangeReadStateListener {
        fun onOperateMarkThreadRead()
    }

    companion object {
        private const val TAG = "AmeConversationFragment"
    }

    private lateinit var mMasterSecret: MasterSecret
    private lateinit var mLocale: Locale

    private var mRecipient: Recipient? = null

    private var mLastSeen: Long = -1L
    private var mChangeReadStateListener: ChangeReadStateListener? = null
    private var mLayoutManager: LinearLayoutManager? = null
    private var mAdapter: AmeConversationAdapter? = null

    private var mBottomUnreadBubble: UnreadMessageBubbleView? = null
    private var mTopUnreadBubble: UnreadMessageBubbleView? = null

    private var mStickNoticeLayout: ConversationStickNoticeLayout? = null

    private var mConversationViewModel: AmeConversationViewModel? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        this.mMasterSecret = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET)
                ?: BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT) ?: throw Exception("master secret is null")
        this.mLocale = arguments?.getSerializable(ARouterConstants.PARAM.PARAM_LOCALE) as? Locale ?: Locale.getDefault()

        activity?.let {
            mConversationViewModel = ViewModelProviders.of(it).get(AmeConversationViewModel::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View? {
        val convertView = inflater.inflate(R.layout.chats_conversation_fragment, container, false)
        mTopUnreadBubble = convertView.findViewById(R.id.message_unread_up_bubble)
        mBottomUnreadBubble = convertView.findViewById(R.id.message_unread_down_bubble)
        return convertView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mTopUnreadBubble?.setOrientationIcon(R.drawable.chats_10_up)
        mBottomUnreadBubble?.setOrientationIcon(R.drawable.chats_10_down)

        mLayoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, true)

        private_conversation_list?.setHasFixedSize(false)
        private_conversation_list?.layoutManager = mLayoutManager
        private_conversation_list?.itemAnimator = null
        private_conversation_list?.overScrollMode = View.OVER_SCROLL_NEVER

    }

    override fun onActivityCreated(bundle: Bundle?) {
        super.onActivityCreated(bundle)
        initResource()
        initViewModel()
    }

    fun onNewIntent() {
        initResource()
        initViewModel()
    }

    override fun onModified(recipient: Recipient) {
        if (this.mRecipient == recipient) {
            private_conversation_list?.post {
                setBackground(recipient.expireMessages)
            }
            mStickNoticeLayout?.setRecipient(recipient)
        }
    }

    fun setChangeReadStateListener(changeReadStateListener: ChangeReadStateListener) {
        this.mChangeReadStateListener = changeReadStateListener
    }


    fun setMultiSelectedItem(batchSelected: Set<MessageRecord>?) {
        mAdapter?.selectedItems = batchSelected
    }


    fun reloadList() {
        mConversationViewModel?.reload()
    }


    private fun initResource() {
        val activity = activity ?: return

        this.mLastSeen = activity.intent.getLongExtra(ARouterConstants.PARAM.PRIVATE_CHAT.LAST_SEEN_EXTRA, -1)
        this.mStickNoticeLayout = null

        checkRecipientUpdate(Recipient.from(activity, activity.intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ADDRESS), true))
    }

    private fun initViewModel() {

        mConversationViewModel?.messageLiveData?.observe(this, Observer {
            ALog.d(TAG, "receive message data: ${it.type.name}")
            if (it.type == AmeConversationViewModel.AmeConversationData.ACType.RESET) {
                val newRecipient = mConversationViewModel?.getRecipient()
                checkRecipientUpdate(newRecipient)
            }
            handleConversationMessageUpdate(it, mLastSeen)
        })
    }


    private fun checkRecipientUpdate(recipient: Recipient?) {
        val needReload = mRecipient == null
        if (mRecipient != recipient) {
            ALog.d(TAG, "checkReciepientUpdate true")
            mRecipient?.removeListener(this)
            mRecipient = recipient
            mRecipient?.addListener(this)

            setBackground(mRecipient?.expireMessages ?: 0)
            initAdapter(needReload)
        }
    }


    private fun initAdapter(needReload: Boolean) {
        val activity = activity ?: return
        val recipient = this.mRecipient
        if (recipient != null) {

            val adapter = AmeConversationAdapter(activity, mMasterSecret, GlideApp.with(AppContextHolder.APP_CONTEXT), mLocale, recipient, object : AmeConversationAdapter.IConversationDelegate {
                override fun onViewClicked(adapter: AmeConversationAdapter, viewHolder: RecyclerView.ViewHolder) {
                    //隐藏输入键盘
                    if (activity is AmeConversationActivity) {
                        activity.hideInput()
                    }
                }
            })

            this.mStickNoticeLayout = adapter.addStickNotification(activity)
            this.mStickNoticeLayout?.setRecipient(recipient)
            private_conversation_list?.addItemDecoration(AmeConversationAdapter.LastSeenHeader(adapter))
            private_conversation_list?.adapter = adapter
            this.mAdapter = adapter

            private_conversation_list?.addOnScrollListener(object : OnScrollListener() {

                private var isSlidingToLast = false
                private var isSlidingToFirst = false

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    //dx>0:slide to right,dx<0:slide to left
                    //dy>0:slide to down,dy<0:slide to up
                    isSlidingToLast = dy < 0
                    isSlidingToFirst = dy > 0

                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mLayoutManager?.let { layoutManager ->

                            val totalItemCount = layoutManager.itemCount
                            ALog.i(TAG, "totalItemCount: ${totalItemCount}, lastCompletelyVisibleItem: ${layoutManager.findLastCompletelyVisibleItemPosition()}, firstCompletelyVisible: ${layoutManager.findFirstCompletelyVisibleItemPosition()}")
                            if (isSlidingToLast && layoutManager.findLastCompletelyVisibleItemPosition() >= (totalItemCount - if (mAdapter?.hasStickyNotification() == true) 2 else 1)) {
                                loadMore()
                            }

                            if (isSlidingToLast && mTopUnreadBubble?.visibility == View.VISIBLE && layoutManager.findLastVisibleItemPosition() >= mTopUnreadBubble?.lastSeenPosition ?: 0) {
                                mTopUnreadBubble?.visibility = View.GONE
                                mChangeReadStateListener?.onOperateMarkThreadRead()
                            }

                            if (isSlidingToFirst && mBottomUnreadBubble?.visibility == View.VISIBLE && layoutManager.findFirstCompletelyVisibleItemPosition() <= (mBottomUnreadBubble?.unreadCount
                                            ?: 0) - 1) {
                                mBottomUnreadBubble?.visibility = View.GONE
                                mChangeReadStateListener?.onOperateMarkThreadRead()
                            }

                        }

                    } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        if (activity is AmeConversationActivity) {
                            activity.hideInput()
                        }
                    }
                }
            })
            private_conversation_list?.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onTouchEvent(p0: RecyclerView, p1: MotionEvent) {}

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    val holderView = rv.findChildViewUnder(e.x, e.y)
                    if (holderView == null) {
                        if (activity is AmeConversationActivity) {
                            activity.hideInput()
                        }
                    }
                    return false
                }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })

            mStickNoticeLayout?.setOnStickNoticeClickListener(object : ConversationStickNoticeLayout.OnStickNoticeClickListener {

                override fun onSecureClick() {
                }

                override fun onRelationClick() {
                    val r = mRecipient ?: return
                    BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND)
                            .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, r.address)
                            .navigation(activity)
                }

            })

            if (needReload) {
                mConversationViewModel?.reload()
            }
        }
    }

    private fun setBackground(expire: Int) {

        private_conversation_root?.post {
            if (expire > 0) {
                private_conversation_root?.setBackgroundResource(R.drawable.chats_auto_delete_bg)
                mStickNoticeLayout?.setBackgroundResource(R.drawable.chats_auto_delete_bg)
            } else {
                private_conversation_root?.setBackgroundColor(getColorCompat(R.color.common_background_color))
                mStickNoticeLayout?.setBackgroundColor(getColorCompat(R.color.common_background_color))
            }
        }
    }

    fun getExitView(indexId: Long): View? {
        val firstPos = mLayoutManager?.findFirstVisibleItemPosition() ?: 0
        val lastPos = mLayoutManager?.findLastVisibleItemPosition() ?: 0
        for (i in firstPos..lastPos) {
            val view = mLayoutManager?.findViewByPosition(i)
            if(view != null) {
                val holder = private_conversation_list?.getChildViewHolder(view)
                if (holder is AmeConversationAdapter.ViewHolder) {
                    val itemView = holder.getItem()
                    if (itemView is ConversationItem) {
                        val bodyView = itemView.getView(indexId)
                        if (bodyView != null) return bodyView
                    }
                }
            }
        }
        return null
    }

    fun loadMore() {
        private_conversation_list?.post {
            mConversationViewModel?.loadMore() {
                mAdapter?.showStickyNotificationLoading(it)
            }
        }
    }

    fun scrollToBottom(isSmooth: Boolean = true) {
        scrollToPosition(isSmooth, 0)
    }

    fun scrollToBottomLater(isSmooth: Boolean = true, delay: Long = 150) {
        private_conversation_list?.postDelayed({
            scrollToBottom(isSmooth)
        }, delay)
    }

    fun scrollToPosition(isSmooth: Boolean = true, position: Int = 0) {
        private_conversation_list?.post {
            try {
                if (isSmooth) {
                    private_conversation_list?.smoothScrollToPosition(position)
                } else {
                    private_conversation_list?.scrollToPosition(position)
                }
            }catch (ex: Exception) {
                ALog.e(TAG, "scrollToPosition error, pos: $position", ex)
            }
        }
    }

    private fun handleConversationMessageUpdate(data: AmeConversationViewModel.AmeConversationData, lastSeen: Long) {

        Observable.create<Int> {
            val lastSeenPosition = if (mTopUnreadBubble?.visibility == View.VISIBLE && mTopUnreadBubble?.lastSeen != -1L) {
                mAdapter?.findLastSeenPosition(mTopUnreadBubble?.lastSeen ?: -1L) ?: -1
            }else if (lastSeen != -1L) {
                mAdapter?.findLastSeenPosition(lastSeen) ?: -1
            }else {
                -1
            }
            it.onNext(lastSeenPosition)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({lastSeenPosition ->
                    var toScroll = !ConversationItemPopWindow.isConversationPopShowing() && isListAtBottom()
                    try {
                        when(data.type) {
                            AmeConversationViewModel.AmeConversationData.ACType.RESET -> {
                                ConversationItemPopWindow.dismissConversationPop()
                                mAdapter?.initRecordList(data.data, true)
                                toScroll = false
                                private_conversation_list?.post {
                                    val showItemCount = mLayoutManager?.childCount ?: 0
                                    if (data.unread > showItemCount) {
                                        ALog.i(TAG, "handleConversationMessageUpdate showItemCount: $showItemCount, unreadCount: ${data.unread}, lastSeen: $lastSeen, lastSeenPosition: $lastSeenPosition")
                                        mTopUnreadBubble?.visibility = View.VISIBLE
                                        mTopUnreadBubble?.isEnabled = true
                                        mTopUnreadBubble?.setUnreadMessageCount((data.unread - showItemCount).toInt())
                                        mTopUnreadBubble?.lastSeen = lastSeen
                                        mTopUnreadBubble?.lastSeenPosition = if (lastSeenPosition == -1) {data.unread.toInt()} else {lastSeenPosition}
                                        mTopUnreadBubble?.setOnClickListener { v ->
                                            v.visibility = View.GONE
                                            val lp = mTopUnreadBubble?.lastSeenPosition ?: -1
                                            if (lp > 0) {
                                                private_conversation_list?.smoothScrollToPosition(lp)
                                            }
                                            mTopUnreadBubble?.visibility = View.GONE
                                            mChangeReadStateListener?.onOperateMarkThreadRead()
                                        }

                                    }
                                }
                            }
                            AmeConversationViewModel.AmeConversationData.ACType.UPDATE -> {
                                ConversationItemPopWindow.dismissConversationPop()
                                mAdapter?.clearRecord(false)
                                mAdapter?.loadRecord(data.data)
                            }
                            AmeConversationViewModel.AmeConversationData.ACType.MORE -> {
                                ConversationItemPopWindow.dismissConversationPop()
                                val offset = mAdapter?.getRecordList()?.size ?: 0
                                mAdapter?.loadRecord(offset, data.data, true)
                            }
                            AmeConversationViewModel.AmeConversationData.ACType.NEW -> {
                                if (toScroll) {
                                    mAdapter?.loadRecord(0, data.data, true)

                                }else {
                                    mAdapter?.loadRecord(0, data.data, false)
                                    if (data.unread > 0) {
                                        mBottomUnreadBubble?.visibility = View.VISIBLE
                                        mBottomUnreadBubble?.setUnreadMessageCount(data.unread.toInt())
                                        mBottomUnreadBubble?.setOnClickListener { it ->
                                            it.visibility = View.GONE
                                            scrollToBottom()
                                            mChangeReadStateListener?.onOperateMarkThreadRead()

                                        }
                                    }
                                }

                            }
                        }

                        if (toScroll) {
                            mChangeReadStateListener?.onOperateMarkThreadRead()
                            scrollToBottom(false)
                        }
                    }
                    catch (ex: Exception) {
                        ALog.e(TAG, "handleConversationMessageUpdate error", ex)
                    }
                }, {
                    ALog.e(TAG, "handleConversationMessageUpdate error", it)
                })

    }

    private fun isListAtBottom(): Boolean {
        if (private_conversation_list?.childCount ?: 0 == 0) {
            return true
        }
        return (mLayoutManager?.findFirstVisibleItemPosition() ?: 0) == 0
    }

}
