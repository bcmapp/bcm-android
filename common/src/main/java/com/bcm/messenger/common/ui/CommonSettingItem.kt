package com.bcm.messenger.common.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.CompoundButton
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.setDrawableLeft
import kotlinx.android.synthetic.main.common_setting_item.view.*
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getAttribute

/**
 * Created by wjh on 2018/6/6
 */
class CommonSettingItem @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        const val RIGHT_NONE = 0
        const val RIGHT_LOADING = 1
        const val RIGHT_ARROW = 2
        const val RIGHT_YES = 3
        const val RIGHT_ARROW_WHITE = 4
        const val RIGHT_ARROW_BLACK = 5
        const val RIGHT_CUSTOM = 6
    }

    private var switchListener: CompoundButton.OnCheckedChangeListener? = null

    private var mHeadColor: Int = 0
    private var mNameColor: Int = 0
    private var mSubNameColor: Int = 0
    private var mTipColor: Int = 0
    private var mTipIcon: Int = 0
    private var mIconColor = 0
    private var mTipIconColor = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.common_setting_item, this)
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonSettingItemStyle)
        initView(typedArray)
        typedArray.recycle()

    }

    private fun initView(typeArray: TypedArray) {
        val logoRes = typeArray.getResourceId(R.styleable.CommonSettingItemStyle_setting_item_logo, 0)
        val logoSize = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_logo_size, 0)
        mIconColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_logo_color, context.getAttrColor(R.attr.common_icon_color))
        val head = typeArray.getString(R.styleable.CommonSettingItemStyle_setting_item_head) ?: ""
        mHeadColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_head_color, context.getAttrColor(R.attr.common_text_third_color))
        val name = typeArray.getString(R.styleable.CommonSettingItemStyle_setting_item_name) ?: ""
        val subName = typeArray.getString(R.styleable.CommonSettingItemStyle_setting_item_sub_name) ?: ""
        mNameColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_name_color, context.getAttrColor(R.attr.common_setting_item_main_text_color))
        mSubNameColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_sub_name_color, context.getAttrColor(R.attr.common_setting_item_detail_text_color))
        val showLine = typeArray.getBoolean(R.styleable.CommonSettingItemStyle_setting_item_line, true)
        val tipText = typeArray.getString(R.styleable.CommonSettingItemStyle_setting_item_tip_text) ?: ""
        mTipColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_tip_color, context.getAttrColor(R.attr.common_setting_item_warn_color))
        mTipIcon = typeArray.getResourceId(R.styleable.CommonSettingItemStyle_setting_item_tip_icon, 0)
        mTipIconColor = typeArray.getColor(R.styleable.CommonSettingItemStyle_setting_item_tip_icon_color, context.getAttrColor(R.attr.common_setting_item_warn_color))
        val switchStatus = typeArray.getInt(R.styleable.CommonSettingItemStyle_setting_item_switch, 0)
        val rightStatus = typeArray.getInt(R.styleable.CommonSettingItemStyle_setting_item_right, RIGHT_ARROW)
        val customRightDrawable = typeArray.getResourceId(R.styleable.CommonSettingItemStyle_setting_item_right_custom, 0)

        val headBackground = typeArray.getResourceId(R.styleable.CommonSettingItemStyle_setting_item_head_background, context.getAttribute(R.attr.common_setting_item_background))
        val bodyBackground = typeArray.getResourceId(R.styleable.CommonSettingItemStyle_setting_item_body_background, R.drawable.common_item_ripple_bg)

        val headPaddingTop = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_head_padding_top, 26.dp2Px())
        val headPaddingBottom = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_head_padding_bottom, 10.dp2Px())
        val itemPaddingTop = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_padding_top, 16.dp2Px())
        val itemPaddingBottom = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_padding_bottom, 16.dp2Px())
        val itemLogoMargin = typeArray.getDimensionPixelSize(R.styleable.CommonSettingItemStyle_setting_item_logo_margin, resources.getDimensionPixelSize(R.dimen.common_horizontal_gap))
        measureInternalLayout(Pair(headPaddingTop, headPaddingBottom), Pair(itemPaddingTop, itemPaddingBottom), itemLogoMargin)

        showLine(showLine)
        if (background == null) {
            item_head.setBackgroundResource(headBackground)
        }
        if (head.isBlank()) {
            hideHead()
        } else {
            setHead(head, mHeadColor)
        }
        if (background == null) {
            item_content_layout.setBackgroundResource(bodyBackground)
        }
        setLogo(logoRes, logoSize)
        setName(name, mNameColor)
        setSubName(subName, mSubNameColor)
        setTip(tipText, mTipIcon, mTipColor)

        item_switch.setOnCheckedChangeListener { buttonView, isChecked ->
            changeSwitch(isChecked)
            switchListener?.onCheckedChanged(buttonView, isChecked)
        }

        if (customRightDrawable != 0) {
            showRightIcon(customRightDrawable)
        } else {
            showRightStatus(rightStatus)
        }

        when (switchStatus) {
            0 -> item_switch.visibility = View.GONE
            1 -> {
                item_switch.visibility = View.VISIBLE
                item_switch.isChecked = true
                changeSwitch(true)
            }
            else -> {
                item_switch.visibility = View.VISIBLE
                item_switch.isChecked = false
                changeSwitch(false)
            }
        }

    }

    fun setOnTipClickListener(listener: OnClickListener) {
        item_tip.setOnClickListener(listener)
    }

    fun setOnSubTitleClickListener(listener: OnClickListener) {
        item_sub_name.setOnClickListener(listener)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        item_content_layout.setOnClickListener(l)
    }

    /**
     * Register a callback to be invoked when the checked state of this button
     * changes.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        switchListener = listener
    }


    fun showLine(show: Boolean) {
        item_line?.visibility = if (show) View.VISIBLE else View.GONE
    }


    fun setLogo(logoRes: Int, size: Int? = null) {
        if (logoRes == 0) {
            item_left.visibility = View.GONE
        } else {
            item_left.visibility = View.VISIBLE
            item_left_body.setImageResource(logoRes)
            item_left_body.drawable.setTint(mIconColor)
            if (size != null && size > 0) {
                item_left_body.layoutParams = item_left_body.layoutParams.apply {
                    height = size
                    width = size
                }
            }
        }
    }

    fun getName(): CharSequence {
        return item_name.text
    }

    fun getHeader(): CharSequence {
        return item_head.text
    }

    fun hideHead() {
        item_head.visibility = View.GONE
    }

    fun setHead(head: CharSequence, headColor: Int? = null) {
        if (head.isBlank()) {
            item_head.visibility = View.GONE
        } else {
            item_head.visibility = View.VISIBLE
            item_head.text = head
            if (headColor == null) {
                item_head.setTextColor(mHeadColor)
            } else {
                item_head.setTextColor(headColor)
            }
        }
    }


    fun setName(name: CharSequence, nameColor: Int? = null) {
        item_name.text = name
        if (nameColor == null) {
            item_name.setTextColor(mNameColor)
        } else {
            item_name.setTextColor(nameColor)
        }
    }

    fun setSubName(subName: CharSequence, subNameColor: Int? = null) {
        if (subName.isEmpty()) {
            item_sub_name.visibility = View.GONE
        } else {
            item_sub_name.visibility = View.VISIBLE
            item_sub_name.text = subName
            if (subNameColor == null) {
                item_sub_name.setTextColor(mSubNameColor)
            } else {
                item_sub_name.setTextColor(subNameColor)
            }
        }
    }

    /**
     *
     */
    fun hideTip() {
        item_tip.visibility = View.GONE
    }

    /**
     *
     */
    fun setTip(content: CharSequence, iconRes: Int = mTipIcon, contentColor: Int? = null) {
        item_tip.visibility = View.VISIBLE
        item_tip.setDrawableLeft(iconRes, 10.dp2Px(), mTipIconColor)
        item_tip.text = content
        if (contentColor == null) {
            item_tip.setTextColor(mTipColor)
        } else {
            item_tip.setTextColor(contentColor)
        }
    }


    /**
     *
     */
    private fun changeSwitch(isOn: Boolean) {
        if (isOn) {
            item_switch.trackTintList = resources.getColorStateList(R.color.common_default_switcher_color)
        } else {
            item_switch.trackTintList = resources.getColorStateList(R.color.common_color_F1F2F3)
        }
    }

    /**
     *
     */
    fun setSwitchStatus(isOn: Boolean) {
        item_switch.visibility = View.VISIBLE
        item_switch.isChecked = isOn
        changeSwitch(isOn)
    }

    /**
     *
     */
    fun getSwitchStatus(): Boolean {
        return item_switch.isChecked
    }

    /**
     *
     */
    fun showRightIcon(iconRes: Int) {
        if (iconRes != 0) {
            item_right.visibility = View.VISIBLE
            item_right_body.setImageResource(iconRes)
            item_right_body.drawable.setTint(context.getAttrColor(R.attr.common_icon_color))
        } else {
            item_right.visibility = View.GONE
        }
    }

    /**
     * (RIGHT_NONE, RIGHT_LOADING, RIGHT_ARROW)
     */
    fun showRightStatus(status: Int) {
        when (status) {
            RIGHT_NONE -> {
                item_right.visibility = View.GONE
                item_right_body.clearAnimation()
            }
            RIGHT_LOADING -> {
                item_right.visibility = View.VISIBLE
                item_right_body.setImageResource(R.drawable.common_loading_icon)
                val loading = AnimationUtils.loadAnimation(context, R.anim.common_loading_rotate)
                loading.interpolator = LinearInterpolator()
                item_right_body.clearAnimation()
                item_right_body.startAnimation(loading)
            }
            RIGHT_ARROW -> {
                item_right.visibility = View.VISIBLE
                item_right_body.clearAnimation()
                item_right_body.setImageResource(R.drawable.common_arrow_right_icon)
                item_right_body.drawable.setTint(context.getAttrColor(R.attr.common_icon_color_grey))
            }
            RIGHT_ARROW_WHITE -> {
                item_right.visibility = View.VISIBLE
                item_right_body.clearAnimation()
                item_right_body.setImageResource(R.drawable.common_arrow_right_icon)
                item_right_body.drawable.setTint(context.getAttrColor(R.attr.common_white_color))
            }
            RIGHT_YES -> {
                item_right.visibility = View.VISIBLE
                item_right_body.clearAnimation()
                item_right_body.setImageResource(R.drawable.common_tick_icon)
            }
        }
    }

    /**
     *
     */
    fun setSwitchEnable(enable: Boolean) {
        item_switch.isEnabled = enable
        item_switch.isClickable = enable
    }

    /**
     *
     */
    private fun setItemBodyPadding(paddingTop: Int, paddingBottom: Int) {

        var lp = item_left.layoutParams as LayoutParams
        lp.topMargin = paddingTop
        lp.bottomMargin = paddingBottom
        item_left.layoutParams = lp

        lp = item_center.layoutParams as LayoutParams
        lp.topMargin = paddingTop
        lp.bottomMargin = paddingBottom
        item_center.layoutParams = lp
    }

    /**
     *
     */
    private fun setItemHeadPadding(paddingTop: Int, paddingBottom: Int) {
//        val lp = item_head.layoutParams as ConstraintLayout.LayoutParams
//        lp.topMargin = paddingTop
//        lp.bottomMargin = paddingBottom
//        item_head.layoutParams = lp
        item_head.setPadding(item_head.paddingStart, paddingTop, item_head.paddingEnd, paddingBottom)
    }

    /**
     *
     */
    private fun setLogoMargin(margin: Int) {
        item_left.setPadding(item_left.paddingStart, item_left.paddingTop, margin, item_left.paddingBottom)
    }

    /**
     *
     */
    private fun measureInternalLayout(headPaddingPair: Pair<Int, Int>, itemPaddingPair: Pair<Int, Int>, logoMargin: Int) {

        setItemHeadPadding(headPaddingPair.first, headPaddingPair.second)
        setItemBodyPadding(itemPaddingPair.first, itemPaddingPair.second)
        setLogoMargin(logoMargin)

//        var paddingStart = this.paddingStart
//        var paddingEnd = this.paddingEnd
//        if (paddingStart == 0) {
//            paddingStart = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
//        }
//        if (paddingEnd == 0) {
//            paddingEnd = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
//        }
//        setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom)
    }
}