package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException;
import com.bcm.messenger.common.jobs.ContextJob;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;


public class RequestGroupInfoJob extends ContextJob {

    private static final String TAG = RequestGroupInfoJob.class.getSimpleName();

    private static final long serialVersionUID = 0L;


    private final String source;
    private final byte[] groupId;

    public RequestGroupInfoJob(@NonNull Context context, @NonNull AccountContext accountContext, @NonNull String source, @NonNull byte[] groupId) {
        super(context, accountContext, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withPersistence()
                .withRetryCount(50)
                .create());

        this.source = source;
        this.groupId = groupId;
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException, VersionTooLowException {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(Type.REQUEST_INFO)
                .withId(groupId)
                .build();

        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group)
                .withTimestamp(System.currentTimeMillis())
                .build();

        BcmChatCore.INSTANCE.sendMessage(new SignalServiceAddress(source), message, false);
    }

    @Override
    public boolean onShouldRetry(Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {

    }
}
