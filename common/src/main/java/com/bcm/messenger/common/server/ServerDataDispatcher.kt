package com.bcm.messenger.common.server

import com.bcm.messenger.common.event.ClientAccountDisabledEvent
import com.bcm.messenger.common.event.ServerConnStateChangedEvent
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.listener.WeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.AbstractMessage
import org.greenrobot.eventbus.EventBus
import org.whispersystems.libsignal.InvalidVersionException
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.util.Base64
import org.whispersystems.signalservice.internal.util.Hex
import org.whispersystems.signalservice.internal.websocket.FriendProtos
import org.whispersystems.signalservice.internal.websocket.GroupMessageProtos
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ServerDataDispatcher() : IServerConnectionEvent, IServerDataDispatcher {

    companion object {
        private const val TAG = "ServerDataDispatcher"
        private const val SUPPORTED_VERSION = 1
        private const val CIPHER_KEY_SIZE = 32
        private const val MAC_KEY_SIZE = 20
        private const val MAC_SIZE = 10

        private const val VERSION_OFFSET = 0
        private const val VERSION_LENGTH = 1
        private const val IV_OFFSET = VERSION_OFFSET + VERSION_LENGTH
        private const val IV_LENGTH = 16
        private const val CIPHER_TEXT_OFFSET = IV_OFFSET + IV_LENGTH
    }

    //，
    private val listener = WeakListeners<IServerDataListener>()

    override fun addListener(listener: IServerDataListener) {
        this.listener.addListener(listener)
    }

    override fun removeListener(listener: IServerDataListener) {
        this.listener.removeListener(listener)
    }

    override fun onServiceConnected(state: ConnectState, connectToken: Int) {
        ALog.i(TAG, "onServiceConnected $state")

        val newState = when (state) {
            ConnectState.CONNECTED -> ServerConnStateChangedEvent.ON
            ConnectState.CONNECTING -> ServerConnStateChangedEvent.CONNECTING
            else -> ServerConnStateChangedEvent.OFF
        }
        EventBus.getDefault().post(ServerConnStateChangedEvent(newState))

    }

    override fun onClientForceLogout(type: KickEvent, info: String?) {
        ALog.i("ForceLogout", "onClientForceLogout: $type")
        when (type) {
            KickEvent.OTHER_LOGIN -> {
                val deviceInfo = try {
                    //infobase64，
                    if (info == null) {
                        null
                    } else {
                        String(Base64.decode(info))
                    }
                } catch (ex: Exception) {
                    ALog.e("ForceLogout", "onClientForceLogout fail", ex)
                    null
                }
                EventBus.getDefault().post(ClientAccountDisabledEvent(ClientAccountDisabledEvent.TYPE_EXCEPTION_LOGIN, deviceInfo))
            }
            KickEvent.ACCOUNT_GONE -> {
                EventBus.getDefault().post(ClientAccountDisabledEvent(ClientAccountDisabledEvent.TYPE_ACCOUNT_GONE))
            }
        }
    }

    override fun onMessageArrive(message: WebSocketProtos.WebSocketRequestMessage): Boolean {
        if ("PUT" == message.verb) {
            try {
                val proto: AbstractMessage = when (message.path) {
                    "/api/v1/messages" -> {
                        val plainProtoData = decryptPrivateProtoData(message.body.toByteArray(), AMELogin.signalingKey)
                        SignalServiceProtos.Mailbox.parseFrom(plainProtoData)
                    }
                    "/api/v1/message" -> {
                        val plainProtoData = decryptPrivateProtoData(message.body.toByteArray(), AMELogin.signalingKey)
                        SignalServiceProtos.Envelope.parseFrom(plainProtoData)
                    }
                    "/api/v1/group_message" -> {
                        GroupMessageProtos.GroupMsg.parseFrom(message.body.toByteArray())
                    }
                    "/api/v1/friends" -> {
                        FriendProtos.FriendMessage.parseFrom(message.body.toByteArray())
                    }
                    else -> return false
                }

                if (!listener.any { it.onReceiveData(proto) }) {
                    ALog.w(TAG, "unknown message api ${message.path}")
                }
            } catch (e: Throwable) {
                ALog.e(TAG, "ServerMessageParser failed", e)
                return false
            }
            return true
        }
        return false
    }

    @Throws(IOException::class, InvalidVersionException::class)
    private fun decryptPrivateProtoData(data: ByteArray, password: String): ByteArray {
        if (data.size < VERSION_LENGTH || data[VERSION_OFFSET].toInt() != SUPPORTED_VERSION)
            throw InvalidVersionException("Unsupported version!")

        val cipherKey = getCipherKey(password)
        val macKey = getMacKey(password)

        verifyMac(data, macKey)
        return getPlaintext(data, cipherKey)
    }

    @Throws(IOException::class)
    private fun getPlaintext(ciphertext: ByteArray, cipherKey: SecretKeySpec): ByteArray {
        try {
            val ivBytes = ByteArray(IV_LENGTH)
            System.arraycopy(ciphertext, IV_OFFSET, ivBytes, 0, ivBytes.size)
            val iv = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv)

            return cipher.doFinal(ciphertext, CIPHER_TEXT_OFFSET,
                    ciphertext.size - VERSION_LENGTH - IV_LENGTH - MAC_SIZE)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: NoSuchPaddingException) {
            throw AssertionError(e)
        } catch (e: InvalidKeyException) {
            throw AssertionError(e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw AssertionError(e)
        } catch (e: IllegalBlockSizeException) {
            throw AssertionError(e)
        } catch (e: BadPaddingException) {
            Log.w(TAG, e)
            throw IOException("Bad padding?")
        }

    }


    @Throws(IOException::class)
    private fun getCipherKey(signalingKey: String): SecretKeySpec {
        val signalingKeyBytes = Base64.decode(signalingKey)
        val cipherKey = ByteArray(CIPHER_KEY_SIZE)
        System.arraycopy(signalingKeyBytes, 0, cipherKey, 0, cipherKey.size)

        return SecretKeySpec(cipherKey, "AES")
    }


    @Throws(IOException::class)
    private fun getMacKey(signalingKey: String): SecretKeySpec {
        val signalingKeyBytes = Base64.decode(signalingKey)
        val macKey = ByteArray(MAC_KEY_SIZE)
        System.arraycopy(signalingKeyBytes, CIPHER_KEY_SIZE, macKey, 0, macKey.size)

        return SecretKeySpec(macKey, "HmacSHA256")
    }

    @Throws(IOException::class)
    private fun verifyMac(ciphertext: ByteArray, macKey: SecretKeySpec) {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(macKey)

            if (ciphertext.size < MAC_SIZE + 1)
                throw IOException("Invalid MAC!")

            mac.update(ciphertext, 0, ciphertext.size - MAC_SIZE)

            val ourMacFull = mac.doFinal()
            val ourMacBytes = ByteArray(MAC_SIZE)
            System.arraycopy(ourMacFull, 0, ourMacBytes, 0, ourMacBytes.size)

            val theirMacBytes = ByteArray(MAC_SIZE)
            System.arraycopy(ciphertext, ciphertext.size - MAC_SIZE, theirMacBytes, 0, theirMacBytes.size)

            Log.w(TAG, "Our MAC: " + Hex.toString(ourMacBytes))
            Log.w(TAG, "Thr MAC: " + Hex.toString(theirMacBytes))

            if (!ourMacBytes.contentEquals(theirMacBytes)) {
                throw IOException("Invalid MAC compare!")
            }
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: InvalidKeyException) {
            throw AssertionError(e)
        }
    }


}
