package com.bcm.messenger.chats.group.core

import com.bcm.messenger.chats.group.core.group.GetMessageListEntity
import com.bcm.messenger.chats.group.core.group.GroupSendMessageResult
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.google.gson.reflect.TypeToken

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import io.reactivex.Observable

/**
 * ling created in 2018/5/23
 */
object GroupMessageCore {

    fun recallMessage(accountContext: AccountContext, gid: Long?, mid: Long?, ivBase64: String, derivePubKeyBase64: String): Observable<ServerResult<Void>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("mid", mid)
            obj.put("iv", ivBase64)
            obj.put("pub_key", derivePubKeyBase64)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.RECALL_MESSAGE),
                null, obj.toString(), object : TypeToken<ServerResult<Void>>() {

        }.type)
    }


    fun sendGroupMessage(accountContext: AccountContext, gid: Long, text: String, signIvBase64: String, derivePubKeyBase64: String, atListString: String?): Observable<ServerResult<GroupSendMessageResult>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("text", text)
            obj.put("sig", signIvBase64)
            obj.put("pub_key", derivePubKeyBase64)
            if (atListString != null) {
                obj.put("at_list", JSONArray(atListString))
                obj.put("at_all", 0)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.SEND_GROUP_MESSAGE_URL), null, obj.toString(), object : TypeToken<ServerResult<GroupSendMessageResult>>() {

        }.type)
    }


    fun getMessagesWithRange(accountContext: AccountContext, gid: Long, from: Long?, to: Long?): Observable<ServerResult<GetMessageListEntity>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("from", from)
            obj.put("to", to)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_MESSAGE_WITH_RANGE_URL), null, obj.toString(), object : TypeToken<ServerResult<GetMessageListEntity>>() {

        }.type)
    }


    fun ackMessage(accountContext: AccountContext, gid: Long, lastMid: Long?): Observable<ServerResult<Void>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("last_mid", lastMid)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.ACK_GROUP_MESSAGE__URL), null, obj.toString(), object : TypeToken<ServerResult<Void>>() {

        }.type)
    }
}
