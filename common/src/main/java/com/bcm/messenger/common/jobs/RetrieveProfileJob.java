package com.bcm.messenger.common.jobs;


import android.content.Context;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.provider.AmeProvider;
import com.bcm.messenger.common.provider.IContactModule;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.logger.ALog;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 *
 */
public class RetrieveProfileJob extends ContextJob {

    private static final String TAG = RetrieveProfileJob.class.getSimpleName();

    private final List<Recipient> mRecipientList;

    public RetrieveProfileJob(Context context, List<Recipient> recipientList) {
        super(context, JobParameters.newBuilder()
                .withGroupId(RetrieveProfileJob.class.getSimpleName())
                .withRequirement(new NetworkRequirement(context))
                .withRetryCount(3)
                .create());
        this.mRecipientList = recipientList;

    }

    public RetrieveProfileJob(Context context, Recipient recipient) {
        this(context, Arrays.asList(recipient));
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() {

        ALog.i(TAG, "onRun");

        List<Recipient> recipientList = new ArrayList<Recipient>(mRecipientList.size());
        for (Recipient recipient : mRecipientList) {
            if (!recipient.isGroupRecipient()) {
                recipientList.add(recipient);
            }
        }
        AmeModuleCenter.INSTANCE.contact().checkNeedFetchProfile(recipientList.toArray(new Recipient[recipientList.size()]), null);
    }

    @Override
    public boolean onShouldRetry(Exception e) {
        ALog.e("RetrieveProfileJob", "retrieve profile fail", e);
        return e instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {

    }
}
