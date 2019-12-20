package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.*


/*
 * 快速定位导航栏
 * Created by wjh, 2018-02-28
 */
class Sidebar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "Sidebar"
        const val DURATION_HINT = 1000L
    }

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mTextSize: Int = 0
    private var mTextColor: Int = 0
    private var mTextSelectedColor: Int = 0
    private var mLetterList: List<String> = arrayListOf()

    private var mSelectedIndex = -1

    private var mListener: OnTouchingLetterChangedListener? = null

    private val mTextRect: Rect = Rect() //用于计算导航字母的尺寸

    private var mRelativeRecyclerView: RecyclerView? = null     //对应的列表view
    private var mFastScrollHelper: FastScrollHelper? = null     //对应的快速导航帮助类
    private var mRecyclerViewScrollListener: RecyclerView.OnScrollListener? = null

    private var mHintSize = 0f
    private var mHintGap = 0f
    private var mHintFont = 0f
    private var mHintBackground: Int = 0
    private var mHintRect: RectF = RectF()
    private var mTouchWidth = 22.dp2Px()

    private var mFromTouching = false //表示是否因为touch导致的字符选择
    private var mNoTouchRunnable = Runnable {
        mFromTouching = false
        invalidate()
    }

    init {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonSidebarStyle)
        mTextSize = typedArray.getDimensionPixelSize(R.styleable.CommonSidebarStyle_sidebar_text_size, 10.dp2Px())
        mTextColor = typedArray.getColor(R.styleable.CommonSidebarStyle_sidebar_text_color, context.getColorCompat(R.color.common_color_999999))
        mTextSelectedColor = typedArray.getColor(R.styleable.CommonSidebarStyle_sidebar_text_selected_color, context.getColorCompat(R.color.common_color_black))
        mHintSize = typedArray.getDimensionPixelOffset(R.styleable.CommonSidebarStyle_sidebar_hint_size, 48.dp2Px()).toFloat()
        mHintFont = typedArray.getDimensionPixelOffset(R.styleable.CommonSidebarStyle_sidebar_hint_font, 24.sp2Px()).toFloat()
        mHintGap = typedArray.getDimensionPixelOffset(R.styleable.CommonSidebarStyle_sidebar_hint_gap, 10.dp2Px()).toFloat()
        mHintBackground = typedArray.getColor(R.styleable.CommonSidebarStyle_sidebar_hint_size, Color.parseColor("#f2f3f4"))

        typedArray.recycle()

        mPaint.typeface = Typeface.DEFAULT_BOLD
        mPaint.getTextBounds("W", 0, 1, mTextRect)
        mTouchWidth = mTextRect.width() + 12.dp2Px()

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layoutParams?.let {
            //ALog.i(TAG, "sidebar layoutParams width: ${it.width}")
            if (it.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                it.width = (mHintSize + mTouchWidth + mHintGap + 6.dp2Px()).toInt()
                layoutParams = it
                postInvalidateDelayed(500)
                return
            }
        }
        if (mLetterList.isEmpty()) {    //空的不绘制
            return
        }
        if (height == 0) {
            post {
                invalidate()
            }
            return
        }
        mPaint.typeface = Typeface.DEFAULT_BOLD

        val sh = height - mHintSize // 获取对应一个字母栏的高度
        val sw = mTouchWidth  // 获取对应字母栏的宽度
        val halfW = sw / 2.0f
        val halfHintSize = mHintSize / 2.0f

        var singleHeight = sh / mLetterList.size   // 获取每一个字母的高度
        singleHeight -= singleHeight / mLetterList.size //解决最后一个字母的选中状态时候的圆显示不全
        val radius = if (singleHeight < sw) {
            singleHeight / 2.0f
        } else {
            halfW
        }

        for (i in mLetterList.indices) {
            val letterText = mLetterList[i]
            mPaint.getTextBounds(letterText, 0, letterText.length, mTextRect)

            // x坐标等于中间-字符串宽度的一半.
            val xPos = width - halfW - mTextRect.width() / 2.0f
            val yPos = singleHeight * i + singleHeight + halfHintSize

            // 选中的状态
            if (i == mSelectedIndex) {

                val cx = width - halfW
                val cy = yPos - mTextRect.height() / 2.0f
                mPaint.color = mTextSelectedColor
                canvas.drawCircle(cx, cy, radius, mPaint)

                if (mFromTouching) {
                    //描绘hint
                    mHintRect.left = 0.0f
                    mHintRect.top = cy - halfHintSize
                    mHintRect.right = mHintSize
                    mHintRect.bottom = cy + halfHintSize
                    mPaint.color = mHintBackground
                    canvas.drawOval(mHintRect, mPaint)
                    mPaint.textSize = mHintFont
                    mPaint.color = Color.BLACK
                    mPaint.getTextBounds(letterText, 0, letterText.length, mTextRect)
                    canvas.drawText(letterText, mHintRect.left + halfHintSize - mTextRect.width() / 2.0f, cy + mTextRect.height() / 2.0f, mPaint)
                }

                mPaint.color = Color.WHITE
            } else {
                mPaint.color = mTextColor
            }
            mPaint.textSize = mTextSize.toFloat()
            canvas.drawText(letterText, xPos, yPos, mPaint)

        }

        showHint(false, DURATION_HINT)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val lastIndex = mSelectedIndex
        val listener = mListener
        // 点击y坐标所占总高度的比例*字符数组的长度就等于sideList点击中的个数.
        val touchY = (event.y - mHintSize / 2.0f) / (height - mHintSize)
        ALog.i(TAG, "dispatchTouchEvent touchY: $touchY")
        val index = ( touchY * mLetterList.size).toInt()
        ALog.i(TAG, "dispatchTouchEvent index: $index")
        if (event.x >= (width - mTouchWidth - 5.dp2Px()) && event.x <= width) { //只有点击在可点击的区域才可触发定位
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                }
                else -> if (lastIndex != index && index >= 0 && index < mLetterList.size) {
                    var pos = mFastScrollHelper?.findSidePosition(mLetterList[index]) ?: -1
                    ALog.i(TAG, "dispatchTouchEvent pos: $pos, selectIndex: $index")
                    if (pos == -1) {
                        pos = listener?.onLetterChanged(mLetterList[index]) ?: -1
                    }
                    ALog.i(TAG, "dispatchTouchEvent pos: $pos, selectIndex: $index")
                    if (pos >= 0) {// 如果match，标示选中某个side字符，应该黑圈显示，同时应该延长展示时间
                        mSelectedIndex = index
                        showHint(true, 0)
                        listener?.onLetterScroll(pos)
                        val recyclerView = mRelativeRecyclerView
                        if (recyclerView?.layoutManager is LinearLayoutManager) {
                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            layoutManager.scrollToPositionWithOffset(pos, 0)
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ALog.i(TAG,"onAttachedToWindow")
        val recyclerViewScrollListener = object : RecyclerView.OnScrollListener() {

            var isDragging = false
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val view = mRelativeRecyclerView ?: return
                if (!isDragging && view.layoutManager is LinearLayoutManager) {
                    val layoutManager = view.layoutManager as LinearLayoutManager
                    //选中当前列表显示的第一个item对应的字母
                    selectLetter(mFastScrollHelper?.findSideLetter(layoutManager.findFirstVisibleItemPosition())
                            ?: return)
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING
            }
        }
        mRecyclerViewScrollListener = recyclerViewScrollListener
        mRelativeRecyclerView?.addOnScrollListener(recyclerViewScrollListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ALog.i(TAG, "onDetachedFromWindow")
        val recyclerViewScrollListener = mRecyclerViewScrollListener
        if (recyclerViewScrollListener != null) {
            mRelativeRecyclerView?.removeOnScrollListener(recyclerViewScrollListener)
        }
    }

    /**
     * 展示
     */
    fun show() {
        this.visibility = VISIBLE
    }

    /**
     * 隐藏
     */
    fun hide() {
        this.visibility = INVISIBLE
    }

    /**
     * 设置快速导航相关帮助类
     */
    fun setFastScrollHelper(recyclerView: RecyclerView, scrollHelper: FastScrollHelper) {
        if (mRelativeRecyclerView !== recyclerView) {
            mRelativeRecyclerView = recyclerView
            val recyclerViewScrollListener = mRecyclerViewScrollListener
            if (recyclerViewScrollListener != null) {
                mRelativeRecyclerView?.addOnScrollListener(recyclerViewScrollListener)
            }
        }
        mFastScrollHelper = scrollHelper
    }

    /**
     * 设置触摸首字母变更回调
     * @param listener
     * @return
     */
    fun setOnTouchingLetterChangedListener(listener: OnTouchingLetterChangedListener): Sidebar {
        mListener = listener
        return this
    }

    /**
     * 设置导航字母
     */
    fun setLetterList(list: List<String>): Sidebar {
        mLetterList = list
        return this
    }

    /**
     * 设置文本尺寸
     * @param textSize
     * @return
     */
    fun setLetterTextSize(textSize: Int): Sidebar {
        mTextSize = textSize
        return this
    }

    /**
     * 设置文本字体颜色和选中时颜色
     * @param normalColor
     * @param selectedColor
     * @return
     */
    fun setLetterTextColor(normalColor: Int, selectedColor: Int): Sidebar {
        mTextColor = normalColor
        mTextSelectedColor = selectedColor
        return this
    }

    /**
     * 选中指定的字符
     * @param target
     */
    fun selectLetter(target: String?) {
        if (mFromTouching) {
            ALog.i(TAG, "selectLetter target: $target, isFromTouching")
            showHint(false, DURATION_HINT)
        }else {
            mSelectedIndex = mLetterList.indexOf(target)
            invalidate()
        }
    }

    /**
     * 展示选中提示
     */
    private fun showHint(show: Boolean, delay: Long) {
        removeCallbacks(mNoTouchRunnable)
        if (show) {
            mFromTouching = true
            postInvalidateDelayed(delay)
        } else {
            postDelayed(mNoTouchRunnable, delay)
        }
    }

    /**
     * 触摸触发的字符改变回调
     */
    interface OnTouchingLetterChangedListener {
        fun onLetterChanged(letter: String): Int
        fun onLetterScroll(position: Int)
    }

    /**
     * 快速定位帮助类
     */
    interface FastScrollHelper {
        /**
         * 找出指定位置的字母
         */
        fun findSideLetter(position: Int): String?       //根据位置查找指定的字母

        /**
         * 查询指定字母的位置，如果不存在则应该返回-1
         */
        fun findSidePosition(letter: String): Int       //根据字母查找指定的位置

        /**
         * 是否展示sidebar
         */
        fun showSideBar(): Boolean
    }
}
