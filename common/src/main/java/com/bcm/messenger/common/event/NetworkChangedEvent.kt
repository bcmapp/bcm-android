package com.bcm.messenger.common.event

/**
 * 
 * Created by bcm.social.01 on 2018/6/15.
 */
class NetworkChangedEvent(val state: Int) {

    companion object {
        /**
         * 
         */
        val CONNECTING = 2
        /**
         * 
         */
        val ON = 1
        /**
         * 
         */
        val OFF = 0
    }
}