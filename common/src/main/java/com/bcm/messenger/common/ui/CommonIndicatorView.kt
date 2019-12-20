package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.AppUtil

/**
 * Created by Kin on 2018/7/26
 */
class CommonIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val indicatorList = mutableListOf<View>()
    private var lastActiveIndicator = 0

    fun setIndicators(count: Int) {
        if (count <= 0) {
            return
        }
        for (i in 0 until count) {
            val view = View(context)
            view.layoutParams = LayoutParams(AppUtil.dp2Px(context.resources, 7), AppUtil.dp2Px(context.resources, 7)).apply {
                setMargins(AppUtil.dp2Px(context.resources, 4), 0, AppUtil.dp2Px(context.resources, 4), 0)
            }
            view.background = context.getDrawable(R.drawable.common_indicator_selector)
            view.isActivated = false
            indicatorList.add(view)
            addView(view)
        }
        indicatorList[0].isActivated = true
    }

    fun setCurrentIndicator(index: Int) {
        indicatorList[lastActiveIndicator].isActivated = false
        indicatorList[index].isActivated = true
        lastActiveIndicator = index
    }
}