/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import java.util.List;

public class MismatchedDevices {
  private List<Integer> missingDevices;
  private List<Integer> extraDevices;

  public List<Integer> getMissingDevices() {
    return missingDevices;
  }

  public List<Integer> getExtraDevices() {
    return extraDevices;
  }
}
