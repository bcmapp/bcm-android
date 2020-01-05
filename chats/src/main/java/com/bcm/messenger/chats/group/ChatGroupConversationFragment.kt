package com.bcm.messenger.chats.group

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.ReplyMessageEvent
import com.bcm.messenger.chats.components.ChatPinView
import com.bcm.messenger.chats.components.ConversationItemPopWindow
import com.bcm.messenger.chats.components.UnreadMessageBubbleView
import com.bcm.messenger.chats.group.live.LiveFlowController
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.chats.group.setting.ChatGroupContentClear
import com.bcm.messenger.chats.group.viewholder.*
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.grouprepository.events.MessageEvent
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonConversationAdapter
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_group_conversation_fragment.*
import kotlinx.android.synthetic.main.chats_tt_conversation_activity.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Fragment for group chat
 */
class ChatGroupConversationFragment : BaseFragment() {

    private val TAG = "ChatTTConversationFragment"
    private val GET_COUNT = 100

    private lateinit var recipient: Recipient
    private var groupId = -1L
    private var threadId: Long = -1L

    private var mSelectedBatch: MutableSet<AmeGroupMessageDetail>? = null

    private var groupModel: GroupViewModel? = null
    private lateinit var glideRequests: GlideRequests
    private lateinit var layoutManager: LinearLayoutManager

    private var bottomUnreadBubble: UnreadMessageBubbleView? = null
    private var topUnreadBubble: UnreadMessageBubbleView? = null
    private var liveFlowController: LiveFlowController? = null

    private var frontIndexAtomic: AtomicLong = AtomicLong(0)
    private var lastIndexAtomic: AtomicLong = AtomicLong(0)

    private var hasMore = false
    private var hasFront = false

    private var listAdapter: CommonConversationAdapter<AmeGroupMessageDetail>? = null

    private var mStickyTopData: AmeGroupMessageDetail? = null //for pin message，sending for loadingMore

    /**
     * 是否在列表底部
     */
    private val isListAtBottom: Boolean
        get() {
            if (group_conversation_list?.childCount == 0) {
                return true
            }
            return layoutManager.findFirstVisibleItemPosition() == 0
        }

    override fun onActivityCreated(bundle: Bundle?) {
        super.onActivityCreated(bundle)
        ALog.i(TAG, "onActivityCreated")
        initResources()
        initListAdapter()

        RxBus.subscribe<ChatGroupContentClear>(ChatGroupContentClear.GROUP_CONTENT_CLEAR) {
            if (it.groupId == groupId) {
                listAdapter?.removeAllNotRefresh()
                listAdapter?.loadData(createStickySecureMessage())
                topUnreadBubble?.visibility = View.GONE
                bottomUnreadBubble?.visibility = View.GONE
                ToastUtil.show(AppContextHolder.APP_CONTEXT, AppUtil.getString(R.string.chats_clean_succeed))
            }
        }
    }

