package com.bcm.messenger.common.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.utility.logger.ALog;


public class PersistentConnectionBootListener extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
      try {
          ALog.i("BootListener", "MessageRetrievalService");
          if (AMELogin.INSTANCE.isGcmDisabled() && AMELogin.INSTANCE.isLogin()) {
              AmeModuleCenter.INSTANCE.serverDaemon().checkConnection(false);
          }
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
}
