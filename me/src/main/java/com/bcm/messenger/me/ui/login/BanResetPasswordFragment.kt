package com.bcm.messenger.me.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.me_fragment_ban_reset_password.*

/**

 * Created by zjl on 2018/9/7.
 */
class BanResetPasswordFragment : AbsRegistrationFragment() {

    private val TAG = "BanResetPasswordFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_ban_reset_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ban_reset_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {

            override fun onClickLeft() {
                activity?.apply { supportFragmentManager.popBackStack() }

            }
            override fun onClickRight() {
                UserModuleImp().gotoBackupTutorial()
            }
        })


        ban_reset_back_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            activity?.apply { supportFragmentManager.popBackStack() }
        }

        activity?.window?.setStatusBarLightMode()
    }


}