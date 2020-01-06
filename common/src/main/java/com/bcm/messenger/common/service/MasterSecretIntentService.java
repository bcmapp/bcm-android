package com.bcm.messenger.common.service;

import android.app.IntentService;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.common.provider.AMELogin;

public abstract class MasterSecretIntentService extends IntentService {

  public MasterSecretIntentService(String name) {
    super(name);
  }

  @Override
  protected final void onHandleIntent(Intent intent) {
    onHandleIntent(intent, BCMEncryptUtils.INSTANCE.getMasterSecret(AMELogin.INSTANCE.getMajorContext()));
  }

  protected abstract void onHandleIntent(Intent intent, @Nullable MasterSecret masterSecret);
}
