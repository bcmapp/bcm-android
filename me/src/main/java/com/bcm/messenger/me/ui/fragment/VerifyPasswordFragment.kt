package com.bcm.messenger.me.ui.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.backup.VerifyFingerprintActivity
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import kotlinx.android.synthetic.main.me_fragment_verify_password.*

/**
 * Created by Kin on 2018/9/3
 */
class VerifyPasswordFragment : Fragment() {
    private val TAG = "VerifyPasswordFragment"

    private var verifyCallback: ((success: Boolean) -> Unit)? = null
    private var hasFingerprint = false
    private var lockout = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_verify_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        verify_password_input.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_CLASS_TEXT

        verify_password_fingerprint.setOnClickListener {
            (activity as? VerifyFingerprintActivity)?.switchToFingerprintFragment()
        }
        verify_password_confirm.setOnClickListener {
            verifyPassword(verify_password_input.text.toString())
        }
        verify_password_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                verify_password_confirm.visibility = if (s != null && s.isNotEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                verify_password_input.background = context?.getDrawable(R.drawable.me_register_input_bg)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        verify_password_cancel.setOnClickListener {
            verifyCallback?.invoke(false)
        }
        if (!hasFingerprint) {
            // 设备没有指纹识别，隐藏跳转图标
            verify_password_fingerprint.visibility = View.GONE
            verify_password_try_again.visibility = View.GONE
        }
        if (lockout) {
            // 失败过多被锁，不允许跳转到指纹识别
            verify_password_fingerprint.setOnClickListener {
                AmePopup.result.failure(activity, getString(R.string.me_fingerprint_lockout), true)
            }
            AmePopup.result.failure(activity, getString(R.string.me_fingerprint_lockout), true)
        }
    }

    fun setCallback(callback: (success: Boolean) -> Unit): VerifyPasswordFragment {
        verifyCallback = callback
        return this
    }

    fun setHasFingerprint(hasFingerprint: Boolean): VerifyPasswordFragment {
        this.hasFingerprint = hasFingerprint
        return this
    }

    fun setHasLockout(lockout: Boolean): VerifyPasswordFragment {
        this.lockout = lockout
        return this
    }

    override fun onDetach() {
        super.onDetach()
        verifyCallback = null
    }

    private fun verifyPassword(inputPassword: String) {
        AmePopup.loading.show(activity)
        AmeDispatcher.io.dispatch {
            try {
                val profile = AmeLoginLogic.getCurrentAccount()
                profile?.let {
                    if (AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(it, inputPassword) != null) {
                        AmeDispatcher.mainThread.dispatch {
                            AmePopup.loading.dismiss()
                            verifyCallback?.invoke(true)
                        }
                    }
                    return@dispatch
                }
            } catch (e: Exception) {}
            AmeDispatcher.mainThread.dispatch {
                AmePopup.loading.dismiss()
                verify_password_input.background = context?.getDrawable(R.drawable.me_register_input_error_bg)
                AmePopup.result.failure(activity, getString(R.string.me_fingerprint_wrong_password), true)
            }
        }
    }
}