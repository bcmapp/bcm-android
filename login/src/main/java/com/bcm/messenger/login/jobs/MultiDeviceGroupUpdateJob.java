package com.bcm.messenger.login.jobs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class MultiDeviceGroupUpdateJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;
    private static final String TAG = MultiDeviceGroupUpdateJob.class.getSimpleName();

    public MultiDeviceGroupUpdateJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withGroupId(MultiDeviceGroupUpdateJob.class.getSimpleName())
                .withPersistence()
                .create());
    }

    @Override
    public void onRun(MasterSecret masterSecret) throws Exception {
        File contactDataFile = createTempFile("multidevice-contact-update");

        try {
            DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(new FileOutputStream(contactDataFile));

            out.close();

            if (contactDataFile.exists() && contactDataFile.length() > 0) {
            } else {
                Log.w(TAG, "No groups present for sync message...");
            }

        } finally {
            if (contactDataFile != null) contactDataFile.delete();
        }

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

    }

    private Optional<SignalServiceAttachmentStream> getAvatar(@Nullable byte[] avatar) {
        if (avatar == null) return Optional.absent();

        return Optional.of(SignalServiceAttachment.newStreamBuilder()
                .withStream(new ByteArrayInputStream(avatar))
                .withContentType("image/*")
                .withLength(avatar.length)
                .build());
    }

    private File createTempFile(String prefix) throws IOException {
        File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
        file.deleteOnExit();

        return file;
    }


}
