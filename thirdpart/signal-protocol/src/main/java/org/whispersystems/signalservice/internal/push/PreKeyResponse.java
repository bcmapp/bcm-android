/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.google.gson.annotations.JsonAdapter;

import org.whispersystems.libsignal.IdentityKey;

import java.util.List;

public class PreKeyResponse {
  @JsonAdapter(IdentityKeyAdapter.class)
  private IdentityKey identityKey;

  private List<PreKeyResponseItem> devices;

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public List<PreKeyResponseItem> getDevices() {
    return devices;
  }
}
