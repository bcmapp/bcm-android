package com.bcm.messenger.chats.privatechat.logic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.common.provider.AMELogin;

public abstract class MasterSecretBroadcastReceiver extends BroadcastReceiver {

  @Override
  public final void onReceive(Context context, Intent intent) {
    onReceive(context, intent, AMELogin.INSTANCE.getMajorContext().getMasterSecret());
  }

  protected abstract void onReceive(Context context, Intent intent, @Nullable MasterSecret masterSecret);
}
