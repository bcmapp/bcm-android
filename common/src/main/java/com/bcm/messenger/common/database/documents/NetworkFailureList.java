package com.bcm.messenger.common.database.documents;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedList;
import java.util.List;

public class NetworkFailureList implements Document<NetworkFailure> {

  @SerializedName(value = "l")
  private List<NetworkFailure> failures;

  public NetworkFailureList() {
    this.failures = new LinkedList<>();
  }

  public NetworkFailureList(List<NetworkFailure> failures) {
    this.failures = failures;
  }

  @Override
  public int size() {
    if (failures == null) return 0;
    else                  return failures.size();
  }

  @Override
  public List<NetworkFailure> list() {
    return failures;
  }
}
