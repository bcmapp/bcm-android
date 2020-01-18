package com.bcm.messenger.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.R
import com.bcm.messenger.adapter.HomeAccountItem
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.qrcode.BcmMyQRCodeActivity
import com.bcm.messenger.me.ui.setting.SettingActivity
import com.bcm.messenger.ui.widget.IProfileView
import com.bcm.messenger.ui.widget.centerPosition
import com.bcm.messenger.ui.widget.leftPosition
import com.bcm.messenger.ui.widget.rightPosition
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.home_profile_view.view.*
import kotlin.math.min

/**
 * Created by Kin on 2019/12/9
 */
class HomeProfileView @JvmOverloads constructor(context: Context,
                                                attrs: AttributeSet? = null,
                                                defStyleAttr: Int = 0)
    : ConstraintLayout(context, attrs, defStyleAttr), RecipientModifiedListener, IProfileView {
    private val TAG = "HomeProfileView"

    interface ProfileViewCallback {
        fun onClickExit()
        fun onClickDelete(uid: String)
        fun onClickLogin(uid: String)
        fun checkPosition(): Float
        fun onClickToSwipe(pagePosition: Int)
    }

    private var listener: ProfileViewCallback? = null

    override var isActive = false
    override var isLogin = false
    override var position = 0f
    override var pagePosition = 0

    private val dp10 = 10.dp2Px()
    private val dp60 = 60.dp2Px()
    private val avatarMargin = (AppContextHolder.APP_CONTEXT.getScreenWidth() - 100.dp2Px()) / 2
    private val badgeMarginEnd = (160.dp2Px() * 0.3f / 2).toInt() - dp10
    private val badgeMarginStart = 150.dp2Px() - badgeMarginEnd - 37.dp2Px()

    private lateinit var recipient: Recipient
    private lateinit var accountItem: HomeAccountItem

    private var isAccountBackup = true
    private var unreadCount = 0

    fun setAccountItem(accountItem: HomeAccountItem) {
        this.accountItem = accountItem
//        recipient.removeListener(this)
        recipient = Recipient.from(accountItem.accountContext, accountItem.account.uid, true)
        recipient.addListener(this)

        isAccountBackup = accountItem.account.backupTime > 0

        setProfile()
    }

    private fun showAnimation() = AnimatorSet().apply {
        val updateListener = ValueAnimator.AnimatorUpdateListener {
            setViewsAlpha(it.animatedValue as Float)
        }
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.addUpdateListener(updateListener)

        play(valueAnimator)

        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                showAllViews()
            }

            override fun onAnimationEnd(animation: Animator?) {
                valueAnimator.removeAllUpdateListeners()
                this@apply.removeAllListeners()
            }
        })
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.home_profile_view, this, true)
    }

    override fun initView() {
        home_profile_unread.setTextSize(17f)

        val iconSize = min(64.dp2Px(), (AppContextHolder.APP_CONTEXT.getScreenWidth() - 184.dp2Px()) / 3)
        home_profile_wallet_icon.layoutParams = home_profile_wallet_icon.layoutParams.apply {
            height = iconSize
            width = iconSize
        }
        home_profile_vault_icon.layoutParams = home_profile_vault_icon.layoutParams.apply {
            height = iconSize
            width = iconSize
        }
        home_profile_air_chat_icon.layoutParams = home_profile_air_chat_icon.layoutParams.apply {
            height = iconSize
            width = iconSize
        }

        home_profile_avatar.setOnClickListener {
            ALog.i(TAG, "Click avatar")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (!isActive) {
                listener?.onClickToSwipe(pagePosition)
                return@setOnClickListener
            }
            if (isLogin) {
                listener?.onClickExit()
            } else {
                listener?.onClickLogin(recipient.address.serialize())
            }
        }
        home_profile_login.setOnClickListener {
            ALog.i(TAG, "Click login")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickLogin(recipient.address.serialize())
        }
        home_profile_delete.setOnClickListener {
            ALog.i(TAG, "Click delete")
            listener?.onClickDelete(recipient.address.serialize())
        }
        home_profile_qr.setOnClickListener {
            ALog.i(TAG, "Click QR")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            it.context.startBcmActivity(accountItem.accountContext, Intent(it.context, BcmMyQRCodeActivity::class.java))
        }
        home_profile_more.setOnClickListener {
            ALog.i(TAG, "Click more button")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            context.startBcmActivity(accountItem.accountContext, Intent(context, SettingActivity::class.java))
        }
        home_profile_wallet_icon.setOnClickListener {
            ALog.i(TAG, "Click wallet")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (!isActive) {
                return@setOnClickListener
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WALLET_MAIN)
                    .startBcmActivity(accountItem.accountContext)
        }
        home_profile_vault_icon.setOnClickListener {
            ALog.i(TAG, "Click data vault")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (!isActive) {
                return@setOnClickListener
            }
            AmeModuleCenter.user(accountItem.accountContext)?.gotoDataNote(context, accountItem.accountContext)
        }
        home_profile_air_chat_icon.setOnClickListener {
            ALog.i(TAG, "Click air chat")
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (!isActive) {
                return@setOnClickListener
            }
            val adhocModule = AmeModuleCenter.adhoc()
            adhocModule.configHocMode(accountItem.accountContext)
        }

        showAllViews()
        showAvatar()
        setViewsAlpha(1f)
    }

    override fun setViewsAlpha(alpha: Float) {
        when {
            !isLogin -> logoutSetAlpha(alpha)
            !isActive -> inactiveSetAlpha(alpha)
            else -> activeSetAlpha(alpha)
        }
    }

    private fun logoutSetAlpha(alpha: Float) {
        if (isActive) {
            home_profile_delete.alpha = alpha
        }
        home_profile_login.alpha = alpha
        if (alpha > 0.3f) {
            home_profile_avatar.alpha = 0.3f
            home_profile_name.alpha = 0.3f
        } else {
            home_profile_avatar.alpha = alpha
            home_profile_name.alpha = alpha
        }
    }

    private fun inactiveSetAlpha(alpha: Float) {
        home_profile_avatar.alpha = alpha
        home_profile_unread.alpha = alpha
    }

    private fun activeSetAlpha(alpha: Float) {
        home_profile_avatar.alpha = 1f
        home_profile_name.alpha = alpha
        home_profile_setting_layout.alpha = alpha
        home_profile_backup_icon.alpha = alpha
        home_profile_unread.alpha = alpha
        home_profile_func_layout.alpha = alpha
    }

    override fun showAllViewsWithAnimation() {
        showAnimation().start()
    }

    override fun showAllViews() {
        when {
            !isLogin -> logoutShowViews()
            !isActive -> inactiveShowViews()
            else -> activeShowViews()
        }
    }

    private fun logoutShowViews() {
        setProfile()

        home_profile_avatar.visibility = View.VISIBLE
        home_profile_delete.visibility = View.VISIBLE
        home_profile_login.visibility = View.VISIBLE
        home_profile_name.visibility = View.VISIBLE

        home_profile_setting_layout.visibility = View.INVISIBLE
        home_profile_func_layout.visibility = View.INVISIBLE
        home_profile_unread.visibility = View.INVISIBLE
        home_profile_backup_icon.visibility = View.INVISIBLE
    }

    private fun inactiveShowViews() {
        setViewsAlpha(0f)
        setProfile()

        home_profile_avatar.visibility = View.VISIBLE
        home_profile_delete.visibility = View.GONE
        home_profile_login.visibility = View.GONE

        home_profile_name.visibility = View.VISIBLE
        home_profile_setting_layout.visibility = View.VISIBLE
        home_profile_func_layout.visibility = View.VISIBLE
        home_profile_unread.visibility = View.VISIBLE
        home_profile_backup_icon.visibility = View.INVISIBLE
    }

    private fun activeShowViews() {
        setProfile()

        home_profile_avatar.visibility = View.INVISIBLE
        home_profile_delete.visibility = View.GONE
        home_profile_login.visibility = View.GONE

        home_profile_name.visibility = View.VISIBLE
        home_profile_setting_layout.visibility = View.VISIBLE
        home_profile_func_layout.visibility = View.VISIBLE
        home_profile_unread.visibility = View.VISIBLE
        if (isAccountBackup) {
            home_profile_backup_icon.visibility = View.GONE
        } else {
            home_profile_backup_icon.visibility = View.VISIBLE
        }
    }

    override fun showAvatar() {
        home_profile_avatar.visibility = View.VISIBLE
    }

    override fun hideAllViews() {
        home_profile_name.visibility = View.GONE
        home_profile_setting_layout.visibility = View.GONE
        home_profile_func_layout.visibility = View.GONE
        home_profile_avatar.visibility = View.GONE
        home_profile_unread.visibility = View.GONE
        home_profile_login.visibility = View.GONE
        home_profile_delete.visibility = View.GONE
    }

    override fun hideAvatar() {
        home_profile_avatar.visibility = View.INVISIBLE
    }

    override fun setMargin(topMargin: Int) {
        home_profile_avatar_layout.layoutParams = (home_profile_avatar_layout.layoutParams as LayoutParams).apply {
            this.topMargin = dp60 + topMargin / 2
        }
        home_profile_func_layout.layoutParams = (home_profile_func_layout.layoutParams as LayoutParams).apply {
            this.bottomMargin = -topMargin / 2
        }
    }

    override fun resetMargin() {
        home_profile_avatar.layoutParams = (home_profile_avatar.layoutParams as LayoutParams).apply {
            marginStart = 0
            marginEnd = 0
        }

        home_profile_avatar_layout.layoutParams = (home_profile_avatar_layout.layoutParams as LayoutParams).apply {
            this.topMargin = dp60
        }
        home_profile_func_layout.layoutParams = (home_profile_func_layout.layoutParams as LayoutParams).apply {
            this.bottomMargin = 0
        }

        if (!isActive) {
            val pos = listener?.checkPosition() ?: 0f
            if (pos < 0f) {
                onViewPositionChanged(leftPosition, 0f)
            } else {
                onViewPositionChanged(rightPosition, 0f)
            }
        } else {
            onViewPositionChanged(centerPosition, 1f)
        }
    }

    override fun setPagerChangedAlpha(alpha: Float) {
        if (isLogin) {
            loginPagerChangedAlpha(alpha)
        } else {
            logoutPagerChangedAlpha(alpha)
        }
    }

    override fun onViewPositionChanged(position: Float, percent: Float) {
        val avatarScale = 0.7f + 0.3f * percent
        val badgeScale = 0.8f + 0.2f * percent
        val curAvatarMargin = avatarMargin * (1 - percent)

        home_profile_avatar.layoutParams = (home_profile_avatar.layoutParams as LayoutParams).apply {
            if (position < centerPosition) {
                marginStart = curAvatarMargin.toInt()
            } else {
                marginEnd = curAvatarMargin.toInt()
            }
        }
        home_profile_avatar.scaleX = avatarScale
        home_profile_avatar.scaleY = avatarScale

        home_profile_unread.layoutParams = (home_profile_unread.layoutParams as LayoutParams).apply {
            marginEnd = if (position < centerPosition) {
                dp10 + (badgeMarginEnd * (1 - percent)).toInt()
            } else {
                dp10 + (badgeMarginStart * (1 - percent)).toInt()
            }
            topMargin = dp10 + (badgeMarginEnd * (1 - percent)).toInt()
        }
        home_profile_unread.scaleX = badgeScale
        home_profile_unread.scaleY = badgeScale

        setPagerChangedAlpha(percent)
    }

    override fun getCurrentContext(): AccountContext? {
        if (!isLogin) {
            return null
        }
        return accountItem.accountContext
    }

    override fun getCurrentRecipient(): Recipient? {
        return if (isLogin) {
            recipient
        } else {
            null
        }
    }

    override fun checkAccountBackup() {
        val backupTime = AmeLoginLogic.accountHistory.getBackupTime(accountItem.accountContext.uid)
        isAccountBackup = backupTime > 0
        if (!isAccountBackup) {
            if (isLogin) {
                home_profile_backup_icon.visibility = View.VISIBLE
            }
        } else {
            home_profile_backup_icon.visibility = View.GONE
            accountItem.account.backupTime = backupTime
        }
    }

    override fun setUnreadCount(unreadCount: Int) {
        this.unreadCount = unreadCount
        home_profile_unread.unreadCount = unreadCount
    }

    private fun logoutPagerChangedAlpha(alpha: Float) {
        if (alpha > 0.3f) {
            home_profile_name.alpha = 0.3f
        } else {
            home_profile_name.alpha = alpha
        }

        home_profile_delete.alpha = alpha
        home_profile_login.alpha = alpha
    }

    private fun loginPagerChangedAlpha(alpha: Float) {
        home_profile_name.alpha = alpha
        home_profile_setting_layout.alpha = alpha
        home_profile_func_layout.alpha = alpha
    }

    fun setListener(listener: ProfileViewCallback) {
        this.listener = listener
    }

    override fun onDetachedFromWindow() {
        recipient.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            this.recipient = recipient
            setProfile()
        }
    }

    private fun setProfile() {
        if (isLogin) {
            home_profile_avatar.setPhoto(recipient)
            home_profile_name.text = recipient.name
        } else {
            val account = accountItem.account
            home_profile_avatar.setPhoto(account.uid, account.name, account.avatar)
            home_profile_name.text = account.name
        }
    }
}