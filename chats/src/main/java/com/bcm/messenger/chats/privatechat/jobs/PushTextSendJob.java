package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.NoSuchMessageException;
import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.event.TextSendEvent;
import com.bcm.messenger.common.event.UserOfflineEvent;
import com.bcm.messenger.common.event.VersionTooLowEvent;
import com.bcm.messenger.common.exception.InsecureFallbackApprovalException;
import com.bcm.messenger.common.exception.RetryLaterException;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.jobs.RetrieveProfileJob;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.logger.ALog;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;


public class PushTextSendJob extends PushSendJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = "PushTextSendJob";

    private final long messageId;
    private final boolean sendSilently;

    public PushTextSendJob(Context context, long messageId, Address destination) {
        this(context, messageId, destination, false);
    }

    public PushTextSendJob(Context context, long messageId, Address destination, boolean sendSilently) {
        super(context, constructParameters(context, destination, "text"));
        this.messageId = messageId;
        this.sendSilently = sendSilently;
    }

    @Override
    public void onAdded() {
        ALog.i(TAG, "add " + messageId);
    }

    @Override
    public void onPushSend(MasterSecret masterSecret) throws NoSuchMessageException, RetryLaterException {

        PrivateChatRepo chatRepo = Repository.getChatRepo();
        MessageRecord record = chatRepo.getMessage(messageId);
        if (record == null) {
            throw new NoSuchMessageException("Message id " + messageId + " not found");
        }

        try {
            ALog.i(TAG, "onPushSend " + messageId + " time:" + record.getDateSent());
            deliver(record);
            EventBus.getDefault().post(new TextSendEvent(messageId, true, "success"));
            chatRepo.setMessageSendSuccess(messageId);
            if (record.getExpiresTime() > 0) {
                chatRepo.setMessageExpiresStart(messageId);

                ExpirationManager.INSTANCE.scheduler().scheduleDeletion(record.getId(), record.isMediaMessage(), record.getExpiresTime());
            }

            ALog.i(TAG, "send complete:" + messageId);
        } catch (InsecureFallbackApprovalException e) {
            ALog.e(TAG, e);
            EventBus.getDefault().post(new TextSendEvent(messageId, false, e.getMessage()));
            chatRepo.setMessagePendingInsecureSmsFallback(record.getId());
        } catch (UntrustedIdentityException e) {
            ALog.d(TAG, e + "address = " + e.getE164Number() + " identitykey = " + e.getIdentityKey());
            ALog.e(TAG, "UntrustedIdentityException occurred", e);
            EventBus.getDefault().post(new TextSendEvent(messageId, false, e.getMessage()));

            JobManager manager = AmeModuleCenter.INSTANCE.accountJobMgr();
            if (manager != null) {
                manager.add(new RetrieveProfileJob(context, Recipient.from(context, Address.fromSerialized(e.getE164Number()), false)));
            }

            chatRepo.setMessageSendFail(record.getId());

        } finally {
            checkMessageType(record);
        }


    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof RetryLaterException;

    }

    @Override
    public void onCanceled() {
        ALog.i(TAG, "onCanceled");
        Repository.getChatRepo().setMessageSendFail(messageId);
    }

    private void deliver(MessageRecord message)
            throws UntrustedIdentityException, InsecureFallbackApprovalException, RetryLaterException {
        try {
            SignalServiceAddress address = getPushAddress(message.getRecipient().getAddress());
            Optional<byte[]> profileKey = getProfileKey(message.getRecipient());
            int expiresIn = (int) (message.getExpiresTime() / 1000);
            SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
                    .withTimestamp(message.getDateSent())
                    .withBody(message.getBody())
                    .withExpiration(expiresIn)
                    .withProfileKey(profileKey.orNull())
                    .asEndSessionMessage(message.isEndSession())
                    .asLocation(message.isLocation())
                    .build();


            if (!sendSilently) {
                BcmChatCore.INSTANCE.sendMessage(address, textSecureMessage, false);
            } else {
                BcmChatCore.INSTANCE.sendSilentMessage(address, textSecureMessage);
            }

        } catch (UnregisteredUserException e) {
            ALog.e(TAG, e);
            EventBus.getDefault().post(new UserOfflineEvent(message.getRecipient().getAddress()));
            throw new InsecureFallbackApprovalException(e);
        } catch (VersionTooLowException e) {
            ALog.e(TAG, e);
            EventBus.getDefault().post(new VersionTooLowEvent());
            throw new InsecureFallbackApprovalException(e);
        } catch (IOException e) {
            ALog.e(TAG, e);
            throw new RetryLaterException(e);
        }
    }

    private void checkMessageType(MessageRecord messageRecord) {
        if (messageRecord.isLocation()) {
            AmeGroupMessage message = AmeGroupMessage.Companion.messageFromJson(messageRecord.getBody());
            if (message.getType() == AmeGroupMessage.EXCHANGE_PROFILE || message.getType() == AmeGroupMessage.RECEIPT) {
                Repository.getChatRepo().deleteMessage(messageRecord.getThreadId(), messageRecord.getId());
            }
        }
    }
}
