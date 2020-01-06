package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.bcm.messenger.chats.privatechat.core.ChatFileHttp;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.attachments.AttachmentId;
import com.bcm.messenger.common.core.BcmHttpApiHelper;
import com.bcm.messenger.common.crypto.AsymmetricMasterSecret;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.MasterSecretUtil;
import com.bcm.messenger.common.crypto.MediaKey;
import com.bcm.messenger.common.crypto.encrypt.ChatFileEncryptDecryptUtil;
import com.bcm.messenger.common.crypto.encrypt.FileInfo;
import com.bcm.messenger.common.database.model.AttachmentDbModel;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.AttachmentRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.event.PartProgressEvent;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.utils.AttachmentUtil;
import com.bcm.messenger.utility.HexUtil;
import com.bcm.messenger.utility.Util;
import com.bcm.messenger.utility.bcmhttp.exception.RemoteFileNotFoundException;
import com.bcm.messenger.utility.logger.ALog;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.File;
import java.io.IOException;


public class AttachmentDownloadJob extends MasterSecretJob {

    private static final int MAX_TRY = 3;
    private static final long serialVersionUID = 2L;
    private static final int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
    private static final String TAG = AttachmentDownloadJob.class.getSimpleName();

    private final long messageId;
    private final long partRowId;
    private final long partUniqueId;
    private final boolean manual;

    private int mTry = 0;

    public AttachmentDownloadJob(Context context, AccountContext accountContext, long messageId, AttachmentId attachmentId, boolean manual) {
        this(context, accountContext, messageId, attachmentId.getRowId(), attachmentId.getUniqueId(), manual);
    }

    public AttachmentDownloadJob(Context context, AccountContext accountContext, long messageId, long attachmentId, long uniqueId, boolean manual) {
        super(context, accountContext, JobParameters.newBuilder()
                .withGroupId(AttachmentDownloadJob.class.getCanonicalName())
                .withRequirement(new MasterSecretRequirement(context))
                .withRequirement(new NetworkRequirement(context))
                .withPersistence()
                .create());

        this.messageId = messageId;
        this.partRowId = attachmentId;
        this.partUniqueId = uniqueId;
        this.manual = manual;

    }

    @Override
    public void onAdded() {
        mTry = 0;
    }

    @Override
    public void onRun(MasterSecret masterSecret) {
        final AttachmentRepo repo = Repository.getAttachmentRepo(accountContext);
        final AttachmentId attachmentId = new AttachmentId(partRowId, partUniqueId);
        final AttachmentRecord record = repo.getAttachment(partRowId, partUniqueId);

        if (record == null) {
            Log.w(TAG, "attachment no longer exists.");
            return;
        }

        if (!record.isInProgress()) {
            Log.w(TAG, "Attachment was already downloaded.");
            return;
        }

        if (!manual && !AttachmentUtil.isAutoDownloadPermitted(context, record)) {
            Log.w(TAG, "Attachment can't be auto downloaded...");
            return;
        }

        repo.setTransferState(record, AttachmentDbModel.TransferState.STARTED);

        try {
            retrieveAttachment(masterSecret, attachmentId, record);
        } catch (Throwable t) {
            markFailed(attachmentId);
            ALog.e(TAG, t);
        }

    }

