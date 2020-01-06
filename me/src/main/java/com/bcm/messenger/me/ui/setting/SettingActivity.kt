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
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.accountmodule.IAdHocModule
import com.bcm.messenger.common.provider.accountmodule.IChatModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeLoadingPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.block.BlockUsersActivity
import com.bcm.messenger.me.ui.feedback.AmeFeedbackActivity
import com.bcm.messenger.me.ui.language.LanguageSelectActivity
import com.bcm.messenger.me.ui.language.LanguageViewModel
import com.bcm.messenger.me.ui.pinlock.PinLockInitActivity
import com.bcm.messenger.me.ui.pinlock.PinLockSettingActivity
import com.bcm.messenger.me.ui.proxy.ProxySettingActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_settings.*
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by zjl on 2018/4/27.
 */
@Route(routePath = ARouterConstants.Activity.SETTINGS)
class SettingActivity : SwipeBaseActivity() {
    private val TAG = "SettingActivity"

    private val REQUEST_SETTING = 100
    private val TAG_SSR = "ssrStageChanged"
    private var mChatModule: IChatModule? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

        mChatModule = AmeModuleCenter.chat(accountContext)

        initView()
        initData()
    }

    override fun onResume() {
        super.onResume()
        notification_noticer.checkNotice()
    }

    override fun onLoginRecipientRefresh() {
        setting_block_stranger.setSwitchStatus(!getAccountRecipient().isAllowStranger)
    }

    private fun initView() {
        notification_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        setting_notification.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (!RomUtil.isMiui() || RomUtil.getMIUIVersionCode() >= 8)) {
                gotoNotificationChannelSetting(AmeNotification.getDefaultChannel(baseContext))
            } else {
                startBcmActivity(accountContext, Intent(this, NotificationSettingActivity::class.java))
            }
        }

        setting_language.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivityForResult(accountContext, Intent(this, LanguageSelectActivity::class.java), REQUEST_SETTING)
        }

        setting_tts.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            try {
                val intent = Intent().apply {
                    action = "com.android.settings.TTS_SETTINGS"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                overridePendingTransition(R.anim.common_slide_from_right, R.anim.utility_slide_silent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.common_slide_from_right, R.anim.utility_slide_silent)
                } catch (e: Exception) {
                    AmeAppLifecycle.failure(getString(R.string.me_setting_tts_cannot_open), true)
                }
            }
        }

        setting_storage.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.CLEAN_STORAGE)
                    .startBcmActivity(accountContext, this, REQUEST_SETTING)
        }

        setting_adhoc.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val adhocProvider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
            adhocProvider?.configHocMode()
        }

        setting_proxy.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(it.context, ProxySettingActivity::class.java)
            startBcmActivity(accountContext, intent)
        }

        setting_blocked_user.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivity(accountContext, Intent(this, BlockUsersActivity::class.java))
        }

        setting_block_stranger.setSwitchEnable(false)
        setting_block_stranger.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val allowStranger = !getAccountRecipient().isAllowStranger
            AmePopup.loading.show(this)
            AmeModuleCenter.login().updateAllowReceiveStrangers(accountContext, allowStranger) { success ->
                if (!success) {
                    AmePopup.loading.dismiss()
                    AmePopup.result.failure(this, getString(R.string.me_setting_block_stranger_error), true)
                } else {
                    getAccountRecipient().privacyProfile.allowStranger = allowStranger
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
                startBcmActivityForResult(accountContext, Intent(this, PinLockSettingActivity::class.java), REQUEST_SETTING)
            } else {
                startBcmActivityForResult(accountContext, Intent(this, PinLockInitActivity::class.java), REQUEST_SETTING)
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

        setting_feedback.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(this, AmeFeedbackActivity::class.java)
            startBcmActivity(accountContext, intent)
        }

        setting_faq.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_ZH_ADDRESS).navigation(this)
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_EN_ADDRESS).navigation(this)
            }
        }

        setting_about.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(this, AboutActivity::class.java)
            startBcmActivity(accountContext, intent)
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
        setting_pin_lock.setTip(if (AmePinLogic.hasPin()) getString(R.string.me_setting_pin_on_tip) else getString(R.string.me_setting_pin_off_tip), contentColor = tipColor)
        setting_rtc_p2p.setSwitchStatus(TextSecurePreferences.isTurnOnly(this))

        setting_screen_secure.setSwitchStatus(TextSecurePreferences.isScreenSecurityEnabled(this))

        val weakThis = WeakReference(this)
        BcmUpdateUtil.checkUpdate { hasUpdate, _, _ ->
            if (hasUpdate) {
                weakThis.get()?.setting_about?.setTip("", iconRes = R.drawable.common_red_dot_circle)
            } else {
                weakThis.get()?.setting_about?.hideTip()
            }
        }
    }


    private fun updateStrangerState() {
        setting_block_stranger.setSwitchStatus(!getAccountRecipient().isAllowStranger)
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