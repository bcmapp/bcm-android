package com.bcm.messenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.R
import com.bcm.messenger.adapter.HomeViewPagerAdapter
import com.bcm.messenger.chats.thread.MessageListFragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ARouterConstants.Fragment.CONTACTS_HOST
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.metrics.ReportUtil
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.IAdHocModule
import com.bcm.messenger.common.ui.BadgeLayout
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.ClipboardUtil
import com.bcm.messenger.common.utils.PushUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.pixel.PixelManager
import com.bcm.messenger.contacts.ContactsFragment
import com.bcm.messenger.logic.SchemeLaunchHelper
import com.bcm.messenger.me.ui.profile.MeMoreFragment
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_home.*
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
    }

    private val mLaunchHelper = SchemeLaunchHelper(this)
    private var checkDisposable: Disposable? = null
    private var checkStartTime = System.currentTimeMillis()
    private val mAdHocModule: IAdHocModule by lazy { AmeModuleCenter.adhoc() }
    private var mPixelManager: PixelManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ALog.i(TAG, "onCreate")
        ReportUtil.launchEnded()

        setSwipeBackEnable(false)

        setContentView(R.layout.activity_home)

        if (null != savedInstanceState) {
            recyclerFragments()
        }
        initView()

        checkUnreadFriendRequest()

        BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, _ ->
            if (hasUpdate) {
                if (forceUpdate) {
                    BcmUpdateUtil.showForceUpdateDialog()
                } else {
                    BcmUpdateUtil.showUpdateDialog()
                }
            }
        }

        home_viewpager.setCurrentItem(TAB_CHAT, false)
        selectItem(TAB_CHAT)

        RxBus.subscribe<HomeTabEvent>(TAG) {

            ALog.i(TAG, "receive HomeTabEvent position: ${it.position}, figure: ${it.showFigure}")
            fun updateBadge(badgeView: BadgeLayout?, showFigure: Int, showDot: Boolean, badgeCount: Int) {
                when {
                    showFigure > 0 -> badgeView?.showFigure(showFigure)
                    showDot -> badgeView?.showDot()
                    else -> badgeView?.hideBadge()
                }
                ALog.i(TAG, "updateBadge: $badgeCount")
                AmePushProcess.updateAppBadge(AppContextHolder.APP_CONTEXT, badgeCount)
            }

            var showFigure: Int = 0
            var showDot: Boolean = false
            var badgeView: BadgeLayout? = null
            var badgeCount: Int = 0
            var updateBadge: Boolean = false
            when(it.position) {
                TAB_CHAT -> {

                    if ((it.showFigure != null || it.showDot != null) && !mAdHocModule.isAdHocMode()) {
                        updateBadge = true
                        badgeView = tab_chat_badge
                        showFigure = it.showFigure ?: 0
                        showDot = it.showDot ?: false
                        badgeCount = showFigure + (tab_contacts_badge?.getBadgeCount() ?: 0) + (tab_me_badge?.getBadgeCount() ?: 0)

                    }
                }
                TAB_CONTACT -> {

                    if ((it.showFigure != null || it.showDot != null) && !mAdHocModule.isAdHocMode()) {
                        updateBadge = true
                        badgeView = tab_contacts_badge
                        showFigure = it.showFigure ?: 0
                        showDot = it.showDot ?: false
                        badgeCount = (tab_chat_badge?.getBadgeCount()
                                ?: 0) + showFigure + (tab_me_badge?.getBadgeCount() ?: 0)

                    }

                }
                TAB_ME -> {

                    if ((it.showFigure != null || it.showDot != null) && !mAdHocModule.isAdHocMode()) {
                        updateBadge = true
                        badgeView = tab_me_badge
                        showFigure = it.showFigure ?: 0
                        showDot = it.showDot ?: false
                        badgeCount = (tab_chat_badge?.getBadgeCount()
                                ?: 0) + (tab_contacts_badge?.getBadgeCount() ?: 0) + showFigure

                    }

                }
                TAB_ADHOC -> {

                    if ((it.showFigure != null || it.showDot != null) && mAdHocModule.isAdHocMode()) {
                        updateBadge = true
                        badgeView = null
                        showFigure = it.showFigure ?: 0
                        showDot = it.showDot ?: false
                        badgeCount = showFigure

                    }
                }
            }
            if (updateBadge) {
                updateBadge(badgeView, showFigure, showDot, badgeCount)
            }
        }

        checkSchemeLaunch()

        initClipboardUtil()

        checkAdHocMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(TAG)
        ClipboardUtil.unInitClipboardUtil()
    }

    override fun onResume() {
        super.onResume()

        if(!checkAdHocMode()){
            checkClipboardDelay()
            if (SchemeLaunchHelper.hasIntent()) {
                checkSchemeLaunch()
            }
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
        ALog.i(TAG, "onNewIntent")
        intent = newIntent

        home_viewpager.setCurrentItem(TAB_CHAT, false)
        selectItem(TAB_CHAT)

        if (AmeModuleCenter.login().isPinLocked()) {
            AmeModuleCenter.login().showPinLock()
            SchemeLaunchHelper.storeSchemeIntent(newIntent)
        } else {
            AmePushProcess.checkSystemBannerNotice()
            PushUtil.loadSystemMessages()
            checkSchemeLaunch()
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (null != savedInstanceState) {
            home_viewpager.setCurrentItem(TAB_CHAT, false)
            selectItem(TAB_CHAT)
        }
    }

    private fun checkAdHocMode(): Boolean {
        val adHocMainTag = "adhoc_main"
        val adHocMainFragment = supportFragmentManager.findFragmentByTag(adHocMainTag)
        if (mAdHocModule.isAdHocMode()) {
            if(null != adHocMainFragment) {
                return true
            }
            val addFragment = supportFragmentManager.beginTransaction()

            val adhocMain = BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_HOME_AD_HOC_MAIN)
                    .navigation() as Fragment

            addFragment.add(R.id.home_adhoc_main, adhocMain, adHocMainTag)
            addFragment.commit()

            home_adhoc_main.visibility = View.VISIBLE

            AmeModuleCenter.serverDaemon().stopDaemon()
            AmeModuleCenter.serverDaemon().stopConnection()
            ReportUtil.setAdhocRunning(true)

            mPixelManager = PixelManager.Builder().target(PixelActivity::class.java).build()
            mPixelManager?.start(AppContextHolder.APP_CONTEXT)
            return true

        } else {
            if (null != adHocMainFragment) {
                val removeFragment = supportFragmentManager.beginTransaction()
                removeFragment.remove(adHocMainFragment).commitNow()
            }

            home_adhoc_main.visibility = View.GONE

            AmeModuleCenter.serverDaemon().startDaemon()
            AmeModuleCenter.serverDaemon().checkConnection(true)
            ReportUtil.setAdhocRunning(false)

            mPixelManager?.quit(AppContextHolder.APP_CONTEXT)
        }
        return false
    }


    /**
     * Check called by other apps
     */
    private fun checkSchemeLaunch() {
        val schemeLaunchIntent = SchemeLaunchHelper.pullOutLaunchIntent()
        if (schemeLaunchIntent != null) {
            ALog.i(TAG, "Found unhandled outLaunch intent, continue handling")
            mLaunchHelper.route(schemeLaunchIntent)
        }else {
            ALog.i(TAG, "Try handle current intent，check called by other app")
            mLaunchHelper.route(intent)
        }
    }

    private fun recyclerFragments() {
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
        val fms = ArrayList<Fragment>()
        val conversationFragment = MessageListFragment()
        conversationFragment.arguments = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET, getMasterSecret())
        }
        fms.add(conversationFragment)
        conversationFragment.setActive(true)

        val contactsFragment = BcmRouter.getInstance().get(CONTACTS_HOST).navigationWithCast<ContactsFragment>()
        fms.add(contactsFragment)

        val meFragment = MeMoreFragment()
        val args = Bundle()
        args.putBoolean(ARouterConstants.PARAM.PARAM_LOGIN_FROM_REGISTER, intent.getBooleanExtra(ARouterConstants.PARAM.PARAM_LOGIN_FROM_REGISTER, false))
        meFragment.arguments = args
        fms.add(meFragment)

        val homeViewPagerAdapter = HomeViewPagerAdapter(supportFragmentManager, fms)
        home_viewpager.adapter = homeViewPagerAdapter
        home_viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {
                RxBus.post(HomeTabEvent(position, false))

                for ((index, value) in fms.withIndex()) {
                    if (value is BaseFragment) {
                        if (index == position) {
                            value.setActive(true)
                        } else {
                            if (value.isActive()) {
                                value.setActive(false)
                            }
                        }
                    }
                }

                when (position) {
                    0 -> selectItem(TAB_CHAT)
                    1 -> selectItem(TAB_CONTACT)
                    2 -> selectItem(TAB_ME)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}

        })
        home_viewpager.isSlidingEnable = true
        home_viewpager.offscreenPageLimit = 3

        tab_contacts_badge.setOnClickListener {
            home_viewpager.setCurrentItem(TAB_CONTACT, false)
            selectItem(TAB_CONTACT)
        }

        tab_me_badge.setOnClickListener {
            home_viewpager.setCurrentItem(TAB_ME, false)
            selectItem(TAB_ME)
        }
    }

    private fun selectItem(numId: Int) {
        tab_chat.setImageResource(R.drawable.tab_chat_icon)
        tab_contacts.setImageResource(R.drawable.tab_contacts_icon)
        tab_me.setImageResource(R.drawable.tab_me_icon)

        when (numId) {
            TAB_CHAT -> tab_chat.setImageResource(R.drawable.tab_chat_selected_icon)
            TAB_CONTACT -> tab_contacts.setImageResource(R.drawable.tab_contacts_selected_icon)
            TAB_ME -> tab_me.setImageResource(R.drawable.tab_me_selected_icon)
        }

        when (numId) {
            TAB_CHAT -> tab_chat_badge.setOnClickListener(MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
                override fun onMultiClick(view: View?, count: Int) {
                    RxBus.post(HomeTabEvent(TAB_CHAT, true))
                }
            }))
            else -> tab_chat_badge.setOnClickListener {
                home_viewpager.setCurrentItem(TAB_CHAT, false)
                selectItem(TAB_CHAT)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val meMoreFragment = (home_viewpager.adapter as HomeViewPagerAdapter).getItem(2) as MeMoreFragment
            if (meMoreFragment.isTopViewExpanded()) {
                meMoreFragment.closeTopView()
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

    @SuppressLint("CheckResult")
    private fun checkUnreadFriendRequest() {
        Observable.create<Int> {
            it.onNext(UserDatabase.getDatabase().friendRequestDao().queryUnreadCount())
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it > 0) {
                        tab_contacts_badge.showFigure(it)
                    }
                }, {})
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

}