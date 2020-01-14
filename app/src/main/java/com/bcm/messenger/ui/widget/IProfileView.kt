package com.bcm.messenger.ui.widget

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2020/1/2
 */
val centerPosition = 60f.dp2Px() / (AppContextHolder.APP_CONTEXT.getScreenWidth() - 120.dp2Px())
val leftPosition = centerPosition - 1
val rightPosition = centerPosition + 1

//const val centerPosition = 0.20588236f
//const val leftPosition = -0.79411764f
//const val rightPosition = 1.20588236f

interface IProfileView {
    var isActive: Boolean
    var isLogin: Boolean
    var position: Float
    var pagePosition: Int

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

    fun setUnreadCount(unreadCount: Int)

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
            if ((this.position == leftPosition || this.position == rightPosition) && (position == centerPosition || position == 0f || position == -1f)) {
                return
            }
        } else {
            if (this.position == centerPosition && (position == 0f || position == -1f)) {
                return
            }
        }
        this.position = innerPos
        onViewPositionChanged(innerPos, percent)
    }
}