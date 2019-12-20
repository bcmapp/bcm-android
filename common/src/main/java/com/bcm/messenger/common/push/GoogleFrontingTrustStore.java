package com.bcm.messenger.common.push;


import android.content.Context;

import com.bcm.messenger.common.R;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

public class GoogleFrontingTrustStore implements TrustStore {

  private final Context context;

  public GoogleFrontingTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.censorship_fronting);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }

}
