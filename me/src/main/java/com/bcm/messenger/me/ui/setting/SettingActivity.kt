package com.bcm.messenger.me.ui.setting

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AmeNotification
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.IChatModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeLoadingPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.getPackageInfo
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.block.BlockUsersActivity
import com.bcm.messenger.me.ui.language.LanguageSelectActivity
import com.bcm.messenger.me.ui.language.LanguageViewModel
import com.bcm.messenger.me.ui.pinlock.PinLockInitActivity
import com.bcm.messenger.me.ui.pinlock.PinLockSettingActivity
import com.bcm.messenger.me.ui.proxy.ProxySettingActivity
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_settings.*
import java.util.*


/**
 * Created by zjl on 2018/4/27.
 */
class SettingActivity : SwipeBaseActivity(), RecipientModifiedListener {

    private val TAG = "SettingActivity"
    private val REQUEST_SETTING = 100
    private val TAG_SSR = "ssrStageChanged"
    private lateinit var mSelf: Recipient
    private var mChatModule: IChatModule? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //从各种设置详情回来的时候，需要刷新下data，因为有可能配置已经变更
        initData()
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(TAG_SSR)
        mChatModule?.finishAllConversationStorageQuery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_settings)

        mChatModule = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONVERSATION_BASE).navigationWithCast()
        try {
            mSelf = Recipient.fromSelf(this, true)
            mSelf.addListener(this)
        }catch (ex: Exception) {
            finish()
            return
        }

        initView()
        initData()
    }

    override fun onResume() {
        super.onResume()
        notification_noticer.checkNotice()
    }

    override fun onModified(recipient: Recipient) {
        if (recipient == mSelf) {
            setting_block_stranger.setSwitchStatus(!mSelf.isAllowStranger)
        }
    }

    private fun initView() {


        notification_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        setting_notification.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }
            // Android O及之后版本需要跳转到系统通知设置页面。MIUI需要MIUI 10或更新版本，MIUI 9的Android O走老方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (!RomUtil.isMiui() || RomUtil.getMIUIVersionCode() >= 8)) {
                gotoNotificationChannelSetting(AmeNotification.getDefaultChannel(baseContext))
            } else {
                startActivity(Intent(this, NotificationSettingActivity::class.java))
            }
        }

        setting_language.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }
            startActivityForResult(Intent(this, LanguageSelectActivity::class.java), REQUEST_SETTING)
        }

        setting_tts.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }

            try {
                // 首先尝试打开TTS设置页
                val intent = Intent().apply {
                    action = "com.android.settings.TTS_SETTINGS"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                overridePendingTransition(R.anim.common_slide_from_right, R.anim.utility_slide_silent)

            } catch (e: Exception) {
                // 打开TTS设置页失败，尝试打开无障碍设置页面
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.common_slide_from_right, R.anim.utility_slide_silent)

                } catch (e: Exception) {
                    // 都失败，提示手动进入设置页进行设置
                    AmeAppLifecycle.failure(getString(R.string.me_setting_tts_cannot_open), true)
                }
            }
        }


        setting_storage.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.CLEAN_STORAGE).navigation(this, REQUEST_SETTING)
        }

        setting_proxy.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            startActivity(Intent(it.context, ProxySettingActivity::class.java))
        }

        setting_blocked_user.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }
            startActivity(Intent(this, BlockUsersActivity::class.java))
        }

        setting_block_stranger.setSwitchEnable(false)
        setting_block_stranger.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val allowStranger = !mSelf.isAllowStranger
            AmePopup.loading.show(this)
            AmeModuleCenter.login().updateAllowReceiveStrangers(allowStranger) { success ->
                if (!success) {
                    AmePopup.loading.dismiss()
                    AmePopup.result.failure(this, getString(R.string.me_setting_block_stranger_error), true)
                }else {
                    mSelf.privacyProfile.allowStranger = allowStranger
                    updateStrangerState()
                    AmePopup.loading.dismiss(AmeLoadingPopup.DELAY_DEFAULT)

                }
            }
        }

        setting_pin_lock.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (AmePinLogic.hasPin()) {
                startActivityForResult(Intent(this, PinLockSettingActivity::class.java), REQUEST_SETTING)
            } else {
                startActivityForResult(Intent(this, PinLockInitActivity::class.java), REQUEST_SETTING)
            }
        }

        setting_screen_secure.setSwitchEnable(false)
        setting_screen_secure.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val newSecureEnable = !TextSecurePreferences.isScreenSecurityEnabled(this)
            TextSecurePreferences.setScreenSecurityEnabled(this, newSecureEnable)
            setting_screen_secure.setSwitchStatus(newSecureEnable)
            updateScreenshotSecurity()
            if (newSecureEnable) {
                AmePopup.center.newBuilder()
                        .withContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                        .withContent(getString(R.string.me_screen_secure_enable_tip))
                        .withOkTitle(getString(R.string.common_understood))
                        .show(this)
            }
        }

        setting_rtc_p2p.setSwitchEnable(false)
        setting_rtc_p2p.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val turnOnly = !TextSecurePreferences.isTurnOnly(it.context)
            TextSecurePreferences.setTurnOnly(it.context, turnOnly)
            setting_rtc_p2p.setSwitchStatus(turnOnly)
        }
    }

    private fun initData() {

        val tipColor = getColorCompat(R.color.common_content_second_color)
        setting_language.setTip(LanguageViewModel.getDisplayName(SuperPreferences.getLanguageString(this, Locale.getDefault().language)), contentColor = tipColor)

        updateStrangerState()
        setting_storage.setTip(getString(R.string.me_setting_data_stoarge_calulating_tip), contentColor = tipColor)
        mChatModule?.queryAllConversationStorageSize {
            ALog.i(TAG, "queryAllConversationStorageSize  callback")
            setting_storage?.setTip(StringAppearanceUtil.formatByteSizeString(it.storageUsed()), contentColor = tipColor)
        }
        setting_pin_lock.setTip(if(AmePinLogic.hasPin()) getString(R.string.me_setting_pin_on_tip) else getString(R.string.me_setting_pin_off_tip), contentColor = tipColor)
        setting_rtc_p2p.setSwitchStatus(TextSecurePreferences.isTurnOnly(this))

        setting_screen_secure.setSwitchStatus(TextSecurePreferences.isScreenSecurityEnabled(this))
    }


    private fun updateStrangerState() {
        setting_block_stranger.setSwitchStatus(!mSelf.isAllowStranger)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun gotoNotificationChannelSetting(channelId: String) {
        try {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageInfo().packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            startActivity(intent)
            overridePendingTransition(R.anim.common_slide_from_right, R.anim.utility_slide_silent)

        } catch (e: Exception) {
            ALog.d(TAG, "Cannot open notification page.")
        }
    }
}