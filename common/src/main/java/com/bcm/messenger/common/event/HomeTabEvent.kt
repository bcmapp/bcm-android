package com.bcm.messenger.common.event

/**
 * tab
 * Created by wjh on 2019/5/30
 */
data class HomeTabEvent(val position: Int, val isDoubleClick: Boolean = false, val showFigure: Int? = null, val showDot: Boolean? = null) {
    companion object {
        const val TAB_CHAT = 0
        const val TAB_CONTACT = 1
        const val TAB_ME = 2
        const val TAB_ADHOC = 3
    }
}