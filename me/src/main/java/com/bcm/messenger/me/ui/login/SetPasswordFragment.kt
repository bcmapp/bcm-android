package com.bcm.messenger.me.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import kotlinx.android.synthetic.main.me_fragment_set_password.*
import org.whispersystems.libsignal.ecc.ECKeyPair
import java.util.regex.Pattern

class SetPasswordFragment : AbsRegistrationFragment() {

    private var nonce:Long = 0L
    private var keyPair:ECKeyPair? = null

    fun initParams(nonce:Long, keyPair: ECKeyPair){
        this.nonce = nonce
        this.keyPair = keyPair
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_set_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        set_password_back.setOnClickListener {
            activity?.onBackPressed()
        }
        set_new_password_done_button.setOnClickListener {
            if (!checkInputLegitimate()){
                AmePopup.loading.dismiss()
                return@setOnClickListener
            }

            val keyPair = this.keyPair
            if (null == keyPair || nonce == 0L){
                AmePopup.result.failure(activity, getString(R.string.me_login_account_key_null_warning))
                AmePopup.loading.dismiss()
                return@setOnClickListener
            }

            val f = PickNicknameFragment()
            f.initParams(nonce, keyPair, new_password_edit.text.toString(), "")
            activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.register_container, f, "pick_nickname_fragment")
                    ?.addToBackStack("set_password_fragment")
                    ?.commit()
        }

        new_password_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                new_password_edit.background = context?.getDrawable(R.drawable.me_register_input_bg)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        new_password_edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && new_password_edit.length() < ARouterConstants.PASSWORD_LEN_MIN) {
                new_password_edit.background = context?.getDrawable(R.drawable.me_register_input_error_bg)
            }
        }

        confirm_pwd_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                confirm_pwd_edit.background = context?.getDrawable(R.drawable.me_register_input_bg)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun checkInputLegitimate(): Boolean {
        if (new_password_edit.length() < ARouterConstants.PASSWORD_LEN_MIN) {
            AmePopup.result.failure(activity, getString(R.string.common_password_too_short_warning), true)
            new_password_edit.background = context?.getDrawable(R.drawable.me_register_input_error_bg)
            return false
        } else if (!TextUtils.equals(new_password_edit.text, confirm_pwd_edit.text)) {

            AmePopup.result.failure(activity, resources.getString(R.string.me_psw_not_equals_confirm_psw), true)
            confirm_pwd_edit.background = context?.getDrawable(R.drawable.me_register_input_error_bg)
            return false

        } else if (!Pattern.matches(ARouterConstants.PASSWORD_REGEX, new_password_edit.text)) {
            AmePopup.result.failure(activity, resources.getString(R.string.common_password_format_error), true)
            return false
        }
        return true
    }


}
