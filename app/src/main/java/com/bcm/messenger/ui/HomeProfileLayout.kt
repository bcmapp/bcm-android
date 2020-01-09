package com.bcm.messenger.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.R
import com.bcm.messenger.adapter.HomeAccountAdapter
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.ui.widget.HomeProfilePageTransformer
import com.bcm.messenger.ui.widget.HomeViewPager
import com.bcm.messenger.ui.widget.IProfileView
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.home_profile_layout.view.*

/**
 * Created by Kin on 2019/12/30
 */
class HomeProfileLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val TAG = "HomeProfileView"

    interface HomeProfileListener {
        fun onClickExit()
        fun onDragVertically(ev: MotionEvent?): Boolean
        fun onInterceptEvent(ev: MotionEvent?): Boolean
        fun onViewPagerScrollStateChanged(newState: Int)
        fun onViewChanged(newRecipient: Recipient?)
    }

    private val statusBarHeight = context.getStatusBarHeight()
    private var listener: HomeProfileListener? = null
    private val viewPagerAdapter = HomeAccountAdapter(context)
    private var shownViews = emptyList<IProfileView>()

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

        viewPagerAdapter.listener = object : HomeAccountAdapter.AdapterListener {
            override fun onViewClickedClose() {
                listener?.onClickExit()
            }

            override fun onAccountLoadSuccess() {
            }

            override fun onResortSuccess() {
                home_profile_view_pager.setCurrentItem(1, false)
//                viewPagerAdapter.setActiveView(1)
            }

            override fun onViewClickLogin(uid: String) {
                loginAccount(uid)
            }

            override fun onViewClickDelete(uid: String) {
                deleteAccount(uid)
            }
        }
        home_profile_view_pager.adapter = viewPagerAdapter
        home_profile_view_pager.setPageTransformer(false, HomeProfilePageTransformer())
        home_profile_view_pager.offscreenPageLimit = 3
        home_profile_view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                listener?.onViewPagerScrollStateChanged(state)
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                viewPagerAdapter.setActiveView(position)
                shownViews = viewPagerAdapter.getCurrentShownViews()
                listener?.onViewChanged(viewPagerAdapter.getCurrentView(position)?.getCurrentRecipient())
            }
        })
        home_profile_view_pager.listener = object : HomeViewPager.DragListener {
            override fun onDragVertically(ev: MotionEvent?): Boolean {
                return listener?.onDragVertically(ev) ?: false
            }

            override fun onInterceptEvent(ev: MotionEvent?): Boolean {
                return listener?.onInterceptEvent(ev) ?: false
            }
        }
    }

    fun setViewsAlpha(alpha: Float) {
        home_profile_exit.alpha = alpha
        shownViews.forEach {
            it.setViewsAlpha(alpha)
        }
    }

    fun showAllViewsWithAnimation() {
        showAnimation().start()
        shownViews.forEach {
            it.showAllViewsWithAnimation()
        }
    }

    fun showAllViews() {
        home_profile_exit.visibility = View.VISIBLE
    }

    fun showAvatar() {
        viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.showAvatar()
    }

    fun hideAllViewsWithAnimation() {
        hideAnimation().start()
    }

    fun hideAllViews() {
        home_profile_exit.visibility = View.GONE
        shownViews.forEach {
            it.hideAllViews()
        }
    }

    fun hideAvatar() {
        viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.hideAvatar()
    }

    fun setMargin(topMargin: Int) {
        viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.setMargin(topMargin)
    }

    fun resetMargin() {
        shownViews.forEach {
            it.resetMargin()
        }
    }

    fun setListener(listener: HomeProfileListener) {
        this.listener = listener
    }

    fun checkCurrentPage(uid: String) {
        val currentView = viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)
        if (currentView != null && !currentView.isLogin) {
            val index = viewPagerAdapter.checkIndex(uid)
            if (index != -1) {
                home_profile_view_pager.setCurrentItem(index, false)
                viewPagerAdapter.setActiveView(index)
            }
        }
    }

    fun analyseQrCode(data: Intent?, toLogin: Boolean) {
        val qrCode = data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT) ?: return
        AmeLoginLogic.saveBackupFromExportModelWithWarning(qrCode) { accountId ->
            if (!accountId.isNullOrEmpty()){
                viewPagerAdapter.loadAccounts()
                if (toLogin) {
                    loginAccount(accountId)
                }
            }
        }
    }

    fun getCloseAccount(): AccountContext? {
        return viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.getCurrentContext()
    }

    fun checkAccountLogin(): Boolean {
        if (shownViews.isEmpty()) {
            home_profile_view_pager.setCurrentItem(1, false)
            viewPagerAdapter.setActiveView(1)
        }
        return viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.isLogin ?: false
    }

    fun reSortAccountList() {
        viewPagerAdapter.reSortAccounts()
    }

    fun reloadAccountList() {
        viewPagerAdapter.loadAccounts()
    }

    fun checkBackupStatus() {
        shownViews.forEach {
            it.checkAccountBackup()
        }
    }

    private fun deleteAccount(uid: String) {
        AlertDialog.Builder(context)
                .setMessage(R.string.tabless_ui_delete_account_content)
                .setPositiveButton(
                        StringAppearanceUtil.applyAppearance(getString(R.string.tabless_ui_delete_account_str_remote),
                                color = getColor(R.color.common_color_ff3737))) { _, _ ->
                    viewPagerAdapter.deleteAccount(uid)
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
    }

    private fun loginAccount(uid: String) {
        if (!checkOnlineAccount()) {
            return
        }
        val intent = Intent(context, VerifyKeyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(VerifyKeyActivity.BACKUP_JUMP_ACTION, VerifyKeyActivity.LOGIN_PROFILE)
            putExtra(RegistrationActivity.RE_LOGIN_ID, uid)
        }
        context.startBcmActivity(AmeLoginLogic.getAccountContext(uid), intent)
    }

    private fun checkOnlineAccount(): Boolean {
        if (AmeModuleCenter.login().minorUidList().size == 2) {
            AmePopup.result.notice((context as AccountSwipeBaseActivity), getString(R.string.tabless_ui_account_reach_max_size))
            return false
        }
        return true
    }
}