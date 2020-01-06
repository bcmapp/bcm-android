package com.bcm.messenger.me.ui.login

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.activity.DatabaseMigrateActivity
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.utils.keyboard.KeyboardWatcher
import com.bcm.messenger.login.logic.AmeLoginErrorPasswordException
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.AmeLoginUnknownException
import com.bcm.messenger.login.logic.AmeLoginWrongAccountException
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_layout_login_success.*
import kotlinx.android.synthetic.main.me_layout_relogin_input_pin.*
import java.lang.ref.WeakReference

/**
 * ling created in 2018/6/7
 **/
class LoginVerifyPinFragment : AbsRegistrationFragment(), KeyboardWatcher.SoftKeyboardStateListener, IProxyStateChanged {
    private lateinit var keyboardWatcher: KeyboardWatcher
    private var uid: String? = null

    private var tryingProxy = 0L

    companion object {
        private const val TAG = "LoginVerifyPinFragment"
        const val ACTION = "VERIFY_ACTION"
        const val ACTION_BACKUP_MODE = 1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_login_input_pin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        uid = arguments?.getString(RegistrationActivity.RE_LOGIN_ID)
        fetchProfile(uid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        relogin_input_pin_editText.removeCallbacks(inputPinRunnable)
        keyboardWatcher.removeSoftKeyboardStateListener(this)
    }

    private fun decodeAndLogin(pin: String, retry: Boolean = false) {

        activity?.apply {
            if (!retry) {
                hideKeyboard()
                AmePopup.tipLoading.show(this, false)
            }
            val reLoginUid = arguments?.getString(RegistrationActivity.RE_LOGIN_ID)
            if (reLoginUid?.isNotEmpty() == true) {
                relogin_pin_input_done_button.isEnabled = false
                AmeLoginLogic.login(reLoginUid, pin) { succeed, exception, error ->
                    relogin_pin_input_done_button?.isEnabled = true
                    if (succeed) {
                        AmePopup.tipLoading.dismiss()
                        relogin_input_pin_layout?.visibility = View.GONE
                        login_success_header?.text = resources.getString(R.string.me_login_successful)
                        login_success_layout?.visibility = View.VISIBLE
                        showAnimatorForView(login_success_done_button)
                        AmeDispatcher.mainThread.dispatch({
                            val context = AppContextHolder.APP_CONTEXT
                            if (DatabaseFactory.isDatabaseExist(context) && !TextSecurePreferences.isDatabaseMigrated(context)) {
                                startActivity(Intent(this, DatabaseMigrateActivity::class.java).apply {
                                    putExtra(DatabaseMigrateActivity.IS_LOGIN_PROGRESS, true)
                                })
                            } else {
                                gotoHomeActivity(false)
                            }
                        }, 1500)
                    } else {
                        ALog.e(TAG, "decodeAndLogin error$error, retry: $retry", exception)
                        val failedByFunction = when(exception) {
                            is AmeLoginErrorPasswordException, is AmeLoginWrongAccountException, is AmeLoginUnknownException -> true
                            else -> false
                        }
                        if (retry || failedByFunction) {
                            tryingProxy = 0
                            AmePopup.tipLoading.dismiss()
                            AmePopup.result.failure(this, error)
                        } else {
                            AmePopup.tipLoading.updateTip(getString(R.string.me_proxy_connecing_to_server))
                            tryProxy()
                        }
                    }
                }
            }
        }
    }

    private fun tryProxy() {
        if (tryingProxy > 0) {
            ALog.i(TAG, "proxy trying")
        } else {
            ProxyManager.setListener(this)
            tryingProxy = System.currentTimeMillis()
            ProxyManager.checkConnectionState {
                if (!it) {
                    when {
                        ProxyManager.isReady() -> ProxyManager.startProxy()
                        else -> {
                            ProxyManager.refresh()
                            tryingProxy = 0
                            AmePopup.tipLoading.dismiss()
                            AmeAppLifecycle.failure(getString(R.string.login_login_failed), false)
                        }
                    }
                } else {
                    ALog.i(TAG, "network is working, ignore start proxy")
                }
            }
        }
    }

    override fun onProxyConnecting(proxyName: String, isOfficial: Boolean) {
        val tip = if (isOfficial) {
            getString(R.string.login_try_user_proxy_using_xxx, proxyName)
        } else {
            getString(R.string.login_try_official_proxy_using_xxx)
        }

        AmePopup.tipLoading.updateSubTip(tip)
    }

    override fun onProxyConnectFinished() {
        val context = this.context
        if (context != null && context is Activity) {
            if (context.isFinishing || context.isDestroyed) {
                return
            }

            if (ProxyManager.isProxyRunning()) {
                AmePopup.tipLoading.updateTip("")
                AmePopup.tipLoading.updateSubTip("")
                decodeAndLogin(relogin_input_pin_editText.text.toString())
            } else {
                tryingProxy = 0
                AmePopup.tipLoading.dismiss()
                AmeAppLifecycle.failure(getString(R.string.login_login_failed), false)
            }
        }
    }

    private fun fetchProfile(uid: String?) {
        if (null != uid) {
            val account = AmeLoginLogic.accountHistory.getAccount(uid)
            val realUid: String? = account?.uid
            val name: String? = account?.name
            val avatar: String? = account?.avatar

            if (!realUid.isNullOrEmpty()) {

                val weakThis = WeakReference(this)
                Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                    try {
                        val recipient = Recipient.from(getAccountContext(), realUid, false)
                        val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                            avatar
                        }else {
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
                            weakThis.get()?.relogin_input_pin_nikename?.text = recipient.name
                            weakThis.get()?.relogin_input_pin_avatar?.setPhoto(getAccountContext(), recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
                        }, {
                        })

            }
        }
    }

    override fun onSoftKeyboardOpened(keyboardHeightInPx: Int) {
        activity?.apply {
            val location = IntArray(2)
            content_layout.getLocationOnScreen(location)    
            val x = location[0]
            val y = location[1]
            val bottom = getScreenHeight() - (y + content_layout.height)
            if (keyboardHeightInPx > bottom) {
                val mAnimatorTranslateY = ObjectAnimator.ofFloat(content_layout, "translationY",
                        0.0f, -(keyboardHeightInPx - bottom + 16f.dp2Px()))
                mAnimatorTranslateY.duration = 200
                mAnimatorTranslateY.interpolator = AccelerateDecelerateInterpolator()
                mAnimatorTranslateY.start()
            }
        }

    }

    override fun onSoftKeyboardClosed() {
        val mAnimatorTranslateY = ObjectAnimator.ofFloat(content_layout, "translationY", content_layout.translationY, 0f)
        mAnimatorTranslateY.duration = 200
        mAnimatorTranslateY.interpolator = AccelerateDecelerateInterpolator()
        mAnimatorTranslateY.start()
    }

    private val inputPinRunnable = Runnable {
        relogin_input_pin_editText.requestFocus()
        (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(relogin_input_pin_editText, 0)
    }

    private fun initViews() {
        relogin_pin_input_done_button.setOnClickListener {
            if (TextUtils.isEmpty(relogin_input_pin_editText.text)) {
                Toast.makeText(activity!!, R.string.me_input_pin_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (!AppUtil.checkNetwork()) {
                return@setOnClickListener
            }

            decodeAndLogin(relogin_input_pin_editText.text.toString())
        }
        relogin_input_pin_back.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            if (uid != null) {
                if (AMELogin.isLogin) {
                    act.finish()
                } else {
                    act.hideKeyboard()
                    val intent = Intent(act, RegistrationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                    this.startActivity(intent)
                    act.finish()
                }

            } else
                act.apply { supportFragmentManager.popBackStack() }
        }

        relogin_input_pin_editText.postDelayed(inputPinRunnable, 200L)

        val action = arguments?.getInt(ACTION, 0)
        if (action == ACTION_BACKUP_MODE) {
            forget_password_button.visibility = View.GONE
        } else {
            forget_password_button.visibility = View.VISIBLE
            forget_password_button.setOnClickListener {
                activity?.apply {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.register_container, BanResetPasswordFragment(), "ban_reset_password_fragment")
                            .addToBackStack("login_verify_pin")
                            .commit()
                }
            }
        }
        keyboardWatcher = KeyboardWatcher(activity?.findViewById(Window.ID_ANDROID_CONTENT))
        keyboardWatcher.addSoftKeyboardStateListener(this)
    }
}