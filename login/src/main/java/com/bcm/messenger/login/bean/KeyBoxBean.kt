package com.bcm.messenger.login.bean

import com.bcm.messenger.utility.proguard.NotGuard

data class KeyBoxAccountItem(var profile: LoginProfile, var backupTime: Long): NotGuard

data class KeyBoxItem(val type: Int, val data: Any) : NotGuard {
    companion object {
        const val CURRENT_ITEM_TITLE = 0
        const val INACTIVE_ITEM_TITLE = 1
        const val ACCOUNT_DATA = 2
    }
}