package com.bcm.messenger.ui.widget

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by Kin on 2020/1/2
 */
interface IProfileView {
    var isActive: Boolean
    var isLogin: Boolean
    var position: Float

    fun initView()

    fun showAllViews()

    fun hideAllViews()

    fun setViewsAlpha(alpha: Float)

    fun showAllViewsWithAnimation()

    fun showAvatar()

    fun hideAvatar()

    fun setMargin(topMargin: Int)

    fun resetMargin()

    fun setPagerChangedAlpha(alpha: Float)

    fun onViewPositionChanged(position: Float, percent: Float)

    fun getCurrentContext(): AccountContext?

    fun getCurrentRecipient(): Recipient?

    fun positionChanged(position: Float) {
        val innerPos = when {
            position < -1f -> -1f
            position > 1f -> 1f
            else -> position
        }

        val percent = if (innerPos < 0) {
            (1 + innerPos) / 1
        } else {
            (1 - innerPos) / 1
        }

        if (!isActive) {
            if ((this.position == -1f || this.position == 1f) && position == 0f) {
                return
            }
        }
        this.position = innerPos
        onViewPositionChanged(innerPos, percent)
    }
}