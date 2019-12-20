package com.bcm.messenger.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

/**
 * Home activity Viewpager
 * Created by zjl on 2018/2/28.
 */

class HomeViewPagerAdapter : FragmentPagerAdapter {
    lateinit var titles: List<String>
    lateinit var fragmentList: List<Fragment>

    constructor(fm: FragmentManager) : super(fm)

    constructor(fm: FragmentManager, lf: List<Fragment>) : super(fm) {
        fragmentList = lf
    }

    constructor(fm: FragmentManager, lf: List<Fragment>, titles: List<String>) : super(fm) {
        this.titles = titles
        fragmentList = lf
    }

    override fun getItem(position: Int): Fragment {
        return fragmentList[position]
    }

    override fun getPageTitle(position: Int): CharSequence {
        return titles[position]
    }

    override fun getCount(): Int {
        return fragmentList.size
    }
}