package com.bcm.messenger.chats.group

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.provider.IForwardSelectProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by wjh on 2019/6/14
 */
@Route(routePath = ARouterConstants.Activity.GROUP_SHARE_FORWARD)
class GroupShareForwardActivity : SwipeBaseActivity() {

    private val TAG = "GroupShareForwardActivity"
    private var mFromGroupId: Long = -1L
    private var mGroupShareString: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        disableDefaultTransitionAnimation()
        overridePendingTransition(R.anim.common_slide_from_bottom_fast, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_forward)
        setSwipeBackEnable(false)

        mFromGroupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1L)
        mGroupShareString = intent.getStringExtra(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT) ?: ""
        initView()
    }

    private fun initView() {
        val forwardProvider = BcmRouter.getInstance().get(ARouterConstants.Fragment.FORWARD_FRAGMENT).navigation()
        if (forwardProvider is IForwardSelectProvider) {
            forwardProvider.setCallback(object : IForwardSelectProvider.ForwardSelectCallback {
                override fun onClickContact(recipient: Recipient) {
                    if (recipient.isGroupRecipient) {
                        doForwardForGroup(GroupUtil.gidFromAddress(recipient.address))
                    }else {
                        doForwardForPrivate(recipient)
                    }
                }
            })
            forwardProvider.setContactSelectContainer(R.id.activity_forward_root)
            forwardProvider.setGroupSelectContainer(R.id.activity_forward_root)
        }
        supportFragmentManager.beginTransaction()
                .add(R.id.activity_forward_root, forwardProvider as Fragment)
                .commit()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.common_slide_to_bottom_fast)
    }


    private fun doForwardForGroup(toGroupId: Long) {
        try {
            AmeAppLifecycle.showLoading()
            GroupMessageLogic.get(accountContext).messageSender.sendGroupShareMessage(toGroupId, AmeGroupMessage.GroupShareContent.fromJson(mGroupShareString),
                    object : com.bcm.messenger.chats.group.logic.MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    ALog.d(TAG, "doForwardForGroup result: $isSuccess")
                    AmeAppLifecycle.hideLoading()
                    if (isSuccess) {
                        AmeAppLifecycle.succeed(getString(R.string.chats_group_share_forward_success), true) {
                            finish()
                        }
                    } else {
                        AmeAppLifecycle.failure(getString(R.string.chats_group_share_forward_fail), true)
                    }
                }
            })
        }catch (ex: Exception) {
            ALog.e(TAG, "doForwardForGroup error", ex)
        }
    }


    private fun doForwardForPrivate(toRecipient: Recipient) {
        AmeAppLifecycle.showLoading()
        val ameGroupMessage = AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_CARD, AmeGroupMessage.GroupShareContent.fromJson(mGroupShareString)).toString()
        val message = OutgoingLocationMessage(toRecipient, ameGroupMessage, (toRecipient.expireMessages * 1000).toLong())
        Observable.create(ObservableOnSubscribe<Long> {
            it.onNext(MessageSender.send(this, accountContext, message, -1L) {

            })
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    AmeAppLifecycle.hideLoading()
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_share_forward_success), true) {
                        finish()
                    }
                }, {
                    ALog.e(TAG, "doForwardForPrivate error", it)
                    AmeAppLifecycle.hideLoading()
                    AmeAppLifecycle.failure(getString(R.string.chats_group_share_forward_fail), true)
                })
    }
}