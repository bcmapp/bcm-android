package com.bcm.messenger.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.R
import kotlinx.android.synthetic.main.home_avatar_badge_layout.view.*

/**
 * Created by Kin on 2019/12/11
 */
class HomeAvatarBadgeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, style: Int = 0) : ConstraintLayout(context, attrs, style) {
    var isAccountBackup = true
        set(value) {
            field = value
            updateUI()
        }
    var unreadCount = 0
        set(value) {
            field = value
            updateUI()
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.home_avatar_badge_layout, this, true)

        initView()
    }

    fun setTextSize(spSize: Float) {
        badge_count.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
    }

    private fun initView() {
        badge_count.visibility = View.GONE
        badge_warning.visibility = View.GONE

        badge_count.minWidth = minWidth
        badge_count.minHeight = minHeight
    }

    private fun updateUI() {
        when {
            unreadCount > 0 -> {
                badge_warning.visibility = View.GONE
                badge_count.visibility = View.VISIBLE
                badge_count.text = unreadCount.toString()
            }
            !isAccountBackup -> {
                badge_warning.visibility = View.VISIBLE
                badge_count.visibility = View.GONE
            }
            else -> {
                badge_warning.visibility = View.GONE
                badge_count.visibility = View.GONE
            }
        }
    }
}