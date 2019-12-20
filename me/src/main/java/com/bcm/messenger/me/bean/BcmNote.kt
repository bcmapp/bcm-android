package com.bcm.messenger.me.bean

import com.bcm.messenger.utility.AmeTimeUtil


class BcmNote(val topicId: String, var topic: String = "", var author: String = "", var pin: Boolean = false,
              var lastEditPosition: Int = 0, var timestamp: Long = AmeTimeUtil.serverTimeMillis()) {

}