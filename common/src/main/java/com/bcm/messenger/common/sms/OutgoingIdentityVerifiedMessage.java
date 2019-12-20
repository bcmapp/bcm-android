package com.bcm.messenger.common.sms;


import com.bcm.messenger.common.recipients.Recipient;

public class OutgoingIdentityVerifiedMessage extends OutgoingTextMessage {

  public OutgoingIdentityVerifiedMessage(Recipient recipient) {
    this(recipient, "");
  }

  private OutgoingIdentityVerifiedMessage(Recipient recipient, String body) {
    super(recipient, body, -1);
  }

  @Override
  public boolean isIdentityVerified() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingIdentityVerifiedMessage(getRecipient(), body);
  }
}
