package com.bcm.messenger.common.bcmhttp.configure.sslfactory

import org.whispersystems.signalservice.api.push.TrustStore
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class IMServerTrustManager(private val trustManager: X509TrustManager) : X509TrustManager {
    companion object {
        fun createFor(trustManagers: Array<TrustManager>): Array<TrustManager> {
            for (trustManager in trustManagers) {
                if (trustManager is X509TrustManager) {
                    val t:TrustManager = IMServerTrustManager(trustManager)
                    return arrayOf(t)
                }
            }

            throw AssertionError("No X509 Trust Managers!")
        }

        fun createFor(trustStore: TrustStore): Array<TrustManager> {
            try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null)

                val certificateInput = trustStore.keyStoreInputStream
                keyStore.setCertificateEntry("0", certificateFactory.generateCertificate(certificateInput))
                val trustManagerFactory = TrustManagerFactory.getInstance("X509")
                trustManagerFactory.init(keyStore)

                return createFor(trustManagerFactory.trustManagers)
            } catch (e: KeyStoreException) {
                throw AssertionError(e)
            } catch (e: CertificateException) {
                throw AssertionError(e)
            } catch (e: IOException) {
                throw AssertionError(e)
            } catch (e: NoSuchAlgorithmException) {
                throw AssertionError(e)
            }

        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        trustManager.checkClientTrusted(chain, authType)
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        trustManager.checkServerTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return trustManager.acceptedIssuers
    }
}