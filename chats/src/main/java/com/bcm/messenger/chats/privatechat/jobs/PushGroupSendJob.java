package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.DatabaseFactory;
import com.bcm.messenger.common.database.GroupReceiptDatabase.GroupReceiptInfo;
import com.bcm.messenger.common.database.NoSuchMessageException;
import com.bcm.messenger.common.database.documents.NetworkFailure;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.exception.UndeliverableMessageException;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.jobs.RetrieveProfileJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.mms.MediaConstraints;
import com.bcm.messenger.common.mms.MmsException;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.recipients.RecipientFormattingException;

import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


public class PushGroupSendJob extends PushSendJob {

    private static final long serialVersionUID = 1L;

    private static final String TAG = PushGroupSendJob.class.getSimpleName();

    private final long messageId;
    private final long filterRecipientId; // Deprecated
    private final String filterAddress;

    public PushGroupSendJob(Context context, AccountContext accountContext, long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
        super(context, accountContext, JobParameters.newBuilder()
                .withPersistence()
                .withGroupId(destination.toGroupString())
                .withRequirement(new MasterSecretRequirement(context))
                .withRetryCount(5)
                .create());

        this.messageId = messageId;
        this.filterAddress = filterAddress == null ? null : filterAddress.serialize();
        this.filterRecipientId = -1;
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onPushSend(MasterSecret masterSecret)
            throws MmsException, IOException, NoSuchMessageException {
        PrivateChatRepo chatRepo = Repository.getChatRepo(accountContext);
        MessageRecord record = chatRepo.getMessage(messageId);

        try {
            deliver(masterSecret, record, filterAddress == null ? null : Address.from(accountContext, filterAddress));

            chatRepo.setMessageSendSuccess(messageId);
            markAttachmentsUploaded(messageId, record.getAttachments());
            if (record.getExpiresTime() > 0 && !record.isExpirationTimerUpdate()) {
                chatRepo.setMessageExpiresStart(messageId);
                ExpirationManager.INSTANCE.scheduler(accountContext).scheduleDeletion(messageId, true, record.getExpiresTime());
            }
        } catch (InvalidNumberException | RecipientFormattingException | UndeliverableMessageException e) {
            Log.w(TAG, e);
            chatRepo.setMessageSendFail(messageId);
            notifyMediaMessageDeliveryFailed(context, messageId);
        } catch (EncapsulatedExceptions e) {
            Log.w(TAG, e);
            List<NetworkFailure> failures = new LinkedList<>();

            for (NetworkFailureException nfe : e.getNetworkExceptions()) {
                failures.add(new NetworkFailure(Address.from(accountContext, nfe.getE164number())));
            }

            for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
                Log.w(TAG, uie + "number= " + uie.getE164Number() + " identityKey = " + uie.getIdentityKey());

                JobManager manager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
                if (manager != null) {
                    manager.add(new RetrieveProfileJob(context, accountContext, Recipient.from(accountContext, uie.getE164Number(), false)));
                }
            }

            chatRepo.setMessageSendFail(messageId);

            if (e.getNetworkExceptions().isEmpty() && e.getUntrustedIdentityExceptions().isEmpty()) {
                chatRepo.setMessageSendSuccess(messageId);
                markAttachmentsUploaded(messageId, record.getAttachments());
            } else {
                chatRepo.setMessageSendFail(messageId);
                notifyMediaMessageDeliveryFailed(context, messageId);
            }
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof IOException) {
            return true;
        }
        return false;
    }

    @Override
    public void onCanceled() {
        Repository.getChatRepo(accountContext).setMessageSendFail(messageId);
    }

    private void deliver(MasterSecret masterSecret, MessageRecord message, @Nullable Address filterAddress)
            throws IOException, RecipientFormattingException, InvalidNumberException,
            EncapsulatedExceptions, UndeliverableMessageException {
        String groupId = message.getRecipient(accountContext).getAddress().toGroupString();
        Optional<byte[]> profileKey = getProfileKey(message.getRecipient(accountContext));
        List<Address> recipients = getGroupMessageRecipients(groupId, messageId);
        MediaConstraints mediaConstraints = MediaConstraints.getPushMediaConstraints();
        List<AttachmentRecord> scaledAttachments = scaleAttachments(masterSecret, mediaConstraints, message.getAttachments());
        List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);

        List<SignalServiceAddress> addresses;

        if (filterAddress != null) {
            addresses = getPushAddresses(filterAddress);
        } else {
            addresses = getPushAddresses(recipients);
        }

//        if (message.isGroup()) {
//            OutgoingGroupMediaMessage groupMessage = (OutgoingGroupMediaMessage) message;
//            GroupContext groupContext = groupMessage.getGroupContext();
//            SignalServiceAttachment avatar = attachmentStreams.isEmpty() ? null : attachmentStreams.get(0);
//            SignalServiceGroup.Type type = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
//            SignalServiceGroup group = new SignalServiceGroup(type, GroupUtil.getDecodedId(groupId), groupContext.getName(), groupContext.getMembersList(), avatar);
//            SignalServiceDataMessage groupDataMessage = SignalServiceDataMessage.newBuilder()
//                    .withTimestamp(message.getDateSent())
//                    .asGroupMessage(group)
//                    .build();
//
//            messageSender.sendMessage(addresses, groupDataMessage);
//        } else {
//            SignalServiceGroup group = new SignalServiceGroup(GroupUtil.getDecodedId(groupId));
//            SignalServiceDataMessage groupMessage = SignalServiceDataMessage.newBuilder()
//                    .withTimestamp(message.getDateSent())
//                    .asGroupMessage(group)
//                    .withAttachments(attachmentStreams)
//                    .withBody(message.getBody())
//                    .withExpiration((int) (message.getExpiresTime() / 1000))
//                    .asExpirationUpdate(message.isExpirationTimerUpdate())
//                    .withProfileKey(profileKey.orNull())
//                    .asLocation(message.isLocation())
//                    .build();
//
//            BcmChatCore.INSTANCE.sendMessage(addresses, groupMessage);
//        }
    }

    private List<SignalServiceAddress> getPushAddresses(Address address) {
        List<SignalServiceAddress> addresses = new LinkedList<>();
        addresses.add(getPushAddress(address));
        return addresses;
    }

    private List<SignalServiceAddress> getPushAddresses(List<Address> addresses) {
        return Stream.of(addresses).map(this::getPushAddress).toList();
    }

    private @NonNull
    List<Address> getGroupMessageRecipients(String groupId, long messageId) {
        List<GroupReceiptInfo> destinations = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId);
        if (!destinations.isEmpty()) {
            return Stream.of(destinations).map(GroupReceiptInfo::getAddress).toList();
        }

        List<Recipient> members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
        return Stream.of(members).map(Recipient::getAddress).toList();
    }
}
