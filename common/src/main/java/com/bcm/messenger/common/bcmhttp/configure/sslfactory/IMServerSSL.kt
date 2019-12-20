package com.bcm.messenger.common.bcmhttp.configure.sslfactory

import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class IMServerSSL {
    private val trustManagers:Array<TrustManager>

    init {
        try {
            val imTrustStore = IMServerKeyStore()
            trustManagers = IMServerTrustManager.createFor(imTrustStore)
        } catch (e: KeyManagementException) {
            throw AssertionError(e)
        }
    }

    fun getSSLFactory(): SSLSocketFactory {
        try {
            val context = SSLContext.getInstance("TLS")
            context.init(null, trustManagers, null)
            return context.socketFactory
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: KeyManagementException) {
            throw AssertionError(e)
        }
    }

    fun getTrustManager():X509TrustManager {
        return trustManagers[0] as X509TrustManager
    }
}