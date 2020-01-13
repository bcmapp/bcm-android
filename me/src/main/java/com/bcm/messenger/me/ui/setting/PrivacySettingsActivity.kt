package com.bcm.messenger.me.ui.setting

import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeLoadingPopup
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.utils.startBcmActivityForResult
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.block.BlockUsersActivity
import com.bcm.messenger.me.ui.pinlock.PinLockInitActivity
import com.bcm.messenger.me.ui.pinlock.PinLockSettingActivity
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.me_activity_privacy_settings.*

/**
 * Created by Kin on 2020/1/8
 */
class PrivacySettingsActivity : AccountSwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.me_activity_privacy_settings)

        initView()
        initData()
    }

    override fun onLoginRecipientRefresh() {
        privacy_block_stranger.setSwitchStatus(!accountRecipient.isAllowStranger)
    }

    private fun initView() {
        privacy_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })
        privacy_blocked_user.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivity(Intent(this, BlockUsersActivity::class.java))
        }

        privacy_block_stranger.setSwitchEnable(false)
        privacy_block_stranger.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val allowStranger = !accountRecipient.isAllowStranger
            AmePopup.loading.show(this)
            AmeModuleCenter.login().updateAllowReceiveStrangers(accountContext, allowStranger) { success ->
                if (!success) {
                    AmePopup.loading.dismiss()
                    AmePopup.result.failure(this, getString(R.string.me_setting_block_stranger_error), true)
                } else {
                    accountRecipient.privacyProfile.allowStranger = allowStranger
                    updateStrangerState()
                    AmePopup.loading.dismiss(AmeLoadingPopup.DELAY_DEFAULT)
                }
            }
        }

        privacy_rtc_p2p.setSwitchEnable(false)
        privacy_rtc_p2p.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val turnOnly = !TextSecurePreferences.isTurnOnly(accountContext)
            TextSecurePreferences.setTurnOnly(accountContext, turnOnly)
            privacy_rtc_p2p.setSwitchStatus(turnOnly)
        }
    }

    private fun initData() {
        updateStrangerState()

        privacy_rtc_p2p.setSwitchStatus(TextSecurePreferences.isTurnOnly(accountContext))
    }

    private fun updateStrangerState() {
        privacy_block_stranger.setSwitchStatus(!accountRecipient.isAllowStranger)
    }
}