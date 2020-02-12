package com.bcm.messenger.chats.group.setting

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import kotlinx.android.synthetic.main.chats_activity_edit_group_name.*

/**
 * Created by Kin on 2019/7/4
 */
class ChatGroupEditNameActivity : AccountSwipeBaseActivity() {
    private lateinit var groupModel: GroupViewModel
    private var gid = 0L
    private var groupName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_activity_edit_group_name)

        gid = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        val model = GroupLogic.get(accountContext).getModel(gid)
        if (null == model) {
            finish()
            return
        }

        this.groupModel = model
        groupName = model.getGroupInfo().name

        edit_name_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                doSave()
            }
        })

        edit_name_input.filters = arrayOf(InputLengthFilter())
        edit_name_input.setText(groupModel.getGroupInfo().name ?: "")
        edit_name_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
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

        AmeDispatcher.mainThread.dispatch({
            edit_name_input.requestFocus()
            showKeyboard(edit_name_input)
        }, 100)
    }

    override fun onPause() {
        super.onPause()
        edit_name_input.clearFocus()
        edit_name_input.hideKeyboard()
    }


    private fun doSave() {
        val newName = edit_name_input.text.toString()
        if (newName.isBlank()) {
            ToastUtil.show(this, getString(R.string.chats_group_name_is_empty))
            return
        }

        edit_name_input.clearFocus()
        edit_name_input.hideKeyboard()

        AmePopup.loading.show(this)

        if (groupName == newName) {
            return
        }

        groupModel.updateGroupName(newName) {
            succeed, error ->
            AmePopup.loading.dismiss()
            if (succeed) {
                AmePopup.result.succeed(this, getString(R.string.chats_group_info_edit_save_success)) {
                    finish()
                }
            } else {
                var msg = getString(R.string.chats_group_info_edit_save_failed)
                if (!error.isNullOrEmpty())  {
                    msg = error
                }
                AmePopup.result.failure(this, msg)
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, 0)
    }
}