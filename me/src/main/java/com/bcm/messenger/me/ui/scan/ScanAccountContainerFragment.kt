package com.bcm.messenger.me.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import kotlinx.android.synthetic.main.me_fragment_container_scan_account.*

/**
 * Created by wjh on 2019/7/3
 */
class ScanAccountContainerFragment : Fragment() {

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
        UserModuleImp().gotoBackupTutorial()
    }
}