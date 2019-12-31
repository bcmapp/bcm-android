package com.bcm.messenger.me.ui.profile

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_fragment_edit_name.*

/**
 * Created by Kin on 2018/9/7
 */
@Route(routePath = ARouterConstants.Activity.EDIT_NAME)
class EditNameActivity : SwipeBaseActivity(), RecipientModifiedListener {
    private val TAG = "EditNameActivity"

    private lateinit var recipient: Recipient
    private var mForLocal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_fragment_edit_name)

        mForLocal = intent.getBooleanExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, false)
        val address = intent.getParcelableExtra<Address?>(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (address == null) {
            ALog.e(TAG, "address is null")
            finish()
            return
        }

        recipient = Recipient.from(AppContextHolder.APP_CONTEXT, address, true)
        recipient.addListener(this)
        initView()
    }

    override fun onPause() {
        super.onPause()
        edit_name_input.clearFocus()
        edit_name_input.hideKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        recipient.removeListener(this)
    }

    private fun initView() {
        edit_name_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                edit_name_input.hideKeyboard()
                finish()
            }

            override fun onClickRight() {
                saveName()
            }
        })

        edit_name_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s == null) {
                    return
                }
                if (s.isNotEmpty() || mForLocal) {
                    edit_name_title_bar.setRightTextColor(getColorCompat(R.color.common_color_379BFF))
                    edit_name_title_bar.setRightClickable(true)
                    edit_name_clear.visibility = View.VISIBLE
                } else {
                    edit_name_clear.visibility = View.GONE
                    edit_name_title_bar.setRightTextColor(getColorCompat(R.color.common_color_C2C2C2))
                    edit_name_title_bar.setRightClickable(false)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        edit_name_clear.setOnClickListener {
            edit_name_input.text?.clear()
        }

        initName(recipient)
    }

    private fun initName(recipient: Recipient) {
        if (!mForLocal) {
            edit_name_title_bar.setCenterText(getString(R.string.me_profile_nickname))
            edit_name_input.setText(recipient.bcmName)
        } else {
            edit_name_title_bar.setCenterText(getString(R.string.me_other_local_display_name))
            edit_name_input.setText(recipient.localName)
        }
        edit_name_input.setSelection(edit_name_input.text?.length ?: 0)
        edit_name_input.appendFilter(InputLengthFilter())

        AmeDispatcher.mainThread.dispatch({
            edit_name_input.requestFocus()
            showKeyboard(edit_name_input)
        }, 100)
    }

    private fun saveName() {
        val name = edit_name_input.text.toString()
        edit_name_input.clearFocus()
        edit_name_input.hideKeyboard()
        AmePopup.loading.show(this)
        val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
        userProvider.updateNameProfile(recipient, name) {
            AmePopup.loading.dismiss()
            if (it) {
                AmePopup.result.succeed(this, getString(R.string.me_profile_alias_save_success), true)
                AmeDispatcher.mainThread.dispatch({
                    finish()
                }, 1500)
            } else {
                AmePopup.result.failure(this, getString(R.string.me_save_fail), true)
            }
        }
    }

    override fun onModified(recipient: Recipient) {
        edit_name_input.post {
            if (this.recipient == recipient) {
                initName(recipient)
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, 0)
    }
}