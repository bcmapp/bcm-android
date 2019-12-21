package com.bcm.messenger.common.core.corebean

enum class GroupKeyMode(val m:Int) {
    UNKNOWN_MODE(-1),
    NORMAL_MODE(1), //，
    STRONG_MODE(0); //,

    companion object {
        private val map = values().associateBy(GroupKeyMode::m)
        fun ofValue(m: Int) = map[m]?: UNKNOWN_MODE
    }
}