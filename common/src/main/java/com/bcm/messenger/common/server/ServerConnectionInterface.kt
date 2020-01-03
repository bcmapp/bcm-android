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
    fun state(): ConnectState
    @Throws(IOException::class)
    fun sendMessage(list: OutgoingPushMessageList): SendMessageResponse

    fun addConnectionListener(listener: IServerConnectStateListener) {}
    fun removeConnectionListener(listener: IServerConnectStateListener) {}
    fun setForceLogoutListener(listener: IServerConnectForceLogoutListener?){}
}

interface IServerDataListener {
    fun onReceiveData(accountContext: AccountContext, proto: AbstractMessage): Boolean
}

interface IServerConnectStateListener {
    fun onServerConnectionChanged(accountContext: AccountContext, newState:ConnectState)
}

interface IServerConnectForceLogoutListener {
    fun onClientForceLogout(accountContext: AccountContext, info: String?, type: KickEvent)
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