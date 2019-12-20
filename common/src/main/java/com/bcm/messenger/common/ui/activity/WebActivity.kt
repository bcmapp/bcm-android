package com.bcm.messenger.common.ui.activity

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.route.api.BcmRouter
import com.bcm.messenger.common.R
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * 网页显示
 * Created by zjl on 2017/3/29.
 */
@Route(routePath = ARouterConstants.Activity.WEB)
class WebActivity : SwipeBaseActivity() {

    private val TAG = "WebActivity"

    private lateinit var mainLayout: ViewGroup
    private lateinit var titleView: TextView
    private lateinit var backBtn: ImageView
    private lateinit var refreshBtn: ImageView
    private lateinit var closeBtn: ImageView
    private lateinit var mTitleBar: View
    private var mFragment: BaseWebFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.common_web_activity)
        initToolbar()
        initWeb()
    }

    private fun initToolbar() {
        mainLayout = findViewById(R.id.web_main)
        mTitleBar = findViewById(R.id.web_titlebar)
        backBtn = findViewById(R.id.web_back)
        refreshBtn = findViewById(R.id.web_refresh)
        closeBtn = findViewById(R.id.web_close)
        backBtn.setOnClickListener {
            onBackPressed()
        }

        refreshBtn.setOnClickListener {
            mFragment?.reload()
        }

        closeBtn.setOnClickListener {
            finishAfterTransition()
        }

        titleView = findViewById(R.id.web_title_text)
        titleView.text = intent.getStringExtra(ARouterConstants.PARAM.WEB_TITLE) ?: ""
    }

    private fun initWeb() {

        mTitleBar.layoutParams = mTitleBar.layoutParams.apply {
            height += AppUtil.getStatusBarHeight(this@WebActivity)
        }

        val clazz = intent.getStringExtra(ARouterConstants.PARAM.WEB_FRAGMENT)
        val fragment = if (clazz.isNullOrEmpty()) {
            BaseWebFragment()
        }else {
            BcmRouter.getInstance().get(clazz).navigationWithCast()
        }
        mFragment = initFragment(R.id.web_fragment, fragment, null)
        mFragment?.setListener(object : BaseWebFragment.OnWebActionListener {

            override fun onPageLoad(finished: Boolean, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrEmpty()) {
                    titleView.text = url
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mainLayout.removeAllViews()
    }

    override fun onBackPressed() {
        if (mFragment?.checkGoBack() == true) {
            // 不做任何事
        } else {
            super.onBackPressed()
        }
    }

}
