package com.bcm.messenger.me.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.me.R
import com.bcm.messenger.me.fingerprint.BiometricVerifyUtil
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.login.backup.VerifyFingerprintActivity
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_fragment_verify_fingerprint.*

/**
 * Created by Kin on 2018/9/3
 */
class VerifyFingerprintFragment : Fragment() {

    private val TAG = "VerifyFingerprintFragment"
    private val DIALOG_TAG = "Fingerprint_Dialog"

    private var verifyCallback: ((success: Boolean) -> Unit)? = null
    private var authenticated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_verify_fingerprint, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        verify_fingerprint_cancel.setOnClickListener {
            verifyCallback?.invoke(false)
        }
        verify_fingerprint_password.setOnClickListener {
            (activity as? VerifyFingerprintActivity)?.switchToPasswordFragment(true)
        }
    }

    private fun startAuthenticate() {
        ALog.d(TAG, "Start authenticate")
        activity?.let {
            BiometricVerifyUtil.BiometricBuilder(it)
                    .setTitle(getString(R.string.me_unlock_account_key))
                    .setDescription(getString(R.string.me_unlock_account_key_description))
                    .setCancelTitle(getString(R.string.common_cancel))
                    .setCallback { success, hasHw, isLocked ->
                        authenticated = true
                        if (success) {
                            verifyCallback?.invoke(true)
                        } else {
                            (activity as? VerifyFingerprintActivity)?.switchToPasswordFragment(hasHw, isLocked)
                        }
                    }
                    .build()
        }
    }

    fun setCallback(callback: (success: Boolean) -> Unit): VerifyFingerprintFragment {
        verifyCallback = callback
        return this
    }

    override fun onResume() {
        super.onResume()
        if (!authenticated) {
            startAuthenticate()
        }
    }

    override fun onDetach() {
        super.onDetach()
        verifyCallback = null
    }
}