package com.bcm.messenger.chats

import android.os.Bundle
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.ui.CommonTitleBar2
import kotlinx.android.synthetic.main.chats_qr_display_activity.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Display QRCode scan result
 */
@Route(routePath = ARouterConstants.Activity.QR_DISPLAY)
class QRCodeDisplayActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_qr_display_activity)

        qr_display_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                AppUtil.saveCodeToBoard(this@QRCodeDisplayActivity, qr_display_content.text.toString())

                AmePopup.result.succeed(this@QRCodeDisplayActivity, getString(R.string.chats_qr_copy_description), true)
            }
        })

        qr_display_content.text = intent.getStringExtra(ARouterConstants.PARAM.PARAM_QR_CODE)


    }
}