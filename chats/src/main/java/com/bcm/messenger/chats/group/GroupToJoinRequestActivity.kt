package com.bcm.messenger.chats.group

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.chats_activity_join_request.*

/**
 * 进群申请页面
 * Created by wjh on 2019/6/3
 */
@Route(routePath = ARouterConstants.Activity.GROUP_JOIN_REQUEST)
class GroupToJoinRequestActivity : SwipeBaseActivity(), RecipientModifiedListener {

    private val TAG = "GroupToJoinRequestActivity"
    private var mSelf: Recipient? = null
    private var mUseDefaultHint: Boolean = true
    private var mGroupShareContent: AmeGroupMessage.GroupShareContent? = null
    private val mMemoWatcher = object : TextWatcher {

        override fun afterTextChanged(s: Editable?) {
            if (!s?.toString().isNullOrEmpty()) {
                request_memo_input.showClearButton()
            } else {
                request_memo_input.hideClearButton()
            }
            mUseDefaultHint = false
            request_memo_input.setSelection(request_memo_input.text?.length ?: 0)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        request_memo_input.removeTextChangedListener(mMemoWatcher)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_join_request)


        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                doRequest()
            }
        })

        request_memo_input.addTextChangedListener(mMemoWatcher)
        request_memo_input.filters = arrayOf(InputLengthFilter(60))

        val shareContentString = intent.getStringExtra(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT)
        if (!shareContentString.isNullOrEmpty()) {
            mGroupShareContent = AmeGroupMessage.GroupShareContent.fromJson(shareContentString)
        }
        try {
            mSelf = Recipient.fromSelf(this, true)
            mSelf?.addListener(this)
        }catch (ex: Exception) {
            finish()
            return
        }
        init()
    }

    override fun onModified(recipient: Recipient) {
        if (mSelf == recipient) {
            init()
        }
    }

    private fun init() {
        if (mUseDefaultHint) {
            request_memo_input.setText(getString(R.string.chats_group_join_comment_hint, mSelf?.name ?: ""))
        }
        request_memo_input.requestFocus()
    }

    private fun doRequest() {
        val groupShareContent = mGroupShareContent
        if (groupShareContent == null) {
            AmeAppLifecycle.succeed(getString(R.string.chats_group_join_request_fail), true)
        }else {
            AmeAppLifecycle.showLoading()

            val eKey = groupShareContent.ekey
            val eKeyByteArray = if (!eKey.isNullOrEmpty()) {
                try {
                    eKey.base64Decode()
                } catch(e:Throwable) {
                    null
                }
            } else {
                null
            }

            GroupLogic.joinGroupByShareCode(groupShareContent.groupId, groupShareContent.shareCode, groupShareContent.shareSignature, eKeyByteArray, request_memo_input.text.toString()) { succeed, error ->
                ALog.d(TAG, "joinGroupByShareCode success: $succeed, error: $error")
                AmeAppLifecycle.hideLoading()
                if (succeed) {
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_join_request_success), true) {
                        finish()
                    }
                }else {
                    if (error.isNullOrEmpty()) {
                        AmeAppLifecycle.failure(getString(R.string.chats_group_join_request_fail), true)
                    }else {
                        AmeAppLifecycle.failure(getString(R.string.chats_group_join_request_fail) + "\n" + error, true)
                    }
                }
            }
        }
    }
}