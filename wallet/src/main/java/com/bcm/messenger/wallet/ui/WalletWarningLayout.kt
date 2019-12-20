package com.bcm.messenger.wallet.ui

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.wallet.R

/**
 * Created by wjh on 2018/6/2
 */
class WalletWarningLayout : FrameLayout {

    companion object {
        const val MODE_REMINDER = 1//表示提醒模式
        const val MODE_AGREEMENT = 2//表示同意模式
    }

    private var mReminder: TextView? = null
    private var mAgreeView: CheckBox? = null
    private var mMode: Int = MODE_AGREEMENT

    var listener: WarningActionListener? = null

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

        initView()
        val array = context.obtainStyledAttributes(attributeSet, R.styleable.WalletWarningLayout)
        setMode(array.getInt(R.styleable.WalletWarningLayout_wallet_warning_modeType, MODE_AGREEMENT))
        array.recycle()
    }

    private fun initView() {
        val padding = 8.dp2Px()
        mReminder = TextView(context)
        mReminder?.text = StringAppearanceUtil.applyAppearance(context.getString(R.string.wallet_reminder_description),
                12.sp2Px(), Color.parseColor("#190000"))
        val d = AppUtil.getDrawable(resources, R.drawable.wallet_reminder_icon)
        d.setBounds(0, 0, 12.dp2Px(), 12.dp2Px())
        mReminder?.setCompoundDrawables(d, null, null, null)
        mReminder?.compoundDrawablePadding = 5.dp2Px()
        mReminder?.setOnClickListener {
            listener?.onClick(true)
        }
        mReminder?.setPadding(0, padding, 0, padding)
        addView(mReminder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        mAgreeView = CheckBox(context)
        val checkBox = AppUtil.getDrawable(resources, R.drawable.wallet_agree_button_selector)
        checkBox.setBounds(0, 0, 12.dp2Px(), 12.dp2Px())
        mAgreeView?.buttonDrawable = null
        mAgreeView?.compoundDrawablePadding = 8.dp2Px()
        mAgreeView?.setCompoundDrawables(checkBox, null, null, null)
        val builder = SpannableStringBuilder()
        builder.append(StringAppearanceUtil.applyAppearance(context.getString(R.string.wallet_policy_first_part),
                12.sp2Px(), Color.parseColor("#190000")))
        builder.append(StringAppearanceUtil.applyAppearance(context.getString(R.string.wallet_policy_second_part),
                12.sp2Px(), getColor(R.color.common_app_primary_color)))
        mAgreeView?.text = builder
        mAgreeView?.setOnCheckedChangeListener { buttonView, isChecked ->
            listener?.onClick(isChecked)
        }
        mAgreeView?.setPadding(0, padding, 0, padding)
        addView(mAgreeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun isChecked(): Boolean {
        if (mMode == MODE_AGREEMENT) {
            return mAgreeView?.isChecked ?: false
        }
        return true
    }

    fun setMode(mode: Int) {
        mMode = mode
        when (mode) {
            MODE_AGREEMENT -> {
                mReminder?.visibility = View.GONE
                mAgreeView?.visibility = View.VISIBLE
            }
            MODE_REMINDER -> {
                mReminder?.visibility = View.VISIBLE
                mAgreeView?.visibility = View.GONE
            }
        }
    }

    interface WarningActionListener {
        fun onClick(checked: Boolean)
    }
}