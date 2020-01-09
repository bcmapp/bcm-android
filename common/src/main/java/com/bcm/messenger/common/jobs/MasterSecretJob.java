package com.bcm.messenger.common.jobs;

import android.content.Context;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.orhanobut.logger.Logger;

import org.whispersystems.jobqueue.JobParameters;

public abstract class MasterSecretJob extends ContextJob implements NotGuard {

    public MasterSecretJob(Context context, AccountContext accountContext, JobParameters parameters) {
        super(context, accountContext, parameters);
    }

    @Override
    public void onRun() throws Exception {
        MasterSecret masterSecret = getMasterSecret();
        try {
            onRun(masterSecret);
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                ALog.e("MasterSecretJob", e);
                throw new Exception(e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean onShouldRetry(Exception exception) {
        Logger.e(exception, "MasterSecretJob catch error");
        if (exception instanceof RequirementNotMetException) {
            return true;
        }
        return onShouldRetryThrowable(exception);
    }

    public abstract void onRun(MasterSecret masterSecret) throws Exception;

    public abstract boolean onShouldRetryThrowable(Exception exception);

    private MasterSecret getMasterSecret() throws RequirementNotMetException {
        MasterSecret masterSecret = accountContext.getMasterSecret();

        if (masterSecret == null) throw new RequirementNotMetException();
        else return masterSecret;
    }

    protected static class RequirementNotMetException extends Exception {
    }

}
