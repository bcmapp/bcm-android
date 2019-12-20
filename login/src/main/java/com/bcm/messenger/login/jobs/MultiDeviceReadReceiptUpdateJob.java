package com.bcm.messenger.login.jobs;


import android.content.Context;
import android.util.Log;
import com.bcm.messenger.common.jobs.ContextJob;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;


public class MultiDeviceReadReceiptUpdateJob extends ContextJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = MultiDeviceReadReceiptUpdateJob.class.getSimpleName();

    private final boolean enabled;

    public MultiDeviceReadReceiptUpdateJob(Context context, boolean enabled) {
        super(context, JobParameters.newBuilder()
                .withPersistence()
                .withGroupId("__MULTI_DEVICE_READ_RECEIPT_UPDATE_JOB__")
                .withRequirement(new NetworkRequirement(context))
                .create());

        this.enabled = enabled;
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
    }

    @Override
    public boolean onShouldRetry(Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {
        Log.w(TAG, "**** Failed to synchronize read receipts state!");
    }
}
