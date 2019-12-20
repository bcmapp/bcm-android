package com.bcm.messenger.chats.privatechat.jobs;


import android.content.Context;
import android.util.Log;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.database.DatabaseFactory;
import com.bcm.messenger.common.database.GroupDatabase;
import com.bcm.messenger.common.database.GroupDatabase.GroupRecord;
import com.bcm.messenger.common.groups.GroupUpdateModel;
import com.bcm.messenger.common.jobs.ContextJob;
import com.bcm.messenger.common.utils.GroupUtil;
import com.google.gson.Gson;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


public class PushGroupUpdateJob extends ContextJob {

    private static final String TAG = PushGroupUpdateJob.class.getSimpleName();

    private static final long serialVersionUID = 0L;

    private final String source;
    private final byte[] groupId;


    public PushGroupUpdateJob(Context context, String source, byte[] groupId) {
        super(context, JobParameters.newBuilder()
                .withPersistence()
                .withRequirement(new NetworkRequirement(context))
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
        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<GroupRecord> record = groupDatabase.getGroup(GroupUtil.getEncodedId(groupId, false));
        SignalServiceAttachment avatar = null;

        if (record == null) {
            Log.w(TAG, "No information for group record info request: " + new String(groupId));
            return;
        }

        if (record.get().getAvatar() != null) {
            avatar = SignalServiceAttachmentStream.newStreamBuilder()
                    .withContentType("image/jpeg")
                    .withStream(new ByteArrayInputStream(record.get().getAvatar()))
                    .withLength(record.get().getAvatar().length)
                    .build();
        }

        Gson gson = new Gson();
        GroupUpdateModel groupUpdateModel = new GroupUpdateModel();
        List<String> members = new LinkedList<>();
        for (Address member : record.get().getMembers()) {
            members.add(member.serialize());
        }
        groupUpdateModel.setNumbers(members);
        groupUpdateModel.setAction(GroupUpdateModel.GROUP_UPDATE);
        List<String> updateInfos = new LinkedList<>();
        updateInfos.add(gson.toJson(groupUpdateModel));

        SignalServiceGroup groupContext = SignalServiceGroup.newBuilder(Type.UPDATE)
                .withAvatar(avatar)
                .withId(groupId)
                .withMembers(updateInfos)
                .withName(record.get().getTitle())
                .build();

        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(groupContext)
                .withTimestamp(System.currentTimeMillis())
                .build();

        BcmChatCore.INSTANCE.sendMessage(new SignalServiceAddress(source), message, false);
    }

    @Override
    public boolean onShouldRetry(Exception e) {
        Log.w(TAG, e);
        return e instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {

    }
}
