package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.dp2Px

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
            view.layoutParams = LayoutParams(7.dp2Px(), 7.dp2Px()).apply {
                setMargins(4.dp2Px(), 0, 4.dp2Px(), 0)
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