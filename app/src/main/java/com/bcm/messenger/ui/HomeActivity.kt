package com.bcm.messenger.ui

import android.animation.*
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.bcm.messenger.R
import com.bcm.messenger.chats.NewChatActivity
import com.bcm.messenger.chats.thread.MessageListFragment
import com.bcm.messenger.chats.thread.MessageListTitleView
import com.bcm.messenger.chats.thread.MessageListUnreadObserver
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.BcmRecyclerView
import com.bcm.messenger.common.ui.CommonShareView
import com.bcm.messenger.common.ui.ConstraintPullDownLayout
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.utils.pixel.PixelManager
import com.bcm.messenger.logic.SchemeLaunchHelper
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.pinlock.PinLockInitActivity
import com.bcm.messenger.me.ui.pinlock.PinLockSettingActivity
import com.bcm.messenger.me.ui.scan.NewScanActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.home_profile_layout.*
import java.util.concurrent.TimeUnit

/**
 * home
 * Created by zjl on 2018/2/27.
 */
@Route(routePath = ARouterConstants.Activity.APP_HOME_PATH)
class HomeActivity : AccountSwipeBaseActivity() {
    companion object {
        private const val TAG = "HomeActivity"

        const val REQ_SCAN_ACCOUNT = 1001
        const val REQ_SCAN_LOGIN = 1002
    }

    private val mLaunchHelper = SchemeLaunchHelper(this)
    private var checkDisposable: Disposable? = null
    private var checkStartTime = System.currentTimeMillis()
    private var mPixelManager: PixelManager? = null

    private lateinit var titleView: MessageListTitleView
    private var mWaitForShortLink: Boolean = false //是否等待短链生成
    private var messageListFragment: MessageListFragment? = null

    private val unreadObserver = MessageListUnreadObserver()
    private val unreadMap = mutableMapOf<AccountContext, MessageListUnreadObserver.UnreadCountEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (accountContext != AMELogin.majorContext) {
            setAccountContext(AMELogin.majorContext)
        }

        setSwipeBackEnable(false)

        setContentView(R.layout.activity_home)

        checkShowIntroPage()

        if (null != savedInstanceState) {
            recycleFragments()
        }
        initView()
        initPullDownView()
        initRecipientData()

        AmeModuleCenter.metric(accountContext)?.launchEnd()

        BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, _ ->
            if (hasUpdate) {
                if (forceUpdate) {
                    BcmUpdateUtil.showForceUpdateDialog()
                } else {
                    BcmUpdateUtil.showUpdateDialog()
                }
            }
        }

        initUnreadObserver()

        checkSchemeLaunch()

        initClipboardUtil()
        checkAdHocMode()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_SCAN_ACCOUNT -> home_profile_layout.analyseQrCode(data, false)
            REQ_SCAN_LOGIN -> home_profile_layout.analyseQrCode(data, true)
        }
    }

    private fun checkAdHocMode(): Boolean {
        val adHocMainTag = "adhoc_main"
        val adHocMainFragment = supportFragmentManager.findFragmentByTag(adHocMainTag)


        val adhocMode = AmeModuleCenter.adhoc().isAdHocMode()
        if (adhocMode) {
            if (null != adHocMainFragment) {
                return true
            }

            AmeModuleCenter.adhoc().initModule()
            val addFragment = supportFragmentManager.beginTransaction()

            val adhocUid = AmeModuleCenter.login().getAdHocUid()
            val adhocContext = AmeModuleCenter.login().getAccountContext(adhocUid)
            val adhocMain = BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_HOME_AD_HOC_MAIN)
                    .navigation() as Fragment

            adhocMain.arguments = Bundle().apply {
                putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, adhocContext)
            }
            addFragment.add(R.id.home_adhoc_main, adhocMain, adHocMainTag)
            addFragment.commit()

            home_adhoc_main.visibility = View.VISIBLE

            AmeModuleCenter.serverDaemon(accountContext).stopDaemon()
            AmeModuleCenter.serverDaemon(accountContext).stopConnection()

            mPixelManager = PixelManager.Builder().target(PixelActivity::class.java).build()
            mPixelManager?.start(AppContextHolder.APP_CONTEXT)

            return true
        } else {
            AmeModuleCenter.adhoc().uninitModule()

            if (null != adHocMainFragment) {
                val removeFragment = supportFragmentManager.beginTransaction()
                removeFragment.remove(adHocMainFragment).commitNow()
            }

            home_adhoc_main.visibility = View.GONE

            AmeModuleCenter.serverDaemon(accountContext).startDaemon()
            AmeModuleCenter.serverDaemon(accountContext).checkConnection(true)

            mPixelManager?.quit(AppContextHolder.APP_CONTEXT)
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(TAG)
        ClipboardUtil.unInitClipboardUtil()
        unreadObserver.unInit()
        titleView.unInit()
    }

    override fun onResume() {
        super.onResume()
        home_profile_layout.onResume()
        titleView.update()

        checkPinUpgrade()

        if (!checkAdHocMode()) {
            checkClipboardDelay()
            if (SchemeLaunchHelper.hasIntent()) {
                checkSchemeLaunch()
            }
            checkBackupNotice()
        }

        // check need fetch profile or avatar
        if (accountRecipient.needRefreshProfile()) {
            AmeModuleCenter.contact(accountContext)?.checkNeedFetchProfileAndIdentity(accountRecipient, callback = null)
        } else {
            AmeModuleCenter.contact(accountContext)?.checkNeedDownloadAvatarWithAll(accountRecipient)
        }
        unreadObserver.onResume()
    }

    override fun onStop() {
        super.onStop()
        home_profile_layout.onStop()
    }

    override fun onPause() {
        super.onPause()
        if (System.currentTimeMillis() - checkStartTime < 1000) {
            ALog.i(TAG, "HomeActivity onPause, Start check clipboard time to pause time less then 1 seconds, means have pin lock, canceled check")
            checkDisposable?.dispose()
        }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        intent = newIntent

        if (AmeModuleCenter.user(accountContext)?.isPinLocked() == true) {
            AmeModuleCenter.user(accountContext)?.showPinLock()
            SchemeLaunchHelper.storeSchemeIntent(newIntent)
        } else {
            //查看系统banner消息是否存在
            AmePushProcess.checkSystemBannerNotice()
            //拉取系统消息
            PushUtil.loadSystemMessages(accountContext)

            checkSchemeLaunch()
        }
    }

    /**
     * Check called by other apps
     */
    private fun checkSchemeLaunch() {
        val schemeLaunchIntent = SchemeLaunchHelper.pullOutLaunchIntent()
        if (schemeLaunchIntent != null) {
            ALog.i(TAG, "Found unhandled outLaunch intent, continue handling")
            mLaunchHelper.route(accountContext, schemeLaunchIntent)
        } else {
            ALog.i(TAG, "Try handle current intent，check called by other app")
            mLaunchHelper.route(accountContext, intent)
        }
    }

    private fun initView() {
        titleView = home_toolbar_name as MessageListTitleView
        titleView.init()
        titleView.updateContext(accountContext)

        home_toolbar_name.setOnClickListener(MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
            override fun onMultiClick(view: View?, count: Int) {
                messageListFragment?.clearThreadUnreadState()
            }
        }))
        home_toolbar_more.setOnClickListener {
            showMenu()
        }
        home_toolbar_chat.setOnClickListener {
            startBcmActivity(Intent(this, NewChatActivity::class.java))
        }

        messageListFragment = MessageListFragment()
        messageListFragment?.callback = object : MessageListFragment.MessageListCallback {
            override fun onRecyclerViewCreated(recyclerView: BcmRecyclerView) {
                home_pull_down_layout.setScrollView(recyclerView)
            }

            override fun onClickInvite() {
                doInvite(accountRecipient)
            }
        }

        messageListFragment?.let {
            initFragment(R.id.home_chats_main, it, it.arguments)
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val adhocMode = AmeModuleCenter.adhoc().isAdHocMode()
            if (home_pull_down_layout.isTopViewExpanded() && !adhocMode) {
                home_pull_down_layout.closeTopViewAndCallback()
                return true
            }

            if (supportFragmentManager.backStackEntryCount != 0) {
                supportFragmentManager.popBackStack()
            } else {
                moveTaskToBack(true)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initRecipientData() {
        updateRecipientData(accountRecipient)
        checkBackupNotice()
    }

    private fun updateRecipientData(recipient: Recipient) {
        home_toolbar_avatar.showPrivateAvatar(recipient)
    }

    private fun initPullDownView() {
        val statusBarHeight = getStatusBarHeight()
        val screenWidth = getScreenWidth()

        val topViewHeight = if (checkDeviceHasNavigationBar()) {
            getRealScreenHeight() - getNavigationBarHeight()
        } else {
            getRealScreenHeight()
        }

        home_toolbar.layoutParams = home_toolbar.layoutParams.apply {
            height = 60.dp2Px()
        }
        home_status_fill.layoutParams = home_status_fill.layoutParams.apply {
            height = statusBarHeight
        }
        home_profile_top_line.setGuidelineBegin(statusBarHeight - 8.dp2Px())
        home_pull_down_layout.setInterceptTouch()
        home_pull_down_layout.setReboundSize(75.dp2Px())
        home_pull_down_layout.setTopViewSize(topViewHeight, 0)

        val callback = object : ConstraintPullDownLayout.PullDownLayoutCallback() {
            private fun hideAnimation() = AnimatorSet().apply {
                val updateListener = ValueAnimator.AnimatorUpdateListener {
                    home_profile_bg.alpha = it.animatedValue as Float
                }
                val valueAnimator = ValueAnimator.ofFloat(1f, 0f)
                valueAnimator.addUpdateListener(updateListener)

                play(valueAnimator)

                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        valueAnimator.removeAllUpdateListeners()
                        this@apply.removeAllListeners()
                    }
                })
            }

            private val dp40 = 40.dp2Px()
            private val dp15 = 15.dp2Px()
            private val dp10 = 10.dp2Px()
            private val dp120 = 120.dp2Px()
            private val dp75 = 75.dp2Px()
            private val dp6 = 6.dp2Px()
            private val gapSize = (topViewHeight - statusBarHeight - 446.dp2Px()) / 3 + 30.dp2Px()

            private val avatarMaxSize = 160.dp2Px()
            private val avatarStartMaxMargin = (screenWidth - avatarMaxSize) / 2 - dp15

            private var checked = false
            private var isCurrentLogin = true

            override fun onTopViewHeightChanged(height: Int, direction: Int) {
                val percentage = height.toFloat() / topViewHeight
                val distance = if (direction == ConstraintPullDownLayout.MOVE_DOWN) {
                    height
                } else {
                    topViewHeight - height
                }
                var alpha = distance.toFloat() / dp75
                alpha = when {
                    alpha > 1f -> 1f
                    alpha < 0f -> 0f
                    else -> alpha
                }

                val avatarSize = (dp120.toFloat() * percentage).toInt() + dp40
                val marginStart = dp15 + (avatarStartMaxMargin.toFloat() * percentage).toInt()
                val marginTop = (gapSize.toFloat() * percentage).toInt() + dp10

                if (!checked) {
                    isCurrentLogin = home_profile_layout.checkAccountLogin()
                }

                home_toolbar_avatar.layoutParams = (home_toolbar_avatar.layoutParams as ConstraintLayout.LayoutParams).apply {
                    if (direction == ConstraintPullDownLayout.MOVE_DOWN || isCurrentLogin) {
                        this.marginStart = marginStart
                        this.topMargin = marginTop
                        this.width = avatarSize
                        this.height = avatarSize
                    } else {
                        this.marginStart = dp15
                        this.topMargin = dp10
                        this.width = dp40
                        this.height = dp40
                    }
                }
                home_toolbar_avatar.updateOval()

                if (direction == ConstraintPullDownLayout.MOVE_DOWN) {
                    home_profile_bg.alpha = alpha
                    home_toolbar.layoutParams = (home_toolbar.layoutParams as ConstraintLayout.LayoutParams).apply {
                        topMargin = (marginTop - dp10) * 2
                    }
                    home_toolbar_badge.layoutParams = (home_toolbar_badge.layoutParams as ConstraintLayout.LayoutParams).apply {
                        topMargin = (marginTop - dp10) * 2 + dp6
                    }
                } else if (direction == ConstraintPullDownLayout.MOVE_UP) {
                    home_profile_bg.alpha = 1 - alpha
                    home_profile_layout.setMargin(-(dp120 * alpha).toInt())
                    home_profile_layout.setViewsAlpha(1 - alpha)
                }

                when {
                    height != 0 && height != topViewHeight -> {
                        if (isCurrentLogin) {
                            home_toolbar_avatar.setPrivateElevation(32f.dp2Px())
                            home_toolbar_avatar.getIndividualAvatarView().hideCoverText()
                            if (home_toolbar_avatar.visibility == View.GONE) {
                                home_toolbar_avatar.visibility = View.VISIBLE
                                home_profile_layout.hideAvatar()
                            }
                        } else {
                            home_toolbar_avatar.visibility = View.VISIBLE
                        }
                    }
                    height == 0 -> {
                        isCurrentLogin = true

                        home_toolbar_avatar.setPrivateElevation(0f)
                        home_toolbar_avatar.getIndividualAvatarView().showCoverText()
                        home_toolbar_avatar.showPrivateAvatar(accountRecipient)

                        home_toolbar.layoutParams = (home_toolbar.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = 0
                        }
                        home_toolbar_badge.layoutParams = (home_toolbar_badge.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = dp6
                        }

                        val ctx = home_profile_layout.getCloseAccount()
                        if (ctx != null && ctx.uid != AMELogin.majorUid) {
                            AmeModuleCenter.login().setMajorAccount(ctx)
                            home_profile_layout.resetPosition()
                        } else {
                            home_profile_layout.checkCurrentPage(accountRecipient.address.serialize())
                        }
                    }
                    height == topViewHeight -> {
                        checked = false
                        isCurrentLogin = true

                        home_toolbar_avatar.setPrivateElevation(0f)
                        home_toolbar_avatar.visibility = View.GONE
                        home_profile_layout.showAvatar()

                        home_toolbar.layoutParams = (home_toolbar.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = 0
                        }
                        home_toolbar_badge.layoutParams = (home_toolbar_badge.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = dp6
                        }
                    }
                }
            }

            override fun onTouchUp(direction: Int) {
                ALog.i(TAG, "PullDownView onTouchUp, direction = $direction")
                when (direction) {
                    ConstraintPullDownLayout.MOVE_UP -> {
                        home_profile_layout.visibility = View.GONE
                        hideAnimation().start()
                    }
                    ConstraintPullDownLayout.MOVE_DOWN -> {
                        if (home_profile_layout.visibility != View.VISIBLE) {
                            home_profile_layout.visibility = View.VISIBLE
                            home_profile_layout.showAllViewsWithAnimation()
                            home_profile_layout.resetMargin()
                        }
                    }
                }
            }

            override fun onTouchDown() {
                ALog.i(TAG, "PullDownView onTouchDown.")
            }
        }
        home_pull_down_layout.setCallback(callback)

        home_toolbar_avatar.setOnClickListener {
            home_pull_down_layout.expandTopViewAndCallback()
        }
        home_toolbar_unread_view.setOnClickListener {
            home_pull_down_layout.expandTopViewAndCallback()
        }

        home_profile_layout.setListener(object : HomeProfileLayout.HomeProfileListener {
            override fun onClickExit() {
                home_pull_down_layout.closeTopViewAndCallback()
            }

            override fun onDragVertically(ev: MotionEvent?): Boolean {
                return home_pull_down_layout.onTouchEvent(ev)
            }

            override fun onInterceptEvent(ev: MotionEvent?): Boolean {
                return home_pull_down_layout.onInterceptTouchEventFromOutside(ev)
            }

            override fun onViewPagerScrollStateChanged(newState: Int) {
                home_pull_down_layout.setViewPagerState(newState)
            }

            override fun onViewChanged(newRecipient: Recipient?) {
                if (newRecipient != null) {
                    home_toolbar_avatar.showPrivateAvatar(newRecipient)
                } else {
                    home_toolbar_avatar.showPrivateAvatar(accountRecipient)
                }
            }
        })
    }

    private fun initClipboardUtil() {
        ClipboardUtil.initClipboardUtil()
        checkClipboardDelay(5L)
    }

    /**
     * Check clipboard data, have default delay time 1 Sec
     *
     * @param delayTime Delay time, unit is seconds
     */
    private fun checkClipboardDelay(delayTime: Long = 1L) {
        ALog.i(TAG, "Delay to check clipboard, delay time is $delayTime seconds")
        checkStartTime = System.currentTimeMillis()
        checkDisposable = Observable.timer(delayTime, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    ClipboardUtil.checkClipboard(this)
                }
    }

    private fun showMenu() {
        showAnim(home_toolbar_more)
        BcmPopupMenu.Builder(this)
                .setMenuItem(listOf(
                        BcmPopupMenu.MenuItem(getString(com.bcm.messenger.chats.R.string.chats_main_scan_and_add), com.bcm.messenger.chats.R.drawable.chats_menu_scan_icon),
                        BcmPopupMenu.MenuItem(getString(com.bcm.messenger.chats.R.string.chats_main_invite_friend), com.bcm.messenger.chats.R.drawable.chats_menu_invite_icon)
                ))
                .setAnchorView(home_toolbar_more)
                .setSelectedCallback { index ->
                    when (index) {
                        0 -> startBcmActivity(Intent(this, NewScanActivity::class.java).apply {
                            putExtra(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_SCAN)
                            putExtra(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, true)
                        })
                        1 -> doInvite(accountRecipient)
                    }
                }
                .setDismissCallback {
                    hideAnim(home_toolbar_more)
                }
                .setGravity(Gravity.BOTTOM)
                .show()
    }

    private fun doInvite(recipient: Recipient) {
        val shareLink = recipient.privacyProfile.shortLink
        if (shareLink.isNullOrEmpty()) {
            mWaitForShortLink = true
            AmeAppLifecycle.showLoading()
            AmeModuleCenter.contact(accountContext)?.updateShareLink(AppContextHolder.APP_CONTEXT, recipient) {
                AmeAppLifecycle.hideLoading()
            }
        } else {
            mWaitForShortLink = false
            CommonShareView.Builder()
                    .setText(getString(com.bcm.messenger.chats.R.string.common_invite_user_message, shareLink))
                    .setType(CommonShareView.Config.TYPE_TEXT)
                    .show(this)
        }
    }

    private fun showAnim(view: View) {
        ObjectAnimator.ofFloat(view, "rotation", 0f, 45f).apply {
            duration = 250
        }.start()
    }

    private fun hideAnim(view: View) {
        ObjectAnimator.ofFloat(view, "rotation", 45f, 0f).apply {
            duration = 250
        }.start()
    }

    private fun checkBackupNotice() {
        home_toolbar_badge.isAccountBackup = AmeLoginLogic.accountHistory.getBackupTime(accountContext.uid) > 0
        home_profile_layout.checkBackupStatus()
    }

    private fun checkShowIntroPage() {
        if (!SuperPreferences.getTablessIntroductionFlag(this)) {
            startActivity(Intent(this, TablessIntroActivity::class.java))
        }
    }

    private fun recycleFragments() {
        val list = ArrayList<Fragment>()
        if (supportFragmentManager.fragments.size > 0) {
            for (i in supportFragmentManager.fragments) {
                if (i is BaseFragment) {
                    list.add(i)
                    ALog.i(TAG, "fragment exit ${i.javaClass.name}")
                }
            }
        }

        for (fragment in list) {
            supportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
    }

    override fun onAccountContextSwitch(newAccountContext: AccountContext) {
        setAccountContext(newAccountContext)
        titleView.updateContext(newAccountContext)
        home_toolbar_avatar.showRecipientAvatar(accountRecipient)
        messageListFragment?.updateAccountContext(newAccountContext)
        checkBackupNotice()

        val unreadStatus = unreadMap[newAccountContext] ?: return
        if (accountContext == this@HomeActivity.accountContext) {
            messageListFragment?.updateFriendRequest(unreadStatus.friendUnhandle, unreadStatus.friendUnread)
        }
    }

    override fun onLoginStateChanged() {
        home_profile_layout.reloadAccountList()
    }

    private fun checkPinUpgrade() {
        val user = AmeModuleCenter.user(accountContext) ?: return

        if (user.isPinLocked()) {
            return
        }

        var content = ""
        var okTitle = ""
        var act: Class<out Activity>? = null
        if (user.majorHasPin()) {
            content = getString(R.string.account_pin_clear_tip_major_content)
            okTitle = getString(R.string.account_pin_clear_tip_major_ok)
            act = PinLockSettingActivity::class.java

            user.clearAccountPin()
        } else if (user.anyAccountHasPin()) {
            content = getString(R.string.account_pin_clear_tip_content)
            okTitle = getString(R.string.account_pin_clear_tip_ok)
            act = PinLockInitActivity::class.java

            user.clearAccountPin()
        }

        if (content.isNotEmpty()) {
            AmePopup.center.newBuilder()
                    .withCancelable(false)
                    .withTitle(getString(R.string.common_alert_tip))
                    .withContent(content)
                    .withOkTitle(okTitle)
                    .withOkListener {
                        startBcmActivity(Intent(this, act))
                    }
                    .withCancelTitle(getString(R.string.common_understood))
                    .show(this)
        }
    }

    private fun initUnreadObserver() {
        unreadObserver.setListener(object : MessageListUnreadObserver.UnreadCountChangeListener {
            override fun onChatUnreadCountChanged(accountContext: AccountContext, unreadCount: Int) {
                var entity = unreadMap[accountContext]
                if (entity == null) {
                    entity = MessageListUnreadObserver.UnreadCountEntity()
                    unreadMap[accountContext] = entity
                }
                entity.chatUnread = unreadCount

                val showUnread = entity.chatUnread + entity.friendUnread
                home_profile_layout.setUnreadCount(accountContext, showUnread)

                updateHomeBadge()
            }

            override fun onFriendUnreadCountChanged(accountContext: AccountContext, unreadCount: Int, unhandledCount: Int) {
                var entity1 = unreadMap[accountContext]
                if (entity1 == null) {
                    entity1 = MessageListUnreadObserver.UnreadCountEntity()
                    unreadMap[accountContext] = entity1
                }
                entity1.friendUnread = unreadCount
                entity1.friendUnhandle = unhandledCount

                val showUnread = entity1.chatUnread + entity1.friendUnread
                home_profile_layout.setUnreadCount(accountContext, showUnread)
                if (accountContext == this@HomeActivity.accountContext) {
                    messageListFragment?.updateFriendRequest(entity1.friendUnhandle, entity1.friendUnread)
                }

                updateHomeBadge()
            }

            override fun onAdHocUnreadCountChanged(accountContext: AccountContext, unreadCount: Int) {
                var entity2 = unreadMap[accountContext]
                if (entity2 == null) {
                    entity2 = MessageListUnreadObserver.UnreadCountEntity()
                    unreadMap[accountContext] = entity2
                }
                entity2.adHocUnread = unreadCount

                updateHomeBadge()
            }

            override fun onAccountListChanged() {
                unreadMap.clear()
                updateHomeBadge()
            }

            private fun updateHomeBadge() {
                var unread = 0
                val backgroundUnreadList = mutableListOf<AccountContext>()
                unreadMap.forEach {
                    unread += it.value.chatUnread
                    unread += it.value.friendUnread
                    unread += it.value.adHocUnread

                    if (it.key != AMELogin.majorContext) {
                        val value = it.value
                        if (value.chatUnread > 0 || value.friendUnread > 0) {
                            backgroundUnreadList.add(it.key)
                        }
                    }
                }

                titleView.enableGuide(backgroundUnreadList.isEmpty())
                home_toolbar_unread_view.updateUnreadAccounts(backgroundUnreadList)
                AmePushProcess.updateAppBadge(AppContextHolder.APP_CONTEXT, unread)
            }
        })

        unreadObserver.init()
    }
}