package com.bcm.messenger.login.jobs;


import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.ProfileKeyUtil;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMESelfData;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class MultiDeviceProfileKeyUpdateJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;
    private static final String TAG = MultiDeviceProfileKeyUpdateJob.class.getSimpleName();

    public MultiDeviceProfileKeyUpdateJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withPersistence()
                .withGroupId(MultiDeviceProfileKeyUpdateJob.class.getSimpleName())
                .create());
    }

    @Override
    public void onRun(MasterSecret masterSecret) throws IOException, UntrustedIdentityException {
        if (!TextSecurePreferences.isMultiDevice(getContext())) {
            Log.w(TAG, "Not multi device...");
            return;
        }

        Optional<byte[]> profileKey = Optional.of(ProfileKeyUtil.getProfileKey(getContext()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceContactsOutputStream out = new DeviceContactsOutputStream(baos);

        out.write(new DeviceContact(AMESelfData.INSTANCE.getUid(),
                Optional.<String>absent(),
                Optional.<SignalServiceAttachmentStream>absent(),
                Optional.<String>absent(),
                Optional.<VerifiedMessage>absent(),
                profileKey));

        out.close();

        SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                .withStream(new ByteArrayInputStream(baos.toByteArray()))
                .withContentType("application/octet-stream")
                .withLength(baos.toByteArray().length)
                .build();

        SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, false));

    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {
        Log.w(TAG, "Profile key sync failed!");
    }
}
