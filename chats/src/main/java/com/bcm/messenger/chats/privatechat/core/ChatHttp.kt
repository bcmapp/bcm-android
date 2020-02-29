package com.bcm.messenger.chats.privatechat.core

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.bcmhttp.BcmBaseHttp
import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.error.IMServerErrorCodeInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.AccountMetricsInterceptor
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.facade.SyncHttpWrapper
import com.bcm.messenger.utility.proguard.NotGuard
import okhttp3.OkHttpClient
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.Pair
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo
import org.whispersystems.signalservice.api.push.ContactTokenDetails
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList
import org.whispersystems.signalservice.internal.push.PreKeyResponse
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import org.whispersystems.signalservice.internal.push.SendMessageResponse
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

object ChatHttp : AccountContextMap<ChatHttp.ChatHttpImpl> ({
    ChatHttpImpl(it)
}) {
    private const val TURN_SERVER_INFO = "/v1/accounts/turn"
    private const val PREKEY_DEVICE_PATH = "/v2/keys/%s/%s"

    private const val DIRECTORY_VERIFY_PATH = "/v1/directory/%s"
    private const val MESSAGE_PATH = "/v1/messages/%s"
    private const val AWS_UPLOAD_INFO_PATH = "/v1/attachments/s3/upload_certification"

    private val baseHttpClient:OkHttpClient

    init {
        val sslFactory = IMServerSSL()
        val builder = OkHttpClient.Builder()
                .readTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
        if (RedirectInterceptor.accessPoint == null) {
            builder.sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                    .hostnameVerifier(BaseHttp.trustAllHostVerify())
        }

        baseHttpClient = builder.build()
    }

    class ChatHttpImpl(accountContext: AccountContext) : SyncHttpWrapper(BcmBaseHttp()) {
        init {
            val client = baseHttpClient.newBuilder()
                    .addInterceptor(BcmAuthHeaderInterceptor(accountContext))
                    .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
                    .addInterceptor(IMServerErrorCodeInterceptor())
                    .addInterceptor(AccountMetricsInterceptor(accountContext))
                    .build()
            setClient(client)
        }

        @Throws(NoContentException::class, BaseHttp.HttpErrorException::class)
        fun getTurnServerInfo(): TurnServerInfo {
            val url = BcmHttpApiHelper.getApi(TURN_SERVER_INFO)
            return get(url, null, TurnServerInfo::class.java)
        }

        @Throws(NoContentException::class, UnregisteredUserException::class, BaseHttp.HttpErrorException::class)
        fun sendMessage(bundle: OutgoingPushMessageList): SendMessageResponse {
            try {
                return put(BcmHttpApiHelper.getApi(String.format(MESSAGE_PATH, bundle.destination))
                        , GsonUtils.toJson(bundle)
                        , SendMessageResponse::class.java)
            } catch (nfe: NotFoundException) {
                throw UnregisteredUserException(bundle.destination, nfe)
            }
        }

        @Throws(IOException::class, UnregisteredUserException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
        fun getPreKeys(destination: SignalServiceAddress, deviceIdInteger: Int): List<PreKeyBundle> {
            try {
                var deviceId = deviceIdInteger.toString()

                if (deviceId == "1") {
                    deviceId = "*"
                }

                var path = String.format(PREKEY_DEVICE_PATH, destination.number, deviceId)

                if (destination.relay.isPresent) {
                    path = path + "?relay=" + destination.relay.get()
                }

                val response = get<PreKeyResponse>(BcmHttpApiHelper.getApi(path), null, PreKeyResponse::class.java)
                val bundles = LinkedList<PreKeyBundle>()

                if (response.devices?.isNotEmpty() == true) {
                    for (device in response.devices) {
                        var preKey: ECPublicKey? = null
                        var signedPreKey: ECPublicKey? = null
                        var signedPreKeySignature: ByteArray? = null
                        var preKeyId = -1
                        var signedPreKeyId = -1

                        if (device.signedPreKey != null) {
                            signedPreKey = device.signedPreKey.publicKey
                            signedPreKeyId = device.signedPreKey.keyId
                            signedPreKeySignature = device.signedPreKey.signature
                        }

                        if (device.preKey != null) {
                            preKeyId = device.preKey.keyId
                            preKey = device.preKey.publicKey
                        }

                        bundles.add(PreKeyBundle(device.registrationId, device.deviceId, preKeyId,
                                preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                                response.identityKey))
                    }
                } else {
                    throw IOException("Empty prekey list")
                }
                return bundles
            } catch (nfe: NotFoundException) {
                throw UnregisteredUserException(destination.number, nfe)
            }
        }

        @Throws(IOException::class, UnregisteredUserException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
        fun getPreKey(destination: SignalServiceAddress, deviceId: Int): PreKeyBundle {
            try {
                var path = String.format(PREKEY_DEVICE_PATH, destination.number,
                        deviceId.toString())

                if (destination.relay.isPresent) {
                    path = path + "?relay=" + destination.relay.get()
                }

                val response = get<PreKeyResponse>(BcmHttpApiHelper.getApi(path), null, PreKeyResponse::class.java)
                if (response.devices == null || response.devices.size < 1) {
                    throw IOException("Empty prekey list")
                }

                val device = response.devices[0]
                var preKey: ECPublicKey? = null
                var signedPreKey: ECPublicKey? = null
                var signedPreKeySignature: ByteArray? = null
                var preKeyId = -1
                var signedPreKeyId = -1

                if (device.preKey != null) {
                    preKeyId = device.preKey.keyId
                    preKey = device.preKey.publicKey
                }

                if (device.signedPreKey != null) {
                    signedPreKeyId = device.signedPreKey.keyId
                    signedPreKey = device.signedPreKey.publicKey
                    signedPreKeySignature = device.signedPreKey.signature
                }

                return PreKeyBundle(device.registrationId, device.deviceId, preKeyId, preKey,
                        signedPreKeyId, signedPreKey, signedPreKeySignature, response.identityKey)
            } catch (nfe: NotFoundException) {
                throw UnregisteredUserException(destination.number, nfe)
            }
        }

        @Throws(IOException::class)
        fun getContactTokenDetails(contactToken: String): ContactTokenDetails? {
            try {
                return get<ContactTokenDetails>(BcmHttpApiHelper.getApi(String.format(DIRECTORY_VERIFY_PATH, contactToken))
                        , null, ContactTokenDetails::class.java)
            } catch (nfe: NotFoundException) {
                return null
            }
        }

        @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
        fun uploadAttachmentToAws(attachment: PushAttachmentData, fileName: String?): Pair<String, ByteArray> {
            val region = RedirectInterceptorHelper.imServerInterceptor.getCurrentServer().area.toString()

            val regionDescriptor = AttachmentUploadReqDescriptor("pmsg", region)
            val descriptor = post<AttachmentUploadDescriptor>(BcmHttpApiHelper.getApi(AWS_UPLOAD_INFO_PATH), GsonUtils.toJson(regionDescriptor), AttachmentUploadDescriptor::class.java)

            if (!descriptor.isDataCompleted) {
                throw IOException("Server return a bad upload info")
            }

            val digest = ChatFileHttp.uploadAttachmentToAws(descriptor.postUrl!!, descriptor.fields, attachment.data, attachment.dataSize,
                    fileName, attachment.contentType, attachment.outputStreamFactory)

            return Pair(descriptor.downloadUrl!!, digest)
        }


        private data class AttachmentUploadReqDescriptor(val type: String, val region: String) : NotGuard
        private class AttachmentUploadDescriptor(val postUrl: String?, val fields: Array<ChatFileHttp.AttachmentUploadField>? = null, val downloadUrl: String?) : NotGuard {
            val isDataCompleted: Boolean
                get() = postUrl != null && fields != null && fields.isNotEmpty() && downloadUrl != null
        }
    }
}