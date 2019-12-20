package com.bcm.messenger.adhoc.logic

import android.app.Activity
import com.bcm.imcore.im.ChannelUserInfo
import com.bcm.messenger.adhoc.sdk.AdHocConnState
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.listener.WeakListeners

object AdHocChannelLogic: AdHocSDK.IAdHocSDKEventListener {

    private const val TAG = "AdHocChannelLogic"

    private var mLastState: IAdHocChannelListener.CONNECT_STATE? = null
    private var listenerList = WeakListeners<IAdHocChannelListener>()

    private val channelCache = AdHocChannelCache {
        listenerList.forEach {
            it.onChannelListChanged()
            it.onReady()
        }
    }

    init {
        AdHocSDK.addEventListener(this)
    }

    fun instance(): AdHocChannelLogic {
        return this
    }

    /**
     * init
     * @return true success，false fail
     */
    fun initAdHoc():Boolean {
        return AdHocSDK.init(AppContextHolder.APP_CONTEXT)
    }

    fun unInitAdHoc() {
        AdHocSDK.unInit(AppContextHolder.APP_CONTEXT)
    }

    fun addListener(listener: IAdHocChannelListener) {
        this.listenerList.addListener(listener)
    }

    fun removeListener(listener: IAdHocChannelListener) {
        this.listenerList.removeListener(listener)
    }

    fun setNotifyClass(clazz: Class<out Activity>?) {
        AdHocSDK.setTargetClass(clazz)
    }

    fun isAdHocInited(): Boolean{
        return AdHocSDK.isInited()
    }

    fun getChannelUserList(sessionId:String): List<ChannelUserInfo> {
        return AdHocSDK.getChannelUserList(sessionId)
    }

    fun getChannelUser(sessionId: String, uid:String): ChannelUserInfo? {
        val list = getChannelUserList(sessionId).filter { it.uid == uid }
        if (list.isNotEmpty()) {
            return list.first()
        }
        return null
    }

    fun getChannelUserCount(sessionId: String): Int {
        return  AdHocSDK.getChannelUserCount(sessionId)
    }

    fun isOnline(): Boolean {
        val snapshot = AdHocSDK.getNetSnapshot()
        return snapshot.connState == AdHocConnState.CONNECTED || snapshot.clientSet.isNotEmpty()
    }

    fun addChannel(channelName: String, passwd:String){
        if (AdHocSDK.isReady()) {
            if(channelCache.addChannel(channelName, passwd)) {
                listenerList.forEach {
                    it.onChannelListChanged()
                }
            }
        } else {
            ALog.w(TAG, "addChannel failed, AdHockSDK not ready")
        }
    }

    fun removeChannel(sessionId: String, cid:String): Boolean{
        if (AdHocSDK.isReady()) {
            channelCache.removeChannel(cid)
            AdHocSDK.messengerSdk.removeChannel(sessionId)
            listenerList.forEach {
                it.onChannelListChanged()
            }
            return true
        } else {
            ALog.w(TAG, "removeChannel failed, AdHockSDK not ready")
        }

        return false
    }

    fun removeChat(sessionId: String): Boolean {
        if (AdHocSDK.isReady()) {
            AdHocSDK.messengerSdk.removeChat(sessionId)
            return true
        } else {
            ALog.w(TAG, "removeChat failed, AdHockSDK not ready")
        }
        return false
    }

    fun getChannel(cid: String): AdHocChannel? {
        return channelCache.getChannel(cid)
    }

    fun getChannelList(): List<AdHocChannel> {
        return channelCache.getChannelList()
    }

    override fun onAdHockStateChanged() {
        val snapshot = AdHocSDK.getNetSnapshot()
        val connected = (snapshot.serverIp.isNotBlank() && snapshot.myIp.isNotBlank()) || snapshot.clientSet.isNotEmpty()

        var newState = IAdHocChannelListener.CONNECT_STATE.SCANNING
        if (connected) {
            newState = IAdHocChannelListener.CONNECT_STATE.CONNECTED
        } else if (snapshot.serverName.isNotBlank() && snapshot.errorCode == 0){
            newState = IAdHocChannelListener.CONNECT_STATE.CONNECTING
        }

        if (mLastState == newState) {
            ALog.w(TAG, "onAdHockStateChanged newState: $newState is same, do nothing")
            return
        }
        mLastState = newState
        listenerList.forEach {
            it.onScanStateChanged(newState)
        }
    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        AmeDispatcher.mainThread.dispatch {
            listenerList.forEach {
                it.onChannelUserChanged(sessionList)
            }
        }
    }

    interface IAdHocChannelListener {
        enum class CONNECT_STATE {
            CONNECTED,
            SCANNING,
            CONNECTING,
        }
        fun onChannelListChanged(){}
        fun onChannelUserChanged(sessionList: List<String>){}
        fun onScanStateChanged(state:CONNECT_STATE){}
        fun onReady(){}
    }
}