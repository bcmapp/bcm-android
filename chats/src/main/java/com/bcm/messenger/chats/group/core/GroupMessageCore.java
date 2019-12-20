package com.bcm.messenger.chats.group.core;

import androidx.annotation.Nullable;

import com.bcm.messenger.chats.group.core.group.GetMessageListEntity;
import com.bcm.messenger.chats.group.core.group.GroupSendMessageResult;
import com.bcm.messenger.common.core.BcmHttpApiHelper;
import com.bcm.messenger.common.core.ServerResult;
import com.bcm.messenger.common.bcmhttp.RxIMHttp;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.reactivex.Observable;

/**
 * ling created in 2018/5/23
 **/
public class GroupMessageCore {

    public static Observable<ServerResult<Void>> recallMessage(Long gid, Long mid, String ivBase64, String derivePubKeyBase64) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("gid", gid);
            obj.put("mid", mid);
            obj.put("iv", ivBase64);
            obj.put("pub_key", derivePubKeyBase64);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return RxIMHttp.INSTANCE.put(BcmHttpApiHelper.INSTANCE.getApi(GroupCoreConstants.RECALL_MESSAGE),
                null, obj.toString(), new TypeToken<ServerResult<Void>>() {
                }.getType());
    }


    public static Observable<ServerResult<GroupSendMessageResult>> sendGroupMessage(long gid, String text, String signIvBase64, String derivePubKeyBase64, @Nullable String atListString) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("gid", gid);
            obj.put("text", text);
            obj.put("sig", signIvBase64);
            obj.put("pub_key", derivePubKeyBase64);
            if (atListString != null) {
                obj.put("at_list", new JSONArray(atListString));
                obj.put("at_all", 0);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return RxIMHttp.INSTANCE.put(BcmHttpApiHelper.INSTANCE.getApi(GroupCoreConstants.SEND_GROUP_MESSAGE_URL),
                null, obj.toString(), new TypeToken<ServerResult<GroupSendMessageResult>>() {
                }.getType());
    }


    public static Observable<ServerResult<GetMessageListEntity>> getMessagesWithRange(long gid, Long from, Long to) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("gid", gid);
            obj.put("from", from);
            obj.put("to", to);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return RxIMHttp.INSTANCE.put(BcmHttpApiHelper.INSTANCE.getApi(GroupCoreConstants.GET_GROUP_MESSAGE_WITH_RANGE_URL),
                null, obj.toString(), new TypeToken<ServerResult<GetMessageListEntity>>() {
                }.getType());
    }


    public static Observable<ServerResult<Void>> ackMessage(long gid, Long lastMid) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("gid", gid);
            obj.put("last_mid", lastMid);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return RxIMHttp.INSTANCE.put(BcmHttpApiHelper.INSTANCE.getApi(GroupCoreConstants.ACK_GROUP_MESSAGE__URL),
                null, obj.toString(), new TypeToken<ServerResult<Void>>() {
                }.getType());
    }
}
