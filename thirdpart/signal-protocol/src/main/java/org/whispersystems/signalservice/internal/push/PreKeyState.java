package org.whispersystems.signalservice.internal.push;

import com.google.gson.annotations.JsonAdapter;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;

import java.util.List;

public class PreKeyState {

  @JsonAdapter(IdentityKeyAdapter.class)
  private IdentityKey identityKey;
  private List<PreKeyEntity> preKeys;
  private SignedPreKeyEntity signedPreKey;

  public PreKeyState(List<PreKeyEntity> preKeys, SignedPreKeyEntity signedPreKey, IdentityKey identityKey) {
    this.preKeys = preKeys;
    this.signedPreKey = signedPreKey;
    this.identityKey = identityKey;
  }
}

