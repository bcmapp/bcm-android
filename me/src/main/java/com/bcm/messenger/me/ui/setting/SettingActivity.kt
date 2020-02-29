package com.bcm.messenger.me.ui.setting

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AmeNotification
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.accountmodule.IChatModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.accesspoint.AccessPointSettingActivity
import com.bcm.messenger.me.ui.feedback.AmeFeedbackActivity
import com.bcm.messenger.me.ui.language.LanguageSelectActivity
import com.bcm.messenger.me.ui.language.LanguageViewModel
import com.bcm.messenger.me.ui.pinlock.PinLockInitActivity
import com.bcm.messenger.me.ui.pinlock.PinLockSettingActivity
import com.bcm.messenger.me.ui.profile.ProfileActivity
import com.bcm.messenger.me.ui.proxy.ProxySettingActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.setDrawableLeft
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_settings.*
import kotlinx.android.synthetic.main.me_settings_head_view.*
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by zjl on 2018/4/27.
 */
@Route(routePath = ARouterConstants.Activity.SETTINGS)
class SettingActivity : AccountSwipeBaseActivity(), RecipientModifiedListener {
    private val TAG = "SettingActivity"

    private val REQUEST_SETTING = 100
    private val TAG_SSR = "ssrStageChanged"
    private var mChatModule: IChatModule? = null
    private lateinit var recipient: Recipient

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        initData()
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(TAG_SSR)
        mChatModule?.finishAllConversationStorageQuery()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_settings)

        mChatModule = AmeModuleCenter.chat(accountContext)
        recipient = Recipient.from(accountContext, accountContext.uid, true)
        recipient.addListener(this)

        initView()
        initData()
        initHeadView()
    }

    override fun onResume() {
        super.onResume()
        notification_noticer.checkNotice()
        privacy_pin_lock.setTip(if (AmePinLogic.hasPin()) getString(R.string.me_setting_pin_on_tip) else getString(R.string.me_setting_pin_off_tip),
                contentColor = getAttrColor(R.attr.common_text_secondary_color))
        checkBackup()
    }

    private fun initView() {
        notification_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        head_view_info_layout.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivity(Intent(this, ProfileActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT_SELF, true)
            })
        }

        setting_privacy.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivity(Intent(this, PrivacySettingsActivity::class.java))
        }

        setting_notification.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (!RomUtil.isMiui() || RomUtil.getMIUIVersionCode() >= 8)) {
                gotoNotificationChannelSetting(AmeNotification.getDefaultChannel(baseContext))
            } else {
                startBcmActivity(Intent(this, NotificationSettingActivity::class.java))
            }
        }

        setting_language.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivityForResult(Intent(this, LanguageSelectActivity::class.java), REQUEST_SETTING)
        }

        setting_theme.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
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

        privacy_pin_lock.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (AmePinLogic.hasPin()) {
                startBcmActivityForResult(Intent(this, PinLockSettingActivity::class.java), REQUEST_SETTING)
            } else {
                startBcmActivityForResult(Intent(this, PinLockInitActivity::class.java), REQUEST_SETTING)
            }
        }

        setting_proxy.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(it.context, ProxySettingActivity::class.java)
            startBcmActivity(intent)
        }

        setting_access_point.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(it.context, AccessPointSettingActivity::class.java)
            startBcmActivity(intent)
        }

        setting_screen_secure.setSwitchEnable(false)
        setting_screen_secure.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val newSecureEnable = !SuperPreferences.isScreenSecurityEnabled(this)
            SuperPreferences.setScreenSecurityEnabled(this, newSecureEnable)
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
            val turnOnly = !SuperPreferences.isTurnOnly(this)
            SuperPreferences.setTurnOnly(this, turnOnly)
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
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB)
                        .putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_ZH_ADDRESS)
                        .startBcmActivity(accountContext, this)
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB)
                        .putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_EN_ADDRESS)
                        .startBcmActivity(accountContext, this)
            }
        }

        setting_about.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(this, AboutActivity::class.java)
            startBcmActivity(intent)
        }
    }

    private fun initData() {
        val tipColor = getAttrColor(R.attr.common_text_secondary_color)
        setting_language.setTip(LanguageViewModel.getDisplayName(SuperPreferences.getLanguageString(this, Locale.getDefault().language)), contentColor = tipColor)

        setting_storage.setTip(getString(R.string.me_setting_data_stoarge_calulating_tip), contentColor = tipColor)
        mChatModule?.queryAllConversationStorageSize(accountContext) {
            ALog.i(TAG, "queryAllConversationStorageSize  callback")
            setting_storage?.setTip(StringAppearanceUtil.formatByteSizeString(it.storageUsed()), contentColor = tipColor)
        }

        setting_screen_secure.setSwitchStatus(SuperPreferences.isScreenSecurityEnabled(this))

        val weakThis = WeakReference(this)
        BcmUpdateUtil.checkUpdate { hasUpdate, _, _ ->
            if (hasUpdate) {
                weakThis.get()?.setting_about?.setTip("", iconRes = R.drawable.common_red_dot_circle)
            } else {
                weakThis.get()?.setting_about?.hideTip()
            }
        }

        privacy_pin_lock.setTip(if (AmePinLogic.hasPin()) getString(R.string.me_setting_pin_on_tip) else getString(R.string.me_setting_pin_off_tip),
                contentColor = getAttrColor(R.attr.common_text_secondary_color))

        setting_rtc_p2p.setSwitchStatus(SuperPreferences.isTurnOnly(this))
    }

    private fun initHeadView() {
        head_view_avatar.showPrivateAvatar(recipient)
        head_view_name.text = recipient.name
        head_view_id.text = "ID: ${recipient.address.serialize()}"
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

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            this.recipient = recipient
            initHeadView()
        }
    }

    override fun onLoginStateChanged() {
        if (!accountContext.isLogin) {
            finish()
        }
    }

    private fun checkBackup() {
        val hadBackup = AmeLoginLogic.accountHistory.getBackupTime(recipient.address.serialize()) > 0
        if (hadBackup) {
            head_view_id.setCompoundDrawables(null, null, null, null)
        } else {
            head_view_id.setDrawableLeft(R.drawable.common_warning_icon)
        }
    }
}