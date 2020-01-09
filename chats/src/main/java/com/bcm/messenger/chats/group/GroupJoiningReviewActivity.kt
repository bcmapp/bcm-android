package com.bcm.messenger.chats.group

import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.GroupJoiningReviewItem
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.corebean.BcmGroupJoinRequest
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.core.corebean.BcmReviewGroupJoinRequest
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.chats_activity_joining_check.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Created by wjh on 2019/6/3
 */
@Route(routePath = ARouterConstants.Activity.GROUP_JOIN_CHECK)
class GroupJoiningReviewActivity : AccountSwipeBaseActivity() {

    private val TAG = "GroupJoiningReviewActivity"
    private var mGroupModel: GroupViewModel? = null
    private var mMessageId: Long = 0
    private var mJoiningRequestList: List<BcmGroupJoinRequest> = listOf()
    private var mAdapter: JoiningRequestAdapter? = null
    private var mUnHandleCount: Int = 0 
    private var mUnReadCount: Int = 0 

    override fun onDestroy() {
        super.onDestroy()
        if (mMessageId == 0L) {
            mGroupModel?.readAllJoinRequest()
        } else {
            mGroupModel?.readJoinRequest(mJoiningRequestList)
        }

        EventBus.getDefault().unregister(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_joining_check)


        title_bar.hideRightViews()
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                approveAll()
            }
        })

        mMessageId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_INDEX_ID, 0)
        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1L)
        mGroupModel = GroupLogic.get(accountContext).getModel(groupId)
        EventBus.getDefault().register(this)

        initJoiningRequestList()
    }

    private fun approveAll() {
        AmeAppLifecycle.showLoading()
        val groupModel = mGroupModel ?: return
        groupModel.reviewJoinRequests(mJoiningRequestList.map {
            BcmReviewGroupJoinRequest(it.uid, it.reqId, true)
        }) { succeed, error ->
            ALog.d(TAG, "approveAll result: $succeed error: $error")
            AmeAppLifecycle.hideLoading()
            if (succeed) {
                AmeAppLifecycle.succeed(getString(R.string.chats_group_join_approve_success), true)
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_group_join_approve_fail), true)
            }
        }
    }

    private fun initJoiningRequestList() {

        if (mMessageId == 0L) {
            mJoiningRequestList = mGroupModel?.getJoinRequestList() ?: listOf()
            // Check all applications if message id is 0 or uid is empty
            mUnReadCount = 0
            mUnHandleCount = 0

            if (mJoiningRequestList.isNotEmpty()) {
                title_bar?.setRightText(getString(R.string.chats_group_join_approve_all_action))
            } else {
                title_bar?.hideRightViews()

            }
        } else {
            mJoiningRequestList = mGroupModel?.getJoinRequest(mMessageId) ?: listOf()
            // Show all applications entrance if check specific application
            mUnHandleCount = 0
            mUnReadCount = 0
            mJoiningRequestList.forEach {
                if (it.status == BcmGroupJoinStatus.WAIT_OWNER_REVIEW) {
                    // Unhandled count minus current count
                    mUnHandleCount++
                }
                if (!it.read) {
                    // Rest unread count
                    mUnReadCount++
                }
            }

            ALog.d(TAG, "initJoiningRequestList unHandleCount: $mUnHandleCount unReadCount: $mUnReadCount")
            title_bar?.hideRightViews()
        }

        if (mAdapter == null) {
            mAdapter = JoiningRequestAdapter()
            join_list_rv.layoutManager = LinearLayoutManager(this)
            join_list_rv.adapter = mAdapter
        } else {
            mAdapter?.notifyDataSetChanged()
        }

        if (mJoiningRequestList.isEmpty()) {
            join_list_shade.showContent(getString(R.string.chats_group_joining_check_list_empty))
        } else {
            join_list_shade.hide()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupViewModel.JoinRequestListChanged) {
        ALog.d(TAG, "receive JoinRequestListChanged event: ${event.groupId}")
        if (event.groupId == mGroupModel?.groupId()) {
            initJoiningRequestList()
        }
    }

    inner class JoiningRequestAdapter : RecyclerView.Adapter<JoiningRequestViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JoiningRequestViewHolder {
            return JoiningRequestViewHolder(GroupJoiningReviewItem(this@GroupJoiningReviewActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

        override fun getItemCount(): Int {
            return mJoiningRequestList.size
        }

        override fun onBindViewHolder(holder: JoiningRequestViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun onViewRecycled(holder: JoiningRequestViewHolder) {
            holder.unbind()
        }
    }

    inner class JoiningRequestViewHolder(private val joiningReviewItem: GroupJoiningReviewItem) : RecyclerView.ViewHolder(joiningReviewItem) {

        fun bind(position: Int) {
            var unHandleCount = 0
            var unReadCount = 0
            if (position == (mAdapter?.itemCount
                            ?: 0) - 1 && mMessageId != 0L) { 
                val newUnHandleCount = (mGroupModel?.getJoinRequestCount() ?: 0)
                if (newUnHandleCount > 0 && newUnHandleCount != mUnHandleCount) {
                    unHandleCount = newUnHandleCount
                    unReadCount = mGroupModel?.getJoinRequestUnreadCount() ?: 0
                }

            }
            joiningReviewItem.bind(accountContext, mJoiningRequestList[position], unHandleCount, unReadCount)
        }

        fun unbind() {
            joiningReviewItem.unbind()
        }
    }
}