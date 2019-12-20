package com.bcm.messenger.common.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.recipients.Recipient;

import com.bcm.messenger.common.database.repositories.ThreadRepo;
import com.bcm.messenger.utility.Base64;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class OutgoingGroupMediaMessage extends OutgoingSecureMediaMessage {

  private final GroupContext group;

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull String encodedGroupContext,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn)
      throws IOException
  {
    super(recipient, encodedGroupContext, avatar, sentTimeMillis,
            ThreadRepo.DistributionTypes.CONVERSATION, expiresIn);

    this.group = GroupContext.parseFrom(Base64.decode(encodedGroupContext));
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn)
  {
    super(recipient, Base64.encodeBytes(group.toByteArray()),
          new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
            sentTimeMillis,
            ThreadRepo.DistributionTypes.CONVERSATION, expireIn);

    this.group = group;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isGroupUpdate() {
    return group.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isGroupQuit() {
    return group.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

  public GroupContext getGroupContext() {
    return group;
  }
}
