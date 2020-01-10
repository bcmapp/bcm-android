package com.bcm.messenger.me.ui.destroy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.front
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_fragment_forced_logout.*

/**
 * Created by Kin on 2018/9/18
 */
class ForcedLogOutFragment : Fragment() {

    private val TAG = "ForcedLogOutFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_forced_logout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        force_logout_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                iKnown()
            }
        })
        force_logout_understand_btn.setOnClickListener {
            iKnown()
        }
        force_logout_destroy_btn.setOnClickListener {
            gotoDestroy()
        }

        init()

        activity?.window?.setStatusBarLightMode()
    }

    private fun gotoReLogin(uid:String) {
        if (AMELogin.isLogin) {
            val intent = Intent(context, VerifyKeyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(RegistrationActivity.RE_LOGIN_ID, uid)
            }
            startBcmActivity(AmeLoginLogic.getAccountContext(uid), intent)

            AmeDispatcher.mainThread.dispatch({
                (context as? Activity)?.finish()
            }, 1000)
        } else {
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_SWITCHER)
                    .putString(ARouterConstants.PARAM.PARAM_UID, uid)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .navigation()
        }
    }

    private fun init() {
        val accountContext = arguments?.getSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT) as? AccountContext

        var name = ""
        var icon = ""
        var uid = ""
        if (accountContext != null) {
            uid = accountContext.uid

            val account = AmeLoginLogic.getAccount(accountContext.uid)
            if (null != account) {
                icon = account.avatar
                name = account.name
                if (name.isEmpty()) {
                    name = uid.front()
                }
            }
        }

        val contentBuilder = SpannableStringBuilder(getString(R.string.me_destroy_force_logout_content))
        contentBuilder.append(" $name")
        contentBuilder.append(getString(R.string.me_destroy_force_logout_tips))
        force_logout_content.text = contentBuilder
        force_logout_avatar.setPhoto(uid, name, icon)

        val otherClientInfo = arguments?.getString(ARouterConstants.PARAM.PARAM_CLIENT_INFO)

        force_logout_name.text = name
        force_logout_phone_name.text = otherClientInfo?:getString(R.string.me_destroy_other_client_unknown)

        force_logout_re_login.setOnClickListener {
            gotoReLogin(uid)
        }
    }

    private fun gotoDestroy() {
        val fragment = DestroyCheckPasswordFragment()
        val fm = fragmentManager ?: return
        fragment.arguments = arguments
        DestroyAccountDialog().setCallback {
            fragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.common_slide_from_right, R.anim.common_popup_alpha_out, R.anim.common_popup_alpha_in, R.anim.common_slide_to_right)
                    ?.replace(R.id.destroy_container, fragment)
                    ?.addToBackStack("check_password")
                    ?.commit()
        }.show(fm, "destroy_dialog")
    }

    private fun iKnown() {
        if (AMELogin.isLogin) {
            ALog.i(TAG, "iKnown 1")
            (context as Activity).finish()
        } else {
            ALog.i(TAG, "iKnown 2")
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_SWITCHER)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .navigation()
        }
    }
}