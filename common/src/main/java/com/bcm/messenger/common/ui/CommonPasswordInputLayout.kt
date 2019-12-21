package com.bcm.messenger.common.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import kotlinx.android.synthetic.main.common_password_layout.view.*

/**
 * Created by wjh on 2018/6/6
 */
class CommonPasswordInputLayout : ConstraintLayout {

   
    private var showSecret: Boolean = false

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.common_password_layout, this)
        initView()
    }

    private val passwordWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (password_text.text.isNotEmpty()) {
                password_clear.visibility = View.VISIBLE
            } else {
                password_clear.visibility = View.GONE
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        password_text.addTextChangedListener(passwordWatcher)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        password_text.removeTextChangedListener(passwordWatcher)
    }

 
    fun getPassword(): CharSequence {
        return password_text.text.toString()
    }

    fun setPassword(password: CharSequence?) {
        this.password_text.setText(password)
    }

    fun getHint(): CharSequence {
        return password_text.hint
    }

    fun setHint(hint: CharSequence?) {
        this.password_text.hint = hint
    }

    fun setSecretEnable(enable: Boolean) {
        if (enable) {
            password_eye.visibility = View.VISIBLE
        } else {
            password_eye.visibility = View.GONE
        }
    }

    fun showLoading(show: Boolean) {
        if (show) {
            password_action.visibility = View.VISIBLE
            val anim = RotateAnimation(0f, 359f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            anim.interpolator = LinearInterpolator()
            anim.repeatCount = Animation.INFINITE
            anim.duration = 1200
            anim.fillAfter = true
            password_action.clearAnimation()
            password_action.startAnimation(anim)
        } else {
            password_action.clearAnimation()
            password_action.visibility = View.GONE
        }
    }

    fun showWarning(warningTip: CharSequence?) {
        if (warningTip == null) {
            password_warning.visibility = View.GONE
        } else {
            password_warning.visibility = View.VISIBLE
            password_warning.text = warningTip
        }
    }

    private fun initView() {
        openPasswordEye(showSecret)
        password_eye.setOnClickListener {
            openPasswordEye(!showSecret)
        }
        password_clear.setOnClickListener {
            password_text.setText("")
        }
        //setBackgroundResource(R.drawable.common_password_bg)
    }

   
    fun openPasswordEye(open: Boolean) {
        this.showSecret = open
        if (open) {
            password_eye.setImageResource(R.drawable.common_visible_icon)
            password_text.inputType = EditorInfo.TYPE_TEXT_VARIATION_NORMAL or EditorInfo.TYPE_CLASS_TEXT

        } else {
            password_eye.setImageResource(R.drawable.common_invisible_icon)

            password_text.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_CLASS_TEXT
        }

    }

    fun addTextChangedListener(watcher: TextWatcher?) {
        password_text.addTextChangedListener(watcher ?: return)
    }

    fun removeTextChangedListener(watcher: TextWatcher?) {
        password_text.removeTextChangedListener(watcher ?: return)
    }
}