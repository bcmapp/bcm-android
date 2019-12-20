package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode

class DevMetricsServerIterator : IServerIterator {
    private val serverUrl: IMServerUrl = IMServerUrl(ServerNode.SCHEME, "39.108.124.60", 6666, ServerNode.DEFAULT_AREA)
    override fun next(): IMServerUrl {
        return serverUrl
    }

    override fun isValid(): Boolean {
        return true
    }
}