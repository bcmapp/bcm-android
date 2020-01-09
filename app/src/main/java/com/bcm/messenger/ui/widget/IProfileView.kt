package com.bcm.messenger.ui.widget

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by Kin on 2020/1/2
 */
const val centerPosition = 0.20588236f
const val leftPosition = -0.7941176f
const val rightPosition = 1.2058823f

//const val centerPosition = 0.20588236f
//const val leftPosition = -0.79411764f
//const val rightPosition = 1.20588236f

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

    fun checkAccountBackup()

    fun positionChanged(position: Float) {
        val innerPos = when {
            position < leftPosition -> leftPosition
            position > rightPosition -> rightPosition
            else -> position
        }

        val percent = if (innerPos < centerPosition) {
            (1 - centerPosition + innerPos) / 1
        } else {
            (centerPosition - innerPos + 1) / 1
        }

        if (!isActive) {
            if ((this.position == leftPosition || this.position == rightPosition) && position == centerPosition) {
                return
            }
        }
        this.position = innerPos
        onViewPositionChanged(innerPos, percent)
    }
}