package com.bcm.messenger.chats.thread

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.NewChatActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.adapter.MessageListAdapterNew
import com.bcm.messenger.chats.bean.MessageListItem
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.GroupInfoCacheReadyEvent
import com.bcm.messenger.common.event.GroupListChangedEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.grouprepository.events.GroupInfoUpdateNotify
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.SystemNoticeDialog
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.PushUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
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

    private val TAG = "MessageListFragment"
    private var masterSecret: MasterSecret? = null

    private lateinit var viewModel: MessageListViewModel

    private var archive = false

    private lateinit var recipient: Recipient
    private var mAdapter: MessageListAdapterNew? = null

    private var firstVisiblePosition = 0
    private var lastVisiblePosition = 0

    private lateinit var titleView: MessageListTitleView

    private var webAlertData: AmePushProcess.SystemNotifyData.WebAlertData? = null

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)

        if(::recipient.isInitialized) {
            recipient.removeListener(this)
        }

        RxBus.unSubscribe(ARouterConstants.PARAM.PARAM_HOME_TAB_SELECT + TAG)

        titleView.unInit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        masterSecret = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET) ?: BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
        archive = arguments?.getBoolean(ARouterConstants.PARAM.PRIVATE_CHAT.IS_ARCHIVED_EXTRA, false) ?: false

        EventBus.getDefault().register(this)
    }

    override fun setActive(isActive: Boolean) {
        super.setActive(isActive)
        if (isActive) {
            activity?.let {a ->
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
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_message_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.d(TAG, "onViewCreated")
        try {
            recipient = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true)
            recipient.addListener(this)
        }catch (ex: Exception) {
            ALog.e(TAG, "onActivityCreated fail, get self recipient fail", ex)
            try {
                val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_LOGIN_BASE).navigationWithCast<ILoginModule>()
                provider.logoutMenu()
            }catch (ex: Exception) {
                activity?.finish()
            }
        }

        activity?.let {
            initView(it)
            initializeListAdapter(it)
            initData()
        }

    }

    private fun initView(context: Context) {
        titleView = chats_toolbar.getCenterView().second as MessageListTitleView
        titleView.init()

        chats_toolbar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickRight() {
                startActivity(Intent(activity, NewChatActivity::class.java))
            }
        })

        chats_toolbar.setMultiClickListener(
                MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
                    override fun onMultiClick(view: View?, count: Int) {
                        clearThreadUnreadState()
                        chats_title_double_click_tips.visibility = View.GONE
                    }
                })
        )

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
    }

    private fun initData() {
        viewModel = ViewModelProviders.of(this).get(MessageListViewModel::class.java)
        viewModel.threadLiveData.observe(this, Observer {data ->
            ALog.i(TAG, "updateThread, size: ${data.data.size}, unread: ${data.unread}")
            RxBus.post(HomeTabEvent(HomeTabEvent.TAB_CHAT, false, data.unread, false))
            mAdapter?.setThreadList(data.data)

        })

        RxBus.subscribe<HomeTabEvent>(ARouterConstants.PARAM.PARAM_HOME_TAB_SELECT + TAG) { event ->
            ALog.i(TAG, "receive RxBus event pos: ${event.position}")
            if (event.position == 0 && event.isDoubleClick) {
                goToNextUnread()
            }
        }

        AmePushProcess.checkSystemBannerNotice()
        PushUtil.loadSystemMessages()
    }


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
        }catch (ex: Exception) {
            ALog.e(TAG, "goToNextUnread error", ex)
        }
    }

    private fun initializeListAdapter(context: Context) {
        ALog.d(TAG, "initializeListAdapter")
        val masterSecret = this.masterSecret ?: return
        val adapter = MessageListAdapterNew(context, masterSecret, GlideApp.with(AppContextHolder.APP_CONTEXT), getSelectedLocale(context), object : MessageListAdapterNew.IThreadHolderDelete {
            override fun onViewClicked(adapter: MessageListAdapterNew, viewHolder: RecyclerView.ViewHolder) {
                if (viewHolder is MessageListAdapterNew.ThreadViewHolder) {
                    val item = viewHolder.getItem()
                    item.clearUnreadCount()
                    handleCreateConversation(item, item.threadId, item.recipient ?: return, item.distributionType, item.lastSeen)
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
}