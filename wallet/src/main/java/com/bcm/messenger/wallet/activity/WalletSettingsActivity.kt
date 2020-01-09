package com.bcm.messenger.wallet.activity

import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.wallet.R
import kotlinx.android.synthetic.main.wallet_settings_activity.*

/**
 * 钱包配置页面
 * Created by wjh on 2018/6/2
 */
class WalletSettingsActivity : AccountSwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_settings_activity)

        settings_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        currency_entrance_layout.setOnClickListener {
            val intent = Intent(this, CurrencyActivity::class.java)
            startBcmActivity(intent)
        }


    }
}