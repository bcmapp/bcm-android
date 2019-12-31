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
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.accountmodule.IAdHocModule
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.me.ui.scan.NewScanActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.home_profile_layout.view.*

/**
 * Created by Kin on 2019/12/9
 */
class HomeProfileView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val TAG = "MessageProfileView"

    interface ClickListener {
        fun onClickExit()
    }

    private val statusBarHeight = context.getStatusBarHeight()
    private var listener: ClickListener? = null
    var recipient = Recipient.self()
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

    private fun hideAnimation() = AnimatorSet().apply {
        val updateListener = ValueAnimator.AnimatorUpdateListener {
            setViewsAlpha(it.animatedValue as Float)
        }
        val valueAnimator = ValueAnimator.ofFloat(1f, 0f)
        valueAnimator.addUpdateListener(updateListener)

        play(valueAnimator)

        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                hideAllViews()
                valueAnimator.removeAllUpdateListeners()
                this@apply.removeAllListeners()
            }
        })
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.home_profile_layout, this, true)

        initView()
    }

    private fun initView() {
        setBackgroundColor(getColor(R.color.common_color_white))

        home_profile_unread.setTextSize(17f)
        hideAllViews()

        home_profile_status_fill.layoutParams = home_profile_status_fill.layoutParams.apply {
            height = statusBarHeight
        }
        home_profile_exit.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickExit()
        }

        home_profile_avatar.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickExit()
        }
        home_profile_name.setOnClickListener {
            // Do nothing.
        }
        home_profile_qr.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            it.context.startActivity(Intent(it.context, NewScanActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_CONTACT)
                putExtra(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, true)
            })
        }
        home_profile_more.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.PROFILE_EDIT)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, Address.from(context, AMESelfData.uid))
                    .navigation(context)
        }
        home_profile_wallet.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WALLET_MAIN)
                    .navigation(context)
        }
        home_profile_data_vault.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val meProvider = AmeProvider.get<IUserModule>(ARouterConstants.Provider.PROVIDER_USER_BASE)
            meProvider?.gotoDataNote(context)
        }
        home_profile_settings.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.SETTINGS)
                    .navigation(context)
        }
        home_profile_air_chat.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val adhocProvider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
            adhocProvider?.configHocMode()
        }
        home_profile_keybox.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).navigation(context)
        }
    }

    fun setViewsAlpha(alpha: Float) {
        home_profile_exit.alpha = alpha
        home_profile_name.alpha = alpha
        home_profile_qr.alpha = alpha
        home_profile_more.alpha = alpha
        home_profile_wallet.alpha = alpha
        home_profile_data_vault.alpha = alpha
        home_profile_settings.alpha = alpha
        home_profile_air_chat.alpha = alpha
        home_profile_keybox.alpha = alpha
        home_profile_backup_icon.alpha = alpha
        home_profile_unread.alpha = alpha
    }

    fun showAllViewsWithAnimation() {
        showAnimation().start()
    }

    fun showAllViews() {
        home_profile_exit.visibility = View.VISIBLE
        home_profile_name.visibility = View.VISIBLE
        home_profile_qr.visibility = View.VISIBLE
        home_profile_more.visibility = View.VISIBLE
        home_profile_wallet.visibility = View.VISIBLE
        home_profile_data_vault.visibility = View.VISIBLE
        home_profile_settings.visibility = View.VISIBLE
        home_profile_air_chat.visibility = View.VISIBLE
        home_profile_keybox.visibility = View.VISIBLE
        home_profile_avatar.visibility = View.INVISIBLE
        home_profile_unread.visibility = View.VISIBLE
        if (isAccountBackup) {
            home_profile_backup_icon.visibility = View.GONE
        } else {
            home_profile_backup_icon.visibility = View.VISIBLE
        }
        home_profile_avatar.showPrivateAvatar(recipient)
    }

    fun showAvatar() {
        home_profile_avatar.visibility = View.VISIBLE
    }

    fun hideAllViewsWithAnimation() {
        hideAnimation().start()
    }

    fun hideAllViews() {
        home_profile_exit.visibility = View.GONE
        home_profile_name.visibility = View.GONE
        home_profile_qr.visibility = View.GONE
        home_profile_more.visibility = View.GONE
        home_profile_wallet.visibility = View.GONE
        home_profile_data_vault.visibility = View.GONE
        home_profile_settings.visibility = View.GONE
        home_profile_air_chat.visibility = View.GONE
        home_profile_keybox.visibility = View.GONE
        home_profile_backup_icon.visibility = View.GONE
        home_profile_avatar.visibility = View.GONE
        home_profile_unread.visibility = View.GONE
    }

    fun hideAvatar() {
        home_profile_avatar.visibility = View.INVISIBLE
    }

    fun setMargin(topMargin: Int) {
        home_profile_avatar_layout.layoutParams = (home_profile_avatar_layout.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(0, 60.dp2Px() + topMargin / 2, 0, 0)
        }
    }

    fun resetMargin() {
        home_profile_avatar_layout.layoutParams = (home_profile_avatar_layout.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(0, 60.dp2Px(), 0, 0)
        }
    }

    fun setListener(listener: ClickListener) {
        this.listener = listener
    }
}