package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.AttachmentRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.exception.UndeliverableMessageException;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.mms.MediaConstraints;
import com.bcm.messenger.common.mms.MediaStream;

import org.whispersystems.jobqueue.JobParameters;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public abstract class SendJob extends MasterSecretJob {

    private final static String TAG = SendJob.class.getSimpleName();

    public SendJob(Context context, AccountContext accountContext, JobParameters parameters) {
        super(context, accountContext, parameters);
    }

    @Override
    public final void onRun(MasterSecret masterSecret) throws Exception {

        onSend(masterSecret);
    }

    protected abstract void onSend(MasterSecret masterSecret) throws Exception;

    protected void markAttachmentsUploaded(long messageId, @NonNull List<AttachmentRecord> attachments) {
        Repository.getAttachmentRepo(accountContext).setAttachmentUploaded(attachments);
    }

    protected List<AttachmentRecord> scaleAttachments(@NonNull MasterSecret masterSecret,
                                                      @NonNull MediaConstraints constraints,
                                                      @NonNull List<AttachmentRecord> attachments)
            throws UndeliverableMessageException {
        AttachmentRepo attachmentRepo = Repository.getAttachmentRepo(accountContext);
        List<AttachmentRecord> results = new LinkedList<>();

        for (AttachmentRecord attachment : attachments) {
            try {
                if (constraints.isSatisfied(context, masterSecret, attachment)) {
                    results.add(attachment);
                } else if (constraints.canResize(attachment)) {
                    MediaStream resized = constraints.getResizedMedia(context, masterSecret, attachment);
                    results.add(attachmentRepo.updateAttachmentData(masterSecret, attachment, resized));
                } else {
                    throw new UndeliverableMessageException("Size constraints could not be met!");
                }
            } catch (IOException e) {
                throw new UndeliverableMessageException(e);
            }
        }

        return results;
    }
}
