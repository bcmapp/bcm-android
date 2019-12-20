package com.bcm.messenger.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout


/**
 * 自定义可滚动的标题栏，继承于AppBarLayout(要配合CoordinatorLayout使用）
 *
 * Created by wjh on 2018/3/8
 */
class ScrollTitleBarLayout : AppBarLayout {

    private lateinit var mCollapsingToolbar: CollapsingToolbarLayout
    private lateinit var mToolbar: Toolbar
    private lateinit var mBigTitleView: TextView //大标题
    private lateinit var mTitleView: TextView //收起时标题
    private lateinit var mLineView: View //标线
    private lateinit var mNoticeView: TextView//提示view
    private var mNoticeHeight: Int = 0 //提示栏高度

    private var mNoticeAnimator: ValueAnimator? = null //提示栏当前动画

    private var mState = AppBarStateChangeListener.IDLE //当前状态
    private var mListener: AppBarStateChangeListener? = null
    private val mHandler = Handler() //用于处理一些延时的任务

    private var mCollapsingHeight: Int = 0 //可展开的toolbar高度

    private val mStateChangedListener = OnOffsetChangedListener { appBarLayout, verticalOffset ->
        if (verticalOffset == 0) {
            if (mState != AppBarStateChangeListener.EXPANDED) {
                mState = AppBarStateChangeListener.EXPANDED
                changeByState(mState)
                mListener?.onStateChanged(appBarLayout, AppBarStateChangeListener.EXPANDED)
            }

        } else if (Math.abs(verticalOffset) >= appBarLayout.totalScrollRange) {
            if (mState != AppBarStateChangeListener.COLLAPSED) {
                mState = AppBarStateChangeListener.COLLAPSED
                changeByState(mState)
                mListener?.onStateChanged(appBarLayout, AppBarStateChangeListener.COLLAPSED)
            }

        } else {
            if (mState != AppBarStateChangeListener.IDLE) {
                mState = AppBarStateChangeListener.IDLE
                changeByState(mState)
                mListener?.onStateChanged(appBarLayout, AppBarStateChangeListener.IDLE)
            }
        }
    }

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {

        init(context)

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonScrollTitleLayoutStyle)
        val titleSize = typedArray.getDimension(R.styleable.CommonScrollTitleLayoutStyle_common_title_text_size, 16f)
        val titleColor = typedArray.getColor(R.styleable.CommonScrollTitleLayoutStyle_common_title_text_color,
                Color.parseColor("#131313"))
        val bigTitleSize = typedArray.getDimension(R.styleable.CommonScrollTitleLayoutStyle_common_bigtitle_text_size,
                28f)
        val bigTitleColor = typedArray.getColor(R.styleable.CommonScrollTitleLayoutStyle_common_bigtitle_text_color,
                Color.parseColor("#131313"))
        val noticeSize = typedArray.getDimension(R.styleable.CommonScrollTitleLayoutStyle_common_notice_text_size,
                12f)
        val noticeColor = typedArray.getColor(R.styleable.CommonScrollTitleLayoutStyle_common_notice_text_color,
                context.getColorCompat(R.color.common_color_black))
        val noticeBackgroundColor = typedArray.getColor(R.styleable.CommonScrollTitleLayoutStyle_common_notice_background_color,
                Color.parseColor("#EDEFF2"))

        typedArray.recycle()

