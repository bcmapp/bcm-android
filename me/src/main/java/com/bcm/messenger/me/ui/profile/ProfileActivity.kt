package com.bcm.messenger.me.ui.profile

import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route

/**
 * Created by Kin on 2018/9/6
 */
@Route(routePath = ARouterConstants.Activity.PROFILE_EDIT)
class ProfileActivity : SwipeBaseActivity() {
    companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_profile)
        val recipient: Recipient?
        try {
            val address = intent.getParcelableExtra<Address?>(ARouterConstants.PARAM.PARAM_ADDRESS)
            recipient = if (address == null) {
                Recipient.major()
            } else {
                Recipient.from(address, true)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "from recipient fail", ex)
            finish()
            return
        }

        if (intent.getBooleanExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT_SELF, false)) {
            initFragment(R.id.profile_root_container, MyProfileFragment(), Bundle().apply {
                putString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID, recipient.address.serialize())
            })
        } else {
            initFragment(R.id.profile_root_container, OtherProfileFragment(), Bundle().apply {
                putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
            })
        }
    }
}