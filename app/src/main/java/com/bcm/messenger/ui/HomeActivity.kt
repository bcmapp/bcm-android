package com.bcm.messenger.ui

import android.animation.*
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
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.thread.MessageListFragment
import com.bcm.messenger.chats.thread.MessageListTitleView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.event.AccountLoginStateChangedEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.BcmRecyclerView
import com.bcm.messenger.common.ui.CommonShareView
import com.bcm.messenger.common.ui.ConstraintPullDownLayout
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.utils.pixel.PixelManager
import com.bcm.messenger.logic.SchemeLaunchHelper
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.scan.NewScanActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.home_profile_layout.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

/**
 * home
 * Created by zjl on 2018/2/27.
 */
@Route(routePath = ARouterConstants.Activity.APP_HOME_PATH)
class HomeActivity : SwipeBaseActivity() {
    companion object {
        private const val TAG = "HomeActivity"

        private const val TAB_CHAT = 0
        private const val TAB_CONTACT = 1
        private const val TAB_ME = 2
        private const val TAB_ADHOC = 3

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EventBus.getDefault().register(this)
        setSwipeBackEnable(false)

        setContentView(R.layout.activity_home)

        checkShowIntroPage()

        if (null != savedInstanceState) {
            recycleFragments()
        }
        initView()
        initPullDownView()
        initRecipientData()

        AmeModuleCenter.metric(AMELogin.majorContext)?.launchEnd()

        BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, _ ->
            if (hasUpdate) {
                if (forceUpdate) {
                    BcmUpdateUtil.showForceUpdateDialog()
                } else {
                    BcmUpdateUtil.showUpdateDialog()
                }
            }
        }

        RxBus.subscribe<HomeTabEvent>(TAG) {
            ALog.i(TAG, "receive HomeTabEvent position: ${it.position}, figure: ${it.showFigure}")
            val adhocMode = AmeModuleCenter.adhoc(accountContext)?.isAdHocMode() == true
            when (it.position) {
                TAB_CHAT -> {
                    if ((it.showFigure != null || it.showDot != null) && !adhocMode) {
                        home_profile_layout?.chatUnread = it.showFigure ?: 0
                    }
                }
                TAB_CONTACT -> {
                    if ((it.showFigure != null || it.showDot != null) && !adhocMode) {
                        home_profile_layout?.friendReqUnread = it.showFigure ?: 0
                    }
                }
                TAB_ME -> {
                }
                TAB_ADHOC -> {
                    if ((it.showFigure != null || it.showDot != null) && adhocMode) {
                        home_profile_layout?.friendReqUnread = 0
                        home_profile_layout?.chatUnread = it.showFigure ?: 0
                    }
                }
            }
        }

        checkSchemeLaunch()

        initClipboardUtil()
        checkAdHocMode()
        handleTopEvent()
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

        val adhocMode = AmeModuleCenter.adhoc(accountContext)?.isAdHocMode() == true
        if (adhocMode) {
            if (null != adHocMainFragment) {
                return true
            }
            val addFragment = supportFragmentManager.beginTransaction()

            val adhocUid = AmeModuleCenter.login().getAdHocUid()
            val adhocContext = AmeModuleCenter.login().getAccountContext(adhocUid)
            val adhocMain = BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_HOME_AD_HOC_MAIN)
                    .navigation() as Fragment

