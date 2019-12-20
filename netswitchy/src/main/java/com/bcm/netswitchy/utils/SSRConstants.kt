/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package com.bcm.netswitchy.utils

import android.content.Context
import android.content.Intent
import android.system.ErrnoException
import android.system.Os
import android.text.TextUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.ssrsystem.config.SSParams
import com.bcm.ssrsystem.config.SSRParams
import org.xbill.DNS.*
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.io.*
import java.lang.reflect.Method
import java.util.*


object Key {

    const val id = "profileId"

    const val name = "profileName"

    const val individual = "Proxyed"

    const val isNAT = "isNAT"
    const val route = "route"

    const val isAutoConnect = "isAutoConnect"

    const val proxyApps = "isProxyApps"
    const val bypass = "isBypassApps"
    const val udpdns = "isUdpDns"

    const val dns = "dns"
    const val auth = "isAuth"
    const val ipv6 = "isIpv6"

    const val server = "server"
    const val password = "password"

    const val method = "method"
    const val obfs = "obfs"
    const val obfs_param = "obfs_param"

    const val protocol = "protocol"
    const val protocol_param = "protocol_param"

    const val remotePort = "server_port"
    const val localPort = "local_port"

    const val timeout = "timeout"
    const val profileTip = "profileTip"

    const val kcp = "kcp"
    const val kcpPort = "kcpPort"
    const val kcpcli = "kcpcli"
}

object Executable {
    const val OBSF4 = "libobfs4-tunnel.so"
    const val SS_LOCAL = "libss-local.so"

    fun getCmdDir(context: Context = AppContextHolder.APP_CONTEXT, isNative: Boolean = false): String {
        return if(isNative) {
            context.applicationInfo.nativeLibraryDir
        }else {
            context.applicationInfo.dataDir
        }
    }

    fun getCmdPath(context: Context = AppContextHolder.APP_CONTEXT, path: String, isNative: Boolean = false): String {
        return if(isNative) {
            File(context.applicationInfo.nativeLibraryDir, path).absolutePath
        }else {
            File(context.applicationInfo.dataDir, path).absolutePath
        }
    }

    fun getLocalVpnConf(context: Context = AppContextHolder.APP_CONTEXT, isVpn: Boolean = false): String {
        return if (!isVpn) {
            getCmdPath(context, "ss-local-nat.conf")
        } else {
            getCmdPath(context, "ss-local-vpn.conf")
        }
    }

    fun getLocalTunnelConf(context: Context = AppContextHolder.APP_CONTEXT, isVpn: Boolean = false): String {
        return if (!isVpn) {
            getCmdPath(context, "ss-tunnel-nat.conf")
        } else {
            getCmdPath(context, "ss-tunnel-vpn.conf")
        }
    }
}

object ConfigureUtils {
    private fun escapedJsonString(origin: String): String {
        return origin.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"")

    }

    fun buildSSRConfig(params: SSRParams): String {
        return SHADOWSOCKS.format(Locale.ENGLISH, params.host, params.port, params.localPort, escapedJsonString(params.password),
                params.method, 600, params.protocol, params.obfs, escapedJsonString(params.obfsParams), escapedJsonString(params.protocolParams))
    }

    fun buildSSConfig(params: SSParams): String {
        return SHADOWSOCKS.format(Locale.ENGLISH, params.host, params.port, params.localPort, escapedJsonString(params.password),
                params.method, 600, "", "origin", "", "")
    }


    fun buildTunnelConfig(params: SSRParams, localPort: Int): String {
        return SHADOWSOCKS.format(Locale.ENGLISH, params.host, params.port, localPort, escapedJsonString(params.password),
                params.method, 600, params.protocol, params.obfs, escapedJsonString(params.obfsParams), escapedJsonString(params.protocolParams))
    }

    fun buildSSTunnelConfig(params: SSParams, localPort: Int): String {
        return SHADOWSOCKS.format(Locale.ENGLISH, params.host, params.port, localPort, escapedJsonString(params.password),
                params.method, 600, "", "", "", "")
    }

    private const val SHADOWSOCKS = "{\"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"timeout\": %d, \"protocol\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"protocol_param\": \"%s\"}"
}
