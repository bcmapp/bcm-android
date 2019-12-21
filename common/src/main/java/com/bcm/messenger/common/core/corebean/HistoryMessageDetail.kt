package com.bcm.messenger.common.core.corebean

import com.bcm.messenger.utility.proguard.NotGuard


class HistoryMessageDetail : NotGuard {
    /**
     * sender :
     * sendTime : 0
     * attachmentPsw : {"type":1,"psw":""}
     * thumbPsw : {"type":1,"psw":""}
     * messagePayload : {"type":100,"content":{}}
     */

    var sender: String? = null
    var sendTime: Long = 0L
    var attachmentPsw: PswBean? = null
    var thumbPsw: PswBean? = null
    var messagePayload: String? = null

    class PswBean : NotGuard {
        /**
         * type : 0：；1：
         * psw :
         */
        var type: Int = -1
        var psw: String? = null
    }

}