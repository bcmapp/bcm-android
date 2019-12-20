package com.bcm.messenger.common.jobs.requirements;

import android.content.Context;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

public class MasterSecretRequirementProvider implements RequirementProvider {

    private RequirementListener listener;

    public MasterSecretRequirementProvider(Context context) {

    }

    @Override
    public void setListener(RequirementListener listener) {
        this.listener = listener;
    }
}
