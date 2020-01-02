package com.bcm.messenger.common.server

import com.bcm.messenger.common.AccountContext
import com.google.protobuf.AbstractMessage
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList
import org.whispersystems.signalservice.internal.push.SendMessageResponse
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos
import java.io.IOException

/**
 * 
 * Created by wjh on 2019-11-06
 */

interface IServerConnectionEvent {
    fun onServiceConnected(accountContext: AccountContext, connectToken: Int, state: ConnectState)
    fun onMessageArrive(accountContext: AccountContext, message: WebSocketProtos.WebSocketRequestMessage): Boolean
    fun onClientForceLogout(accountContext: AccountContext, info: String?, type: KickEvent)
}

interface IServerDataDispatcher {
    fun addListener(listener: IServerDataListener) {}
    fun removeListener(listener: IServerDataListener) {}
}

interface IServerConnectionDaemon {
    fun startDaemon()
    fun stopDaemon()
    fun startConnection()
    fun stopConnection()
    fun checkConnection(manual: Boolean = true)
    @Throws(IOException::class)
    fun sendMessage(list: OutgoingPushMessageList): SendMessageResponse
}

interface IServerDataListener {
    fun onReceiveData(accountContext: AccountContext, proto: AbstractMessage): Boolean
}

enum class KickEvent {
    OTHER_LOGIN,//
    ACCOUNT_GONE//
}

enum class ConnectState {
    INIT,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}