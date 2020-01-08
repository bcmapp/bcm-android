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
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.me.ui.scan.NewScanActivity
import com.bcm.messenger.me.ui.setting.SettingActivity
import com.bcm.messenger.ui.widget.IProfileView
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
    private val TAG = "MessageProfileView"

    interface ProfileViewCallback {
        fun onClickExit()
        fun onClickDelete(uid: String)
        fun onClickLogin(uid: String)
        fun checkPosition(): Float
    }

    private var listener: ProfileViewCallback? = null

    override var isActive = false
    override var isLogin = false
    override var position = 0f

    private val dp10 = 10.dp2Px()
    private val avatarMargin = (AppContextHolder.APP_CONTEXT.getScreenWidth() - 120.dp2Px()) / 2
    private val badgeMarginEnd = (160.dp2Px() * 0.3f / 2).toInt()
    private val badgeMarginStart = 150.dp2Px() - badgeMarginEnd - 27.dp2Px()

    private lateinit var recipient: Recipient
    private lateinit var accountItem: HomeAccountItem

    var isAccountBackup = true
        set(value) {
            field = value
            if (home_profile_more.visibility == View.VISIBLE && !value) {
                home_profile_backup_icon.visibility = View.VISIBLE
            } else {
                home_profile_backup_icon.visibility = View.GONE
            }
        }

    var chatUnread = 0
        set(value) {
            if (field != value) {
                field = value
                updateHomeUnread(field + friendReqUnread)
            }
        }

    var friendReqUnread = 0
        set(value) {
            if (field != value) {
                field = value
                updateHomeUnread(field + chatUnread)
            }
        }

    fun setAccountItem(accountItem: HomeAccountItem) {
        recipient.removeListener(this)
        recipient = Recipient.from(accountItem.accountContext, accountItem.account.uid, true)
        recipient.addListener(this)

        setProfile()
    }

    private fun updateHomeUnread(unread: Int) {
        ALog.i(TAG, "updateHomeUnread: $unread")
        home_profile_unread.unreadCount = unread
        AmePushProcess.updateAppBadge(AppContextHolder.APP_CONTEXT, unread)
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
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (isLogin) {
                listener?.onClickExit()
            } else {
                listener?.onClickLogin(recipient.address.serialize())
            }
        }
        home_profile_delete.setOnClickListener {
            listener?.onClickDelete(recipient.address.serialize())
        }
        home_profile_qr.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            it.context.startBcmActivity(accountItem.accountContext, Intent(it.context, NewScanActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_CONTACT)
                putExtra(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, true)
            })
        }
        home_profile_more.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            context.startBcmActivity(accountItem.accountContext, Intent(context, SettingActivity::class.java))
        }
        home_profile_wallet_icon.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WALLET_MAIN)
                    .startBcmActivity(accountItem.accountContext)
        }
        home_profile_vault_icon.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmeModuleCenter.user(accountItem.accountContext)?.gotoDataNote(context, accountItem.accountContext)
        }
        home_profile_air_chat_icon.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val adhocModule = AmeModuleCenter.adhoc(accountItem.accountContext)
            adhocModule?.configHocMode()
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
        ALog.i(TAG, "logout set alpha = $alpha")
        ALog.i(TAG, "logout avatar visibility = ${home_profile_avatar.visibility}")
        home_profile_delete.alpha = alpha
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
        ALog.i(TAG, "inactive set alpha = $alpha")
        ALog.i(TAG, "inactive avatar visibility = ${home_profile_avatar.visibility}")
        home_profile_avatar.alpha = alpha
    }

    private fun activeSetAlpha(alpha: Float) {
        ALog.i(TAG, "active set alpha = $alpha")
        ALog.i(TAG, "active avatar visibility = ${home_profile_avatar.visibility}")
        home_profile_avatar.alpha = 1f
        home_profile_name.alpha = alpha
        home_profile_qr.alpha = alpha
        home_profile_more.alpha = alpha
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
        home_profile_avatar.showPrivateAvatar(recipient)

        home_profile_avatar.visibility = View.VISIBLE
        home_profile_delete.visibility = View.VISIBLE
        home_profile_login.visibility = View.VISIBLE
        home_profile_name.visibility = View.VISIBLE

        home_profile_qr.visibility = View.INVISIBLE
        home_profile_more.visibility = View.INVISIBLE
        home_profile_func_layout.visibility = View.INVISIBLE
        home_profile_unread.visibility = View.INVISIBLE
        home_profile_backup_icon.visibility = View.INVISIBLE
    }

    private fun inactiveShowViews() {
        setViewsAlpha(0f)
        home_profile_avatar.showPrivateAvatar(recipient)

        home_profile_avatar.visibility = View.VISIBLE
        home_profile_delete.visibility = View.GONE
        home_profile_login.visibility = View.GONE

        home_profile_name.visibility = View.VISIBLE
        home_profile_qr.visibility = View.VISIBLE
        home_profile_more.visibility = View.VISIBLE
        home_profile_func_layout.visibility = View.VISIBLE
        home_profile_unread.visibility = View.VISIBLE
        home_profile_backup_icon.visibility = View.INVISIBLE
    }

    private fun activeShowViews() {
        home_profile_avatar.showPrivateAvatar(recipient)

        home_profile_avatar.visibility = View.INVISIBLE
        home_profile_delete.visibility = View.GONE
        home_profile_login.visibility = View.GONE

        home_profile_name.visibility = View.VISIBLE
        home_profile_qr.visibility = View.VISIBLE
        home_profile_more.visibility = View.VISIBLE
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
        home_profile_qr.visibility = View.GONE
        home_profile_more.visibility = View.GONE
        home_profile_func_layout.visibility = View.GONE
        home_profile_backup_icon.visibility = View.GONE
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
            setMargins(0, 60.dp2Px() + topMargin / 2, 0, 0)
        }
    }

    override fun resetMargin() {
        home_profile_avatar_layout.layoutParams = (home_profile_avatar_layout.layoutParams as LayoutParams).apply {
            marginStart = 0
            marginEnd = 0
            setMargins(0, 60.dp2Px(), 0, 0)
        }
        home_profile_avatar.layoutParams = (home_profile_avatar.layoutParams as LayoutParams).apply {
            marginStart = 0
            marginEnd = 0
        }
        if (!isActive) {
            val pos = listener?.checkPosition() ?: 0f
            if (pos < 0f) {
                onViewPositionChanged(-1f, 0f)
            } else {
                onViewPositionChanged(1f, 0f)
            }
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
        ALog.i(TAG, "Position Changed, position = $position, uid = ${recipient.address.serialize()}")
        val scale = 0.7f + 0.3f * percent
        val curAvatarMargin = avatarMargin * (1 - percent)

        home_profile_avatar.layoutParams = (home_profile_avatar.layoutParams as LayoutParams).apply {
            if (position < 0) {
                marginStart = curAvatarMargin.toInt()
            } else {
                marginEnd = curAvatarMargin.toInt()
            }
        }
        home_profile_avatar.scaleX = scale
        home_profile_avatar.scaleY = scale

        home_profile_unread.layoutParams = (home_profile_unread.layoutParams as LayoutParams).apply {
            marginEnd = if (position < 0) {
                dp10 + (badgeMarginEnd * (1 - percent)).toInt()
            } else {
                dp10 + (badgeMarginStart * (1 - percent)).toInt()
            }
            setMargins(leftMargin, dp10 + (badgeMarginEnd * (1 - percent)).toInt(), marginEnd, bottomMargin)
        }

        setPagerChangedAlpha(percent)
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
        home_profile_qr.alpha = alpha
        home_profile_more.alpha = alpha
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
        home_profile_avatar.showPrivateAvatar(recipient)
        home_profile_name.text = recipient.name
    }
}