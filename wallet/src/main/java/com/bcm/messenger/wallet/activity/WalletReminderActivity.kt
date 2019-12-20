package com.bcm.messenger.wallet.activity

import android.os.Bundle
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.wallet.R
import kotlinx.android.synthetic.main.wallet_reminder_activity.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Created by wjh on 2018/6/2
 */
class WalletReminderActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_reminder_activity)

        reminder_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })


    }

}