    @Override
    public void onCanceled() {
        final AttachmentId attachmentId = new AttachmentId(partRowId, partUniqueId);
        markFailed(attachmentId);
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof PushNetworkException) {
            if (mTry >= MAX_TRY) {
                return false;
            } else {
                mTry++;
                return true;
            }
        }
        return false;
    }

    private void retrieveAttachment(MasterSecret masterSecret,
                                    final AttachmentId attachmentId,
                                    final AttachmentRecord attachment) {

        File attachmentFile = null;

        try {
            attachmentFile = createTempFile();
            if (masterSecret != null) {
                SignalServiceAttachmentPointer pointer = createAttachmentPointer(masterSecret, attachment);
                FileInfo fileInfo = retrieveAttachment(masterSecret, pointer, attachmentFile, MAX_ATTACHMENT_SIZE, ((total, progress) -> {
                    if (total == progress) {
                        EventBus.getDefault().post(new PartProgressEvent(attachment, total, progress - 1));
                    } else {
                        EventBus.getDefault().post(new PartProgressEvent(attachment, total, progress));
                    }
                }));

                Repository.getAttachmentRepo(accountContext).insertForPlaceholder(masterSecret, partRowId, partUniqueId, fileInfo);
            } else {
                throw new AssertionError("MasterSecret is null");
            }
        } catch (InvalidPartException | NonSuccessfulResponseCodeException | InvalidMessageException e) {
            Log.w(TAG, e);
            markFailed(attachmentId);
        } catch (RemoteFileNotFoundException e) {
            ALog.w(TAG, "Attachment file not found");
            markNotFound(attachmentId);
        } catch (IOException e) {
            ALog.w(TAG, "Download attachment failed, " + e.getMessage());
            markFailed(attachmentId);
        } finally {
            if (attachmentFile != null) {
                attachmentFile.delete();
            }
        }
    }

    private FileInfo retrieveAttachment(MasterSecret masterSecret, SignalServiceAttachmentPointer pointer, File destination, int maxSizeBytes, SignalServiceAttachment.ProgressListener listener)
            throws IOException, InvalidMessageException {
        if (!pointer.getDigest().isPresent())
            throw new InvalidMessageException("No attachment digest!");

        String url;
        if (pointer.getUrl().isPresent()) {
            url = pointer.getUrl().get();
        } else {
            url = BcmHttpApiHelper.INSTANCE.getDownloadApi(String.format("/attachments/%s", Long.toString(pointer.getId())));
        }

        ChatFileHttp.INSTANCE.downloadAttachment(url, destination, pointer.getSize().or(0), maxSizeBytes, listener);
        return ChatFileEncryptDecryptUtil.decryptAndSaveFile(accountContext, masterSecret, destination, pointer, ChatFileEncryptDecryptUtil.FileType.PRIVATE);
    }

    @VisibleForTesting
    SignalServiceAttachmentPointer createAttachmentPointer(MasterSecret masterSecret, AttachmentRecord attachment)
            throws InvalidPartException {
        if (TextUtils.isEmpty(attachment.getContentLocation())) {
            throw new InvalidPartException("empty content id");
        }

        if (TextUtils.isEmpty(attachment.getContentKey())) {
            throw new InvalidPartException("empty encrypted key");
        }

        try {
            AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(accountContext, masterSecret);
            //
            long id = Long.parseLong(attachment.getContentLocation());
            byte[] key = MediaKey.getDecrypted(masterSecret, asymmetricMasterSecret, attachment.getContentKey());
            String relay = null;

            if (TextUtils.isEmpty(attachment.getName())) {
                relay = attachment.getName();
            }

            if (attachment.getDigest() != null) {
                Log.w(TAG, "Downloading attachment with digest: " + HexUtil.toString(attachment.getDigest()));
            } else {
                Log.w(TAG, "Downloading attachment with no digest...");
            }

            return new SignalServiceAttachmentPointer(id, null, key, relay,
                    Optional.of(Util.toIntExact(attachment.getDataSize())),
                    Optional.absent(),
                    Optional.fromNullable(attachment.getDigest()),
                    Optional.fromNullable(attachment.getFileName()),
                    attachment.isVoiceNote(),
                    Optional.fromNullable(attachment.getUrl()));

        } catch (InvalidMessageException | IOException | ArithmeticException e) {
            Log.w(TAG, e);
            throw new InvalidPartException(e);
        }
    }

    private File createTempFile() throws InvalidPartException {
        try {
            File file = File.createTempFile("push-attachment", "tmp", context.getCacheDir());
            file.deleteOnExit();

            return file;
        } catch (IOException e) {
            throw new InvalidPartException(e);
        }
    }

    private void markNotFound(AttachmentId attachmentId) {
        AttachmentRepo repo = Repository.getAttachmentRepo(accountContext);
        repo.setTransferState(attachmentId.getRowId(), attachmentId.getUniqueId(), AttachmentDbModel.TransferState.NOT_FOUND);
        repo.cleanUris(attachmentId.getRowId(), attachmentId.getUniqueId());
    }

    private void markFailed(AttachmentId attachmentId) {
        Repository.getAttachmentRepo(accountContext)
                .setTransferState(attachmentId.getRowId(), attachmentId.getUniqueId(), AttachmentDbModel.TransferState.FAILED);
    }

    @VisibleForTesting
    static class InvalidPartException extends Exception {
        public InvalidPartException(String s) {
            super(s);
        }

        public InvalidPartException(Exception e) {
            super(e);
        }
    }

}
