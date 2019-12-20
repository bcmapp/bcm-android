package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor

/**
 * 角标布局类
 * Created by wjh on 2019/6/28
 */
class BadgeLayout : ConstraintLayout {

    private var mDotView: View? = null//圆点角标
    private var mFigureView: TextView? = null//数字角标
    private var mLocatePosId: Int = 0//需要依附的控件ID
    private var mLocatePosGap: Int = 0

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.BadgeLayoutStyle)
        mLocatePosId = typedArray.getResourceId(R.styleable.BadgeLayoutStyle_locate_pos_id, 0)
        mLocatePosGap = typedArray.getDimensionPixelSize(R.styleable.BadgeLayoutStyle_locate_pos_gap, 0)
        typedArray.recycle()

    }

    /**
     * 初始化圆点角标（如果没初始化过的话）
     */
    private fun initDot() {
        var dotView = mDotView
        if (dotView == null) {
            dotView = ImageView(context)
            dotView.setBackgroundResource(R.drawable.common_red_dot_circle)
            val dotLp = ConstraintLayout.LayoutParams(12.dp2Px(), 12.dp2Px())
            dotLp.topToTop = mLocatePosId
            dotLp.endToEnd = mLocatePosId
            addView(dotView, dotLp)
            dotView.visibility = View.GONE
            mDotView = dotView
        }
    }

    /**
     * 初始化数字角标(如果没有初始化过的话)
     */
    private fun initFigure() {
        var figureView = mFigureView
        if (figureView == null) {
            figureView = TextView(context)
            figureView.setTextColor(getColor(R.color.common_color_white))
            figureView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            figureView.typeface = Typeface.DEFAULT_BOLD
            figureView.setBackgroundResource(R.drawable.common_figure_badge_bg)
            figureView.gravity = Gravity.CENTER
            figureView.minWidth = 12.dp2Px()
            figureView.minHeight = 12.dp2Px()
            val figureLp = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            figureLp.topToTop = mLocatePosId
            figureLp.endToEnd = mLocatePosId
            addView(figureView, figureLp)
            figureView.visibility = View.GONE
            mFigureView = figureView
        }
    }

    /**
     * 展示红点
     */
    fun showDot() {
        initDot()
        mFigureView?.visibility = View.GONE
        mDotView?.visibility = View.VISIBLE
        measureGap(mDotView ?: return)
    }

    /**
     * 展示数字
     */
    fun showFigure(figure: Int) {
        initFigure()
        if (figure <= 0) {
            hideBadge()
        }else {
            mDotView?.visibility = View.GONE
            mFigureView?.visibility = View.VISIBLE
            mFigureView?.tag = figure
            if (figure > 99) {
                mFigureView?.text = "99+"
            }else {
                mFigureView?.text = figure.toString()
            }
            measureGap(mFigureView ?: return)
        }
    }

    /**
     * 隐藏角标
     */
    fun hideBadge() {
        mFigureView?.visibility = View.GONE
        mDotView?.visibility = View.GONE
    }

    /**
     * 获取当前角标数字（红点对应1）
     */
    fun getBadgeCount(): Int {
        return if (mFigureView?.visibility == View.VISIBLE) {
            (mFigureView?.tag as? Int) ?: 0
        } else if (mDotView?.visibility == View.VISIBLE) {
            0
        } else {
            0
        }
    }

    /**
     * 是否有展示角标
     */
    fun isBadgeShowing(): Boolean {
        return mFigureView?.visibility == View.VISIBLE || mDotView?.visibility == View.VISIBLE
    }

    /**
     * 计算角标的移动距离
     */
    private fun measureGap(targetView: View) {
        if (mLocatePosGap == 0) {
            targetView.post {
                targetView.apply {
                    translationX = width / 3f
                    translationY = -height / 3f
                }
            }
        }else {
            targetView.apply {
                translationX = mLocatePosGap.toFloat()
                translationY = -mLocatePosGap.toFloat()
            }
        }
    }
}