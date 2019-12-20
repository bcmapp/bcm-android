package com.bcm.messenger.common.event

/**
 * Created by bcm.social.01 on 2018/11/28.
 */
/**
 * 服务连接状态变更 conntected true 已连接, false 未连接
 */
class ServiceConnectEvent(val state: STATE) {
    enum class STATE {
        UNKNOWN,
        CONNECTED,
        DISCONNECTED
    }
}