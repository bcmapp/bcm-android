package com.bcm.messenger.common.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.R
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.ISearchAction
import com.bcm.messenger.common.api.ISearchCallback
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.ui.CommonSearchBar
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.utils.startBcmActivityForResult
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_activity_search.*

/**
 * 
 * @created by wjh 2019-04-04
 */
class SearchActivity : SwipeBaseActivity(), ISearchCallback {

    companion object {
        private const val TAG = "SearchActivity"
        const val REQUEST_SEARCH_MORE = 1

        /**
         * 
         */
        fun callSearchActivity(context: Context, accountContext: AccountContext, keyword: String, displayAll: Boolean, hasPrevious: Boolean, searchClass: String, recentClass: String?, requestCode: Int) {
            val intent = Intent(context, SearchActivity::class.java)
            intent.putExtra(ARouterConstants.PARAM.SEARCH.CURRENT_KEYWORD, keyword)
            intent.putExtra(ARouterConstants.PARAM.SEARCH.HAS_PREVIOUS, hasPrevious)
            intent.putExtra(ARouterConstants.PARAM.SEARCH.DISPLAY_ALL, displayAll)
            intent.putExtra(ARouterConstants.PARAM.SEARCH.RECENT_CLAZZ, recentClass)
            intent.putExtra(ARouterConstants.PARAM.SEARCH.CURRENT_CLAZZ, searchClass)
            intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_popup_alpha_in)
            intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_popup_alpha_out)
            intent.putExtra(ARouterConstants.PARAM.PARAM_PREVIOUS_EXIT_ANIM, R.anim.common_popup_alpha_out)
            intent.putExtra(ARouterConstants.PARAM.PARAM_PREVIOUS_ENTER_ANIM, R.anim.common_popup_alpha_in)
            intent.putExtra(ARouterConstants.Account.ACCOUNT_CONTEXT, accountContext)

            if (context is Activity) {
                if (requestCode != 0) {
                    context.startBcmActivityForResult(accountContext, intent, requestCode)
                }else {
                    context.startBcmActivity(accountContext, intent)
                }
            }else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startBcmActivity(accountContext, intent)
            }

        }
    }

    private var mHasPrevious = false
    private var mDisplayAll = false

    private var mRecentFragmentClazz: String? = null
    private var mCurrentFragmentClazz: String? = null
    private var mRecentSearchFragment: Fragment? = null
    private var mCurrentSearchFragment: Fragment? = null
    private var mDisplayFragment: Fragment? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SEARCH_MORE) {
            if (resultCode == Activity.RESULT_OK) {
                finish()
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.common_activity_search)
        init()

    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val y = ev.y
            val targetY = search_bar_layout.y + search_bar_layout.height
            if (y > targetY) {
                hideKeyboard()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun init() {
        ALog.d(TAG, "init")
        search_top_v.post {
            search_top_v.layoutParams = search_top_v.layoutParams.apply {
                height += getStatusBarHeight()
            }
        }

        mDisplayAll = intent.getBooleanExtra(ARouterConstants.PARAM.SEARCH.DISPLAY_ALL, false)
        mHasPrevious = intent.getBooleanExtra(ARouterConstants.PARAM.SEARCH.HAS_PREVIOUS, false)
        mCurrentFragmentClazz = intent.getStringExtra(ARouterConstants.PARAM.SEARCH.CURRENT_CLAZZ)
        mRecentFragmentClazz = intent.getStringExtra(ARouterConstants.PARAM.SEARCH.RECENT_CLAZZ)
        ALog.i(TAG, "init displayAll: $mDisplayAll, hasPrevious: $mHasPrevious, current: $mCurrentFragmentClazz, recent: $mRecentFragmentClazz")
        if (mCurrentFragmentClazz.isNullOrEmpty()) {
            finish()
            return
        }

        if (mHasPrevious) {
            search_back_iv.visibility = View.VISIBLE
        } else {
            search_back_iv.visibility = View.GONE
        }
        if (mRecentFragmentClazz.isNullOrEmpty()) {
            displaySearch(search_main_sb.getSearchText().toString())
        } else {
            displayRecent()
        }

        search_back_iv.setOnClickListener {
            onBackPressed()
        }

        search_cancel_tv.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        search_main_sb.setOnSearchActionListener(object : CommonSearchBar.OnSearchActionListener {
            override fun onJump() {

            }

            override fun onSearch(keyword: String) {
                ALog.d(TAG, "onSearch keyword: $keyword")
                displaySearch(keyword)
            }

            override fun onClear() {
                ALog.d(TAG, "onClear")
                if (!mRecentFragmentClazz.isNullOrEmpty()) {
                    displayRecent()
                }else {
                    displaySearch("")
                }
            }

        })

        search_main_sb.setSearchText(intent.getStringExtra(ARouterConstants.PARAM.SEARCH.CURRENT_KEYWORD) ?: "")

        search_main_sb.post {
            search_main_sb.requestSearchFocus()
        }
    }

    /**
     * 
     */
    private fun displayRecent() {
        if (mDisplayFragment == mRecentSearchFragment && mDisplayFragment != null) {
            ALog.d(TAG, "displayRecent return, displayFragment is same")
            return
        }
        val tran = supportFragmentManager.beginTransaction()
        mDisplayFragment?.let {
            tran.hide(it)
        }
        var f = mRecentSearchFragment
        if (f == null) {
            f = supportFragmentManager.fragmentFactory.instantiate(classLoader, mRecentFragmentClazz.orEmpty())
            mRecentSearchFragment = f
            tran.add(R.id.search_main_layout, f)
        }else {
            tran.show(f)
        }
        mDisplayFragment = mRecentSearchFragment
        tran.commitAllowingStateLoss()

    }

    /**
     * 
     */
    private fun displaySearch(keyword: String) {
        if (mDisplayFragment == mCurrentSearchFragment && mDisplayFragment != null) {
            ALog.d(TAG, "displaySearch return, displayFragment is same")
            val f = mDisplayFragment
            if (f is ISearchAction) {
                f.setKeyword(keyword, !mDisplayAll)
            }
            return
        }
        val tran = supportFragmentManager.beginTransaction()
        mDisplayFragment?.let {
            tran.hide(it)
        }

        var f: Fragment? = mCurrentSearchFragment
        if (f == null) {
            f = supportFragmentManager.fragmentFactory.instantiate(classLoader, mCurrentFragmentClazz.orEmpty()).apply {
                arguments = Bundle().apply {
                    putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
                }
            }
            mCurrentSearchFragment = f
            tran.add(R.id.search_main_layout, f)

        } else {
            tran.show(f)
        }

        if (f is ISearchAction) {
            f.setKeyword(keyword, !mDisplayAll)
        }

        mDisplayFragment = mCurrentSearchFragment
        tran.commitAllowingStateLoss()

    }

    override fun onSelect(type: BcmFinderType, key: String) {
        BcmFinderManager.get(accountContext).saveRecord(type, key)
    }

    override fun onMore(type: BcmFinderType, key: String) {
    }
}