    override fun onNewIntent() {
        super.onNewIntent()
        initResources()
        initListAdapter()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        RxBus.unSubscribe(ChatGroupContentClear.GROUP_CONTENT_CLEAR)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View? {
        EventBus.getDefault().register(this)
        return inflater.inflate(R.layout.chats_group_conversation_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.i(TAG, "onViewCreated")
        topUnreadBubble = view.findViewById(R.id.message_unread_up_bubble)
        topUnreadBubble?.setOrientationIcon(R.drawable.chats_10_up)
        bottomUnreadBubble = view.findViewById(R.id.message_unread_down_bubble)
        bottomUnreadBubble?.setOrientationIcon(R.drawable.chats_10_down)
        layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, true)
        group_conversation_list.setHasFixedSize(false)
        group_conversation_list.layoutManager = layoutManager
        group_conversation_list.itemAnimator = null
        group_conversation_list.overScrollMode = View.OVER_SCROLL_NEVER
        group_conversation_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            private var isSlidingToLast = false
            private var isSlidingToFirst = false

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                //dx>0:slide to right,dx<0:slide to left
                //dy>0:slide to down,dy<0:slide to up
                isSlidingToLast = dy < 0
                isSlidingToFirst = dy > 0
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount

                    if (isSlidingToLast && hasMore && lastVisibleItem >= totalItemCount - 2) {
                        loadMore()
                    }

                    if (isSlidingToLast && topUnreadBubble?.visibility == View.VISIBLE && lastVisibleItem >= topUnreadBubble?.lastSeenPosition ?: 0) {
                        topUnreadBubble?.visibility = View.GONE
                    }

                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                    if (isSlidingToFirst && bottomUnreadBubble?.visibility == View.VISIBLE && firstVisibleItem <= (bottomUnreadBubble?.unreadCount
                                    ?: 0) - 1) {
                        bottomUnreadBubble?.visibility = View.GONE
                    }

                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val c = activity
                    if (c is ChatGroupConversationActivity) {
                        c.hideInput()
                    }
                }
            }
        })
        group_conversation_list.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            }

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                val v = rv.findChildViewUnder(e.x, e.y)
                if (v == null) {
                    activity?.let {
                        if (it is ChatGroupConversationActivity) {
                            it.hideInput()
                        }
                    }
                }
                return false
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

    }

    private fun initResources() {
        val activity = activity ?: return
        threadId = activity.intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, 0L)
        groupId = activity.intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1L)
        groupModel = GroupLogic.getModel(groupId)
        val groupInfo = groupModel?.getGroupInfo() ?: return
        recipient = Recipient.recipientFromNewGroup(getAccountContext(), groupInfo)
        lastIndexAtomic.set(0L)
        frontIndexAtomic.set(0L)
        hasFront = false
        hasMore = false
        mSelectedBatch = null
    }

    private fun initPinLayout() {

        val groupInfo = groupModel?.getGroupInfo() ?: return
        ALog.i(TAG, "initPinLayout pinMid: ${groupInfo.pinMid}, hasPin: ${groupInfo.hasPin}")
        if (groupInfo.pinMid > 0) {
            groupModel?.getMessageDetailByMid(groupInfo.pinMid) { result ->
                if (result != null && groupInfo.hasPin) {
                    showPinLayout(result)
                }
            }
        }
    }

    private fun initListAdapter() {

        if (this.threadId > 0L) {
            glideRequests = GlideApp.with(AppContextHolder.APP_CONTEXT)
            val adapter = CommonConversationAdapter<AmeGroupMessageDetail>(context
                    ?: return, object : CommonConversationAdapter.IConversationDelegate<AmeGroupMessageDetail> {

                private val calendar = Calendar.getInstance()
                override fun getViewHolderType(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, position: Int, data: AmeGroupMessageDetail): Int {
                    return when {
                        data.message.type == AmeGroupMessage.MESSAGE_SECURE_NOTICE -> R.layout.chats_stick_notification_item
                        data.message.type == AmeGroupMessage.SYSTEM_INFO -> R.layout.chats_tip_message_item
                        data.message.type == AmeGroupMessage.PIN -> R.layout.chats_conversation_item_pin
                        data.message.type == AmeGroupMessage.LIVE_MESSAGE -> R.layout.chats_conversation_item_live
                        data.isSendByMe -> R.layout.chats_group_conversation_sent_item
                        else -> R.layout.chats_group_conversation_received_item
                    }
                }

                override fun createViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val holder = when (viewType) {
                        R.layout.chats_stick_notification_item -> {
                            StickNotificationViewHolder(inflater.inflate(viewType, parent, false))
                        }
                        R.layout.chats_tip_message_item -> {
                            SystemTipsViewHolder(inflater.inflate(viewType, parent, false))
                        }
                        R.layout.chats_conversation_item_pin -> {
                            ChatPinViewHolder(inflater.inflate(viewType, parent, false))
                        }
                        R.layout.chats_conversation_item_live -> {
                            ChatLiveViewHolder(inflater.inflate(viewType, parent, false))
                        }
                        R.layout.chats_group_conversation_sent_item -> {
                            OutgoingChatViewHolder(inflater.inflate(viewType, parent, false))
                        }
                        else -> {
                            IncomeChatViewHolder(inflater.inflate(viewType, parent, false))
                        }
                    }
                    holder.itemView.setOnClickListener {
                        activity?.let {
                            if (it is ChatGroupConversationActivity) {
                                it.hideInput()
                            }
                        }
                    }
                    return holder
                }

                override fun bindViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, viewHolder: RecyclerView.ViewHolder, position: Int, data: AmeGroupMessageDetail) {
                    when (viewHolder) {
                        is ChatViewHolder -> {
                            viewHolder.bind(data, glideRequests, mSelectedBatch)
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
                        is StickNotificationViewHolder -> viewHolder.bindData(data)
                        is SystemTipsViewHolder -> viewHolder.bindData(data)
                        is ChatPinViewHolder -> viewHolder.bindData(data)
                        is ChatLiveViewHolder -> viewHolder.bindData(data)
                    }
                }

                override fun unbindViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, viewHolder: RecyclerView.ViewHolder) {
                    when (viewHolder) {
                        is ChatViewHolder -> viewHolder.unBindData()
                        is SystemTipsViewHolder -> viewHolder.unBindData()
                        is ChatPinViewHolder -> viewHolder.unBindData()
                        is ChatLiveViewHolder -> viewHolder.unBindData()
                    }
                }

                override fun getItemId(position: Int, data: AmeGroupMessageDetail): Long {
                    return data.indexId
                }

                override fun getLastSeenHeaderId(position: Int, data: AmeGroupMessageDetail): Long {
                    calendar.time = Date(data.sendTime)
                    return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR)).toLong()
                }

                override fun getReceiveTime(position: Int, data: AmeGroupMessageDetail): Long {
                    //ALog.i(TAG, "getReceiveTime position: $position, data: ${data.indexId}, time: ${data.sendTime}")
                    return data.sendTime
                }

            })

            adapter.setHasStableIds(true)
            val lastSeenDecoration = CommonConversationAdapter.LastSeenHeader(adapter, 3 * 60 * 1000)
            group_conversation_list.addItemDecoration(lastSeenDecoration)
            group_conversation_list.adapter = adapter
            listAdapter = adapter

            fetchMessageByFirstLoad()
        } else {
            listAdapter?.removeAll()
        }
    }

    fun setFlowWindowController(controller: LiveFlowController?) {
        liveFlowController = controller
    }

    fun getExitView(indexId: Long): View? {
        val layoutManager = group_conversation_list?.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val lastPos = layoutManager.findLastVisibleItemPosition()
            for (i in firstPos..lastPos) {
                val view = layoutManager.findViewByPosition(i)
                if (view != null) {
                    val holder = group_conversation_list?.getChildViewHolder(view)
                    if (holder is ChatViewHolder) {
                        val bodyView = holder.getView(indexId)
                        if (bodyView != null) return bodyView
                    }
                }
            }
        }
        return null
    }

    fun setMultiSelectedItem(batchSelected: MutableSet<AmeGroupMessageDetail>?) {
        mSelectedBatch = batchSelected
        listAdapter?.notifyDataSetChanged()
    }

    fun scrollToBottom(isSmooth: Boolean = true) {
        scrollToPosition(isSmooth, 0)
    }

    fun scrollToPosition(isSmooth: Boolean = true, position: Int) {
        group_conversation_list?.let {
            it.post {
                try {
                    if (isSmooth) {
                        it.smoothScrollToPosition(position)
                    } else {
                        it.scrollToPosition(position)
                    }
                } catch (ex: Exception) {
                    ALog.e(TAG, "scrollToPosition error, pos: $position", ex)
                }
            }
        }
    }

    fun notifyDataSetChanged() {
        listAdapter?.notifyDataSetChanged()
    }

    private fun fetchMessageByFirstLoad() {
        ALog.i(TAG, "fetchMessageByFirstLoad")
        groupModel?.fetchMessage(-1, GET_COUNT, true) { result, unread ->
            if (result.isNotEmpty()) {
                ALog.i(TAG, "fetchMessageByFirstLoad result: ${result.size}")
                frontIndexAtomic.set(result.first().indexId)
                lastIndexAtomic.set(result.last().indexId)
                hasMore = result.size == GET_COUNT
                listAdapter?.loadData(result + createStickySecureMessage(), false)
                group_conversation_list?.post {
                    handleTopBubbleState(true, unread.toInt())
                    initPinLayout()
                }
            } else {
                listAdapter?.loadData(createStickySecureMessage())
            }
        }
    }

    private fun fetchBeforeMessage(count: Int, scrollPosition: Int = -1) {
        ALog.i(TAG, "fetchFrontMessage")
        groupModel?.fetchMessage(lastIndexAtomic.get(), count, false) { result, unread ->
            if (result.isNotEmpty()) {
                lastIndexAtomic.set(result.last().indexId)
                listAdapter?.loadData(getLastDataPosition(), result)
                topUnreadBubble?.visibility = View.GONE
                scrollToPosition(true, scrollPosition)
            }
        }
    }

    private fun loadMore() {
        if (mStickyTopData?.isSending == true) { //已经在loadingMore中
            return
        }
        mStickyTopData?.sendState = AmeGroupMessageDetail.SendState.SENDING
        listAdapter?.notifyDataSetChanged()
        groupModel?.fetchMessage(lastIndexAtomic.get(), GET_COUNT, false) { result, unread ->
            ALog.i(TAG, "loadMore lastIndex: ${lastIndexAtomic.get()}, result: ${result.size}")
            mStickyTopData?.sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
            hasMore = result.size == GET_COUNT
            if (result.isNotEmpty()) {
                lastIndexAtomic.set(result.last().indexId)
                listAdapter?.loadData(getLastDataPosition(), result)
            } else {
                listAdapter?.notifyDataSetChanged()
            }
        }
    }

    private fun fetchAndScrollTo(mid: Long, label: Int) {

        fun scroll(position: Int, label: Int) {
            if (position >= 0) {
                val record = listAdapter?.getData(position)
                record?.isLabel = label
                scrollToPosition(true, position)
                group_conversation_list?.post {
                    if (position >= layoutManager.findFirstVisibleItemPosition() && position <= layoutManager.findLastVisibleItemPosition()) {
                        listAdapter?.notifyItemChanged(position)
                    }
                }
            } else {
                ToastUtil.show(AppContextHolder.APP_CONTEXT, AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_reply_can_not_locate))
            }
        }

        fun fetchMessage(fromMid: Long, label: Int) {
            groupModel?.fetchMessage(fromMid, mid) { result ->
                if (result.isNotEmpty()) {
                    lastIndexAtomic.set(result.last().indexId)
                    val scrollPosition = (listAdapter?.getDataCount() ?: 0) + result.size - 1
                    listAdapter?.loadData(getLastDataPosition(), result)
                    scroll(scrollPosition, label)
                }
            }
        }

        if (mid < 0) {
            return
        }
        groupModel?.getMessageDetailByMid(mid) { targetMessage ->
            if (targetMessage != null) {
                Observable.create<Int> {
                    var position = -1
                    var record: AmeGroupMessageDetail
                    for (index in 0 until (listAdapter?.getDataCount() ?: 0)) {
                        record = listAdapter?.getData(index) ?: continue
                        if (record.serverIndex == mid) {
                            position = index
                            break
                        }
                    }
                    it.onNext(position)
                    it.onComplete()
                }.subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ positon ->
                            if (positon >= 0) {
                                scroll(positon, label)
                            } else {
                                val lastPosition = (listAdapter?.getDataCount() ?: 0) - 2
                                val lastRecord = if (lastPosition < 0) {
                                    null
                                } else {
                                    listAdapter?.getData(lastPosition)
                                }
                                fetchMessage(lastRecord?.serverIndex ?: 0, label)
                            }
                        }, {
                            ALog.e(TAG, "fetchAndScrollTo error", it)
                        })
            }
        }
    }

    private fun handleFetchedMessage(event: MessageEvent) {
        ALog.i(TAG, "fetchOfflineMessage")
        val fetchList = event.targetList ?: return
        if (fetchList.isEmpty()) {
            return
        }
        val fetchLastIndex = event.indexId
        if (fetchLastIndex < lastIndexAtomic.get()) {
            hasMore = true
            return
        }
        handleMissedMessage(event)
    }

    private fun handleMissedMessage(event: MessageEvent) {
        ALog.i(TAG, "handleReceiveFaultMessage indexId: ${event.indexId}")
        val adapter = listAdapter
        val fetchList = event.targetList
        if (fetchList != null && adapter != null) {
            Observable.create<Int> {
                var result = 0
                for (i in 0 until adapter.getDataCount()) {
                    if (adapter.getData(i).indexId < event.indexId) {
                        result = i - 1
                        break
                    }
                }
                if (result < 0) {
                    result = 0
                }
                it.onNext(result)
                it.onComplete()
            }.subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        val fixed = (it == 0) && (!isListAtBottom || ConversationItemPopWindow.isConversationPopShowing())
                        adapter.loadData(it, fetchList, !fixed)
                        if (it == 0) {
                            group_conversation_list?.post {
                                if (!fixed) {
                                    scrollToBottom(true)
                                } else {
                                    handleBottomBubbleState(fetchList.size)
                                }
                            }
                        }
                        group_conversation_list?.post {
                            fetchList.forEach {
                                dillPinLayout(it)
                            }
                        }
                    }, {
                        ALog.e(TAG, "handleMissedMessage error", it)
                    })
        }
    }

    private fun handleNewMessage(event: MessageEvent) {
        ALog.logForSecret(TAG, "handleNewMessage list: ${event.targetList}")
        val list = event.targetList
        val fixed = !isListAtBottom || ConversationItemPopWindow.isConversationPopShowing()
        if (!list.isNullOrEmpty()) {
            frontIndexAtomic.set(list.first().indexId)
            listAdapter?.loadData(0, list, !fixed)
            liveFlowController?.addChatFlowMessage(list.first())

            group_conversation_list?.post {
                if (!fixed) {
                    scrollToBottom(true)
                } else {
                    handleBottomBubbleState(list.size)
                }
                list.forEach {
                    dillPinLayout(it)
                }
            }
        }
    }

    private fun handleSentMessage(event: MessageEvent) {
        ALog.logForSecret(TAG, "handleSentMessage list: ${event.targetList}")
        val list = event.targetList
        if (!list.isNullOrEmpty()) {
            frontIndexAtomic.set(list.first().indexId)
            listAdapter?.loadData(0, list)
            group_conversation_list?.post {
                bottomUnreadBubble?.visibility = View.GONE
                scrollToBottom(true)
            }
        }
    }


    private fun handleUpdateMessage(event: MessageEvent) {
        ALog.logForSecret(TAG, "handleUpdateMessage list: ${event.targetList}")
        val list = event.targetList
        val adapter = listAdapter
        if (list != null && adapter != null) {
            Observable.create<Pair<Int, AmeGroupMessageDetail>> {
                var finish = 0
                var m: AmeGroupMessageDetail
                for (i in 0 until adapter.getDataCount()) {
                    if (finish >= list.size) {
                        break
                    }
                    m = adapter.getData(i)
                    for (u in list) {
                        if (m.indexId == u.indexId) {
                            finish++
                            it.onNext(Pair(i, u))
                            break
                        }
                    }
                }
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        adapter.replaceData(it.first, it.second)
                        group_conversation_list?.post {
                            dillPinLayout(it.second)
                        }
                    }, {
                        ALog.e(TAG, "handleUpdateMessage error", it)
                    })
        }
    }

    private fun handleDeleteOneMessage(event: MessageEvent) {
        ALog.i(TAG, "handleDeleteOneMessage indexId: ${event.indexId}")
        val adapter = listAdapter ?: return
        var i = 0
        val size = adapter.getDataCount()
        while (i < size) {
            if (adapter.getData(i).indexId == event.indexId) {
                group_conversation_list?.post {
                    adapter.removeData(i)
                }
                break
            }
            i++
        }
    }

    private fun handleDeleteMessages(event: MessageEvent) {
        ALog.i(TAG, "handleDeleteMessages indexId: ${event.indexIdList}")
        Observable.create<List<AmeGroupMessageDetail>> {
            listAdapter?.let { adapter ->

                val indexList = event.indexIdList
                val willDeleteList = mutableListOf<AmeGroupMessageDetail>()
                var record: AmeGroupMessageDetail
                for (index in 0 until adapter.getDataCount()) {
                    record = adapter.getData(index)
                    if (indexList.contains(record.indexId)) {
                        willDeleteList.add(record)
                    }
                }
                it.onNext(willDeleteList)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    listAdapter?.let { adapter ->
                        it.forEach { any ->
                            adapter.removeData(any)
                        }
                    }
                }, {
                    ALog.e(TAG, "handleDeleteMessages error", it)
                })
    }

    private fun dillPinLayout(record: AmeGroupMessageDetail) {
        if (record.message.type == AmeGroupMessage.PIN) {
            val content = record.message.content as AmeGroupMessage.PinContent
            if (content.mid == -1L) {
                hidePinLayout()
            } else {
                groupModel?.updateGroupPinMid(content.mid) {
                    if (it) {
                        showPinLayout(content.mid)
                    }
                }
            }
        }
    }

    private fun showPinLayout(targetScrollMessage: AmeGroupMessageDetail) {
        group_pin_layout?.visibility = View.VISIBLE
        group_pin_layout?.post {
            group_pin_layout?.setGroupMessage(targetScrollMessage, glideRequests, object : ChatPinView.OnChatPinActionListener {

                override fun onContentClick() {
                    fetchAndScrollTo(targetScrollMessage.serverIndex, AmeGroupMessageDetail.LABEL_PIN)
                }

                override fun onClose() {
                    if (groupModel?.myRole() == AmeGroupMemberInfo.OWNER) {
                        AmePopup.center.newBuilder()
                                .withContent(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_group_pin_dialog_content))
                                .withCancelable(true)
                                .withCancelTitle(getString(R.string.common_cancel))
                                .withOkTitle(AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_unpin))
                                .withOkListener {
                                    GroupMessageLogic.messageSender.sendPinMessage(targetScrollMessage.gid, -1, object : MessageSender.SenderCallback {
                                        override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                                        }
                                    })
                                }
                                .show(activity)
                    } else {
                        groupModel?.updateGroupPinMid(-1) {
                            if (it) {
                                group_pin_layout.visibility = View.GONE
                            }
                        }
                    }
                }
            })
        }
    }

    @SuppressLint("CheckResult")
    private fun showPinLayout(mid: Long) {
        groupModel?.getMessageDetailByMid(mid) { result ->
            if (result != null) {
                showPinLayout(result)
            }
        }
    }

    private fun hidePinLayout() {
        groupModel?.updateGroupPinMid(-1) {
            if (it) {
                group_pin_layout.visibility = View.GONE
            }
        }
    }

    private fun handleTopBubbleState(isFirstLoad: Boolean, unread: Int) {
        if (isFirstLoad) {
            val visibleCount = group_conversation_list?.layoutManager?.childCount ?: 0
            ALog.i(TAG, "handleTopBubbleState child $visibleCount, isFirstLoad: $isFirstLoad, unread: $unread")
            when {
                unread > GET_COUNT -> {
                    topUnreadBubble?.visibility = View.VISIBLE
                    topUnreadBubble?.setUnreadMessageCount(unread - visibleCount)
                    topUnreadBubble?.lastSeenPosition = unread - visibleCount
                    topUnreadBubble?.setOnClickListener { _ ->
                        val remain = unread - GET_COUNT
                        val num = remain / GET_COUNT + 1
                        fetchBeforeMessage(num * GET_COUNT, topUnreadBubble?.lastSeenPosition ?: 0)
                    }
                }
                unread > visibleCount -> {
                    topUnreadBubble?.visibility = View.VISIBLE
                    topUnreadBubble?.setUnreadMessageCount(unread - visibleCount)
                    topUnreadBubble?.lastSeenPosition = unread - visibleCount
                    topUnreadBubble?.setOnClickListener { _ ->
                        scrollToPosition(true, topUnreadBubble?.lastSeenPosition ?: 0)
                        topUnreadBubble?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun handleBottomBubbleState(unread: Int) {
        ALog.i(TAG, "handleBottomBubbleState unread: $unread")
        if (unread > 0) {
            bottomUnreadBubble?.visibility = View.VISIBLE
            bottomUnreadBubble?.setUnreadMessageCount((bottomUnreadBubble?.unreadCount
                    ?: 0) + unread)
            bottomUnreadBubble?.setOnClickListener { v ->
                v.visibility = View.GONE
                scrollToBottom()
            }
        }
    }

    private fun createStickySecureMessage(): AmeGroupMessageDetail {
        val result = AmeGroupMessageDetail().apply {
            indexId = -1
            gid = groupId
            sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
            message = AmeGroupMessage(AmeGroupMessage.MESSAGE_SECURE_NOTICE, AmeGroupMessage.SecureContent())
        }
        mStickyTopData = result
        return result
    }

    private fun getLastDataPosition(): Int {
        var index = (listAdapter?.getDataCount() ?: 0) - 1
        if (index < 0) {
            index = 0
        }
        return index
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: MessageEvent) {

        if (event.gid != groupId) {
            ALog.i(TAG, "receive message gid: ${event.gid}, current gid: $groupId")
            return
        }
        ALog.i(TAG, "receive message indexId: ${event.indexId}, frontIndex: ${frontIndexAtomic.get()}")
        when (event.type) {
            MessageEvent.EventType.RECEIVE_MESSAGE_INSERT,
            MessageEvent.EventType.RECEIVE_MESSAGE_UPDATE -> {
                if (event.indexId > 0L) {
                    if (event.indexId < frontIndexAtomic.get()) {
                        handleMissedMessage(event)
                    } else {
                        handleNewMessage(event)
                    }
                }
            }
            MessageEvent.EventType.SEND_MESSAGE_INSERT -> handleSentMessage(event)
            MessageEvent.EventType.SEND_MESSAGE_UPDATE,
            MessageEvent.EventType.ATTACHMENT_DOWNLOAD_SUCCESS,
            MessageEvent.EventType.THUMBNAIL_DOWNLOAD_SUCCESS -> handleUpdateMessage(event)
            MessageEvent.EventType.DELETE_ONE_MESSAGE -> handleDeleteOneMessage(event)
            MessageEvent.EventType.FETCH_MESSAGE_SUCCESS -> handleFetchedMessage(event)
            MessageEvent.EventType.DELETE_MESSAGES -> handleDeleteMessages(event)
            else -> {
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupViewModel.MemberListChangedEvent) {
        listAdapter?.notifyDataSetChanged()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(replyEvent: ReplyMessageEvent) {
        if (replyEvent.messageDetail.gid == groupId) {
            ALog.d(TAG, "receive ReplyMessageEvent")
            val c = activity
            if (c is ChatGroupConversationActivity) {
                if (replyEvent.action == ReplyMessageEvent.ACTION_REPLY) {
                    c.bottom_panel.setReply(replyEvent.messageDetail) {
                        ALog.d(TAG, "onReplyLocate mid: ${replyEvent.messageDetail.serverIndex}")
                        fetchAndScrollTo(replyEvent.messageDetail.serverIndex, AmeGroupMessageDetail.LABEL_REPLY)
                    }
                } else if (replyEvent.action == ReplyMessageEvent.ACTION_LOCATE) {
                    val replyContent = replyEvent.messageDetail.message.content as? AmeGroupMessage.ReplyContent
                    if (replyContent != null) {
                        fetchAndScrollTo(replyContent.mid, AmeGroupMessageDetail.LABEL_REPLY)
                    }
                }
            }
        }
    }
}
