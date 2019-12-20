/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

public class OutgoingPushMessage {
  private int    type;
  private int    destinationDeviceId;
  private int    destinationRegistrationId;
  private String content;
  private int push;

  public OutgoingPushMessage(int type,
                             int destinationDeviceId,
                             int destinationRegistrationId,
                             String content,
                             PushPurpose push)
  {
    this.type                      = type;
    this.destinationDeviceId       = destinationDeviceId;
    this.destinationRegistrationId = destinationRegistrationId;
    this.content                   = content;
    this.push                      = push.getIndex();
  }
}
