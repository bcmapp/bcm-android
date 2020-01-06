package com.bcm.messenger.login.logic;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.IdentityKeyUtil;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.PreKeyUtil;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.provider.AmeModuleCenter;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;


public class CreateSignedPreKeyJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = CreateSignedPreKeyJob.class.getSimpleName();

    public CreateSignedPreKeyJob(Context context, AccountContext accountContext) {
        super(context, accountContext, JobParameters.newBuilder()
                .withPersistence()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context, accountContext))
                .withGroupId(CreateSignedPreKeyJob.class.getSimpleName())
                .create());
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun(MasterSecret masterSecret) throws IOException {
        if (accountContext.isSignedPreKeyRegistered()) {
            Log.w(TAG, "Signed prekey already registered...");
            return;
        }

        IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(accountContext);
        SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, accountContext, identityKeyPair, true);


        AmeLoginCore.INSTANCE.refreshSignedPreKey(accountContext, signedPreKeyRecord);
        AmeModuleCenter.INSTANCE.login().setSignedPreKeyRegistered(accountContext.getUid(), true);
    }

    @Override
    public void onCanceled() {
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }
}
