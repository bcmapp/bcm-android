package com.bcm.messenger.adhoc.ui.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.showKeyboard
import kotlinx.android.synthetic.main.adhoc_chanel_join_activity.*
import com.bcm.messenger.common.SwipeBaseActivity

class AdHocJoinChannelActivity: SwipeBaseActivity(), TextWatcher {
    companion object {
        fun router(context:Context, title:String, option:String) {
            val intent = Intent(context, AdHocJoinChannelActivity::class.java)
            intent.putExtra("name", title)
            intent.putExtra("option", option)
            context.startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adhoc_join_channel_name_edit?.removeTextChangedListener(this)
        adhoc_join_channel_passwd_edit?.removeTextChangedListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_chanel_join_activity)

        adhoc_join_toolbar.setCenterText(intent.getStringExtra("name"))
        adhoc_join_toolbar.setRightText(intent.getStringExtra("option"))

        adhoc_join_toolbar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                super.onClickLeft()
                finish()
            }

            override fun onClickRight() {
                super.onClickRight()
                val name = adhoc_join_channel_name_edit.text.toString().trim()
                val passwd = adhoc_join_channel_passwd_edit.text.toString().trim()
                joinChannel(name, passwd)
            }
        })

        adhoc_join_channel_name_edit.postDelayed( {
            adhoc_join_channel_name_edit?.requestFocus()
            adhoc_join_channel_name_edit?.showKeyboard()
        }, 400)

        adhoc_join_channel_name_edit.addTextChangedListener(this)
        adhoc_join_channel_passwd_edit.addTextChangedListener(this)
    }

    override fun afterTextChanged(s: Editable?) {
        if (!adhoc_join_channel_name_edit?.text.isNullOrEmpty()) {
            adhoc_join_channel_name_edit?.showClearButton()
        }else {
            adhoc_join_channel_name_edit?.hideClearButton()
        }
        if (!adhoc_join_channel_passwd_edit?.text.isNullOrEmpty()) {
            adhoc_join_channel_passwd_edit?.showClearButton()
        }else {
            adhoc_join_channel_passwd_edit?.hideClearButton()
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }

    private fun joinChannel(name:String, passwd:String) {
        if (name.isBlank()) {
            adhoc_join_channel_name_edit.requestFocus()
            adhoc_join_channel_name_edit.error = getString(R.string.adhoc_join_error_input_channel_name)
            return
        }
        if (passwd.isBlank()) {
            adhoc_join_channel_passwd_edit.requestFocus()
            adhoc_join_channel_passwd_edit.error = getString(R.string.adhoc_join_error_input_channel_password)
            return
        }

        AdHocSessionLogic.addChannelSession(name, passwd) {
            //enter session
            if (it.isNotEmpty()) {
                startActivity(Intent(this, AdHocConversationActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, it)
                })
            }
        }
        finish()
    }

}