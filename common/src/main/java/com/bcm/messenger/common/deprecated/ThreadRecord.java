/*
 * Copyright (C) 2012 Moxie Marlinspike
 * Copyright (C) 2013-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.ExpirationUtil;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 */
@Deprecated
public class ThreadRecord extends DisplayRecord {

    private @NonNull
    final Context context;
    private @Nullable
    final Uri snippetUri;
    //？
    private final long count;
    private int unreadCount;
    private final int distributionType;
    private final boolean archived;
    private final long expiresIn;
    private final long lastSeen;
    private final long pin;
    private final int live_state;
    private @Nullable final AmeGroupMessage groupMessage;
    public String uid;

    public ThreadRecord(@NonNull Context context, @NonNull Body body, @Nullable Uri snippetUri,
                        @NonNull Recipient recipient, long date, long count, int unreadCount,
                        long threadId, int deliveryReceiptCount, int status, long snippetType,
                        int distributionType, boolean archived, long expiresIn, long lastSeen,
                        int readReceiptCount, long pin, int live_state) {
        super(context, body, recipient, date, date, threadId, status, deliveryReceiptCount, snippetType, readReceiptCount);
        this.context = context.getApplicationContext();
        this.snippetUri = snippetUri;
        this.count = count;
        this.unreadCount = unreadCount;
        this.distributionType = distributionType;
        this.archived = archived;
        this.expiresIn = expiresIn;
        this.lastSeen = lastSeen;
        this.live_state = live_state;
        this.pin = pin;

        if (isNewGroup() || isLocation()) {
            this.groupMessage = AmeGroupMessage.Companion.messageFromJson(body.getBody());
        } else  {
            this.groupMessage = null;
        }
    }

    public @Nullable
    Uri getSnippetUri() {
        return snippetUri;
    }

    @Override
    public boolean isFailed() {
        if (isNewGroup()) {
            AmeGroupMessage msg = AmeGroupMessage.Companion.messageFromJson(getBody().getBody());
            //SystemContent 
            return !(msg.getContent() instanceof AmeGroupMessage.SystemContent)
                    && type == AmeGroupMessageDetail.SendState.SEND_FAILED.getValue();
        }
        return super.isFailed();
    }

    @Override
    public boolean isPending() {
        if (isNewGroup()){
            return  type == AmeGroupMessageDetail.SendState.SENDING.getValue();
        }
        return super.isPending();
    }

    @Override
    public SpannableString getDisplayBody() {
        if (count <= 0) {
            return new SpannableString("");
        }
        if (distributionType == ThreadDatabase.DistributionTypes.NEW_GROUP) {
            new SpannableString(getBody().getBody());
        } else {
            if (isPending()) {
                return pendingAdded(getBody().getBody());
            } else if (isFailed()) {
                return failAdded(getBody().getBody());
            } else if (SmsDatabase.Types.isLocationType(type)) {
                return new SpannableString("[Location Message]");
            } else if (SmsDatabase.Types.isDecryptInProgressType(type)) {
                return emphasisAdded(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait));
            } else if (isGroupUpdate()) {
                return emphasisAdded(context.getString(R.string.common_thread_group_updated));
            } else if (isGroupQuit()) {
                return emphasisAdded(context.getString(R.string.common_thread_left_the_group));
            } else if (isKeyExchange()) {
                return emphasisAdded(context.getString(R.string.common_thread_key_exchange_message));
            } else if (SmsDatabase.Types.isFailedDecryptType(type)) {
                return failAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
            } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
                return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
            } else if (!getBody().isPlaintext()) {
                return emphasisAdded(context.getString(R.string.common_locked_message_description));
            } else if (SmsDatabase.Types.isEndSessionType(type)) {
                return emphasisAdded(context.getString(R.string.common_thread_secure_session_reset));
            } else if (MmsSmsColumns.Types.isLegacyType(type)) {
                return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
            } else if (MmsSmsColumns.Types.isDraftMessageType(type)) {
                String draftText = context.getString(R.string.common_thread_draft);
                return colorAdded(draftText + " " + getBody().getBody(), 0, draftText.length());
            } else if (isOutgoingCall()) {
                return emphasisAdded(context.getString(com.bcm.messenger.common.R.string.common_thread_called));
            } else if (isIncomingCall()) {
                return emphasisAdded(context.getString(com.bcm.messenger.common.R.string.common_thread_called_you));
            } else if (isMissedCall()) {
                return emphasisAdded(context.getString(com.bcm.messenger.common.R.string.common_thread_missed_call));
            } else if (isJoined()) {
                return emphasisAdded(context.getString(R.string.common_thread_s_is_on_signal, getRecipient().toShortString()));
            } else if (SmsDatabase.Types.isExpirationTimerUpdate(type)) {
                String time = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
                return emphasisAdded(context.getString(R.string.common_thread_disappearing_message_time_updated_to_s, time));
            } else if (SmsDatabase.Types.isIdentityUpdate(type)) {
                if (getRecipient().isGroupRecipient()) {
                    return emphasisAdded(context.getString(R.string.common_thread_safety_number_changed));
                } else {
                    return emphasisAdded(context.getString(R.string.common_thread_your_safety_number_with_s_has_changed, getRecipient().toShortString()));
                }
            } else if (SmsDatabase.Types.isIdentityVerified(type)) {
                return emphasisAdded(context.getString(R.string.common_thread_you_marked_verified));
            } else if (SmsDatabase.Types.isIdentityDefault(type)) {
                return emphasisAdded(context.getString(R.string.common_thread_you_marked_unverified));
            } else {
                if (TextUtils.isEmpty(getBody().getBody())) {
                    return new SpannableString(emphasisAdded(context.getString(R.string.common_thread_media_message)));
                } else {
                    return new SpannableString(getBody().getBody());
                }
            }
        }
        return new SpannableString(getBody().getBody());

    }

    public void clearUnreadCount() {
        unreadCount = 0;
    }

    private SpannableString emphasisAdded(String sequence) {
        return emphasisAdded(sequence, 0, sequence.length());
    }

    private SpannableString emphasisAdded(String sequence, int start, int end) {
        SpannableString spannable = new SpannableString(sequence);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private SpannableString colorAdded(String sequence, int start, int end) {
        SpannableString spannable = new SpannableString(sequence);
        spannable.setSpan(new ForegroundColorSpan(0xfff34a62),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private SpannableString failAdded(String sequence) {
        StringBuilder s = new StringBuilder();
        s.append(" ");
        s.append(sequence);
        SpannableString spannable = new SpannableString(s);
//        spannable.setSpan(new ImageSpan(context, R.drawable.common_chat_list_failed),
//                0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private SpannableString pendingAdded(String sequence) {
        StringBuilder s = new StringBuilder();
        s.append(" ");
        s.append(sequence);
        SpannableString spannable = new SpannableString(s);
//        spannable.setSpan(new ImageSpan(context, R.drawable.common_chat_list_sending),
//                0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public boolean isNewGroup() {
        if (distributionType == ThreadDatabase.DistributionTypes.NEW_GROUP) {
            return true;
        } else {
            return false;
        }
    }

    public long getCount() {
        return count;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public long getDate() {
        return getDateReceived();
    }

    public boolean isArchived() {
        return archived;
    }

    public int getDistributionType() {
        return distributionType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public long getPin() {
        return pin;
    }

    public int getLive_state() {
        return live_state;
    }

    public @Nullable AmeGroupMessage getGroupMessage() {
        return groupMessage;
    }

    public long getType() {
        return type;
    }
}
