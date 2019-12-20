package com.bcm.messenger.common.event

/**
 * 网络状态变更事件
 * Created by bcm.social.01 on 2018/6/15.
 */
class NetworkChangedEvent(val state: Int) {

    companion object {
        /**
         * 连接中
         */
        val CONNECTING = 2
        /**
         * 已连接
         */
        val ON = 1
        /**
         * 连接断开
         */
        val OFF = 0
    }
}