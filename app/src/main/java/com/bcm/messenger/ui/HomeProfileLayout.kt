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
import com.bcm.messenger.common.event.AccountLogoutEvent
import com.bcm.messenger.common.event.NewAccountAddedEvent
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.ui.widget.HomeProfilePageTransformer
import com.bcm.messenger.ui.widget.HomeViewPager
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.home_profile_layout.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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

    private var toScrollUid = ""
    private var isStop = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    fun onResume() {
        isStop = false
        if (toScrollUid.isNotEmpty()) {
            scrollToSpecificUser()
        }
    }

    fun onStop() {
        isStop = true
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
                if (viewPagerAdapter.accountListIsEmpty()) {
                    viewPagerAdapter.setActiveView(0)
                    home_profile_view_pager.setCurrentItem(0, false)
                } else {
                    viewPagerAdapter.setActiveView(1)
                    home_profile_view_pager.setCurrentItem(1, false)
                }
            }

            override fun onReloadSuccess() {
                if (toScrollUid.isNotEmpty()) {
                    scrollToSpecificUser()
                }
            }

            override fun onResortSuccess() {
                viewPagerAdapter.setActiveView(1)
                home_profile_view_pager.setCurrentItem(1, false)
            }

            override fun onViewClickLogin(uid: String) {
                loginAccount(uid)
            }

            override fun onViewClickDelete(uid: String) {
                deleteAccount(uid)
            }

            override fun onClickToSwipe(pagePosition: Int) {
                val currentPos = home_profile_view_pager.currentItem
                if (pagePosition > currentPos) {
                    home_profile_view_pager.currentItem = currentPos + 1
                } else if (pagePosition < currentPos) {
                    home_profile_view_pager.currentItem = currentPos - 1
                }
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
        viewPagerAdapter.views.values.forEach {
            it.setViewsAlpha(alpha)
        }
    }

    fun showAllViewsWithAnimation() {
        showAnimation().start()
        viewPagerAdapter.views.values.forEach {
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
        viewPagerAdapter.views.values.forEach {
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
        viewPagerAdapter.views.values.forEach {
            it.resetMargin()
        }
    }

    fun setListener(listener: HomeProfileListener) {
        this.listener = listener
    }

    fun checkCurrentPage(uid: String) {
        ALog.i(TAG, "Check current page is major user.")
        val currentView = viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)
        if (currentView != null && !currentView.isLogin) {
            val index = viewPagerAdapter.checkIndex(uid)
            if (index != -1) {
                viewPagerAdapter.setActiveView(index)
                home_profile_view_pager.setCurrentItem(index, false)
            }
        }
    }

    fun analyseQrCode(data: Intent?, toLogin: Boolean) {
        ALog.i(TAG, "Analyse QR code")
        val qrCode = data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT) ?: return
        AmeLoginLogic.saveBackupFromExportModelWithWarning(qrCode) { accountId ->
            if (!accountId.isNullOrEmpty()){
                ALog.i(TAG, "Has QR code")
                val index = viewPagerAdapter.checkIndex(accountId)
                toScrollUid = accountId
                if (index == -1) {
                    reloadAccountList()
                    if (toLogin && !AMELogin.accountContext(accountId).isLogin) {
                        loginAccount(accountId)
                    }
                } else {
                    scrollToSpecificUser()
                }
            }
        }
    }

    fun resetPosition() {
        viewPagerAdapter.setActiveView(1)
        home_profile_view_pager.setCurrentItem(1, false)
    }

    fun getCloseAccount(): AccountContext? {
        return viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.getCurrentContext()
    }

    fun checkAccountLogin(): Boolean {
        return viewPagerAdapter.getCurrentView(home_profile_view_pager.currentItem)?.isLogin ?: false
    }

    fun resortAccountList() {
        viewPagerAdapter.resortAccounts()
    }

    fun reloadAccountList() {
        viewPagerAdapter.loadAccounts()
    }

    fun checkBackupStatus() {
        viewPagerAdapter.views.values.forEach {
            it.checkAccountBackup()
        }
    }

    fun setUnreadCount(accountContext: AccountContext, unreadCount: Int) {
        viewPagerAdapter.views.values.forEach {
            if (it.getCurrentContext() == accountContext) {
                it.setUnreadCount(unreadCount)
            }
        }
    }

    private fun deleteAccount(uid: String) {
        ALog.i(TAG, "Clicked delete account")
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
        ALog.i(TAG, "Clicked login account")
        if (!checkOnlineAccount()) {
            return
        }
        val intent = Intent(context, VerifyKeyActivity::class.java).apply {
            putExtra(RegistrationActivity.RE_LOGIN_ID, uid)
        }
        context.startBcmActivity(AmeLoginLogic.getAccountContext(uid), intent)
    }

    private fun checkOnlineAccount(): Boolean {
        if (AmeModuleCenter.login().minorUidList().size == 2) {
            ALog.i(TAG, "Current has 3 users online, cannot login or create account.")
            AlertDialog.Builder(context)
                    .setMessage(R.string.tabless_ui_account_reach_max_size)
                    .setPositiveButton(R.string.common_understood, null)
                    .show()
            return false
        }
        return true
    }

    private fun scrollToSpecificUser() {
        val index = viewPagerAdapter.checkIndex(toScrollUid)
        if (index > 0) {
            ALog.i(TAG, "Scroll to index $index")
            viewPagerAdapter.setActiveView(index)
            if (!isStop) {
                home_profile_view_pager.setCurrentItem(index, false)
                toScrollUid = ""
                viewPagerAdapter.notifyDataSetChanged()
                listener?.onViewChanged(viewPagerAdapter.getCurrentView(index)?.getCurrentRecipient())
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: NewAccountAddedEvent) {
        ALog.i(TAG, "On event new account add")
        toScrollUid = event.newAccountUid
        val ac = AMELogin.accountContext(event.newAccountUid)
        if (!ac.isLogin) {
            reloadAccountList()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: AccountLogoutEvent) {
        toScrollUid = event.accountUid
    }
}