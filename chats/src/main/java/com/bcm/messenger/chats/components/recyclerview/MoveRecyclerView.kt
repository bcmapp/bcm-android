package com.bcm.messenger.chats.components.recyclerview

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import android.view.MotionEvent
import com.bcm.messenger.common.utils.AppUtil

/**
 * Created by Kin on 2018/7/14
 */
class MoveRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : RecyclerView(context, attrs, defStyle) {
    var canSmoothDownSide = false
    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        if (e?.action == MotionEvent.ACTION_MOVE && !canSmoothDownSide) {
            if (e.rawY > AppUtil.getScreenHeight(context) - AppUtil.dp2Px(context.resources, 80f)) {
                return false
            }
        }
        return super.onInterceptTouchEvent(e)
    }
}