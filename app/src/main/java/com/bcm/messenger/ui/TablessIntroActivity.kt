package com.bcm.messenger.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.R
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.setTranslucentStatus
import kotlinx.android.synthetic.main.activity_tabless_intro.*
import kotlinx.android.synthetic.main.home_tabless_intro_view.view.*

/**
 * Created by Kin on 2019/12/12
 */
class TablessIntroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tabless_intro)
        initView()
    }

    private fun initView() {
        tabless_view_pager.adapter = IntroPagerAdapter()
        tabless_view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                if (position > 2) {
                    SuperPreferences.setTablessIntroductionFlag(this@TablessIntroActivity)
                    finish()
                }
            }
        })

        window.setTranslucentStatus()
    }

    override fun onBackPressed() {
        // Cannot back
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    private inner class IntroPagerAdapter : PagerAdapter() {
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var view = layoutInflater.inflate(R.layout.home_tabless_intro_view, null)
            when (position) {
                0 -> {
                    val introText = Html.fromHtml(getString(R.string.tabless_ui_intro_text_1))
                    view.tabless_bg_view.setImageResource(R.drawable.home_tabless_intro_1)
                    view.tabless_text.text = introText
                    view.tabless_indicator_1.isActivated = true
                    view.tabless_indicator_2.isActivated = false
                    view.tabless_indicator_3.isActivated = false

                    view.tabless_indicator_1.layoutParams = view.tabless_indicator_1.layoutParams.apply {
                        width = 10.dp2Px()
                        height = 10.dp2Px()
                    }
                }
                1 -> {
                    val introText = Html.fromHtml(getString(R.string.tabless_ui_intro_text_2))
                    view.tabless_bg_view.setImageResource(R.drawable.home_tabless_intro_2)
                    view.tabless_text.text = introText
                    view.tabless_indicator_1.isActivated = false
                    view.tabless_indicator_2.isActivated = true
                    view.tabless_indicator_3.isActivated = false

                    view.tabless_indicator_2.layoutParams = view.tabless_indicator_2.layoutParams.apply {
                        width = 10.dp2Px()
                        height = 10.dp2Px()
                    }
                }
                2 -> {
                    val introText = Html.fromHtml(getString(R.string.tabless_ui_intro_text_3))
                    view.tabless_bg_view.setImageResource(R.drawable.home_tabless_intro_3)
                    view.tabless_text.text = introText
                    view.tabless_indicator_1.isActivated = false
                    view.tabless_indicator_2.isActivated = false
                    view.tabless_indicator_3.isActivated = true

                    view.tabless_indicator_3.layoutParams = view.tabless_indicator_3.layoutParams.apply {
                        width = 10.dp2Px()
                        height = 10.dp2Px()
                    }
                }
                else -> {
                    view = View(this@TablessIntroActivity)
                }
            }
            val param = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            container.addView(view, param)
            return view
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }

        override fun getCount(): Int {
            return 4
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
//            container.removeAllViews()
        }
    }
}