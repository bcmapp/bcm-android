package com.bcm.messenger.login.logic;


import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.crypto.IdentityKeyUtil;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.PreKeyUtil;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.utility.logger.ALog;

import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;


public class RotateSignedPreKeyJob extends MasterSecretJob {

    private static final String TAG = RotateSignedPreKeyJob.class.getName();

    public RotateSignedPreKeyJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withRetryCount(5)
                .create());
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun(MasterSecret masterSecret) throws Exception {
        Log.w(TAG, "Rotating signed prekey...");
        if (AMESelfData.INSTANCE.isLogin()) {
            IdentityKeyPair identityKey = IdentityKeyUtil.getIdentityKeyPair(context);
            SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKey, false);

            if (!AmeLoginCore.INSTANCE.refreshSignedPreKey(signedPreKeyRecord)) {
                throw new Exception("refreshSignedPreKey failed");
            }

            PreKeyUtil.setActiveSignedPreKeyId(context, signedPreKeyRecord.getId());
            AMESelfData.INSTANCE.setSignedPreKeyRegistered(true);
            AMESelfData.INSTANCE.setSignedPreKeyFailureCount(0);

            JobManager manager = AmeModuleCenter.INSTANCE.accountJobMgr();
            if (manager != null) {
                manager.add(new CleanPreKeysJob(context));
            }
        } else {
            ALog.w(TAG, "please login first");
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {
        AMESelfData.INSTANCE.setSignedPreKeyFailureCount(AMESelfData.INSTANCE.getSignedPreKeyFailureCount() + 1);
    }
}
