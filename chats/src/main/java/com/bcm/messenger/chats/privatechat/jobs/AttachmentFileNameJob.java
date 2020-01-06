package com.bcm.messenger.chats.privatechat.jobs;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.crypto.AsymmetricMasterCipher;
import com.bcm.messenger.common.crypto.AsymmetricMasterSecret;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.MasterSecretUtil;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.AttachmentRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.mms.IncomingMediaMessage;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;
import java.util.Arrays;

public class AttachmentFileNameJob extends MasterSecretJob {

    private static final long serialVersionUID = 1L;

    private final long attachmentRowId;
    private final long attachmentUniqueId;
    private final String encryptedFileName;

    public AttachmentFileNameJob(@NonNull Context context,
                                 @NonNull AccountContext accountContext,
                                 @NonNull AsymmetricMasterSecret asymmetricMasterSecret,
                                 @NonNull AttachmentRecord attachment,
                                 @NonNull IncomingMediaMessage message) {
        super(context, accountContext, new JobParameters.Builder().withPersistence()
                .withRequirement(new MasterSecretRequirement(context))
                .create());

        this.attachmentRowId = attachment.getId();
        this.attachmentUniqueId = attachment.getUniqueId();
        this.encryptedFileName = getEncryptedFileName(asymmetricMasterSecret, attachment, message);
    }

    @Override
    public void onRun(MasterSecret masterSecret) throws IOException, InvalidMessageException {
        if (encryptedFileName == null) return;

        String plaintextFileName = new AsymmetricMasterCipher(MasterSecretUtil.getAsymmetricMasterSecret(accountContext, masterSecret)).decryptBody(encryptedFileName);

        AttachmentRepo attachmentRepo = Repository.getAttachmentRepo(accountContext);
        if (attachmentRepo != null) {
            attachmentRepo.updateFileName(attachmentRowId, attachmentUniqueId, plaintextFileName);
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {

    }

    private @Nullable
    String getEncryptedFileName(@NonNull AsymmetricMasterSecret asymmetricMasterSecret,
                                @NonNull AttachmentRecord attachment,
                                @NonNull IncomingMediaMessage mediaMessage) {
        for (Attachment messageAttachment : mediaMessage.getAttachments()) {
            if (mediaMessage.getAttachments().size() == 1 ||
                    (messageAttachment.getDigest() != null && Arrays.equals(messageAttachment.getDigest(), attachment.getDigest()))) {
                if (messageAttachment.getFileName() == null) return null;
                else
                    return new AsymmetricMasterCipher(asymmetricMasterSecret).encryptBody(messageAttachment.getFileName());
            }
        }

        return null;
    }
}
