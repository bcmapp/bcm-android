package com.bcm.messenger.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.activity_account_switcher.*

/**
 * Created by Kin on 2020/1/9
 */
@Route(routePath = ARouterConstants.Activity.ACCOUNT_SWITCHER)
class AccountSwitcherActivity : AppCompatActivity() {
    private val REQ_SCAN_ACCOUNT = 1001
    private val REQ_SCAN_LOGIN = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_account_switcher)

        initView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_SCAN_ACCOUNT -> switcher_profile_layout.analyseQrCode(data, false)
            REQ_SCAN_LOGIN -> switcher_profile_layout.analyseQrCode(data, true)
        }
    }

    private fun initView() {
        switcher_profile_layout.setListener(object : HomeProfileLayout.HomeProfileListener {
            override fun onClickExit() {
            }

            override fun onDragVertically(ev: MotionEvent?): Boolean {
                return false
            }

            override fun onInterceptEvent(ev: MotionEvent?): Boolean {
                return false
            }

            override fun onViewPagerScrollStateChanged(newState: Int) {

            }

            override fun onViewChanged(newRecipient: Recipient?) {

            }
        })
    }
}