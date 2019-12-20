package com.bcm.netswitchy.utils

import com.bcm.messenger.utility.logger.ALog
import org.xbill.DNS.*
import java.lang.reflect.Method
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object IPAddressUtil {
    fun resolve(host: String): String {
//        if (isNumeric(host)) {
//            return host
//        }
//        try {
//            if (isIPv6Support()) {
//                var result = resolve(host, Type.AAAA)
//                if (result == null) {
//                    result = resolve(host, Type.A)
//                    if (result == null) {
//                        result = resolveHost(host)
//                    }
//                }
//                return result ?: host
//            }
//        } catch (ex: Exception) {
//
//        }
        return host
    }

    private fun resolve(host: String, addressType: Int): String? {
        try {
            val lookup = Lookup(host, addressType)
            val resolver = SimpleResolver("114.114.114.114")
            resolver.setTimeout(5)
            lookup.setResolver(resolver)
            val result = lookup.run()
            if (result != null) {
                for (r in result) {
                    when (addressType) {
                        Type.A -> {
                            (r as ARecord).address.hostAddress
                        }
                        Type.AAAA -> {
                            (r as AAAARecord).address.hostAddress
                        }
                    }
                }
            }

        } catch (ex: Exception) {

        }
        return null
    }

    private fun resolveHost(host: String): String? {
        try {
            return InetAddress.getByName(host).hostAddress
        } catch (ex: Exception) {

        }
        return null
    }

    private fun isIPv6Support(): Boolean {
        try {
            for (inf in NetworkInterface.getNetworkInterfaces()) {
                for (addr in inf.inetAddresses) {
                    return addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress
                }
            }

        } catch (ex: Exception) {

        }
        return false
    }

    private fun isNumeric(address: String): Boolean {
        try {
            val isNumeric: Method = InetAddress::class.java.getMethod("isNumeric", String::class.java)
            return isNumeric.invoke(null, address) as? Boolean ?: false
        } catch (ex: Exception) {
            ALog.e("VpnConfigureUtils", "isNumeric fail", ex)
        }
        return false
    }
}