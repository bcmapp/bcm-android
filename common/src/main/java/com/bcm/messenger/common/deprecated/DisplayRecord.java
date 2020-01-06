/**
 * Copyright (C) 2012 Moxie Marlinspike
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.text.SpannableString;

import com.bcm.messenger.common.deprecated.MmsSmsColumns;
import com.bcm.messenger.common.deprecated.SmsDatabase;
import com.bcm.messenger.common.recipients.Recipient;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

@Deprecated
public abstract class DisplayRecord {

    protected final Context context;
    protected final long type;

    private final Recipient recipient;
    private final long dateSent;
    private final long dateReceived;
    private final long threadId;
    private final Body body;
    private final int deliveryStatus;
    private final int deliveryReceiptCount;
    private final int readReceiptCount;

    public DisplayRecord(Context context, Body body, Recipient recipient, long dateSent,
                         long dateReceived, long threadId, int deliveryStatus, int deliveryReceiptCount,
                         long type, int readReceiptCount) {
        this.context = context.getApplicationContext();
        this.threadId = threadId;
        this.recipient = recipient;
        this.dateSent = dateSent;
        this.dateReceived = dateReceived;
        this.type = type;
        this.body = body;
        this.deliveryReceiptCount = deliveryReceiptCount;
        this.readReceiptCount = readReceiptCount;
        this.deliveryStatus = deliveryStatus;
    }

    public Body getBody() {
        return body;
    }

    public boolean isFailed() {

        return
                MmsSmsColumns.Types.isFailedMessageType(type) ||
                        MmsSmsColumns.Types.isFailedCannotDecryptType(type) ||
                        MmsSmsColumns.Types.isPendingSecureSmsFallbackType(type) ||
                        deliveryStatus >= SmsDatabase.Status.STATUS_FAILED;
    }

    public boolean isPending() {
        return MmsSmsColumns.Types.isPendingMessageType(type) &&
                !MmsSmsColumns.Types.isIdentityVerified(type) &&
                !MmsSmsColumns.Types.isIdentityDefault(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isDecrypting() {
        return MmsSmsColumns.Types.isDecryptInProgressType(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isDecryptFail() {
        return MmsSmsColumns.Types.isFailedDecryptType(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isNoRemoteSession() {
        return MmsSmsColumns.Types.isNoRemoteSessionType(type);
    }

    public boolean isLegacy() {
        return MmsSmsColumns.Types.isLegacyType(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isDraftMessage() {
        return MmsSmsColumns.Types.isDraftMessageType(type);
    }

    public boolean isOutgoing() {
        return MmsSmsColumns.Types.isOutgoingMessageType(type);
    }

    public abstract SpannableString getDisplayBody();

    public Recipient getRecipient() {
        return recipient;
    }

    public long getDateSent() {
        return dateSent;
    }

    public long getDateReceived() {
        return dateReceived;
    }

    public long getThreadId() {
        return threadId;
    }

    public boolean isKeyExchange() {
        return SmsDatabase.Types.isKeyExchangeType(type);
    }

    public boolean isEndSession() {
        return SmsDatabase.Types.isEndSessionType(type);
    }

    public boolean isLocation() {
        return SmsDatabase.Types.isLocationType(type);
    }

    public boolean isGroupUpdate() {
        return SmsDatabase.Types.isGroupUpdate(type);
    }

    public boolean isGroupQuit() {
        return SmsDatabase.Types.isGroupQuit(type);
    }

    public boolean isGroupAction() {
        return isGroupUpdate() || isGroupQuit();
    }

    public boolean isExpirationTimerUpdate() {
        return SmsDatabase.Types.isExpirationTimerUpdate(type);
    }

    public boolean isCallLog() {
        return SmsDatabase.Types.isCallLog(type);
    }

    public boolean isJoined() {
        return SmsDatabase.Types.isJoinedType(type);
    }

    public boolean isIncomingCall() {
        return SmsDatabase.Types.isIncomingCall(type);
    }

    public boolean isOutgoingCall() {
        return SmsDatabase.Types.isOutgoingCall(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isMissedCall() {
        return SmsDatabase.Types.isIncomingMissedCall(type) || SmsDatabase.Types.isOutgoingMissedCall(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isOutgoingMissedCall() {
        return SmsDatabase.Types.isOutgoingMissedCall(type);
    }

    /**
     * 
     *
     * @return
     */
    public boolean isIncomingMissedCall() {
        return SmsDatabase.Types.isIncomingMissedCall(type);
    }

    public boolean isIdentityVerified() {
        return SmsDatabase.Types.isIdentityVerified(type);
    }

    public boolean isIdentityDefault() {
        return SmsDatabase.Types.isIdentityDefault(type);
    }

    public boolean isVerificationStatusChange() {
        return SmsDatabase.Types.isIdentityDefault(type) || SmsDatabase.Types.isIdentityVerified(type);
    }

    public boolean isIdentityUpdate() {
        return MmsSmsColumns.Types.isIdentityUpdate(type);
    }

    public int getDeliveryStatus() {
        return deliveryStatus;
    }

    public int getDeliveryReceiptCount() {
        return deliveryReceiptCount;
    }

    public int getReadReceiptCount() {
        return readReceiptCount;
    }

    public boolean isDelivered() {
        return (deliveryStatus >= SmsDatabase.Status.STATUS_COMPLETE &&
                deliveryStatus < SmsDatabase.Status.STATUS_PENDING) || deliveryReceiptCount > 0;
    }

    public boolean isRemoteRead() {
        return readReceiptCount > 0;
    }

    public boolean isPendingInsecureSmsFallback() {
        return SmsDatabase.Types.isPendingInsecureSmsFallbackType(type);
    }

    public static class Body {
        private final String body;
        private final boolean plaintext;

        public Body(String body, boolean plaintext) {
            this.body = body;
            this.plaintext = plaintext;
        }

        public boolean isPlaintext() {
            return plaintext;
        }

        public String getBody() {
            return body == null ? "" : body;
        }

    }
}
