package com.bcm.messenger.ui.widget

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.ViewPager
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.ui.HomeAddAccountView
import com.bcm.messenger.ui.HomeProfileView
import com.bcm.messenger.utility.AppContextHolder
import kotlinx.android.synthetic.main.home_profile_add_view.view.*
import kotlinx.android.synthetic.main.home_profile_view.view.*

/**
 * Created by Kin on 2019/12/30
 */
class HomeProfilePageTransformer : ViewPager.PageTransformer {
    private val dp10 = 10.dp2Px()

    private val avatarMargin = (AppContextHolder.APP_CONTEXT.getScreenWidth() - 120.dp2Px()) / 2
    private val badgeMarginEnd = (160.dp2Px() * 0.3f / 2).toInt()
    private val badgeMarginStart = 150.dp2Px() - badgeMarginEnd - 27.dp2Px()

    override fun transformPage(page: View, position: Float) {
//        val innerPos = when {
//            position < -1f -> -1f
//            position > 1f -> 1f
//            else -> position
//        }
//
//        val percent = if (innerPos < 0) {
//            (1 + innerPos) / 1
//        } else {
//            (1 - innerPos) / 1
//        }
//
//        val scale = 0.7f + 0.3f * percent
//        val curAvatarMargin = avatarMargin * (1 - percent)
//
//        if (page is HomeProfileView) {
//            page.home_profile_avatar.layoutParams = (page.home_profile_avatar.layoutParams as ConstraintLayout.LayoutParams).apply {
//                if (innerPos < 0) {
//                    marginStart = curAvatarMargin.toInt()
//                } else {
//                    marginEnd = curAvatarMargin.toInt()
//                }
//            }
//            page.home_profile_avatar.scaleX = scale
//            page.home_profile_avatar.scaleY = scale
//
//            page.home_profile_unread.layoutParams = (page.home_profile_unread.layoutParams as ConstraintLayout.LayoutParams).apply {
//                marginEnd = if (innerPos < 0) {
//                    dp10 + (badgeMarginEnd * (1 - percent)).toInt()
//                } else {
//                    dp10 + (badgeMarginStart * (1 - percent)).toInt()
//                }
//                setMargins(leftMargin, dp10 + (badgeMarginEnd * (1 - percent)).toInt(), marginEnd, bottomMargin)
//            }
//        } else if (page is HomeAddAccountView) {
//            page.home_add_view_add.layoutParams = (page.home_add_view_add.layoutParams as ConstraintLayout.LayoutParams).apply {
//                if (innerPos < 0) {
//                    marginStart = curAvatarMargin.toInt()
//                } else {
//                    marginEnd = curAvatarMargin.toInt()
//                }
//            }
//            page.home_add_view_add.scaleX = scale
//            page.home_add_view_add.scaleY = scale
//        }
//
//        (page as IProfileView).setPagerChangedAlpha(percent)
        (page as IProfileView).positionChanged(position)
    }
}