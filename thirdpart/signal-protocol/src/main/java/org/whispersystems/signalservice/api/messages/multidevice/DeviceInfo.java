/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.google.gson.annotations.SerializedName;

public class DeviceInfo {

  @SerializedName("id")
  private long id;

  @SerializedName("name")
  private String name;

  @SerializedName("created")
  private long created;

  @SerializedName("lastSeen")
  private long lastSeen;

  public DeviceInfo() {}

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public long getCreated() {
    return created;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
