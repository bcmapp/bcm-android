package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.event.ReEditEvent;
import com.bcm.messenger.common.event.RecallFailEvent;
import com.bcm.messenger.common.exception.InsecureFallbackApprovalException;
import com.bcm.messenger.common.exception.RetryLaterException;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.OutgoingLocationMessage;
import com.bcm.messenger.utility.logger.ALog;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;


public class PushControlMessageSendJob extends PushSendJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = PushControlMessageSendJob.class.getSimpleName();

    public enum ControlType {
        DESTROY,
        RECALL
    }

    private ControlType type;
    private String outMessageBody;
    private long messageId;
    private boolean isMms;

    public PushControlMessageSendJob(Context context, AccountContext accountContext, long messageId, boolean isMms, Address destination) {
        super(context, accountContext, constructParameters(context, destination));
        this.messageId = messageId;
        this.isMms = isMms;
    }

    public PushControlMessageSendJob(Context context, AccountContext accountContext, String outMessageBody, long messageId, Address destination, ControlType type) {
        super(context, accountContext, constructParameters(context, destination));
        this.type = type;
        this.outMessageBody = outMessageBody;
        this.messageId = messageId;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onPushSend(MasterSecret masterSecret) {
        ALog.i(TAG, "OnPushSend send recall message start.");
        PrivateChatRepo chatRepo = repository.getChatRepo(accountContext);

        MessageRecord messageRecord = chatRepo.getMessage(messageId);
        if (messageRecord == null) return;

        try {
            SignalServiceAddress address = getPushAddress(Address.from(accountContext, getGroupId()));
            Recipient individualRecipient = Recipient.from(accountContext, getGroupId(), true);
            Optional<byte[]> profileKey = getProfileKey(individualRecipient);
            int expiresIn = (int) (messageRecord.getExpiresTime() / 1000);

            String recallMessage = new AmeGroupMessage<>(AmeGroupMessage.CONTROL_MESSAGE, new AmeGroupMessage.ControlContent(AmeGroupMessage.ControlContent.ACTION_RECALL_MESSAGE,
                    accountContext.getUid(),"", messageRecord.getDateSent())).toString();
            OutgoingLocationMessage recallInsertMessage = new OutgoingLocationMessage(messageRecord.getRecipient(accountContext), recallMessage, (messageRecord.getRecipient(accountContext).getExpireMessages() * 1000));

            SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
                    .withTimestamp(System.currentTimeMillis())
                    .withBody(recallInsertMessage.getMessageBody())
                    .withExpiration(expiresIn)
                    .withProfileKey(profileKey.orNull())
                    .asEndSessionMessage(false)
                    .asLocation(true)
                    .build();


            deliver(textSecureMessage, address);

            long messageId = chatRepo.insertOutgoingTextMessage(messageRecord.getThreadId(), recallInsertMessage, messageRecord.getDateSent(), null);
            chatRepo.setMessageSendSuccess(messageId);

            EventBus.getDefault().post(new ReEditEvent(individualRecipient.getAddress(), messageRecord.getId(), messageRecord.getDisplayBody().toString()));

            chatRepo.deleteMessage(messageRecord.getThreadId(), messageRecord.getId());
        } catch (InsecureFallbackApprovalException e) {
            ALog.e(TAG, "Recall failed, other is offline.", e);
            EventBus.getDefault().post(new RecallFailEvent(getGroupId(), true));
        } catch (Exception e) {
            ALog.e(TAG, "Recall failed, reason is" + e.getMessage(), e);
            EventBus.getDefault().post(new RecallFailEvent(getGroupId(), false));
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof RetryLaterException;
    }

    @Override
    public void onCanceled() {

    }

    private void deliver(SignalServiceDataMessage message, SignalServiceAddress address)
            throws UntrustedIdentityException, InsecureFallbackApprovalException, RetryLaterException {
        try {
            BcmChatCore.INSTANCE.sendSilentMessage(accountContext, address, message);
        } catch (UnregisteredUserException e) {
            ALog.e(TAG, e);
            throw new InsecureFallbackApprovalException(e);
        } catch (VersionTooLowException e) {
            ALog.e(TAG, e);
            throw new InsecureFallbackApprovalException(e);
        } catch (IOException e) {
            ALog.e(TAG, e);
            throw new RetryLaterException(e);
        }
    }
}
