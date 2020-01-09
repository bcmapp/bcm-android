package com.bcm.messenger.me.ui.pinlock

import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import kotlinx.android.synthetic.main.me_activity_init_pin_lock.*
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.utils.startBcmActivity

/**
 * bcm.social.01 2018/10/10.
 */
class PinLockInitActivity : AccountSwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_init_pin_lock)
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        me_enable_pin_lock.setOnClickListener {
            PinInputActivity.router(this, PinInputActivity.INPUT_SIZE_4, PinInputActivity.INPUT_PIN)
        }
    }

    override fun onResume() {
        super.onResume()
        if (AmePinLogic.hasPin()) {
            val intent = Intent(this, PinLockSettingActivity::class.java)
            startBcmActivity(accountContext, intent)
            me_enable_pin_lock.postDelayed({
                finish()
            }, 50)
        }
    }
}