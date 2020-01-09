package com.bcm.messenger.me.ui.qrcode

import android.os.Bundle
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_activity_my_qr_code.*
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.me.ui.scan.MyQRFragment

/**
 * Created by bcm.social.01 on 2018/7/6.
 */
@Route(routePath = ARouterConstants.Activity.ME_QR)
class BcmMyQRCodeActivity: AccountSwipeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.me_activity_my_qr_code)

        val fragment = MyQRFragment()
        initFragment(R.id.me_qr_fragment, fragment, null)

        my_qr_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })
    }
}