        setTitleAppearance(titleSize, titleColor)
        setBigTitleAppearance(bigTitleSize, bigTitleColor)
        setNoticeAppearance(noticeSize, noticeColor, noticeBackgroundColor)
    }


    /**
     * 初始化属性和组件
     */
    private fun init(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.common_scroll_title_layout, this)

        mCollapsingToolbar = findViewById(R.id.toolbar_layout)

        mToolbar = findViewById(R.id.toolbar)
        mBigTitleView = findViewById(R.id.toolbar_big_title)
        mTitleView = findViewById(R.id.toolbar_title)
        mLineView = findViewById(R.id.toolbar_line)
        mNoticeView = findViewById(R.id.toolbar_notice)

        mNoticeHeight = 31.dp2Px()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addOnOffsetChangedListener(mStateChangedListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeOnOffsetChangedListener(mStateChangedListener)
    }

    /**
     * 获取展开的高度
     */
    fun getCollapsingHeight(): Int {
        return mCollapsingHeight
    }

    /**
     * 设置标题样式
     * @param size
     * @param color
     */
    fun setTitleAppearance(size: Float, color: Int): ScrollTitleBarLayout {
        mTitleView.setTextSize(size)
        mTitleView.setTextColor(color)
        return this
    }

    /**
     * 设置大标题样式
     * @param size
     * @param color
     */
    fun setBigTitleAppearance(size: Float, color: Int): ScrollTitleBarLayout {
        mBigTitleView.setTextSize(size)
        mBigTitleView.setTextColor(color)

        changeByTitle()
        return this
    }

    /**
     * 设置提示栏样式
     * @param size
     * @param color
     * @param backgroundColor
     */
    fun setNoticeAppearance(size: Float, color: Int, backgroundColor: Int): ScrollTitleBarLayout {
        mNoticeView.textSize = size
        mNoticeView.setTextColor(color)
        mNoticeView.setBackgroundColor(backgroundColor)
        return this
    }

    /**
     * 展示提示
     * @param content 提示文案
     * @param duration 提示多久之后隐藏，单位毫秒，如果0表示一直展示
     */
    fun showNotice(content: CharSequence, duration: Long = 0) {
        if (mNoticeView.visibility == View.VISIBLE) {
            return
        }

        mNoticeAnimator?.cancel()
        mNoticeAnimator?.removeAllUpdateListeners()
        mNoticeAnimator?.removeAllListeners()

        mNoticeView.text = content
        mNoticeView.visibility = View.VISIBLE


        mNoticeAnimator = ValueAnimator.ofInt(0, mNoticeHeight)
        mNoticeAnimator?.addUpdateListener { animation ->
            val lp = mNoticeView.layoutParams
            lp.height = animation.animatedValue as Int
            mNoticeView.layoutParams = lp
        }
        mNoticeAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (duration > 0) {
                    mHandler.postDelayed({ hideNotice() }, duration)
                }
            }


        })
        mNoticeAnimator?.start()

    }

    /**
     * 隐藏提示
     */
    fun hideNotice() {

        if (mNoticeView.visibility == View.VISIBLE) {
            mNoticeAnimator?.cancel()
            mNoticeAnimator?.removeAllUpdateListeners()
            mNoticeAnimator?.removeAllListeners()


            mNoticeAnimator = ValueAnimator.ofInt(mNoticeHeight, 0)
            mNoticeAnimator?.addUpdateListener { animation ->
                val lp = mNoticeView.layoutParams
                lp.height = animation.animatedValue as Int
                mNoticeView.layoutParams = lp
            }
            mNoticeAnimator?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mNoticeView.visibility = View.GONE
                }
            })
            mNoticeAnimator?.start()
        }
    }

    /**
     * 返回toolbar
     */
    fun getToolbar(): Toolbar {
        return mToolbar
    }

    /**
     * 设置是否可滚动
     * @param enable
     */
    fun setScrollFlag(enable: Boolean) {

        val lp = mCollapsingToolbar.layoutParams as AppBarLayout.LayoutParams
        lp.scrollFlags = if (enable) {
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
        } else {
            setExpanded(true)
            0
        }
        mCollapsingToolbar.layoutParams = lp
    }

    /**
     * 设置折叠状态监听类
     */
    fun setOnStateChangedListener(listener: AppBarStateChangeListener): ScrollTitleBarLayout {
        mListener = listener
        return this
    }

    /**
     * 设置标题
     * @param title
     */
    fun setTitle(title: CharSequence): ScrollTitleBarLayout {

        mTitleView.text = title
        mBigTitleView.text = title

        changeByTitle()
        return this
    }

    /**
     * 获取当前appbar状态
     */
    fun getAppBarState(): Int {
        return mState
    }

    /**
     * 设置返回键图标
     * @param iconResId
     */
    fun setNavigationIcon(iconResId: Int): ScrollTitleBarLayout {
        mToolbar.setNavigationIcon(iconResId)
        return this
    }

    /**
     * 设置返回键点击触发
     * @param clickListener
     */
    fun setNavigationOnClickListener(clickListener: OnClickListener): ScrollTitleBarLayout {
        mToolbar.setNavigationOnClickListener(clickListener)
        return this
    }

    /**
     * 添加toolbar右菜单
     * @param menuResId
     * @param clickListener
     */
    fun addRightMenu(menuResId: Int, clickListener: OnClickListener): ScrollTitleBarLayout {
        val lp = CollapsingToolbarLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.common_toolbar_height))
        lp.gravity = Gravity.RIGHT
        lp.rightMargin = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        lp.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN

        val menu = ImageView(context)
        menu.scaleType = ImageView.ScaleType.CENTER
        menu.setImageResource(menuResId)
        menu.setOnClickListener(clickListener)
        mCollapsingToolbar.addView(menu, lp)

        return this
    }

    /**
     * 根据标题改变尺寸样式
     */
    private fun changeByTitle() {
        //重新计算高度
//        val textPaint = mBigTitleView.paint
//        val bounds = Rect()
//        textPaint.getTextBounds(mBigTitleView.text.toString(), 0, mBigTitleView.text.length, bounds)
//        val lp = mCollapsingToolbar.layoutParams
//        lp.height = bounds.height() + resources.getDimensionPixelSize(R.dimen.common_toolbar_height) + mBigTitleView.paddingTop + mBigTitleView.paddingBottom
//        mCollapsingToolbar.layoutParams = lp

        mBigTitleView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (mBigTitleView.height > 0) {
                    val lp = mCollapsingToolbar.layoutParams
                    mCollapsingHeight = mBigTitleView.height + mToolbar.height
                    lp.height = mCollapsingHeight
                    mCollapsingToolbar.layoutParams = lp

                    mBigTitleView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    /**
     * 根据状态改变样式
     */
    private fun changeByState(state: Int) {
        val lp = mLineView.layoutParams as FrameLayout.LayoutParams
        when (state) {
            AppBarStateChangeListener.COLLAPSED -> {
                mTitleView.visibility = View.VISIBLE
                mBigTitleView.visibility = View.GONE
                lp.leftMargin = 0
            }
            else -> {
                mTitleView.visibility = View.GONE
                mBigTitleView.visibility = View.VISIBLE
                lp.leftMargin = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
            }
        }
    }

    /**
     * appbar状态监听类
     */
    interface AppBarStateChangeListener {

        companion object {
            const val IDLE = 0 //过度
            const val EXPANDED = 1 //展开
            const val COLLAPSED = 2 //收起
        }

        fun onStateChanged(appBarLayout: AppBarLayout, state: Int)

    }

}