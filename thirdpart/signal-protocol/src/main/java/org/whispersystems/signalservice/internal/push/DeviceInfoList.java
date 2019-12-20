package org.whispersystems.signalservice.internal.push;

import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.util.List;

public class DeviceInfoList {

  private List<DeviceInfo> devices;

  public DeviceInfoList() {}

  public List<DeviceInfo> getDevices() {
    return devices;
  }
}
