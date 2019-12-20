package com.bcm.messenger.me.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_fragment_container_scan_and_code.*

/**
 * Created by wjh on 2019/7/3
 */
class ScanWithCodeContainerFragment : BaseFragment() {

    private var mPagerListener: ViewPager.OnPageChangeListener? = null
    private var mTabOther: TextView? = null
    private var mTabMy: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_container_scan_and_code, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val pagerListener = mPagerListener ?: return
        container_pager?.removeOnPageChangeListener(pagerListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.onBackPressed()
            }
        })

        val tabView = title_bar.getCenterView().second
        mTabOther = tabView?.findViewById(R.id.scan_tab_other)
        mTabMy = tabView?.findViewById(R.id.scan_tab_my)
        mTabOther?.setOnClickListener {
            container_pager.setCurrentItem(0, false)
        }
        mTabMy?.setOnClickListener {
            container_pager.setCurrentItem(1, false)
        }

        val fms = listOf<Fragment>(ScanFragment(), MyQRFragment())
        val adapter = object : FragmentPagerAdapter(requireFragmentManager(), BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

            override fun getItem(position: Int): Fragment {
                return fms[position]
            }

            override fun getCount(): Int {
                return fms.size
            }

        }
        container_pager.adapter = adapter
        mPagerListener = object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                selectTab(position)
            }
        }
        mPagerListener?.let {
            container_pager.addOnPageChangeListener(it)
        }

        val selectIndex = arguments?.getInt(ARouterConstants.PARAM.SCAN.TAB) ?: 0
        selectTab(selectIndex)
        container_pager.setCurrentItem(selectIndex, false)
    }

    private fun selectTab(index: Int) {
        when(index) {
            0 -> {
                mTabOther?.isSelected = true
                mTabMy?.isSelected = false
            }
            1 -> {
                mTabOther?.isSelected = false
                mTabMy?.isSelected = true
            }
        }
    }
}