            adhocMain.arguments = Bundle().apply {
                putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, adhocContext)
            }
            addFragment.add(R.id.home_adhoc_main, adhocMain, adHocMainTag)
            addFragment.commit()

            home_adhoc_main.visibility = View.VISIBLE

            AmeModuleCenter.serverDaemon(accountContext).stopDaemon()
            AmeModuleCenter.serverDaemon(accountContext).stopConnection()

            mPixelManager = PixelManager.Builder().target(PixelActivity::class.java).build()
            mPixelManager?.start(AppContextHolder.APP_CONTEXT)

            home_profile_layout?.friendReqUnread = 0
            home_profile_layout?.chatUnread = 0

            return true
        } else {
            if (null != adHocMainFragment) {
                val removeFragment = supportFragmentManager.beginTransaction()
                removeFragment.remove(adHocMainFragment).commitNow()

                home_profile_layout?.friendReqUnread = 0
                home_profile_layout?.chatUnread = 0
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
        EventBus.getDefault().unregister(this)
        RxBus.unSubscribe(TAG)
        ClipboardUtil.unInitClipboardUtil()
        titleView.unInit()
    }

    override fun onResume() {
        super.onResume()
        if (!checkAdHocMode()) {
            checkClipboardDelay()
            if (SchemeLaunchHelper.hasIntent()) {
                checkSchemeLaunch()
            }
            checkBackupNotice(AmeLoginLogic.accountHistory.getBackupTime(accountContext.uid) > 0)
        }

        // check need fetch profile or avatar
        if (accountRecipient.needRefreshProfile()) {
            AmeModuleCenter.contact(accountContext)?.checkNeedFetchProfileAndIdentity(accountRecipient, callback = null)
        } else {
            AmeModuleCenter.contact(accountContext)?.checkNeedDownloadAvatarWithAll(accountRecipient)
        }
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
     * 处理请求过来的topevent事件
     */
    private fun handleTopEvent() {
        try {
            val data = intent.getStringExtra(ARouterConstants.PARAM.PARAM_DATA)
            if (!data.isNullOrEmpty()) {
                val event = GsonUtils.fromJson<HomeTopEvent>(data, object : TypeToken<HomeTopEvent>() {}.type)
                fun continueAction() {
                    if (event.finishTop) {
                        BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH).navigation(this)
                    }

                    val con = event.chatEvent
                    if (con != null) {
                        if (con.path == ARouterConstants.Activity.CHAT_GROUP_CONVERSATION) {
                            val gid = con.address.toLong()
                            BcmRouter.getInstance()
                                    .get(con.path)
                                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, con.threadId)
                                    .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, gid)
                                    .startBcmActivity(accountContext,this)

                        }else if (con.path == ARouterConstants.Activity.CHAT_CONVERSATION_PATH) {
                            BcmRouter.getInstance()
                                    .get(con.path)
                                    .putLong(ARouterConstants.PARAM.PARAM_THREAD, con.threadId)
                                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, Address.from(accountContext, con.address))
                                    .startBcmActivity(accountContext,this)
                        }

                    }

                    val call = event.callEvent
                    if (call != null) {
                        AmeModuleCenter.chat(call.address.context())?.startRtcCallService(AppContextHolder.APP_CONTEXT, call.address, CameraState.Direction.NONE.ordinal)
                    }
                }

                val notify = event.notifyEvent
                if (notify != null) {
                    ALog.d(TAG, "receive HomeTopEvent success: ${notify.success}, message: ${notify.message}")
                    if (notify.success) {
                        AmeAppLifecycle.succeed(notify.message, true) {
                            continueAction()
                        }
                    } else {
                        AmeAppLifecycle.failure(notify.message, true) {
                            continueAction()
                        }
                    }
                } else {
                    continueAction()
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "handleTopEvent error", ex)
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
            supportFragmentManager.beginTransaction().remove(fragment).commit()
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
        messageListFragment?.arguments = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET, getMasterSecret())
        }
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
            val adhocMode = AmeModuleCenter.adhoc(accountContext)?.isAdHocMode() == true
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
        checkBackupNotice(AmeLoginLogic.accountHistory.getBackupTime(accountContext.uid) > 0)
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
            private val gapSize = (topViewHeight - statusBarHeight - 426.dp2Px()) / 3 + 30.dp2Px()

            private val avatarMaxSize = 160.dp2Px()
            private val avatarStartMaxMargin = (screenWidth - avatarMaxSize) / 2 - dp15

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
                home_toolbar_avatar.layoutParams = (home_toolbar_avatar.layoutParams as ConstraintLayout.LayoutParams).apply {
                    this.marginStart = marginStart
                    this.topMargin = marginTop
                    this.width = avatarSize
                    this.height = avatarSize
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
                        home_toolbar_avatar.setPrivateElevation(32f.dp2Px())
                        home_toolbar_avatar.visibility = View.VISIBLE
                        home_toolbar_avatar.getIndividualAvatarView().hideCoverText()
                        home_profile_layout.hideAvatar()
                    }
                    height == 0 -> {
                        home_toolbar_avatar.setPrivateElevation(0f)
                        home_toolbar_avatar.getIndividualAvatarView().showCoverText()
                        home_toolbar_avatar.showPrivateAvatar(accountRecipient)

                        home_toolbar.layoutParams = (home_toolbar.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = 0
                        }
                        home_toolbar_badge.layoutParams = (home_toolbar_badge.layoutParams as ConstraintLayout.LayoutParams).apply {
                            topMargin = dp6
                        }

                        home_profile_layout.checkCurrentPage(accountRecipient.address.serialize())
                    }
                    height == topViewHeight -> {
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
                        val ctx = home_profile_layout.getCloseAccount()
                        if (ctx != null && ctx.uid != accountContext.uid) {
                            AmeModuleCenter.login().setMajorAccount(ctx)
                        }
                    }
                    ConstraintPullDownLayout.MOVE_DOWN -> {
                        if (home_profile_layout.visibility != View.VISIBLE) {
                            home_profile_layout.visibility = View.VISIBLE
                            home_profile_layout.resetMargin()
                            home_profile_layout.showAllViewsWithAnimation()
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

    private fun checkBackupNotice(isHasBackup: Boolean) {
        home_toolbar_badge.isAccountBackup = isHasBackup
//        home_profile_layout.isAccountBackup = isHasBackup
    }

    private fun checkShowIntroPage() {
        if (!SuperPreferences.getTablessIntroductionFlag(this)) {
            startActivity(Intent(this, TablessIntroActivity::class.java))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: AccountLoginStateChangedEvent) {
        if (accountContext.uid != AmeModuleCenter.login().majorUid()) {
            setAccountContext(AMELogin.majorContext)
            titleView.updateContext(accountContext)

            home_toolbar_avatar.showRecipientAvatar(accountRecipient)
        }
    }
}