package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.event.PartProgressEvent;
import com.bcm.messenger.common.exception.TextSecureExpiredException;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.provider.AmeModuleCenter;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public abstract class PushSendJob extends SendJob {

    private static final String TAG = PushSendJob.class.getSimpleName();

    protected PushSendJob(Context context, AccountContext accountContext, JobParameters parameters) {
        super(context, accountContext, parameters);
    }

    @Deprecated
    protected static JobParameters constructParameters(Context context, AccountContext accountContext, Address destination) {
        JobParameters.Builder builder = JobParameters.newBuilder();
        builder.withPersistence();
        builder.withGroupId(destination.serialize());
        builder.withRequirement(new MasterSecretRequirement(context, accountContext));
        //Shield out network conditions, otherwise sending will fail but the status is not updated
        builder.withRetryCount(5);
        return builder.create();
    }

    protected static JobParameters constructParameters(Context context, AccountContext accountContext, Address destination, String type) {
        JobParameters.Builder builder = JobParameters.newBuilder();
        builder.withPersistence();
        builder.withGroupId(destination.serialize() + type);
        builder.withRequirement(new MasterSecretRequirement(context, accountContext));
        builder.withRetryCount(3);
        return builder.create();
    }


    @Override
    protected final void onSend(MasterSecret masterSecret) throws Exception {
        if (accountContext.getSignedPreKeyFailureCount() > getRetryCount()) {
            AmeModuleCenter.INSTANCE.login().rotateSignedPrekey(accountContext);
            throw new TextSecureExpiredException("Too many signed prekey rotation failures");
        }

        onPushSend(masterSecret);
    }

//    protected Optional<byte[]> getProfileKey(@NonNull Recipient recipient) {
//        return Optional.of(ProfileKeyUtil.getProfileKey(context));
//    }

    protected SignalServiceAddress getPushAddress(Address address) {
        String relay = null;
        return new SignalServiceAddress(address.serialize(), Optional.fromNullable(relay));
    }

    protected List<SignalServiceAttachment> getAttachmentsFor(MasterSecret masterSecret, List<AttachmentRecord> parts) {
        List<SignalServiceAttachment> attachments = new LinkedList<>();

        for (final AttachmentRecord attachment : parts) {
            try {
                if (attachment.getDataUri() == null || attachment.getDataSize() == 0)
                    throw new IOException("Assertion failed, outgoing attachment has no data!");
                InputStream is = PartAuthority.getAttachmentStream(context, masterSecret, attachment.getPartUri());
                attachments.add(SignalServiceAttachment.newStreamBuilder()
                        .withStream(is)
                        .withContentType(attachment.getContentType())
                        .withLength(attachment.getDataSize())
                        .withFileName(attachment.getFileName())
                        .withVoiceNote(attachment.isVoiceNote())
                        .withIndex(attachment.getDataUri().toString())
                        .withListener(((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress))))
                        .build());
            } catch (IOException ioe) {
                Log.w(TAG, "Couldn't open attachment", ioe);
            }
        }

        return attachments;
    }

    protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) { }

    protected abstract void onPushSend(MasterSecret masterSecret) throws Exception;
}
