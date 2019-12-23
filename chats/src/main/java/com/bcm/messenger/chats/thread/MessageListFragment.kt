package com.bcm.messenger.chats.thread

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.adapter.MessageListAdapter
import com.bcm.messenger.chats.bean.MessageListItem
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.event.GroupInfoCacheReadyEvent
import com.bcm.messenger.common.event.GroupListChangedEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.grouprepository.events.GroupInfoUpdateNotify
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.BcmRecyclerView
import com.bcm.messenger.common.ui.SystemNoticeDialog
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_fragment_message_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * thread list fragment
 * Created by zjl on 2018/2/28.
 */
@Route(routePath = ARouterConstants.Activity.CHAT_MESSAGE_PATH)
class MessageListFragment : BaseFragment(), RecipientModifiedListener {
    interface MessageListCallback {
        fun onRecyclerViewCreated(recyclerView: BcmRecyclerView)
        fun onClickInvite()
    }

    private val TAG = "MessageListFragment"
    private var masterSecret: MasterSecret? = null

    private lateinit var viewModel: ThreadListViewModel

    private var archive = false

    private lateinit var recipient: Recipient
    private var mAdapter: MessageListAdapter? = null

    private var firstVisiblePosition = 0
    private var lastVisiblePosition = 0

    private var webAlertData: AmePushProcess.SystemNotifyData.WebAlertData? = null
    var callback: MessageListCallback? = null

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)

        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }

        RxBus.unSubscribe(TAG)
        callback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        masterSecret = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET)
                ?: BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
        archive = arguments?.getBoolean(ARouterConstants.PARAM.PRIVATE_CHAT.IS_ARCHIVED_EXTRA, false)
                ?: false

        EventBus.getDefault().register(this)
        showShadeView(true)
    }

    override fun setActive(isActive: Boolean) {
        super.setActive(isActive)
        if (isActive) {
            activity?.let { a ->
                webAlertData?.let { wd ->
                    SystemNoticeDialog.show(a, wd)
                    webAlertData = null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ALog.d(TAG, "onResume")
        chats_app_notification_layout.checkNotice()
        checkUnhandledRequest()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_message_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.d(TAG, "onViewCreated")
        try {
            recipient = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true)
            recipient.addListener(this)
        } catch (ex: Exception) {
            ALog.e(TAG, "onActivityCreated fail, get self recipient fail", ex)
            try {
                val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_LOGIN_BASE).navigationWithCast<ILoginModule>()
                provider.logoutMenu()
            } catch (ex: Exception) {
                activity?.finish()
            }
        }

        activity?.let {
            initView(it)
            initializeListAdapter(it)
            initData()
        }
        callback?.onRecyclerViewCreated(chats_list)
    }

    private fun initView(context: Context) {
        chats_list.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(context)
        chats_list.layoutManager = layoutManager
        chats_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                    lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                }
            }
        })
        firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()

        chats_shade?.setOnContentClickListener {
            if (!it) {
                callback?.onClickInvite()
            }
        }
    }

    private fun initData() {
        viewModel = ViewModelProviders.of(this).get(ThreadListViewModel::class.java)
        viewModel.threadLiveData.observe(this, Observer { data ->
            ALog.i(TAG, "updateThread, size: ${data.data.size}")
            RxBus.post(HomeTabEvent(HomeTabEvent.TAB_CHAT, false, data.unread, false))
            mAdapter?.setThreadList(data.data)
            showShadeView(false)
        })

        RxBus.subscribe<FriendRequestEvent>(TAG) {
            checkUnhandledRequest(it.unreadCount)
        }

        AmePushProcess.checkSystemBannerNotice()
        PushUtil.loadSystemMessages()
    }

    private fun showShadeView(isLoading: Boolean) {
        if (isLoading) {
            chats_shade?.showLoading()
        } else {
            if (mAdapter?.getTrueDataList().isNullOrEmpty()) {
                val context = activity ?: return
                var title: CharSequence = context.getString(R.string.chats_no_conversation_main)
                title = StringAppearanceUtil.applyAppearance(title, 20.dp2Px(), context.getColorCompat(R.color.common_color_black))
                val contentBuilder = SpannableStringBuilder(StringAppearanceUtil.applyBold(title))
                contentBuilder.append("\n\n")
                val key = context.getString(R.string.chats_no_conversation_invite)
                val source = context.getString(R.string.chats_no_conversation_description, key)
                contentBuilder.append(StringAppearanceUtil.applyFilterAppearance(source, key, color = context.getColorCompat(R.color.common_app_primary_color)))
                chats_shade?.showContent(contentBuilder)
            }else {
                chats_shade?.hide()
            }
        }
    }

    /**
     * 显示下一个未读消息
     */
    private fun goToNextUnread() {
        ALog.i(TAG, "gotoNextUnread")
        try {
            val threadCount = mAdapter?.itemCount ?: 0
            val visibleLength = lastVisiblePosition - firstVisiblePosition
            if (threadCount > visibleLength) {
                for (i in firstVisiblePosition + 1 until threadCount) {
                    if (mAdapter?.getUnreadCount(i) ?: 0 > 0) {
                        val length = i - firstVisiblePosition
                        if (length > visibleLength) {
                            chats_list.smoothScrollToPosition(i)
                        } else {
                            chats_list.smoothScrollBy(0, 66.dp2Px() * length)
                        }
                        break
                    }
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "goToNextUnread error", ex)
        }
    }

    private fun initializeListAdapter(context: Context) {
        ALog.d(TAG, "initializeListAdapter")
        val masterSecret = this.masterSecret ?: return
        val adapter = MessageListAdapter(context, masterSecret, GlideApp.with(AppContextHolder.APP_CONTEXT), getSelectedLocale(context), object : MessageListAdapter.IThreadHolderDelete {
            override fun onViewClicked(adapter: MessageListAdapter, viewHolder: RecyclerView.ViewHolder) {
                if (viewHolder is MessageListAdapter.ThreadViewHolder) {
                    val item = viewHolder.getItem()
                    item.clearUnreadCount()
                    handleCreateConversation(item, item.threadId, item.recipient
                            ?: return, item.distributionType, item.lastSeen)
                }
            }
        })
        chats_list.adapter = adapter
        mAdapter = adapter
    }

    fun clearThreadUnreadState() {
        AmeDispatcher.io.dispatch {
            val threadRepo = Repository.getThreadRepo()
            val unreadList = ArrayList<ThreadRecord>()
            mAdapter?.getTrueDataList()?.forEach { threadRecord ->
                if (threadRecord.unreadCount > 0) {
                    unreadList.add(threadRecord)
                }
            }
            if (unreadList.isNotEmpty()) {
                threadRepo.setReadList(unreadList, true)
            }
        }
    }

    private fun handleCreateConversation(item: MessageListItem, threadId: Long, recipient: Recipient, distributionType: Int, lastSeen: Long) {
        var url = ARouterConstants.Activity.CHAT_CONVERSATION_PATH
        var groupId = 0L
        if (distributionType == ThreadRepo.DistributionTypes.NEW_GROUP) {
            groupId = recipient.groupId
            val group = GroupLogic.getGroupInfo(groupId)
            if (null == group) {
                GroupLogic.queryGroupInfo(groupId,null)
                return
            }
            url = ARouterConstants.Activity.CHAT_GROUP_CONVERSATION
        }
        BcmRouter.getInstance()
                .get(url)
                .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
                .putLong(ARouterConstants.PARAM.PARAM_THREAD, threadId)
                .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
                .putLong(ARouterConstants.PARAM.PRIVATE_CHAT.LAST_SEEN_EXTRA, lastSeen)
                .navigation(context)

    }

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            if (recipient.name.isBlank()) {
                showNicknameNotice()
            } else {
                hideNicknameNotice()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupInfoUpdateNotify) {
        val gid = e.groupInfo?.gid ?: return

        val groupInfo = GroupLogic.getGroupInfo(gid)
        val newGroupInfo = e.groupInfo
        if (null != groupInfo && null != newGroupInfo) {
            groupInfo.name = newGroupInfo.name
            groupInfo.iconUrl = newGroupInfo.iconUrl
            chats_list.adapter?.notifyDataSetChanged()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupListChangedEvent) {
        chats_list.adapter?.notifyDataSetChanged()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupInfoCacheReadyEvent) {
        chats_list.adapter?.notifyDataSetChanged()
    }

    private fun showNicknameNotice() {
        ALog.i(TAG, "Show nickname notice")
        chats_nickname_view?.visibility = View.VISIBLE
        chats_nickname_btn?.setOnClickListener {
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.EDIT_NAME)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, Recipient.fromSelf(AppContextHolder.APP_CONTEXT, false).address)
                    .navigation(context)
        }
    }

    private fun hideNicknameNotice() {
        ALog.i(TAG, "Hide nickname notice")
        chats_nickname_view?.visibility = View.GONE
        chats_nickname_btn?.setOnClickListener(null)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEvent(event: AmePushProcess.SystemNotifyData.WebAlertData) {
        if (isActive()) {
            activity?.let {
                SystemNoticeDialog.show(it, event)
            }
        } else {
            webAlertData = event
        }
    }

    private fun checkUnhandledRequest(unreadCount: Int = 0) {
        Observable.create<Pair<Int, Int>> {
            val requestDao = UserDatabase.getDatabase().friendRequestDao()
            var unread = unreadCount
            if (unread == 0) {
                unread = requestDao.queryUnreadCount()
            }
            it.onNext(Pair(requestDao.queryUnhandledCount(), unread))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    ALog.i(TAG, "checkUnHandledFriendRequest, unHandled: ${it.first}, unread: ${it.second}")
                    mAdapter?.updateFriendRequest(it.first, it.second)
                    RxBus.post(HomeTabEvent(HomeTabEvent.TAB_CONTACT, showFigure = it.second))
                }
    }
}