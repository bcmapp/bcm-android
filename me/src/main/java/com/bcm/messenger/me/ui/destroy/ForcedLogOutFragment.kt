package com.bcm.messenger.me.ui.destroy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.me.R
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
        logout_login_btn.setOnClickListener {
            (activity as? DestroyAccountActivity)?.gotoLogin()
        }
        logout_destroy_account.setOnClickListener {
            gotoDestroy()
        }
        init()
    }

    private fun init() {
        val otherClientInfo = arguments?.getString(ARouterConstants.PARAM.PARAM_CLIENT_INFO)
        logout_device_name.text = otherClientInfo ?: getString(R.string.me_destroy_other_client_unknown)
    }

    private fun gotoDestroy() {
        val fragment = DestroyCheckPasswordFragment()
        val fm = fragmentManager ?: return
        fragment.arguments = arguments
        DestroyAccountDialog().setCallback {
            fragmentManager?.beginTransaction()
                    ?.replace(R.id.destroy_container, fragment)
                    ?.addToBackStack("check_password")
                    ?.commit()
        }.show(fm, "destroy_dialog")
    }
}