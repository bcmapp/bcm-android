package com.bcm.messenger.ui.widget

import android.view.View
import androidx.viewpager.widget.ViewPager

/**
 * Created by Kin on 2019/12/30
 */
class HomeProfilePageTransformer : ViewPager.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        (page as IProfileView).positionChanged(position)
    }
}