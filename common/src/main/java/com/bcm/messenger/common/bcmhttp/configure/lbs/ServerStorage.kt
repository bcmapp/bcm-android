package com.bcm.messenger.common.bcmhttp.configure.lbs

import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.storage.SPEditor
import com.google.gson.reflect.TypeToken

class ServerStorage(val type:String) {
    companion object {
        private const val TAG = "ServerStorage"
        private const val IM_SERVER_MAX_SIZE = 8
    }

    private var list = mutableListOf<ServerNode>()
    private val store = SPEditor("lbs_preferences")

    init {
        list.addAll(loadImServerList())
    }

    /**
     * 
     */
    fun getLastServerList(): List<ServerNode> {
        return list
    }

    /**
     * 
     */
    fun saveServerList(ipList:List<ServerNode>) {
        storeServerList(ipList)
    }

    private fun storeServerList(succeedList: List<ServerNode>) {
        ALog.i(TAG, "saveServerList ${succeedList.size}")
        val lastSuccess = list
        //
        lastSuccess.removeAll(succeedList)

        val oldList = lastSuccess.toList()
        val changed: Boolean

        //IM_SERVER_MAX_SIZEIM Server
        if (oldList.size + succeedList.size > IM_SERVER_MAX_SIZE) {
            lastSuccess.addAll(0, succeedList)
            changed = oldList.size != lastSuccess.size

            val overflow = lastSuccess.size - IM_SERVER_MAX_SIZE
            if (overflow > 0 && oldList.size >= overflow ) {
                lastSuccess.removeAll(oldList.subList(0, overflow))
            }
        } else {
            lastSuccess.addAll(0, succeedList)
            changed = oldList.size != lastSuccess.size
        }

        this.list = lastSuccess

        ALog.i(TAG, "saveServerList save succeed ${lastSuccess.size}, changed:$changed")
        if (changed) {
            store.set("svr_list_$type", GsonUtils.toJson(lastSuccess.toList()))
        }
    }

    private fun loadImServerList(): List<ServerNode> {
        try {
            val serverListString = store.get("svr_list_$type", "")
            if (serverListString.isNotEmpty()) {
                return GsonUtils.fromJson(serverListString, object : TypeToken<List<ServerNode>>(){}.type)
            }
        } catch (e:Throwable) {
            ALog.logForSecret(TAG, "load im server ip list", e)
        }

        return listOf()
    }
}