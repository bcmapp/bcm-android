package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.HookSystem
import com.bcm.netswitchy.proxy.ProxyConfigure
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress

open class BcmBaseHttp:BaseHttp() {
    companion object {
        private val hookSystem = HookSystem()
    }
    override fun setClient(client: OkHttpClient) {
        ALog.i("BcmBaseHttp", "setClient")
        val dnsClient = client.newBuilder()
                .dns {
                    ALog.i("BcmBaseHttp", "dns")
                    if (ProxyConfigure.isRunning()) {
                        val ip = hookSystem.lookupIpAddress(it)
                        InetAddress.getAllByName(ip).toList()
                    } else {
                        Dns.SYSTEM.lookup(it)
                    }
                }.build()
        super.setClient(dnsClient)
    }
}