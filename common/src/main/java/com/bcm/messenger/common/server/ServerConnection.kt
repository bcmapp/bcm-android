package com.bcm.messenger.common.server

import com.bcm.messenger.common.bcmhttp.WebSocketHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.InvalidProtocolBufferException
import com.orhanobut.logger.Logger
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.whispersystems.libsignal.util.Pair
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos
import java.io.IOException

class ServerConnection(private val userAgent: String) {

    private val RESPONSE_REFUSE = 403//
    private val RESPONSE_GONE = 410//

    private val TAG = "ServerConnection"

    private val outgoingRequests = mutableMapOf<Long, SettableFuture<Pair<Int, String>>>()

    private var webSocketHttp: WebSocketHttp = WebSocketHttp()
    private var client: WebSocket? = null
    private var connectionEvent: IServerConnectionEvent? = null
    private var websocketEvent = WebsocketConnectionListener()

    private var connectState = ConnectState.INIT

    private val wsUri = BcmHttpApiHelper.getApi("/v1/websocket/?login=%s&password=%s").replace("https://", "wss://")

    private var connectToken = 0

    private var connectingTime = 0L
    /**
     * 
     *
     * @param listener
     */
    fun setConnectionListener(listener: IServerConnectionEvent?) {
        connectionEvent = listener
    }


    fun isDisconnect(): Boolean {
        return connectState == ConnectState.INIT || connectState == ConnectState.DISCONNECTED
    }

    fun isTimeout(): Boolean {
        if (connectingTime > 0L) {
            return (System.currentTimeMillis() - connectingTime) >= 10_000L
        }
        return false
    }

    fun isConnected(): Boolean {
        return connectState == ConnectState.CONNECTED
    }

    fun connect(token:Int = 0): Boolean {
        Logger.i("$TAG WSC connect()...")

        connectingTime = 0
        if (isDisconnect()) {
            connectToken = token
            updateConnectState(ConnectState.CONNECTING)

            val url = String.format(wsUri, AMESelfData.uid, AMESelfData.authPassword)
            Logger.d("WebSocketConnection filledUri: $url")

            connectingTime = System.currentTimeMillis()
            websocketEvent.destroyed = true
            websocketEvent = WebsocketConnectionListener()
            this.client = webSocketHttp.connect(url, userAgent, websocketEvent)
        }
        return true
    }

    fun disconnect() {
        Logger.i("$TAG WSC disconnect()...")

        clearConnection(1000, "OK")
        updateConnectState(connectState)
    }

    fun state(): ConnectState {
        return connectState
    }

    private fun updateConnectState(state: ConnectState) {
        this.connectState = state
        connectionEvent?.onServiceConnected(connectState, connectToken)
    }

    fun sendRequest(request: WebSocketProtos.WebSocketRequestMessage, callback: SettableFuture<Pair<Int, String>>) {
        val client = this.client
        if (null == client || !isConnected()) {
            callback.setException(IOException("No connection!"))
            return
        }

        val message = WebSocketProtos.WebSocketMessage.newBuilder()
                .setType(WebSocketProtos.WebSocketMessage.Type.REQUEST)
                .setRequest(request)
                .build()

        outgoingRequests[request.id] = callback

        if (!client.send(ByteString.of(*message.toByteArray()))) {
            outgoingRequests.remove(request.id)
            callback.setException(IOException("send request failed"))
        }
    }

    fun sendResponse(response: WebSocketProtos.WebSocketResponseMessage, callback: SettableFuture<Boolean>) {
        val client = this.client
        if (null == client || !isConnected()) {
            callback.setException(IOException("No connection!"))
            return
        }

        val message = WebSocketProtos.WebSocketMessage.newBuilder()
                .setType(WebSocketProtos.WebSocketMessage.Type.RESPONSE)
                .setResponse(response)
                .build()

        if (!client.send(ByteString.of(*message.toByteArray()))) {
            callback.setException(IOException("send response failed!"))
        } else {
            callback.set(true)
        }
    }

    fun sendKeepAlive(): Boolean {
        val client = this.client
        Logger.i("$TAG, sending keep alive")
        if (client != null && isConnected()) {
            val message = WebSocketProtos.WebSocketMessage.newBuilder()
                    .setType(WebSocketProtos.WebSocketMessage.Type.REQUEST)
                    .setRequest(WebSocketProtos.WebSocketRequestMessage.newBuilder()
                            .setId(System.currentTimeMillis())
                            .setPath("/v1/keepalive")
                            .setVerb("GET")
                            .build()).build()
                    .toByteArray()

            if (client.send(ByteString.of(*message))) {
                Logger.i("$TAG, keep alive succeed")
                return true
            }
        }

        return false
    }


    private fun clearConnection(code: Int, reason: String?) {
        if (isConnected()) {
            try {
                val iterator = outgoingRequests.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    entry.value.setException(IOException("Closed $code reason:$reason"))
                    iterator.remove()
                }

                val client = this.client
                this.client = null
                client?.close(1000, "OK")

            } catch (e: Throwable) {
                ALog.e(TAG, "disconnect", e)
            }
        }

        connectingTime = 0

        connectState = ConnectState.DISCONNECTED
    }

    inner class WebsocketConnectionListener(var destroyed:Boolean = false): WebSocketListener() {
        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onOpen")
                return
            }

            ALog.i(TAG, "onConnected()")
            connectingTime = 0

            updateConnectState(ConnectState.CONNECTED)
            connectToken = 0
        }

        override fun onMessage(webSocket: WebSocket?, payload: ByteString?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onMessage")
                return
            }

            ALog.i(TAG, "WSC onMessage()")
            try {
                val message = WebSocketProtos.WebSocketMessage.parseFrom(payload!!.toByteArray())

                ALog.i(TAG, " Message Type: " + message.getType().getNumber())

                if (message.type.number == WebSocketProtos.WebSocketMessage.Type.REQUEST_VALUE) {
                    connectionEvent?.onMessageArrive(message.request)
                } else if (message.type.number == WebSocketProtos.WebSocketMessage.Type.RESPONSE_VALUE) {
                    val listener = outgoingRequests[message.response.id]
                    listener?.set(Pair(message.response.status,
                            String(message.response.body.toByteArray())))
                }
            } catch (e: InvalidProtocolBufferException) {
                ALog.e(TAG, "onMessage", e)
            }
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onClosed")
                return
            }

            ALog.i(TAG, "onClose()...")
            clearConnection(code, reason)
        }

        override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onFailure")
                return
            }
            ALog.e(TAG, "onFailure()", t)

            if (response != null) {
                val code = response.code()
                ALog.w(TAG, "onFailure() code: $code")

                if (code == RESPONSE_REFUSE) {
                    val info = response.header("X-Online-Device")
                    //，
                    connectionEvent?.onClientForceLogout(KickEvent.OTHER_LOGIN, info)

                } else if (code == RESPONSE_GONE) {
                    connectionEvent?.onClientForceLogout(KickEvent.ACCOUNT_GONE, null)
                }
            }

            if (!isDisconnect()) {
                clearConnection(1000, "OK")
            }
        }

        override fun onMessage(webSocket: WebSocket?, text: String?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onMessage")
                return
            }
            ALog.i(TAG, "onMessage(text)! $text")
        }

        override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
            if (destroyed) {
                ALog.i(TAG, "ignore onClosing")
                return
            }
            ALog.i(TAG, "onClosing()!...")
            if (!isDisconnect()) {
                webSocket?.close(1000, "OK")
            }
        }
    }

}