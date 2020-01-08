package com.bcm.messenger.contacts

import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.contacts_activity_group_contact.*

/**
 * Created by wjh on 2019/7/1
 */
@Route(routePath = ARouterConstants.Activity.GROUP_CONTACT_MAIN)
class GroupContactActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_activity_group_contact)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_GROUP_CREATE).startBcmActivity(accountContext, this@GroupContactActivity)
            }
        })

        initFragment(R.id.group_contact_container, GroupContactFragment(), null)
        window?.setStatusBarLightMode()
    }
}