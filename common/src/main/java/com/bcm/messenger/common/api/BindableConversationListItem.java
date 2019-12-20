package com.bcm.messenger.common.api;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.database.records.ThreadRecord;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.GlideRequests;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests, @NonNull Locale locale,
                   @NonNull Set<Long> selectedThreads, boolean batchMode);
}
