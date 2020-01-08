package com.bcm.messenger.me.ui.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.utils.showKeyboard
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.ViewUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_verify_password.*
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * Created by Kin on 2018/9/3
 */
class VerifyPasswordFragment : BaseFragment() {
    private val TAG = "VerifyPasswordFragment"

    private var verifyCallback: ((success: Boolean) -> Unit)? = null
    private var hasFingerprint = false
    private var lockout = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_verify_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        verify_pin_input_clear.isEnabled = false
        verify_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                verifyCallback?.invoke(false)
            }
        })

        verify_pin_input_text.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_CLASS_TEXT

        verify_pin_input_go.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            verifyPassword(verify_pin_input_text.text.toString())
        }
        verify_pin_input_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            verify_pin_input_text.text.clear()
        }
        verify_pin_input_text.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
                updateVerifyInput(s != null && s.isNotEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fetchProfile(arguments?.getSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT) as? AccountContext)

        updateVerifyInput(verify_pin_input_text.text.isNotEmpty())

        verify_pin_input_text.postDelayed({
            verify_pin_input_text?.setSelection(0)
            verify_pin_input_text?.isFocusable = true
            verify_pin_input_text?.requestFocus()
            verify_pin_input_text?.showKeyboard()
        }, 250)

        activity?.window?.setStatusBarLightMode()
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

    private fun updateVerifyInput(hasText: Boolean) {
        if (hasText) {
            verify_pin_input_clear.alpha = 1f
            verify_pin_input_clear.isEnabled = true
            verify_pin_input_go.isEnabled = true
            verify_pin_input_go.setImageResource(R.drawable.me_password_verify_go_icon)
            verify_pin_error.visibility = View.GONE
        } else {
            verify_pin_input_clear.alpha = 0.7f
            verify_pin_input_clear.isEnabled = false
            verify_pin_input_go.isEnabled = false
            verify_pin_input_go.setImageResource(R.drawable.me_password_verify_go_disabled_icon)
        }
    }

    private fun fetchProfile(accountContext: AccountContext?) {
        if (accountContext != null) {
            val account = AmeLoginLogic.accountHistory.getAccount(accountContext.uid)
            val realUid: String? = account?.uid
            val name: String? = account?.name
            val avatar: String? = account?.avatar

            if (!realUid.isNullOrEmpty()) {
                val weakThis = WeakReference(this)
                Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                    try {
                        val recipient = Recipient.from(this.accountContext, realUid, false)
                        val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                            avatar
                        } else {
                            null
                        }
                        recipient.setProfile(recipient.profileKey, name, finalAvatar)
                        emitter.onNext(recipient)
                    } finally {
                        emitter.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ recipient ->
                            weakThis.get()?.verify_pin_name?.text = recipient.name
                            weakThis.get()?.verify_pin_avatar?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
                        }, {})
            }
        } else {
            activity?.finish()
        }
    }

    /**
     * 校验密码
     *
     * @param inputPassword 用户输入的密码
     */
    private fun verifyPassword(inputPassword: String) {
        activity?.hideKeyboard()
        ViewUtils.fadeOut(verify_pin_input_go, 250)
        ViewUtils.fadeIn(verify_pin_loading, 250)
        verify_pin_loading.startAnim()

        Observable.create<Boolean> {
            val accountData = AmeLoginLogic.getMajorAccount()
            if (accountData != null) {
                it.onNext(AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(accountData, inputPassword) != null)
            } else {
                it.onNext(false)
            }
            it.onComplete()

        }.delaySubscription(300, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ViewUtils.fadeOut(verify_pin_loading, 250)
                    ViewUtils.fadeIn(verify_pin_input_go, 250)
                    verify_pin_loading.stopAnim()

                    if (it) {
                        verifyCallback?.invoke(true)
                    } else {
                        verify_pin_error.text = getString(R.string.me_fingerprint_wrong_password)
                        ViewUtils.fadeIn(verify_pin_error, 300)
                    }
                }, {
                    ViewUtils.fadeOut(verify_pin_loading, 250)
                    ViewUtils.fadeIn(verify_pin_input_go, 250)
                    verify_pin_loading.stopAnim()

                    verify_pin_error.text = getString(R.string.me_fingerprint_wrong_password)
                    ViewUtils.fadeIn(verify_pin_error, 300)
                })
    }
}