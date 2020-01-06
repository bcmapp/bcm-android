package com.bcm.messenger.chats.group.setting

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.startBcmActivity
import kotlinx.android.synthetic.main.chats_group_activity_group_notice.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * 
 */
class ChatGroupNoticeActivity : SwipeBaseActivity(), RecipientModifiedListener {

    companion object {
        private const val TAG = "GroupNoticeActivity"
    }

    private lateinit var groupModel: GroupViewModel
    private lateinit var recipient: Recipient


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val v = currentFocus
                if (v is EditText) {
                    AppUtil.hideKeyboard(this, ev, v)
                }
            }
            else -> {
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_group_activity_group_notice)
        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)

        EventBus.getDefault().register(this)
        val groupModel = GroupLogic.get(accountContext).getModel(groupId)
        if (null == groupModel) {
            finish()
            return
        }
        this.groupModel = groupModel

        recipient = Recipient.from(accountContext, groupModel.getGroupInfo().owner, true)
        recipient.addListener(this)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        initViews()
    }


    private fun initViews() {

        group_notice_edit_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                startBcmActivity(accountContext, Intent(this@ChatGroupNoticeActivity, ChatGroupNoticeEditActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupModel.groupId())
                })
            }
        })

        if (groupModel.getGroupInfo().noticeContent?.isNotEmpty() == true) {
            group_notice_edit_title.setRightText(getString(R.string.chats_edit))
            notice_container.visibility = View.VISIBLE
            no_notice_layout.visibility = View.GONE
            notice_content_text.text = groupModel.getGroupInfo().noticeContent
            group_owner_avatar.setAvatar(accountContext, AmeGroupMemberInfo.OWNER, recipient.address, groupModel.getGroupMember(recipient.address.serialize())?.keyConfig)
            group_owner_name_text.text = recipient.name
            notice_update_time_text.text = DateUtils.formatDayTimeForMillisecond(groupModel.getGroupInfo()!!.noticeUpdateTime)
        } else {
            notice_container.visibility = View.GONE
            no_notice_layout.visibility = View.VISIBLE
            group_notice_edit_title.setRightText(getString(R.string.chats_add))
        }

        if (groupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            group_notice_edit_title.setRightVisible()
        } else {
            group_notice_edit_title.setRightInvisible()
        }
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.GroupInfoChangedEvent) {
        initViews()
    }


    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            initViews()
        }
    }


}