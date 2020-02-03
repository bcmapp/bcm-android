package com.bcm.messenger.common.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.utility.logger.ALog;


public class PersistentConnectionBootListener extends BroadcastReceiver {
  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  public void onReceive(Context context, Intent intent) {
      try {
          ALog.i("BootListener", "MessageRetrievalService");
          if (AMELogin.INSTANCE.isLogin()) {
              for (AccountContext accountContext : AmeModuleCenter.INSTANCE.login().getLoginAccountContextList()) {
                  AmeModuleCenter.INSTANCE.serverDaemon(accountContext).checkConnection(false);
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
}
