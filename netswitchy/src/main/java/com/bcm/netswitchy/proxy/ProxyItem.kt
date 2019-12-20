package com.bcm.netswitchy.proxy

import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams

data class ProxyItem(val params:ProxyParams, val content:String,var status: Status = Status.UNKNOWN) {
    enum class Status {
        UNKNOWN,
        USABLE,
        UNUSABLE,
        TESTING,
    }
}