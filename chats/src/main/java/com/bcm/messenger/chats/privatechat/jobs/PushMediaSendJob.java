package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException;
import com.bcm.messenger.common.config.BcmFeatureSupport;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.MasterSecretUnion;
import com.bcm.messenger.common.crypto.MediaKey;
import com.bcm.messenger.common.deprecated.NoSuchMessageException;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.event.UserOfflineEvent;
import com.bcm.messenger.common.event.VersionTooLowEvent;
import com.bcm.messenger.common.exception.InsecureFallbackApprovalException;
import com.bcm.messenger.common.exception.RetryLaterException;
import com.bcm.messenger.common.exception.UndeliverableMessageException;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.jobs.RetrieveProfileJob;
import com.bcm.messenger.common.mms.MediaConstraints;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.logger.ALog;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class PushMediaSendJob extends PushSendJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = "PushMediaSendJob";
    private final long messageId;

    public PushMediaSendJob(Context context, AccountContext accountContext, long messageId, Address destination) {
        super(context, accountContext, constructParameters(context, accountContext, destination, "media"));
        this.messageId = messageId;
    }

    @Override
    public void onAdded() {
        ALog.i(TAG, "add " + messageId);
    }

    @Override
    public void onPushSend(MasterSecret masterSecret) throws NoSuchMessageException {
        PrivateChatRepo chatRepo = repository.getChatRepo();

        MessageRecord record = chatRepo.getMessage(messageId);
        if (record == null) {
            throw new NoSuchMessageException("Message id" + messageId + "not found");
        }

        try {
            ALog.i(TAG, "onPushSend " + messageId + " time:" + record.getDateSent());

            deliver(masterSecret, record);
            chatRepo.setMessageSendSuccess(messageId);
            markAttachmentsUploaded(record.getAttachments());

            if (record.getExpiresTime() > 0 && !record.isExpirationTimerUpdate()) {
                chatRepo.setMessageExpiresStart(messageId);

                ExpirationManager.INSTANCE.scheduler(accountContext).scheduleDeletion(messageId, true, record.getExpiresTime());
            }

            ALog.i(TAG, "send complete:" + messageId);

        } catch (InsecureFallbackApprovalException ifae) {
            ALog.e(TAG, ifae);
            chatRepo.setMessagePendingInsecureSmsFallback(record.getId());
            notifyMediaMessageDeliveryFailed(context, messageId);
        } catch (UntrustedIdentityException uie) {
            ALog.d(TAG, uie + " address = " + uie.getE164Number() + " identityKey = " + uie.getIdentityKey());
            ALog.e(TAG, "UntrustedIdentityException occurred", uie);
            JobManager accountJobManager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
            if (accountJobManager != null) {
                accountJobManager.add(new RetrieveProfileJob(context,accountContext, Recipient.from(accountContext, uie.getE164Number(), false)));
            }
            chatRepo.setMessageSendFail(record.getId());
        } catch (Exception e) {
            ALog.e(TAG, e);
            chatRepo.setMessageSendFail(record.getId());
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        ALog.e(TAG, "shouldRetryThrowable", exception);
        if (exception instanceof RequirementNotMetException) return true;
        if (exception instanceof RetryLaterException) return true;

        return false;
    }

    @Override
    public void onCanceled() {
        ALog.i(TAG, "onCanceled");
        repository.getChatRepo().setMessageSendFail(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
    }

    private void deliver(MasterSecret masterSecret, MessageRecord message)
            throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
            UndeliverableMessageException {

        try {
            SignalServiceAddress address = getPushAddress(message.getRecipient(accountContext).getAddress());
            MediaConstraints mediaConstraints = MediaConstraints.getPushMediaConstraints();
            List<AttachmentRecord> scaledAttachments = scaleAttachments(masterSecret, mediaConstraints, message.getAttachments());
            List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);
//            Optional<byte[]> profileKey = getProfileKey(message.getRecipient(accountContext));
            int expiresIn = (int) (message.getExpiresTime() / 1000);
            SignalServiceDataMessage mediaMessage = SignalServiceDataMessage.newBuilder()
                    .withBody(message.getBody())
                    .withAttachments(attachmentStreams)
                    .withTimestamp(message.getDateSent())
                    .withExpiration(expiresIn)
//                    .withProfileKey(profileKey.orNull())
                    .asExpirationUpdate(message.isExpirationTimerUpdate())
                    .build();

            BcmFeatureSupport featureSupport = message.getRecipient(accountContext).getFeatureSupport();
            boolean isSupportAws = featureSupport != null && featureSupport.isSupportAws();
            BcmChatCore.INSTANCE.sendMessage(accountContext, address, mediaMessage, isSupportAws);

            if (!attachmentStreams.isEmpty()) {
                for (SignalServiceAttachment attachmentStream :
                        attachmentStreams) {
                    if (attachmentStream.isStream() && attachmentStream.asStream().isUploaded()) {
                        SignalServiceAttachmentStream stream = attachmentStream.asStream();
                        for (AttachmentRecord attachment : scaledAttachments) {
                            if (attachment.getPartUri() != null) {
                                if (Objects.equals(attachment.getPartUri().toString(), stream.getIndex())) {
                                    attachment.setContentKey(MediaKey.getEncrypted(new MasterSecretUnion(masterSecret), stream.getAttachmentKey()));
                                    attachment.setContentLocation(stream.getUploadResult().attachmentId.toString());
                                    attachment.setDigest(stream.getUploadResult().digest);
                                }
                            }
                        }
                    }

                }
            }

        } catch (UnregisteredUserException e) {
            ALog.e(TAG, e);
            EventBus.getDefault().post(new UserOfflineEvent(message.getRecipient(accountContext).getAddress()));
            throw new InsecureFallbackApprovalException(e);
        } catch (VersionTooLowException e) {
            ALog.e(TAG, e);
            EventBus.getDefault().post(new VersionTooLowEvent());
            throw new InsecureFallbackApprovalException(e);
        } catch (FileNotFoundException e) {
            ALog.e(TAG, e);
            throw new UndeliverableMessageException(e);
        } catch (IOException e) {
            ALog.e(TAG, e);
            throw new RetryLaterException(e);
        }
    }
}
