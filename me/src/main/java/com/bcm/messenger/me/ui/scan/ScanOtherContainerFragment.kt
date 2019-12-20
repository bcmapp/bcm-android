package com.bcm.messenger.me.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_fragment_container_scan_account.*
import com.bcm.messenger.common.BaseFragment

/**
 * Created by wjh on 2019/7/3
 */
class ScanOtherContainerFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_container_scan_other, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.onBackPressed()
            }
        })

        val title = activity?.intent?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_TITLE)
        if (!title.isNullOrEmpty()) {
            title_bar.setCenterText(title)
        }
    }

}