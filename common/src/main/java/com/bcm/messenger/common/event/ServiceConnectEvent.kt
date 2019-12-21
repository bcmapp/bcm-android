package com.bcm.messenger.common.event

/**
 * Created by bcm.social.01 on 2018/11/28.
 */
/**
 *  conntected true , false 
 */
class ServiceConnectEvent(val state: STATE) {
    enum class STATE {
        UNKNOWN,
        CONNECTED,
        DISCONNECTED
    }
}