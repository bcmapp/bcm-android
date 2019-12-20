package com.bcm.netswitchy.utils

import android.os.Build
import com.bcm.messenger.utility.logger.ALog
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


object GenPort {
    fun getSSRPort(host: String, from: Int): Int {
        for (i in from..from + 20) {
            if (isFreePort(host, i) && isFreePort(host, i + 53) && isFreePort(host, i + 63)) {
                return i
            }
        }
        ALog.e("GenPort", "getSSRPort failed:$from")
        return 0
    }

    fun getPort(host: String, from: Int): Int {
        for (i in from..from + 20) {
            if (isFreePort(host, i)) {
                return i
            }
        }
        ALog.e("GenPort", "getPort failed:$from")
        return 0
    }

    private fun isFreePort(host: String, port: Int): Boolean {
        if (Build.VERSION.SDK_INT == 29) {
            return try {
                val server = ServerSocket(port)
                server.close()
                true
            } catch (e: IOException) {
                false
            }
        } else {
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 500)
                socket.close()
                false
            } catch (e: ConnectException) {
                ALog.d("GenPort", "ConnectException")
                true
            } catch (e: Exception) {
                ALog.d("GenPort", e.javaClass.simpleName)
                true
            } catch (e:Throwable) {
                ALog.d("GenPort", e.javaClass.simpleName)
                false
            }
        }
    }
}