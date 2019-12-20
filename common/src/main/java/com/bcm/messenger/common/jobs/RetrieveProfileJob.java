package com.bcm.messenger.common.jobs;


import android.content.Context;

import com.bcm.messenger.common.core.RecipientProfileLogic;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.logger.ALog;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * signal遗留的加载联系人profile信息的工作类（包括旧群组包含的联系人）
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

        // 为了兼容旧版本signal的查询profile工作（部分场景主要是为了更新identityKey）
        List<Recipient> recipientList = new ArrayList<Recipient>(mRecipientList.size());
        for (Recipient recipient : mRecipientList) {
            if (recipient.isGroupRecipient()) {//如果是群组，则把所有相关的联系人一并查询

            } else {
                recipientList.add(recipient);
            }
        }
        RecipientProfileLogic.INSTANCE.checkNeedFetchProfile(recipientList.toArray(new Recipient[recipientList.size()]), null);

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
