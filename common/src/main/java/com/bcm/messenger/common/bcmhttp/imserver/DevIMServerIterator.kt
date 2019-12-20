package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode
import com.bcm.messenger.common.provider.AmeModuleCenter
import java.net.URL

class DevIMServerIterator: IServerIterator {
    private val serverUrl:IMServerUrl
    init {
        val url = URL(AmeModuleCenter.app().serverHost())
        serverUrl = IMServerUrl(ServerNode.SCHEME, url.host, url.port, ServerNode.DEFAULT_AREA)
    }
    override fun next(): IMServerUrl {
        return serverUrl
    }

    override fun isValid(): Boolean {
        return true
    }
}