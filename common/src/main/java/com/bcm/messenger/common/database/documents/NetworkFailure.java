package com.bcm.messenger.common.database.documents;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.SerializedName;

public class NetworkFailure implements NotGuard {

  @SerializedName(value = "a")
  private String address;

  public NetworkFailure(Address address) {
    this.address = address.serialize();
  }

  public NetworkFailure() {}

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof NetworkFailure)) return false;

    NetworkFailure that = (NetworkFailure)other;
    return this.address.equals(that.address);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }
}
