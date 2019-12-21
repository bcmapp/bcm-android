package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.common.utils.dp2Px


/**
 *
 * User: wangjh
 * Date: 2016-07-13
 * 
 */
class ContentShadeView : LinearLayout {

    private lateinit var mLoadingView: CommonLoadingView//
    private lateinit var mContentView: TextView//
//    private lateinit var mRotateAnim: RotateAnimation//

    private lateinit var mTitleAppearance: Pair<Int, Int>//
    private lateinit var mSubTitleAppearance: Pair<Int, Int>//

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonShadeViewStyle)
        val titleColor = typedArray.getColor(R.styleable.CommonShadeViewStyle_common_shade_title_color, Color.parseColor("#3C3D3E"))
        val titleSize = typedArray.getDimensionPixelSize(R.styleable.CommonShadeViewStyle_common_shade_title_size, 14.dp2Px())
        val subTitleColor = typedArray.getColor(R.styleable.CommonShadeViewStyle_common_shade_subtitle_color, Color.parseColor("#999999"))
        val subTitleSize = typedArray.getDimensionPixelSize(R.styleable.CommonShadeViewStyle_common_shade_subtitle_size, 12.dp2Px())
        setTitleAppearance(titleSize, titleColor)
        setSubTitleAppearance(subTitleSize, subTitleColor)
        typedArray.recycle()

        init(context)
    }

    /**
     * 
     */
    private fun init(context: Context) {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        orientation = VERTICAL
        gravity = Gravity.CENTER

        mLoadingView = CommonLoadingView(context)

        addView(mLoadingView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        mContentView = TextView(context)
        mContentView.gravity = Gravity.CENTER
        mContentView.setSingleLine(false)
        mContentView.setLineSpacing(0f, 1.5f)
        addView(mContentView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

    }

    /**
     * 
     */
    fun setOnContentClickListener(callback: ((isLoadingStatus: Boolean) -> Unit)?) {
        mLoadingView.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            callback?.invoke(true)
        }
        mContentView.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            callback?.invoke(false)
        }
    }

    /**
     * 
     */
    fun getTitleSize(): Int {
        return mTitleAppearance.first
    }

    /**
     * 
     */
    fun getTitleColor(): Int {
        return mTitleAppearance.second
    }

    /**
     * 
     */
    fun getSubTitleSize(): Int {
        return mSubTitleAppearance.first
    }

    /**
     * 
     */
    fun getSubTitleColor(): Int {
        return mSubTitleAppearance.second
    }

    /**
     * 
     * @param titleSize
     * @param titleColor
     */
    fun setTitleAppearance(titleSize: Int, titleColor: Int) {
        mTitleAppearance = Pair(titleSize, titleColor)
    }

    /**
     * 
     * @param subTitleSize
     * @param subTitleColor
     */
    fun setSubTitleAppearance(subTitleSize: Int, subTitleColor: Int) {
        mSubTitleAppearance = Pair(subTitleSize, subTitleColor)
    }

    /**
     * 
     */
    fun isLoadingStatus(): Boolean {
        return mLoadingView.visibility == View.VISIBLE
    }

    /**
     * 
     */
    fun showLoading() {
        mLoadingView.visibility = View.VISIBLE
        mContentView.visibility = View.GONE
        mLoadingView.stopAnim()
//        mLoadingView.startAnimation(mRotateAnim)
        mLoadingView.startAnim()
        show()
    }

    /**
     * 
     * @param title
     * @param subTitle
     */
    fun showContent(title: String?, subTitle: String?) {

        val span = SpannableStringBuilder("")
        span.append(StringAppearanceUtil.applyAppearance(if (title == null || title.isEmpty()) {
            ""
        } else {
            title + "\n"
        }, mTitleAppearance.first, mTitleAppearance.second))
        span.append(StringAppearanceUtil.applyAppearance(subTitle
                ?: "", mSubTitleAppearance.first, mSubTitleAppearance.second))

        mContentView.text = span
        mLoadingView.stopAnim()
        mLoadingView.visibility = View.GONE
        mContentView.visibility = View.VISIBLE
        show()
    }

    /**
     * 
     * @param content
     */
    fun showContent(content: CharSequence) {

        mContentView.text = content
        mLoadingView.stopAnim()
        mLoadingView.visibility = View.GONE
        mContentView.visibility = View.VISIBLE
        show()
    }

    /**
     * 
     */
    fun hide() {
        visibility = View.GONE
        mLoadingView.stopAnim()
    }

    /**
     * 
     */
    fun show() {
        visibility = View.VISIBLE
    }

    /**
     * 
     */
    fun isShow(): Boolean {
        return visibility == View.VISIBLE
    }
}

