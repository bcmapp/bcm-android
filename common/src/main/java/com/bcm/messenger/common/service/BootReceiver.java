package com.bcm.messenger.common.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.utility.logger.ALog;

/**
 * 应用启动广播监听器
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            ALog.i("BootReceiver", "MessageRetrievalService");
            if (AMESelfData.INSTANCE.isLogin()){
                AmeModuleCenter.INSTANCE.serverDaemon().checkConnection(false);
            }
        } catch (Exception ex) {
            ALog.e("BootReceiver", "start messageRetrievalService fail", ex);
        }
    }

}
