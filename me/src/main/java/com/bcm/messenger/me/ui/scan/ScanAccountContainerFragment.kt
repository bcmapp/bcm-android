package com.bcm.messenger.me.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.IUserModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_fragment_container_scan_account.*
import com.bcm.messenger.common.BaseFragment

/**
 * Created by wjh on 2019/7/3
 */
class ScanAccountContainerFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_container_scan_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.onBackPressed()
            }

            override fun onClickRight() {
                openBackupInfo()
            }
        })
    }

    private fun openBackupInfo() {
        val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
        userProvider.gotoBackupTutorial()
    }
}