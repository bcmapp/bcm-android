package com.bcm.messenger.common.mms;

import com.bcm.messenger.common.database.repositories.ThreadRepo;

import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.recipients.Recipient;

import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient, "", new LinkedList<Attachment>(), sentTimeMillis,
            ThreadRepo.DistributionTypes.CONVERSATION, expiresIn);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
