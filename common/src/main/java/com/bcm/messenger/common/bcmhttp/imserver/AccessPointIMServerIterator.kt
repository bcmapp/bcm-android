package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode
import com.bcm.messenger.common.provider.AmeModuleCenter
import java.net.URL

class AccessPointIMServerIterator(accessPointUrl:String): IServerIterator {
    private val serverUrl:IMServerUrl
    init {
        val url = URL(accessPointUrl)
        serverUrl = IMServerUrl(ServerNode.SCHEME, url.host, url.port, ServerNode.DEFAULT_AREA)
    }
    override fun next(): IMServerUrl {
        return serverUrl
    }

    override fun isValid(): Boolean {
        return serverUrl.ip.isNotEmpty() && serverUrl.port > 0
    }
}