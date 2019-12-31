package com.bcm.messenger.me.ui.destroy

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.me.R
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
                gotoLogin()
            }
        })
        force_logout_understand_btn.setOnClickListener {
            gotoLogin()
        }
        force_logout_destroy_btn.setOnClickListener {
            gotoDestroy()
        }

        init()

        activity?.window?.setStatusBarLightMode()
    }

    private fun init() {
        val otherClientInfo = arguments?.getString(ARouterConstants.PARAM.PARAM_CLIENT_INFO)
        val contentBuilder = SpannableStringBuilder(getString(R.string.me_destroy_force_logout_content))
        contentBuilder.append("\n\n")
        contentBuilder.append("\"${otherClientInfo ?: getString(R.string.me_destroy_other_client_unknown)}\"")
        contentBuilder.append("\n\n")
        contentBuilder.append(getString(R.string.me_destroy_force_logout_tips))
        force_logout_content.text = contentBuilder
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

    private fun gotoLogin() {
        if (AMESelfData.isLogin) {
            ALog.e(TAG, "登录态还没释放完")
            activity?.finish()
            return
        }

        AmeModuleCenter.onLoginSucceed("")

        BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .navigation(context)
        activity?.finish()
    }
}