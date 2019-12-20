package com.bcm.messenger.common.database.documents;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedList;
import java.util.List;

public class IdentityKeyMismatchList implements Document<IdentityKeyMismatch> {

  @SerializedName(value = "m")
  private List<IdentityKeyMismatch> mismatches;

  public IdentityKeyMismatchList() {
    this.mismatches = new LinkedList<>();
  }

  public IdentityKeyMismatchList(List<IdentityKeyMismatch> mismatches) {
    this.mismatches = mismatches;
  }

  @Override
  public int size() {
    if (mismatches == null) return 0;
    else                    return mismatches.size();
  }

  @Override
  public List<IdentityKeyMismatch> list() {
    return mismatches;
  }
}
