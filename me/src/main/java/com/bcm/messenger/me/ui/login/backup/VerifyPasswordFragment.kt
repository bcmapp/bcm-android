package com.bcm.messenger.me.ui.login.backup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.messenger.me.ui.qrcode.ShowQRCodeActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.ViewUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_check_password.*
import java.lang.ref.WeakReference


class VerifyPasswordFragment : AbsRegistrationFragment() {

    private val TAG = "VerifyPasswordFragment"
    private var action: Int = 0
    private var accountId: String? = null
    private var recipient: Recipient? = null
    private var needFinish: Boolean = false

    companion object {
        const val BACKUP_JUMP_ACTION = "BACKUP_JUMP_ACTION"
        const val NEED_FINISH = "NEED_FINISH"
        const val SHOW_QRCODE_BACKUP = 1
        const val DELETE_PROFILE = 2
        const val DELETE_BACKUP = 3
        const val CHANGE_PIN = 4
        const val CLEAR_PIN = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            action = getInt(BACKUP_JUMP_ACTION, SHOW_QRCODE_BACKUP)
            accountId = getString(VerifyKeyActivity.ACCOUNT_ID)
            needFinish = getBoolean(NEED_FINISH, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_check_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        recipient = null
    }

    private fun initViews() {

        verify_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                if (accountId != null) {
                    activity?.finish()
                } else {
                    activity?.apply { supportFragmentManager.popBackStack() }
                }
            }

            override fun onClickRight() {
                // go to help
            }
        })

        verify_title_bar.setCenterText(when (action) {
            DELETE_PROFILE -> resources.getString(R.string.me_keybox_delete_btn)
            SHOW_QRCODE_BACKUP -> resources.getString(R.string.me_show_qr_code)
            CHANGE_PIN -> resources.getString(R.string.me_change_pin)
            CLEAR_PIN -> resources.getString(R.string.me_clear_pin)
            else -> resources.getString(R.string.me_show_qr_code)
        })


        verify_pin_input_text.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_CLASS_TEXT

        verify_pin_input_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    verify_pin_input_clear.alpha = 1f
                    verify_pin_input_clear.isEnabled = true
                    verify_pin_input_go.setImageResource(R.drawable.me_password_verify_go_icon)
                    verify_pin_input_go.isEnabled = true
                    verify_pin_error.visibility = View.GONE
                } else {
                    verify_pin_input_clear.alpha = 0.7f
                    verify_pin_input_clear.isEnabled = false
                    verify_pin_input_go.setImageResource(R.drawable.me_password_verify_go_disabled_icon)
                    verify_pin_input_go.isEnabled = false
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        verify_pin_input_clear?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            verify_pin_input_text.text.clear()
        }
        verify_pin_input_go.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (!TextUtils.isEmpty(verify_pin_input_text.text)) {
                ViewUtils.fadeOut(verify_pin_input_go, 250)
                ViewUtils.fadeIn(verify_pin_loading, 250)
                verify_pin_loading.startAnim()

                checkPasswordIsRight(verify_pin_input_text.text.toString()) {
                    ViewUtils.fadeIn(verify_pin_input_go, 250)
                    ViewUtils.fadeOut(verify_pin_loading, 250)
                    verify_pin_loading.stopAnim()

                    if (it) {
                        when (action) {
                            SHOW_QRCODE_BACKUP -> {
                                fragmentManager?.popBackStack()
                                //展示二维码
                                val intent = Intent(activity, ShowQRCodeActivity::class.java)
                                if (!arguments?.getString(VerifyKeyActivity.ACCOUNT_ID).isNullOrEmpty()) {
                                    intent.putExtra(VerifyKeyActivity.ACCOUNT_ID, arguments?.getString(VerifyKeyActivity.ACCOUNT_ID))
                                }
                                startActivity(intent)
                                if (needFinish) {
                                    activity?.finish()
                                }
                            }
                            DELETE_BACKUP -> {
                                AmeAppLifecycle.failure(getString(R.string.me_str_delete_account_key_success), true) {
                                    //删除备份
                                    AmeLoginLogic.accountHistory.resetBackupState(AMESelfData.uid)
                                    BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            .navigation(activity)
                                }
                            }
                            DELETE_PROFILE -> {
                                activity?.hideKeyboard()
                                val uid = accountId
                                if (null != uid) {
                                    AmeLoginLogic.accountHistory.deleteAccount(uid)
                                }

                                AmeAppLifecycle.failure(getString(R.string.me_str_delete_account_key_success), true) {
                                    //删除备份
                                    if (needFinish) {
                                        if (AmeLoginLogic.isLogin()) {
                                            activity?.finish()
                                        } else {
                                            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                    .navigation(activity)
                                        }
                                    }
                                }
                            }
                            CLEAR_PIN -> {
                                AmePinLogic.disablePin()
                                AmeAppLifecycle.succeed(resources.getString(R.string.me_pin_clear), true) {
                                    activity?.finish()
                                }
                            }
                            CHANGE_PIN -> {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.PIN_INPUT)
                                        .putInt(PinInputActivity.INPUT_STYPE, PinInputActivity.INPUT_PIN)
                                        .navigation()
                                activity?.finish()
                            }
                        }
                    } else {
                        //密码错误
                        verify_pin_error.text = getString(R.string.me_fingerprint_wrong_password)
                        ViewUtils.fadeIn(verify_pin_error, 300)
                    }
                }
            } else {
                //密码不能为空
                verify_pin_error.text = getString(R.string.me_input_pin_hint)
                ViewUtils.fadeIn(verify_pin_error, 300)
            }
        }

        fetchProfile(accountId)

        activity?.window?.setStatusBarLightMode()

        verify_pin_input_text.postDelayed({
            verify_pin_input_text?.setSelection(0)
            verify_pin_input_text?.isFocusable = true
            verify_pin_input_text?.requestFocus()
            verify_pin_input_text?.let {
                it.showKeyboard()
            }
        }, 250)
    }

    private fun checkPasswordIsRight(password: String, result: (right: Boolean) -> Unit) {
        Observable.create<Boolean> {
            var right = false
            val id = this.accountId
            if (id != null) {
                val account = AmeLoginLogic.getAccount(id)
                if (null != account) {
                    right = AmeLoginLogic.accountHistory.getPrivateKeyWithPassword(account, password) != null
                }
            }
            it.onNext(right)
            it.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    result(it)
                }, {
                    ALog.e(TAG, "checkPasswordIsRight error", it)
                    result(false)
                })
    }

    private fun fetchProfile(accountId: String?) {
        if (!accountId.isNullOrEmpty()) {
            val account = AmeLoginLogic.accountHistory.getAccount(accountId)
            val realUid: String? = account?.uid
            val name: String? = account?.name
            val avatar: String? = account?.avatar

            if (!realUid.isNullOrEmpty()) {
                val weakThis = WeakReference(this)
                Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                    try {
                        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(realUid), false)
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
}