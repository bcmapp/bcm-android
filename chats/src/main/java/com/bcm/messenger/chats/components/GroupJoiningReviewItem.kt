package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.BcmGroupJoinRequest
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.core.corebean.BcmReviewGroupJoinRequest
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.setDrawableLeft
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_item_group_joining_check.view.*

/**
 * Created by wjh on 2019/6/4
 */
class GroupJoiningReviewItem @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyle), RecipientModifiedListener {

    private val TAG = "GroupJoiningReviewItem"
    private var mJoiner: Recipient? = null
    private var mInviter: Recipient? = null
    private var mRequestData: BcmGroupJoinRequest? = null
    private var mUnHandleCount: Int = 0
    private var mUnReadCount: Int = 0

    init {
        View.inflate(context, R.layout.chats_item_group_joining_check, this)
        val p = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        setPadding(p, p, p, p)

        join_action_accept_iv.setOnClickListener {
            val data = mRequestData ?: return@setOnClickListener
            val groupModel = GroupLogic.get(AMELogin.majorContext).getModel(data.gid)?:return@setOnClickListener
            AmeAppLifecycle.showLoading()
            
            val reviewRequest = BcmReviewGroupJoinRequest(data.uid, data.reqId,true)
            groupModel.reviewJoinRequests(listOf(reviewRequest)) { succeed, error ->
                ALog.d(TAG, "reviewJoinRequests success: $succeed, error: $error")
                AmeAppLifecycle.hideLoading()
                if (succeed) {
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_join_approve_success), true)
                }else {
                    AmeAppLifecycle.failure(getString(R.string.chats_group_join_approve_fail), true)
                }
            }
        }
        join_action_deny_iv.setOnClickListener {
            val data = mRequestData ?: return@setOnClickListener
            val groupModel = GroupLogic.get(AMELogin.majorContext).getModel(data.gid)?:return@setOnClickListener
            AmeAppLifecycle.showLoading()
            val reviewRequest = BcmReviewGroupJoinRequest(data.uid, data.reqId, false)
            groupModel.reviewJoinRequests(listOf(reviewRequest)) { succeed, error ->
                ALog.d(TAG, "reviewJoinRequests success: $succeed, error: $error")
                AmeAppLifecycle.hideLoading()
                if (succeed) {
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_join_reject_success), true)
                }else {
                    AmeAppLifecycle.failure(getString(R.string.chats_group_join_reject_fail), true)
                }
            }
        }
        join_member_comment_tv.setOnClickListener {
            if (mInviter != null) {
                val address = mInviter?.address ?: return@setOnClickListener
                AmeModuleCenter.contact(address.context())?.openContactDataActivity(context, address, mRequestData?.gid ?: 0)
            }
        }
        join_member_all_item.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_JOIN_CHECK).putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, mRequestData?.gid ?: -1).startBcmActivity(AMELogin.majorContext, context)
        }
    }

    override fun onModified(recipient: Recipient) {
        if (mJoiner == recipient || mInviter == recipient) {
            bind(recipient.address.context(), mRequestData ?: return, mUnHandleCount, mUnReadCount)
        }
    }

    fun bind(accountContext: AccountContext, data: BcmGroupJoinRequest, unHandleCount: Int, unReadCount: Int) {
        unbind()
        mRequestData = data
        mUnHandleCount = unHandleCount
        mUnReadCount = unReadCount
        mJoiner = Recipient.from(accountContext, data.uid, true)
        mJoiner?.addListener(this)
        if (data.inviter.isNullOrEmpty()) {
            mInviter = null

            if (data.comment.isNullOrEmpty()) {
                join_member_comment_tv.visibility = View.GONE
            }else {
                join_member_comment_tv.visibility = View.VISIBLE
                join_member_comment_tv.text = data.comment
            }

        }else {
            mInviter = Recipient.from(accountContext, data.inviter!!, true)
            mInviter?.addListener(this)

            join_member_comment_tv.visibility = View.VISIBLE
            join_member_comment_tv.text = context.getString(R.string.chats_group_join_check_invited_comment, mInviter?.name ?: "")
        }
        join_member_avatar.setPhoto(mJoiner)
        join_member_name_tv.text = if (!mJoiner?.localName.isNullOrEmpty()) {
            mJoiner?.localName
        }else if(!mJoiner?.bcmName.isNullOrEmpty()) {
            mJoiner?.bcmName
        }else {
            data.name
        }

        if (data.read) {
            join_member_name_tv.setDrawableLeft(0)
        }else {
            join_member_name_tv.setDrawableLeft(R.drawable.common_new_notify_icon)
        }
        when(data.status) {
            BcmGroupJoinStatus.WAIT_OWNER_REVIEW -> {
                join_action_status_tv.visibility = View.GONE
                join_action_accept_iv.visibility = View.VISIBLE
                join_action_deny_iv.visibility = View.VISIBLE
            }
            BcmGroupJoinStatus.OWNER_APPROVED -> {
                join_action_status_tv.visibility = View.VISIBLE
                join_action_accept_iv.visibility = View.GONE
                join_action_deny_iv.visibility = View.GONE
                join_action_status_tv.text = context.getString(R.string.chats_group_join_approved_state)
                join_action_status_tv.setTextColor(getColor(R.color.common_app_green_color))
            }
            BcmGroupJoinStatus.OWNER_REJECTED -> {
                join_action_status_tv.visibility = View.VISIBLE
                join_action_accept_iv.visibility = View.GONE
                join_action_deny_iv.visibility = View.GONE
                join_action_status_tv.text = context.getString(R.string.chats_group_join_rejected_state)
                join_action_status_tv.setTextColor(getColor(R.color.common_content_warning_color))
            }
            else -> {}
        }

        if (mUnHandleCount > 0) {
            join_member_all_item.visibility = View.VISIBLE
            join_member_all_item.setTip(mUnHandleCount.toString(), if (mUnReadCount > 0) R.drawable.common_red_dot else 0)

        }else {
            join_member_all_item.visibility = View.GONE
        }
    }

    fun unbind() {
        mJoiner?.removeListener(this)
        mInviter?.removeListener(this)
    }
}