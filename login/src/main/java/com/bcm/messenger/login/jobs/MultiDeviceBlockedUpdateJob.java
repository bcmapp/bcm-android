package com.bcm.messenger.login.jobs;

import android.content.Context;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.RecipientSettings;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


public class MultiDeviceBlockedUpdateJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = MultiDeviceBlockedUpdateJob.class.getSimpleName();

    public MultiDeviceBlockedUpdateJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withGroupId(MultiDeviceBlockedUpdateJob.class.getSimpleName())
                .withPersistence()
                .create());
    }

    @Override
    public void onRun(MasterSecret masterSecret)
            throws IOException, UntrustedIdentityException {
        List<RecipientSettings> settings = Repository.getRecipientRepo().getBlockedUsers();
        List<String> blocked = new LinkedList<>();
        for (RecipientSettings setting : settings) {
            blocked.add(setting.getUid());
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
}
