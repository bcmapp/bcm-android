package com.bcm.messenger.wallet

import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.setStatusBarDarkMode
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.utils.setTransparencyBar
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.wallet_activity_main.*

/**
 * Created by wjh on 2019/7/2
 */
@Route(routePath = ARouterConstants.Activity.WALLET_MAIN)
class WalletActivity : AccountSwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ThemeManager.isDarkTheme(this)) {
            window.setStatusBarLightMode()
        } else {
            window.setStatusBarDarkMode()
        }
        setContentView(R.layout.wallet_activity_main)
        title_bar.setLeftIconColor(getAttrColor(R.attr.common_activity_background))
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        initFragment(R.id.wallet_main_container, WalletFragment(), null)
    }

    override fun onResume() {
        super.onResume()
        title_bar.setLeftIconColor(getAttrColor(R.attr.common_activity_background))
    }
}