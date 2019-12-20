package com.bcm.messenger.adhoc.logic

import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.adhoc.sdk.AdHocSessionSDK
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import java.util.concurrent.atomic.AtomicInteger

object AdHocSessionLogic: AdHocSDK.IAdHocSDKEventListener, AdHocSessionSDK.IAdHocSessionEventListener {
    private const val TAG = "AdHocSessionLogic"
    private var listener:IAdHocSessionListener? = null
    private val sdk = AdHocSDK.messengerSdk
    private lateinit var sessionCache: AdHocSessionCache

    init {

        fun checkInitFinish(finish: Int) {
            if (finish >= 2) {
                ALog.i(TAG, "initFinish onReady")
                sessionCache.allInitFinish()
                if (AdHocSDK.isReady()) {
                    syncSessions(getSessionList())
                }
                listener?.onReady()
            }
        }

        val initFinish = AtomicInteger(0)
        AdHocSDK.addEventListener(this)
        sdk.addEventListener(this)
        AdHocChannelLogic.instance().addListener(object : AdHocChannelLogic.IAdHocChannelListener {
            override fun onReady() {
                 checkInitFinish(initFinish.addAndGet(1))
            }
        })
        sessionCache = AdHocSessionCache {
            ALog.i(TAG, "session cache ${it.size}")
            this.listener?.onSessionListChanged()
            checkInitFinish(initFinish.addAndGet(1))
        }
        AdHocMessageLogic.instance()
    }

    private fun syncSessions(list: List<AdHocSession>) {
        ALog.i(TAG, "syncSessions list: ${list.size}")
        list.forEach {
            if (it.cid.isNotEmpty()) {
                val channel = AdHocChannelLogic.getChannel(it.cid)
                if (channel != null) {
                    sdk.addChannel(channel.channelName, channel.passwd) { }
                }
            } else if (it.uid.isNotEmpty()) {
                sdk.addChat(it.uid){}
            }
        }

    }


    fun setListener(listener: IAdHocSessionListener) {
        this.listener = listener
    }

    fun getSessionList(): List<AdHocSession> {
        return sessionCache.getSessionList()
    }


    fun getUnReadSessionCount(): Int {
        var unread = 0
        sessionCache.getSessionList().forEach {
            unread += (if (!it.mute && it.unreadCount > 0) 1 else 0)
        }
        return unread
    }


    fun getSession(sessionId: String): AdHocSession? {
        return sessionCache.getSession(sessionId)
    }


    fun updateLastMessage(sessionId: String, text:String, state: Int) {
        ALog.i(TAG, "updateLastMessage sessionId: $sessionId, text: $text, state: $state")
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updateLastMessage(sessionId, text, state)) {
                listener?.onSessionListChanged()
            }
        }
    }


    fun updateDraft(sessionId: String, draft:String) {
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updateDraft(sessionId, draft)) {
                listener?.onSessionListChanged()
            }
        }
    }

    fun updatePin(sessionId: String, pin: Boolean) {
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updatePin(sessionId, pin)) {
                listener?.onSessionListChanged()
            }
        }
    }

    fun updateMute(sessionId: String, mute: Boolean) {
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updateMute(sessionId, mute)) {
                listener?.onSessionListChanged()
            }
        }

    }


    fun updateUnreadCount(sessionId: String, count: Int) {
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updateUnreadCount(sessionId, count)) {
                listener?.onSessionListChanged()
            }
        }

    }

    fun updateAtMeStatus(sessionId: String, hasAtMe: Boolean) {
        AmeDispatcher.mainThread.dispatch {
            if(sessionCache.updateAtMeStatus(sessionId, hasAtMe)) {
                listener?.onSessionListChanged()
            }
        }

    }

    fun deleteSession(sessionId: String) {
        AmeDispatcher.mainThread.dispatch {
            val session = getSession(sessionId)
            if(null != session) {
                sessionCache.deleteSession(sessionId)
                listener?.onSessionListChanged()
            }
        }

    }

    override fun onAtMeNotify(sessionId: String) {
        updateAtMeStatus(sessionId, true)
    }

    override fun onAdHocReady() {
        super.onAdHocReady()
        val list = sessionCache.getSessionList()
        AmeDispatcher.io.dispatch {
            syncSessions(list)
            addChannelSession(AdHocChannel.OFFICIAL_CHANNEL, AdHocChannel.OFFICIAL_PWD) {

            }
        }
    }

    fun addChannelSession(name: String, passwd: String, result: (sessionId:String) -> Unit) {
        sdk.addChannel(name, passwd) {
            if (it.isNotEmpty()) {
                AdHocChannelLogic.addChannel(name, passwd)
                AmeDispatcher.mainThread.dispatch {
                    sessionCache.saveChannelSession(it, AdHocChannel.cid(name, passwd)) {changed ->
                        if (changed) {
                            listener?.onSessionListChanged()
                        }
                    }
                    result(it)
                }
            } else {
                AmeDispatcher.mainThread.dispatch {
                    result("")
                }
            }
        }
    }

    fun addChatSession(uid: String, result: (sessionId: String) -> Unit) {
        sdk.addChat(uid) {
            AmeDispatcher.mainThread.dispatch {
                if (it.isNotEmpty()) {
                    sessionCache.saveChatSession(it, uid) {changed ->
                        if (changed) {
                            listener?.onSessionListChanged()
                        }
                    }
                    result.invoke(it)
                } else {
                    result("")
                }
            }
        }
    }

    interface IAdHocSessionListener {
        fun onSessionListChanged()
        fun onReady()
    }
}