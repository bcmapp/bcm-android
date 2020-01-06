package com.bcm.messenger.login.jobs;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.deprecated.MessagingDatabase.SyncMessageId;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.preferences.TextSecurePreferences;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;


public class MultiDeviceReadUpdateJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;
    private static final String TAG = MultiDeviceReadUpdateJob.class.getSimpleName();

    private final List<SerializableSyncMessageId> messageIds;

    public MultiDeviceReadUpdateJob(Context context, List<SyncMessageId> messageIds) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withPersistence()
                .create());

        this.messageIds = new LinkedList<>();

        for (SyncMessageId messageId : messageIds) {
            this.messageIds.add(new SerializableSyncMessageId(messageId.getAddress().serialize(), messageId.getTimetamp()));
        }
    }


    @Override
    public void onRun(MasterSecret masterSecret) throws IOException, UntrustedIdentityException {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.w(TAG, "Not multi device...");
            return;
        }

        List<ReadMessage> readMessages = new LinkedList<>();

        for (SerializableSyncMessageId messageId : messageIds) {
            readMessages.add(new ReadMessage(messageId.sender, messageId.timestamp));
        }

    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof PushNetworkException;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {

    }

    private static class SerializableSyncMessageId implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String sender;
        private final long timestamp;

        private SerializableSyncMessageId(String sender, long timestamp) {
            this.sender = sender;
            this.timestamp = timestamp;
        }
    }
}
