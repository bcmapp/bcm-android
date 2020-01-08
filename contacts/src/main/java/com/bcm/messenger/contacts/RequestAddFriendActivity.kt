package com.bcm.messenger.contacts

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.contacts_activity_request_friend.*

/**
 * Created by wjh on 2018/02/28
 */
@Route(routePath = ARouterConstants.Activity.REQUEST_FRIEND)
class RequestAddFriendActivity : SwipeBaseActivity(), RecipientModifiedListener {

    private var mRecipient: Recipient? = null
    private var mUseDefaultHint: Boolean = true
    private var mIsHandling = false
    private var mOtherNick: String? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_activity_request_friend)

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

        try {
            mRecipient = Recipient.from(intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ADDRESS), true)
            mRecipient?.addListener(this)

        } catch (ex: Exception) {
            finish()
            return
        }
        mOtherNick = intent.getStringExtra(ARouterConstants.PARAM.PARAM_NICK)
        init()
    }

    override fun onDestroy() {
        super.onDestroy()
        request_memo_input.removeTextChangedListener(mMemoWatcher)
        mRecipient?.removeListener(this)
    }

    override fun onModified(recipient: Recipient) {
        if (mRecipient == recipient) {
            init()
        }
    }

    override fun onLoginRecipientRefresh() {
        init()
    }

    private fun init() {
        var name = mRecipient?.bcmName
        if (name.isNullOrEmpty()) {
            name = mOtherNick
        }
        if (name.isNullOrEmpty()) {
            name = mRecipient?.address?.format() ?: ""
        }
        request_notice_tv.text = getString(R.string.contacts_request_friend_notice, name)
        if (mUseDefaultHint) {
            request_memo_input.setText(getString(R.string.contacts_request_friend_memo_hint, accountRecipient.name))
        }
        request_memo_input.requestFocus()
    }

    private fun doRequest() {
        if (mIsHandling) return
        val recipient = mRecipient ?: return
        mIsHandling = true
        AmePopup.loading.show(this)
        AmeModuleCenter.contact(accountContext)?.addFriend(recipient.address.serialize(), request_memo_input.text.toString(), false) { success ->
            mIsHandling = false
            AmePopup.loading.dismiss()
            if (success) {
                AmePopup.result.succeed(this, getString(R.string.contacts_request_friend_success), true) {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }else {
                AmePopup.result.failure(this, getString(R.string.contacts_request_friend_fail), true)
            }
        }
    }


}
