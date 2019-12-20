package com.bcm.messenger.push;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.bcm.messenger.common.utils.AmePushProcess;
import com.bcm.messenger.utility.logger.ALog;
import com.umeng.message.UmengMessageService;
import com.umeng.message.entity.UMessage;

import org.android.agoo.common.AgooConstants;
import org.json.JSONObject;

public class UmengNotificationService extends UmengMessageService {

    @Override
    public void onMessage(Context context, Intent intent) {
        ALog.i("UmengNotificationService", "recv push");
        String message = intent.getStringExtra(AgooConstants.MESSAGE_BODY);
        try {
            UMessage msg = new UMessage(new JSONObject(message));
            if (msg.extra != null) {
                ALog.d("UmengNotificationService", "recv push, message is " + msg.text);
                ALog.i("UmengNotificationService", "recv push, message is empty? " + TextUtils.isEmpty(msg.text));
                String bcmData = String.format("{\"bcmdata\":%s}", msg.extra.get("bcmdata"));
                AmePushProcess.INSTANCE.processPush(bcmData);
            } else {
                AmePushProcess.INSTANCE.processPush(msg.text);
                ALog.e("UmengNotificationService", "unknown notification");
            }
        } catch (Exception e) {
            ALog.e("UmengNotificationService", e);
        }
    }
}
