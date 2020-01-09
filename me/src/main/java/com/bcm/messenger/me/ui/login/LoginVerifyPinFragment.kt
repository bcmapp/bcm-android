package com.bcm.messenger.me.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.deprecated.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.activity.DatabaseMigrateActivity
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginErrorPasswordException
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.AmeLoginUnknownException
import com.bcm.messenger.login.logic.AmeLoginWrongAccountException
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.ViewUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import kotlinx.android.synthetic.main.me_fragment_verify_password.*
import kotlinx.android.synthetic.main.me_layout_login_success.*

/**
 * ling created in 2018/6/7
 **/
class LoginVerifyPinFragment : AbsRegistrationFragment(), IProxyStateChanged {
    private var uid: String? = null

    private var tryingProxy = 0L

    companion object {
        private const val TAG = "LoginVerifyPinFragment"
        const val ACTION = "VERIFY_ACTION"
        const val ACTION_BACKUP_MODE = 1
    }

    private val statusBarHeight = AppContextHolder.APP_CONTEXT.getStatusBarHeight()

    private val topViewHeight = if (AppContextHolder.APP_CONTEXT.checkDeviceHasNavigationBar()) {
        getRealScreenHeight() - AppContextHolder.APP_CONTEXT.getNavigationBarHeight()
    } else {
        getRealScreenHeight()
    }

    private val gapSize = (topViewHeight - statusBarHeight - 426.dp2Px()) / 3 + 40.dp2Px() - 52.dp2Px()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_verify_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        uid = arguments?.getString(RegistrationActivity.RE_LOGIN_ID)
        fetchProfile(uid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        verify_pin_input_text.removeCallbacks(inputPinRunnable)
    }

    private fun decodeAndLogin(pin: String, retry: Boolean = false) {

        activity?.apply {
            if (!retry) {
                hideKeyboard()
                startAnim()
            }
            val reLoginUid = arguments?.getString(RegistrationActivity.RE_LOGIN_ID)
            if (reLoginUid?.isNotEmpty() == true) {
                verify_pin_input_go.isEnabled = false
                AmeLoginLogic.login(reLoginUid, pin) { succeed, exception, error ->
                    if (succeed) {
                        verify_pin_tips.text = resources.getString(R.string.me_login_successful)
                        ViewUtils.fadeIn(verify_pin_tips, 250)

                        AmeDispatcher.mainThread.dispatch({
                            val accountContext = AmeModuleCenter.login().getAccountContext(reLoginUid)
                            val context = AppContextHolder.APP_CONTEXT
                            if (DatabaseFactory.isDatabaseExist(accountContext, context) && !TextSecurePreferences.isDatabaseMigrated(accountContext)) {
                                startActivity(Intent(this, DatabaseMigrateActivity::class.java).apply {
                                    putExtra(DatabaseMigrateActivity.IS_LOGIN_PROGRESS, true)
                                })
                            } else {
                                gotoHomeActivity(AMELogin.majorContext, false)
                            }
                        }, 1500)
                    } else {
                        stopAnim()
                        verify_pin_input_go?.isEnabled = true

                        ALog.e(TAG, "decodeAndLogin error$error, retry: $retry", exception)
                        val failedByFunction = when (exception) {
                            is AmeLoginErrorPasswordException,
                            is AmeLoginWrongAccountException,
                            is AmeLoginUnknownException -> true
                            else -> false
                        }
                        if (retry || failedByFunction) {
                            tryingProxy = 0
                            ViewUtils.fadeIn(verify_pin_error, 250)
                            verify_pin_error.text = error
                        } else {
                            ViewUtils.fadeIn(verify_pin_tips, 250)
                            verify_pin_tips.text = getString(R.string.me_proxy_connecing_to_server)
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

        verify_pin_tips.text = tip
    }

    override fun onProxyConnectFinished() {
        val context = this.context
        if (context != null && context is Activity) {
            if (context.isFinishing || context.isDestroyed) {
                return
            }

            ViewUtils.fadeOut(verify_pin_tips, 250)
            if (ProxyManager.isProxyRunning()) {
                decodeAndLogin(verify_pin_input_text.text.toString())
            } else {
                tryingProxy = 0
                verify_pin_error.text = getString(R.string.login_login_failed)
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
                val avatarText = name?.substring(0, 1) ?: realUid.substring(0, 1)
                verify_pin_name?.text = name
                verify_pin_avatar?.setPhoto(realUid, avatarText, avatar ?: "")
            }
        }
    }

    private val inputPinRunnable = Runnable {
        verify_pin_input_text.requestFocus()
        (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(verify_pin_input_text, 0)
    }

    private fun initViews() {
        verify_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                val act = activity ?: return
                if (uid != null) {
                    if (AMELogin.isLogin) {
                        act.finish()
                    } else {
                        act.hideKeyboard()
                        val intent = Intent(act, RegistrationActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        act.finish()
                    }
                } else {
                    act.apply { supportFragmentManager.popBackStack() }
                }
            }
        })
        val action = arguments?.getInt(ACTION, 0)
        if (action == ACTION_BACKUP_MODE) {
            verify_title_bar.setCenterText(getString(R.string.me_account_verify_title))
        } else {
            verify_title_bar.setCenterText(getString(R.string.me_str_login))
        }

        verify_pin_avatar.layoutParams = (verify_pin_avatar.layoutParams as ConstraintLayout.LayoutParams).apply {
            topMargin = gapSize
        }

        verify_pin_input_go.setOnClickListener {
            if (TextUtils.isEmpty(verify_pin_input_text.text)) {
                Toast.makeText(activity!!, R.string.me_input_pin_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (!AppUtil.checkNetwork()) {
                return@setOnClickListener
            }

            decodeAndLogin(verify_pin_input_text.text.toString())
        }

        verify_pin_input_text.postDelayed(inputPinRunnable, 200L)
        verify_pin_input_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateVerifyInput(s?.isNotEmpty() == true)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        verify_pin_input_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            verify_pin_input_text.text.clear()
        }
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

    private fun startAnim() {
        ViewUtils.fadeOut(verify_pin_input_go, 250)
        ViewUtils.fadeOut(verify_pin_error, 250)
        ViewUtils.fadeIn(verify_pin_loading, 250)
        verify_pin_loading.startAnim()
    }

    private fun stopAnim() {
        ViewUtils.fadeOut(verify_pin_loading, 250)
        ViewUtils.fadeIn(verify_pin_input_go, 250)
        verify_pin_loading.stopAnim()
    }
}