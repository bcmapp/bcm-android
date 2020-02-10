package com.bcm.messenger.common.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.MultiClickObserver
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.common_title_bar_2.view.*

/**
 * Created by Kin on 2019/5/27
 */
class CommonTitleBar2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    abstract class TitleBarClickListener {
        open fun onClickLeft() {}
        open fun onClickCenter() {}
        open fun onClickRight() {}
    }

    companion object {
        const val TYPE_HIDE = -1
        const val TYPE_NONE = 0
        const val TYPE_TEXT = 1
        const val TYPE_IMAGE = 2
        const val TYPE_CUSTOM = 3

        const val ALIGN_CENTER = 0
        const val ALIGN_LEFT = 1
        const val ALIGN_RIGHT = 2

        const val STYLE_NORMAL = 0
        const val STYLE_BOLD = 1
    }

    private var isFillStatusBar = true
    private var titleBarBackground = 0
    private var titleBarHeight = 0
    private var isShowBottomLine = false
    private var bottomLineColor = 0
    private var bottomLineHeight = 0
    private var elevationSize = 0f

    private var leftType = TYPE_NONE
    private var leftText = ""
    private var leftTextColor = 0
    private var leftTextSize = 0f
    private var leftTextImage = 0
    private var leftImage = 0
    private var leftImageColor = 0
    private var leftCustomViewRes = 0
    private var leftCustomViewId = 0
    private var leftCustomView: View? = null

    private var rightType = TYPE_NONE
    private var rightText = ""
    private var rightTextColor = 0
    private var rightTextSize = 0f
    private var rightTextImage = 0
    private var rightImage = 0
    private var rightImageColor = 0
    private var rightCustomViewRes = 0
    private var rightCustomViewId = 0
    private var rightCustomView: View? = null

    private var centerType = TYPE_TEXT
    private var centerText = ""
    private var centerTextColor = 0
    private var centerTextSize = 0f
    private var centerTextAlign = 0
    private var centerTextStyle = 0
    private var centerCustomViewRes = 0
    private var centerCustomViewId = 0
    private var centerCustomView: View? = null

    private var listener: TitleBarClickListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.common_title_bar_2, this, true)

        initAttrs(attrs)
        initMainView(context)
        initLeftView(context)
        initRightView(context)
        initCenterView(context)
    }

    private fun initAttrs(attrs: AttributeSet?) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.CommonTitleBar2)

        isFillStatusBar = array.getBoolean(R.styleable.CommonTitleBar2_fill_status_bar, true)
        titleBarBackground = array.getResourceId(R.styleable.CommonTitleBar2_title_bar_background, context.getAttribute(R.attr.common_title_bar_background))
        titleBarHeight = array.getDimension(R.styleable.CommonTitleBar2_title_bar_height, 44f.dp2Px()).toInt()
        isShowBottomLine = array.getBoolean(R.styleable.CommonTitleBar2_show_bottom_line, false)
        bottomLineColor = array.getColor(R.styleable.CommonTitleBar2_bottom_line_color, context.getAttrColor(R.attr.common_item_line_color))
        bottomLineHeight = array.getDimension(R.styleable.CommonTitleBar2_bottom_line_height, 0f).toInt()
        elevationSize = array.getDimension(R.styleable.CommonTitleBar2_title_bar_elevation, 0f)

        leftType = array.getInt(R.styleable.CommonTitleBar2_left_type, 0)
        when (leftType) {
            TYPE_TEXT -> {
                leftText = array.getString(R.styleable.CommonTitleBar2_left_text) ?: ""
                leftTextColor = array.getColor(R.styleable.CommonTitleBar2_left_text_color, context.getAttrColor(R.attr.common_title_bar_left_text_color))
                leftTextSize = array.getDimension(R.styleable.CommonTitleBar2_left_text_size, 17f.sp2Px())
                leftTextImage = array.getResourceId(R.styleable.CommonTitleBar2_left_text_image, 0)
            }
            TYPE_IMAGE -> {
                leftImage = array.getResourceId(R.styleable.CommonTitleBar2_left_image, R.drawable.common_arrow_back_icon)
                leftImageColor = array.getColor(R.styleable.CommonTitleBar2_left_image_color, context.getAttrColor(R.attr.common_title_bar_left_icon_color))
            }
            TYPE_CUSTOM -> leftCustomViewRes = array.getResourceId(R.styleable.CommonTitleBar2_left_custom_view, 0)
        }

        rightType = array.getInt(R.styleable.CommonTitleBar2_right_type, 0)
        when (rightType) {
            TYPE_TEXT -> {
                rightText = array.getString(R.styleable.CommonTitleBar2_right_text) ?: ""
                rightTextColor = array.getColor(R.styleable.CommonTitleBar2_right_text_color, context.getAttrColor(R.attr.common_title_bar_right_text_color))
                rightTextSize = array.getDimension(R.styleable.CommonTitleBar2_right_text_size, 17f.sp2Px())
                rightTextImage = array.getResourceId(R.styleable.CommonTitleBar2_right_text_image, 0)
            }
            TYPE_IMAGE -> {
                rightImage = array.getResourceId(R.styleable.CommonTitleBar2_right_image, 0)
                rightImageColor = array.getColor(R.styleable.CommonTitleBar2_right_image_color, context.getAttrColor(R.attr.common_title_bar_right_icon_color))
            }
            TYPE_CUSTOM -> rightCustomViewRes = array.getResourceId(R.styleable.CommonTitleBar2_right_custom_view, 0)
        }

        centerType = array.getInt(R.styleable.CommonTitleBar2_center_type, 0)
        when (centerType) {
            TYPE_TEXT -> {
                centerText = array.getString(R.styleable.CommonTitleBar2_center_text) ?: ""
                centerTextColor = array.getColor(R.styleable.CommonTitleBar2_center_text_color, context.getAttrColor(R.attr.common_title_bar_center_text_color))
                centerTextSize = array.getDimension(R.styleable.CommonTitleBar2_center_text_size, 17f.sp2Px())
                centerTextAlign = array.getInt(R.styleable.CommonTitleBar2_center_text_align, 0)
                centerTextStyle = array.getInt(R.styleable.CommonTitleBar2_center_text_style, 0)
            }
            TYPE_CUSTOM -> centerCustomViewRes = array.getResourceId(R.styleable.CommonTitleBar2_center_custom_view, 0)
        }

        array.recycle()
    }

    private fun initMainView(context: Context) {
        if (isFillStatusBar) {
            title_bar_status_fill.layoutParams = title_bar_status_fill.layoutParams.apply {
                height = context.getStatusBarHeight()
            }
            if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.window.setTranslucentStatus()
            }
        }

        if (titleBarBackground != 0) {
            title_bar_background.setBackgroundResource(titleBarBackground)
        }

        if (isShowBottomLine) {
            title_bar_bottom_line.setBackgroundResource(bottomLineColor)
            title_bar_bottom_line.layoutParams.apply {
                height = bottomLineHeight
            }
        } else if (elevationSize > 0f) {
            elevation = elevationSize
        }
    }

    private fun initLeftView(context: Context) {
        title_bar_left_zone.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickLeft()
        }

        when (leftType) {
            TYPE_HIDE -> {
                title_bar_left_img.visibility = View.GONE
                title_bar_left_text.visibility = View.GONE

                title_bar_center_text.layoutParams = title_bar_center_text.layoutParams.apply {
                    setConstraintSet(ConstraintSet().apply {
                        connect(title_bar_center_text.id, ConstraintSet.START, this@CommonTitleBar2.id, ConstraintSet.START)
                        connect(title_bar_center_text.id, ConstraintSet.END, title_bar_right_zone.id, ConstraintSet.START)
                        connect(title_bar_center_text.id, ConstraintSet.TOP, title_bar_status_fill.id, ConstraintSet.BOTTOM)
                        connect(title_bar_center_text.id, ConstraintSet.BOTTOM, title_bar_bottom_line.id, ConstraintSet.TOP)
                    })
                }
                title_bar_center_text.setPadding(15.dp2Px(), 0, 15.dp2Px(), 0)
                centerTextAlign = ALIGN_LEFT

                title_bar_left_zone.isClickable = false
            }
            TYPE_NONE -> {
                title_bar_left_img.visibility = View.GONE
                title_bar_left_text.visibility = View.GONE

                title_bar_left_zone.isClickable = false
            }
            TYPE_TEXT -> {
                title_bar_left_img.visibility = View.GONE
                title_bar_left_text.text = leftText
                title_bar_left_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, leftTextSize)
                title_bar_left_text.setTextColor(leftTextColor)
            }
            TYPE_IMAGE -> {
                title_bar_left_text.visibility = View.GONE
                title_bar_left_img.setImageResource(leftImage)
                title_bar_left_img.drawable.setTint(leftImageColor)
            }
            TYPE_CUSTOM -> {
                val view = LayoutInflater.from(context).inflate(leftCustomViewRes, this, false)
                addLeftCustomView(view)
            }
        }
    }

    private fun initRightView(context: Context) {
        title_bar_right_zone.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickRight()
        }

        when (rightType) {
            TYPE_NONE -> {
                title_bar_right_img.visibility = View.GONE
                title_bar_right_text.visibility = View.GONE

                title_bar_right_zone.isClickable = false
            }
            TYPE_TEXT -> {
                title_bar_right_img.visibility = View.GONE
                title_bar_right_text.text = rightText
                title_bar_right_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, rightTextSize)
                title_bar_right_text.setTextColor(rightTextColor)
            }
            TYPE_IMAGE -> {
                title_bar_right_text.visibility = View.GONE
                title_bar_right_img.setImageResource(rightImage)
                title_bar_right_img.drawable.setTint(rightImageColor)
            }
            TYPE_CUSTOM -> {
                val view = LayoutInflater.from(context).inflate(rightCustomViewRes, this, false)
                addRightCustomView(view)
            }
        }
    }

    private fun initCenterView(context: Context) {
        when (centerType) {
            TYPE_NONE -> title_bar_center_text.visibility = View.GONE
            TYPE_TEXT -> {
                title_bar_center_text.text = centerText
                title_bar_center_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, centerTextSize)
                title_bar_center_text.setTextColor(centerTextColor)
                title_bar_center_text.gravity = when (centerTextAlign) {
                    ALIGN_CENTER -> Gravity.CENTER
                    ALIGN_LEFT -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                    ALIGN_RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
                    else -> Gravity.CENTER
                }
                title_bar_center_text.typeface = if (centerTextStyle == STYLE_BOLD) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }
                title_bar_center_text.setOnClickListener {
                    if (QuickOpCheck.getDefault().isQuick) {
                        return@setOnClickListener
                    }
                    listener?.onClickCenter()
                }
            }
            TYPE_CUSTOM -> {
                val view = LayoutInflater.from(context).inflate(centerCustomViewRes, this, false)
                addCenterCustomView(view)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        layoutParams = layoutParams.apply {
            height = titleBarHeight + if (isFillStatusBar) context.getStatusBarHeight() else 0
            setPadding(0, 0, 0, 0)
        }

        title_bar_right_zone.post {
            if (leftType != TYPE_HIDE) {
                val margin = if (title_bar_left_zone.width > title_bar_right_zone.width) {
                    title_bar_left_zone.width
                } else {
                    title_bar_right_zone.width
                }
                val centerView = centerCustomView
                if (centerView != null) {
                    centerView.layoutParams = (centerView.layoutParams as LayoutParams).apply {
                        marginStart = margin
                        marginEnd = margin
                    }
                } else {
                    title_bar_center_text.layoutParams = (title_bar_center_text.layoutParams as LayoutParams).apply {
                        marginStart = margin
                        marginEnd = margin
                    }
                }
            }
        }
    }

    fun hideLeftViews() {
        leftType = TYPE_NONE
        title_bar_left_text.visibility = View.GONE
        title_bar_left_img.visibility = View.GONE
        leftCustomView?.visibility = View.GONE

        title_bar_left_zone.isClickable = false
    }

    fun setLeftIcon(@DrawableRes resId: Int) {
        leftType = TYPE_IMAGE
        title_bar_left_text.visibility = View.GONE
        title_bar_left_img.setImageResource(resId)
        title_bar_left_img.drawable.setTint(leftImageColor)
        title_bar_left_img.visibility = View.VISIBLE

        title_bar_left_zone.isClickable = true
    }

    fun setLeftIconColor(color: Int) {
        title_bar_left_img.drawable.setTint(color)
    }

    fun setLeftText(text: String) {
        leftType = TYPE_TEXT
        title_bar_left_text.text = text
        title_bar_left_img.visibility = View.GONE
        title_bar_left_text.visibility = View.VISIBLE

        title_bar_left_zone.isClickable = true
    }

    fun setLeftCustomView(view: View) {
        leftType = TYPE_CUSTOM
        title_bar_left_text.visibility = View.GONE
        title_bar_left_img.visibility = View.GONE

        addLeftCustomView(view)

        title_bar_left_zone.isClickable = true
    }

    fun setLeftTextColor(color: Int) {
        title_bar_left_text.setTextColor(color)
    }

    fun setLeftTextSize(size: Float) {
        title_bar_left_text.setTextSize(size)
    }

    fun setLeftClickable(isClickable: Boolean) {
        title_bar_left_zone.isClickable = isClickable
    }

    fun getLeftView(): Pair<Int, View?> {
        return when (leftType) {
            TYPE_IMAGE -> Pair(TYPE_IMAGE, title_bar_left_img)
            TYPE_TEXT -> Pair(TYPE_TEXT, title_bar_left_text)
            TYPE_CUSTOM -> Pair(TYPE_CUSTOM, leftCustomView)
            else -> Pair(TYPE_NONE, null)
        }
    }

    private fun addLeftCustomView(view: View) {
        if (leftCustomViewId != 0) {
            removeView(getViewById(leftCustomViewId))
        }

        view.id = generateViewId()
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            setPadding(12.dp2Px(), 0, 12.dp2Px(), 0)
            setConstraintSet(ConstraintSet().apply {
                connect(view.id, ConstraintSet.START, this@CommonTitleBar2.id, ConstraintSet.START)
                connect(view.id, ConstraintSet.TOP, this@CommonTitleBar2.id, ConstraintSet.TOP)
                connect(view.id, ConstraintSet.BOTTOM, this@CommonTitleBar2.id, ConstraintSet.BOTTOM)
            })
        }
        addView(view, layoutParams)
        leftCustomViewId = view.id
        leftCustomView = view
    }

    fun hideRightViews() {
        rightType = TYPE_NONE
        title_bar_right_text.visibility = View.GONE
        title_bar_right_img.visibility = View.GONE
        rightCustomView?.visibility = View.GONE

        title_bar_right_zone.isClickable = false
    }

    fun isRightVisible(): Boolean {
        return title_bar_right_img.visibility == View.VISIBLE ||
                title_bar_right_text.visibility == View.VISIBLE ||
                rightCustomView?.visibility == View.VISIBLE
    }

    fun setRightInvisible() {
        title_bar_right_text.visibility = View.GONE
        title_bar_right_img.visibility = View.GONE
        rightCustomView?.visibility = View.GONE

        title_bar_right_zone.isClickable = false
    }

    fun setRightVisible() {
        when (rightType) {
            TYPE_IMAGE -> title_bar_right_img.visibility = View.VISIBLE
            TYPE_TEXT -> title_bar_right_text.visibility = View.VISIBLE
            TYPE_CUSTOM -> rightCustomView?.visibility = View.VISIBLE
        }

        title_bar_right_zone.isClickable = true
    }

    fun disableRight() {
        when (rightType) {
            TYPE_IMAGE -> title_bar_right_img.alpha = 0.5f
            TYPE_TEXT -> title_bar_right_text.alpha = 0.5f
            TYPE_CUSTOM -> rightCustomView?.alpha = 0.5f
        }

        title_bar_right_zone.isClickable = false
    }

    fun enableRight() {
        when (rightType) {
            TYPE_IMAGE -> title_bar_right_img.alpha = 1.0f
            TYPE_TEXT -> title_bar_right_text.alpha = 1.0f
            TYPE_CUSTOM -> rightCustomView?.alpha = 1.0f
        }

        title_bar_right_zone.isClickable = true
    }

    fun isRightEnable(): Boolean {
        return title_bar_right_zone.isClickable
    }

    fun setRightIcon(@DrawableRes resId: Int) {
        rightType = TYPE_IMAGE
        title_bar_right_text.visibility = View.GONE
        title_bar_right_img.setImageResource(resId)
        title_bar_right_img.drawable.setTint(rightImageColor)
        title_bar_right_img.visibility = View.VISIBLE

        title_bar_right_zone.isClickable = true
    }

    fun setRightIconColor(color: Int) {
        title_bar_right_img.drawable.setTint(color)
    }

    fun setRightText(text: String) {
        rightType = TYPE_TEXT
        title_bar_right_text.text = text
        title_bar_right_img.visibility = View.GONE
        title_bar_right_text.visibility = View.VISIBLE

        title_bar_right_zone.isClickable = true
    }

    fun setRightCustomView(view: View) {
        rightType = TYPE_CUSTOM
        title_bar_right_text.visibility = View.GONE
        title_bar_right_img.visibility = View.GONE

        addRightCustomView(view)

        title_bar_right_zone.isClickable = true
    }

    fun setRightTextColor(color: Int) {
        title_bar_right_text.setTextColor(color)
    }

    fun setRightTextSize(size: Float) {
        title_bar_right_text.setTextSize(size)
    }

    fun setRightClickable(isClickable: Boolean) {
        title_bar_right_zone.isClickable = isClickable
    }

    fun getRightView(): Pair<Int, View?> {
        return when (rightType) {
            TYPE_IMAGE -> Pair(TYPE_IMAGE, title_bar_right_img)
            TYPE_TEXT -> Pair(TYPE_TEXT, title_bar_right_text)
            TYPE_CUSTOM -> Pair(TYPE_CUSTOM, rightCustomView)
            else -> Pair(TYPE_NONE, null)
        }
    }

    private fun addRightCustomView(view: View) {
        if (rightCustomViewId != 0) {
            removeView(getViewById(rightCustomViewId))
        }

        view.id = generateViewId()
        val rightLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            setPadding(12.dp2Px(), 0, 12.dp2Px(), 0)
            setConstraintSet(ConstraintSet().apply {
                connect(view.id, ConstraintSet.END, this@CommonTitleBar2.id, ConstraintSet.END)
                connect(view.id, ConstraintSet.TOP, this@CommonTitleBar2.id, ConstraintSet.TOP)
                connect(view.id, ConstraintSet.BOTTOM, this@CommonTitleBar2.id, ConstraintSet.BOTTOM)
            })
        }
        addView(view, rightLayoutParams)
        rightCustomViewId = view.id
        rightCustomView = view
    }

    fun hideCenterViews() {
        centerType = TYPE_NONE
        title_bar_center_text.visibility = View.GONE
        centerCustomView?.visibility = View.GONE
    }

    fun setCenterText(text: String) {
        centerType = TYPE_TEXT
        title_bar_center_text.text = text
    }

    fun setCenterCustomView(view: View) {
        centerType = TYPE_CUSTOM
        title_bar_center_text.text = ""

        addCenterCustomView(view)
    }

    fun setCenterTextAlign(align: Int) {
        centerTextAlign = align
        when (centerTextAlign) {
            ALIGN_CENTER -> Gravity.CENTER
            ALIGN_LEFT -> Gravity.LEFT or Gravity.CENTER_VERTICAL
            ALIGN_RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
        }
    }

    fun getCenterView(): Pair<Int, View?> {
        return when (centerType) {
            TYPE_TEXT -> Pair(TYPE_TEXT, title_bar_center_text)
            TYPE_CUSTOM -> Pair(TYPE_CUSTOM, centerCustomView)
            else -> Pair(TYPE_NONE, null)
        }
    }

    private fun addCenterCustomView(view: View) {
        if (centerCustomViewId != 0) {
            removeView(getViewById(centerCustomViewId))
        }

        view.id = View.generateViewId()

        val layoutParams = if (leftType == TYPE_HIDE) {
            view.setPadding(15.dp2Px(), 0, 15.dp2Px(), 0)
            LayoutParams(0, titleBarHeight).apply {
                setConstraintSet(ConstraintSet().apply {
                    connect(view.id, ConstraintSet.START, this@CommonTitleBar2.id, ConstraintSet.START)
                    connect(view.id, ConstraintSet.END, title_bar_right_zone.id, ConstraintSet.START)
                    connect(view.id, ConstraintSet.TOP, title_bar_status_fill.id, ConstraintSet.BOTTOM)
                    connect(view.id, ConstraintSet.BOTTOM, title_bar_bottom_line.id, ConstraintSet.TOP)
                })
            }
        } else {
            view.setPadding(12.dp2Px(), 0, 12.dp2Px(), 0)
            LayoutParams(title_bar_center_text.layoutParams as LayoutParams)
        }


        view.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            listener?.onClickCenter()
        }
        addView(view, layoutParams)

        title_bar_center_text.visibility = View.GONE
        centerCustomViewId = view.id
        centerCustomView = view
    }

    fun setListener(listener: TitleBarClickListener) {
        this.listener = listener
    }

    fun setMultiClickListener(listener: MultiClickObserver) {
        if (centerCustomView != null) {
            centerCustomView?.setOnClickListener(listener)
        } else {
            title_bar_center_text.setOnClickListener(listener)
        }
    }

    fun setTitleBarAlpha(alpha: Float) {
        title_bar_background.alpha = alpha
        when (centerType) {
            TYPE_TEXT -> title_bar_center_text.alpha = alpha
            TYPE_CUSTOM -> centerCustomView?.alpha = alpha
        }
    }
}