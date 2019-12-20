package com.bcm.messenger.common.core.corebean

enum class GroupKeyMode(val m:Int) {
    UNKNOWN_MODE(-1),
    NORMAL_MODE(1), //普通加密模式，成员变更不会变更群密钥
    STRONG_MODE(0); //强安全模式,成员变更会变更群密钥

    companion object {
        private val map = values().associateBy(GroupKeyMode::m)
        fun ofValue(m: Int) = map[m]?: UNKNOWN_MODE
    }
}