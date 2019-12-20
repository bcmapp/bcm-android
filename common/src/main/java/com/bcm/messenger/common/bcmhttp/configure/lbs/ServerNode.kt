package com.bcm.messenger.common.bcmhttp.configure.lbs

import com.bcm.messenger.utility.proguard.NotGuard

class ServerNode(val scheme:String, val ip:String, val port:Int, val area:Int, val priority:Int): NotGuard {
    companion object {
        const val SCHEME = "https"
        const val DEFAULT_AREA = 210
        val IM_DEFAULT = ServerNode("https", "47.52.143.243", 8080, DEFAULT_AREA, 0)
        val METRICS_DEFAULT = ServerNode("https", "47.52.143.243", 6060, DEFAULT_AREA, 0)
    }

    override fun equals(other: Any?): Boolean {
        val o = other as? ServerNode ?:return false
        return o.ip == ip && o.port == port && o.area == area && o.priority == priority
    }

    override fun hashCode(): Int {
        var result = ip.hashCode()
        result = 31 * result + port
        result = 31 * result + area
        result = 31 * result + priority
        return result
    }
}