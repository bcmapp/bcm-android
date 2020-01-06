package com.bcm.messenger.common.jobs.requirements;

import android.content.Context;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;

import org.whispersystems.jobqueue.dependencies.ContextDependent;
import org.whispersystems.jobqueue.requirements.Requirement;

public class MasterSecretRequirement implements Requirement, ContextDependent {

    private transient Context context;
    private AccountContext accountContext;

    public MasterSecretRequirement(Context context, AccountContext accountContext) {
        this.context = context;
        this.accountContext = accountContext;
    }

    @Override
    public boolean isPresent() {
        return BCMEncryptUtils.INSTANCE.getMasterSecret(accountContext) != null;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }
}
