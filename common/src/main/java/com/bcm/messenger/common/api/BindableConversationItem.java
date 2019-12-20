package com.bcm.messenger.common.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.database.records.MessageRecord;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.GlideRequests;
import com.bcm.messenger.common.recipients.Recipient;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {

    void bind(@NonNull MasterSecret masterSecret,
              @NonNull MessageRecord messageRecord,
              @NonNull GlideRequests glideRequests,
              @NonNull Locale locale,
              @Nullable Set<MessageRecord> batchSelected,
              @NonNull Recipient recipients,
              int position);

    MessageRecord getMessageRecord();
}
