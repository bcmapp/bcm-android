package com.bcm.messenger.chats.privatechat.jobs;


import android.content.Context;
import android.util.Log;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.jobs.ContextJob;
import com.bcm.messenger.common.preferences.TextSecurePreferences;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.List;

public class SendReadReceiptJob extends ContextJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = SendReadReceiptJob.class.getSimpleName();

    private final String address;
    private final List<Long> messageIds;
    private final long timestamp;

    public SendReadReceiptJob(Context context, AccountContext accountContext, Address address, List<Long> messageIds) {
        super(context, accountContext, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withPersistence()
                .create());

        this.address = address.serialize();
        this.messageIds = messageIds;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() throws Exception {
        if (!TextSecurePreferences.isReadReceiptsEnabled(context)) return;

        SignalServiceAddress remoteAddress = new SignalServiceAddress(address);
        SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, messageIds, timestamp);

        BcmChatCore.INSTANCE.sendReceipt(remoteAddress, receiptMessage);
    }

    @Override
    public boolean onShouldRetry(Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {
        Log.w(TAG, "Failed to send read receipts to: " + address);
    }
}
