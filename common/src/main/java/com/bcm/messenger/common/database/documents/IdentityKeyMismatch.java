package com.bcm.messenger.common.database.documents;

import android.util.Log;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.internal.push.IdentityKeyAdapter;

import java.io.IOException;

public class IdentityKeyMismatch implements NotGuard {

  private static final String TAG = IdentityKeyMismatch.class.getSimpleName();

  @SerializedName(value = "a")
  private String address;

  @SerializedName(value = "k")
  @JsonAdapter(IdentityKeyAdapter.class)
  private IdentityKey identityKey;

  public IdentityKeyMismatch() {}

  public IdentityKeyMismatch(Address address, IdentityKey identityKey) {
    this.address     = address.serialize();
    this.identityKey = identityKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof IdentityKeyMismatch)) {
      return false;
    }

    IdentityKeyMismatch that = (IdentityKeyMismatch)other;
    return that.address.equals(this.address) && that.identityKey.equals(this.identityKey);
  }

  @Override
  public int hashCode() {
    return address.hashCode() ^ identityKey.hashCode();
  }
}
