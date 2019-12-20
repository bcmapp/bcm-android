package com.bcm.messenger.common.bcmhttp.configure

import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode

class IMServerUrl(val scheme:String, val ip:String, val port:Int, val area:Int):IBcmUrl {
    companion object {
        val IM_DEFAULT = IMServerUrl(ServerNode.IM_DEFAULT.scheme
                , ServerNode.IM_DEFAULT.ip
                , ServerNode.IM_DEFAULT.port, ServerNode.DEFAULT_AREA)

        val METRICS_DEFAULT = IMServerUrl(ServerNode.METRICS_DEFAULT.scheme
                , ServerNode.METRICS_DEFAULT.ip
                , ServerNode.METRICS_DEFAULT.port, ServerNode.DEFAULT_AREA)
    }

    override fun getURL(): String {
        return "$scheme://$ip:$port"
    }
}