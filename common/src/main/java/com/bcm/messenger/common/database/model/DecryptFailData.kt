package com.bcm.messenger.common.database.model

import com.bcm.messenger.utility.GsonUtils

data class DecryptFailData(var lastShowDialogTime: Long = 0L, var failMessageCount: Int = 0, var firstNotFoundMsgTime: Long = 0, var lastDeleteSessionTime: Long = 0L) {
    fun toJson(): String {
        return GsonUtils.toJson(this)
    }

    fun increaseFailCount() {
        failMessageCount++
    }

    fun resetFailCount() {
        failMessageCount = 0
    }
}