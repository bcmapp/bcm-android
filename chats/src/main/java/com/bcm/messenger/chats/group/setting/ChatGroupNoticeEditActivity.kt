package com.bcm.messenger.chats.group.setting

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.EditText
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import kotlinx.android.synthetic.main.chats_group_activity_edit_notice.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 *
 */
class ChatGroupNoticeEditActivity : AccountSwipeBaseActivity() {
    companion object {
        private const val TAG = "GroupNoticeActivity"
    }

    private lateinit var groupModel: GroupViewModel
    private var notice = ""


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


    override fun onCreate(savedInstanceState: Bundle?) {
        disableDefaultTransitionAnimation()
        overridePendingTransition(R.anim.common_slide_from_bottom, R.anim.common_slide_to_bottom)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_group_activity_edit_notice)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                notice_edit.clearFocus()
                hideKeyboard()

                finish()
            }

            override fun onClickRight() {
                notice_edit.clearFocus()
                hideKeyboard()

                realSave(notice_edit.text.toString())
            }
        })

        EventBus.getDefault().register(this)

        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        val groupModel = GroupLogic.get(accountContext).getModel(groupId)
        if (groupModel == null) {
            finish()
            return
        }

        this.groupModel = groupModel
        notice = groupModel.getGroupInfo().noticeContent
        notice_edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateTitleBar()
            }
        })

        notice_edit.setText(notice)

        notice_edit.requestFocus()
        notice_edit.postDelayed({
            notice_edit.showKeyboard()
        }, 800)


    }


    fun updateTitleBar() {
        if (notice_edit.text != null && notice_edit.text.isNotEmpty()) {
            title_bar.setRightClickable(true)
            title_bar.setRightTextColor(getColorCompat(R.color.common_app_primary_color))

        } else {
            title_bar.setRightClickable(false)
            title_bar.setRightTextColor(getColorCompat(R.color.common_color_A6B3BF))
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }


    @Subscribe
    fun onEvent(event: GroupViewModel.GroupInfoChangedEvent) {
        if (groupModel.getGroupInfo().noticeContent != null && groupModel.getGroupInfo().noticeContent.isNotEmpty()) {
            notice_edit.setText(groupModel.getGroupInfo().noticeContent)
        }
    }

    private fun realSave(plainNotice: String) {
        if (plainNotice == notice) {
            return
        }

        AmePopup.loading.show(this@ChatGroupNoticeEditActivity)

        groupModel.upLoadNoticeContent(plainNotice, System.currentTimeMillis()) { succeed, error ->
            AmePopup.loading.dismiss()
            if (succeed) {
                AmePopup.result.succeed(this@ChatGroupNoticeEditActivity, getString(R.string.chats_save_success))
                finish()
            } else {
                AmePopup.result.failure(this@ChatGroupNoticeEditActivity, getString(R.string.chats_save_fail))
            }
        }
    }
}