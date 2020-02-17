package com.bcm.messenger.chats.privatechat.jobs;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bcm.messenger.chats.privatechat.core.ChatHttp;
import com.bcm.messenger.chats.privatechat.logic.MessageSender;
import com.bcm.messenger.chats.privatechat.webrtc.CameraState;
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService;
import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.attachments.PointerAttachment;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.AddressUtil;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.crypto.IdentityKeyUtil;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.MasterSecretUnion;
import com.bcm.messenger.common.crypto.MasterSecretUtil;
import com.bcm.messenger.common.crypto.SecurityEvent;
import com.bcm.messenger.common.crypto.storage.SignalProtocolStoreImpl;
import com.bcm.messenger.common.crypto.storage.TextSecureSessionStore;
import com.bcm.messenger.common.database.model.DecryptFailData;
import com.bcm.messenger.common.database.model.ProfileKeyModel;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.PushRepo;
import com.bcm.messenger.common.database.repositories.RecipientRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.database.repositories.ThreadRepo;
import com.bcm.messenger.common.deprecated.NoSuchMessageException;
import com.bcm.messenger.common.event.MessageReceiveNotifyEvent;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.expiration.IExpiringScheduler;
import com.bcm.messenger.common.jobs.ContextJob;
import com.bcm.messenger.common.jobs.RetrieveProfileJob;
import com.bcm.messenger.common.mms.IncomingMediaMessage;
import com.bcm.messenger.common.mms.OutgoingExpirationUpdateMessage;
import com.bcm.messenger.common.mms.OutgoingMediaMessage;
import com.bcm.messenger.common.mms.OutgoingSecureMediaMessage;
import com.bcm.messenger.common.mms.SlideDeck;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.provider.accountmodule.IContactModule;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.IncomingEncryptedMessage;
import com.bcm.messenger.common.sms.IncomingEndSessionMessage;
import com.bcm.messenger.common.sms.IncomingLocationMessage;
import com.bcm.messenger.common.sms.IncomingPreKeyBundleMessage;
import com.bcm.messenger.common.sms.IncomingTextMessage;
import com.bcm.messenger.common.sms.OutgoingEncryptedMessage;
import com.bcm.messenger.common.sms.OutgoingEndSessionMessage;
import com.bcm.messenger.common.sms.OutgoingLocationMessage;
import com.bcm.messenger.common.sms.OutgoingTextMessage;
import com.bcm.messenger.common.utils.AmePushProcess;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.common.utils.IdentityUtil;
import com.bcm.messenger.common.utils.RxBus;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.netswitchy.configure.AmeConfigure;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlin.Pair;

public class PushDecryptJob extends ContextJob {

    private static final long serialVersionUID = 3L;

    public static final String TAG = PushDecryptJob.class.getSimpleName();

    private final long messageId;
    private final long smsMessageId;
    private transient Repository repository;

    public PushDecryptJob(Context context, AccountContext accountContext, long pushMessageId) {
        this(context, accountContext, pushMessageId, -1);
    }

    public PushDecryptJob(Context context, AccountContext accountContext, long pushMessageId, long smsMessageId) {
        super(context, accountContext, JobParameters.newBuilder()
                .withPersistence()
                .withGroupId("__PUSH_DECRYPT_JOB__")
                .withWakeLock(true, 5, TimeUnit.SECONDS)
                .create());
        this.messageId = pushMessageId;
        this.smsMessageId = smsMessageId;
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() throws NoSuchMessageException {
        this.repository = Repository.getInstance(accountContext);
        if (repository == null) {
            ALog.logForSecret(TAG, "User " + accountContext.getUid() + " is not login");
            return;
        }
        if (!IdentityKeyUtil.hasIdentityKey(accountContext)) {
            ALog.e(TAG, "Skipping job, waiting for migration...");
            return;
        }

        MasterSecret masterSecret = accountContext.getMasterSecret();
        PushRepo pushRepo = repository.getPushRepo();
        SignalServiceProtos.Envelope envelope = pushRepo.get(messageId);

        MasterSecretUnion masterSecretUnion;

        if (masterSecret == null) {
            masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(accountContext, null));
        } else {
            masterSecretUnion = new MasterSecretUnion(masterSecret);
        }

        handleMessage(masterSecretUnion, envelope);
        pushRepo.delete(messageId);
    }

    @Override
    public boolean onShouldRetry(Exception exception) {
        ALog.e(TAG, "PushDecryptJob catch error", exception);
        return false;
    }

    @Override
    public void onCanceled() {

    }

    /**
     * The envelop here is already the envelop after decrypting the source. There is no problem in directly getting the source.
     * Decrypted sourceExtra is placed in PushReceivedJob
     */
    private void handleMessage(MasterSecretUnion masterSecret, SignalServiceProtos.Envelope envelope) {
        try {
            SignalProtocolStore axolotlStore = new SignalProtocolStoreImpl(context, accountContext);
            SignalServiceAddress localAddress = new SignalServiceAddress(accountContext.getUid());
            SignalServiceCipher cipher = new SignalServiceCipher(localAddress, axolotlStore);

            if (localAddress.getNumber().equals(envelope.getSource())) {
                // Do nothing if message is sent to major
                return;
            }

            SignalServiceContent content = cipher.decrypt(envelope);

            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();
                if (message.isEndSession()) {
                    handleEndSessionMessage(envelope, message);
                } else if (message.isExpirationUpdate()) {
                    handleExpirationUpdate(masterSecret, envelope, message);
                } else if (message.getAttachments().isPresent()) {
                    handleMediaMessage(masterSecret, envelope, message);
                } else if (message.isLocation()) {
                    handleLocationMessage(masterSecret, envelope, message);
                } else if (message.getBody().isPresent()) {
                    handleTextMessage(masterSecret, envelope, message);
                }

                if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
                    handleProfileKey(envelope, message);
                }

            } else if (content.getSyncMessage().isPresent()) {
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

                if (syncMessage.getSent().isPresent())
                    handleSynchronizeSentMessage(masterSecret, envelope, syncMessage.getSent().get());
                else if (syncMessage.getRequest().isPresent())
                    handleSynchronizeRequestMessage(syncMessage.getRequest().get());
                else if (syncMessage.getRead().isPresent())
                    handleSynchronizeReadMessage(syncMessage.getRead().get(), envelope.getTimestamp());
                else if (syncMessage.getVerified().isPresent())
                    handleSynchronizeVerifiedMessage(masterSecret, syncMessage.getVerified().get());
                else
                    ALog.w(TAG, "Contains no known sync types...");
            } else if (content.getCallMessage().isPresent()) {
                ALog.w(TAG, "Got call message...");
                SignalServiceCallMessage message = content.getCallMessage().get();

                if (message.getOfferMessage().isPresent())
                    handleCallOfferMessage(envelope, message.getOfferMessage().get());
                else if (message.getAnswerMessage().isPresent())
                    handleCallAnswerMessage(envelope, message.getAnswerMessage().get());
                else if (message.getIceUpdateMessages().isPresent())
                    handleCallIceUpdateMessage(envelope, message.getIceUpdateMessages().get());
                else if (message.getHangupMessage().isPresent())
                    handleCallHangupMessage(envelope, message.getHangupMessage().get());
                else if (message.getBusyMessage().isPresent())
                    handleCallBusyMessage(envelope, message.getBusyMessage().get());
            } else if (content.getReceiptMessage().isPresent()) {
                SignalServiceReceiptMessage message = content.getReceiptMessage().get();

                if (message.isReadReceipt()) {
                    handleReadReceipt(envelope, message);
                } else if (message.isDeliveryReceipt()) {
                    handleDeliveryReceipt(envelope, message);
                }
            } else {
                ALog.w(TAG, "Got unrecognized message...");
            }

            if (envelope.getType() == SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE) {
                AmeModuleCenter.INSTANCE.login().refreshPrekeys(accountContext);
            }
            //After receiving the message, you should notify the private chat window to update the thread
            EventBus.getDefault().post(new MessageReceiveNotifyEvent(accountContext, envelope.getSource(), repository.getThreadRepo().getThreadIdIfExist(envelope.getSource())));

        } catch (InvalidVersionException e) {
            ALog.e(TAG, e);
            handleInvalidVersionMessage(envelope);
        } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | NoSessionException e) {
            ALog.e(TAG, e);
            handleCorruptMessage(masterSecret, envelope);
        } catch (LegacyMessageException e) {
            ALog.e(TAG, e);
            handleLegacyMessage(envelope);
        } catch (DuplicateMessageException e) {
            ALog.e(TAG, e);
        } catch (UntrustedIdentityException e) {
            ALog.e(TAG, e);
            handleUntrustedIdentityMessage(envelope);
        }
    }

    private void handleCallOfferMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                        @NonNull OfferMessage message) {
        ALog.logForSecret(TAG, "handleCallOfferMessage:" + message.getDescription());

        try {
            Intent intent = new Intent(context, WebRtcCallService.class);
            intent.setAction(WebRtcCallService.ACTION_INCOMING_CALL);
            intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
            CameraState.Direction callType = message.callType()== SignalServiceProtos.CallMessage.Offer.CallType.VIDEO? CameraState.Direction.FRONT:CameraState.Direction.NONE;
            intent.putExtra(WebRtcCallService.EXTRA_CALL_TYPE, callType.ordinal());
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.from(accountContext, envelope.getSource()));
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
            intent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, envelope.getTimestamp());
            intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
            AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
        } catch (Exception ex) {
            ALog.e(TAG, "handleCallOfferMessage error", ex);
        }
    }

    private void handleCallAnswerMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                         @NonNull AnswerMessage message) {
        ALog.i(TAG, "handleCallAnswerMessage...");
        try {
            Intent intent = new Intent(context, WebRtcCallService.class);
            intent.setAction(WebRtcCallService.ACTION_RESPONSE_MESSAGE);
            intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.from(accountContext, envelope.getSource()));
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
            intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
            AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
        } catch (Exception ex) {
            ALog.e(TAG, "handleCallAnswerMessage error", ex);
        }
    }

    private void handleCallIceUpdateMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                            @NonNull List<IceUpdateMessage> messages) {
        ALog.i(TAG, "handleCallIceUpdateMessage... " + messages.size());
        for (IceUpdateMessage message : messages) {
            try {
                Intent intent = new Intent(context, WebRtcCallService.class);
                intent.setAction(WebRtcCallService.ACTION_ICE_MESSAGE);
                intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
                intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.from(accountContext, envelope.getSource()));
                intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP, message.getSdp());
                intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_MID, message.getSdpMid());
                intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_LINE_INDEX, message.getSdpMLineIndex());
                intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
                AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
            } catch (Exception ex) {
                ALog.e(TAG, "handleCallIceUpdateMessage error", ex);
            }
        }
    }

    private void handleCallHangupMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                         @NonNull HangupMessage message) {
        ALog.i(TAG, "handleCallHangupMessage");
        try {
            Intent intent = new Intent(context, WebRtcCallService.class);
            intent.setAction(WebRtcCallService.ACTION_REMOTE_HANGUP);
            intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.from(accountContext, envelope.getSource()));
            intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
            AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
        } catch (Exception ex) {
            ALog.e(TAG, "handleCallHangupMessage error", ex);
        }
    }

    private void handleCallBusyMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                       @NonNull BusyMessage message) {
        ALog.i(TAG, "handleCallBusyMessage");
        try {
            Intent intent = new Intent(context, WebRtcCallService.class);
            intent.setAction(WebRtcCallService.ACTION_REMOTE_BUSY);
            intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.from(accountContext, envelope.getSource()));
            intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
            AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
        } catch (Exception ex) {
            ALog.e(TAG, "handleCallBusyMessage error", ex);
        }
    }

    private void handleEndSessionMessage(@NonNull SignalServiceProtos.Envelope envelope,
                                         @NonNull SignalServiceDataMessage message) {
        ALog.i(TAG, "handleEndSessionMessage");
        PrivateChatRepo chatRepo = repository.getChatRepo();
        IncomingTextMessage incomingTextMessage = new IncomingTextMessage(Address.from(accountContext, envelope.getSource()),
                envelope.getSourceDevice(),
                message.getTimestamp(),
                "", Optional.absent(), 0);

        long threadId;

        IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
        kotlin.Pair<Long, Long> insertResult = chatRepo.insertIncomingTextMessage(incomingEndSessionMessage);

        if (insertResult.getSecond() > 0)
            threadId = insertResult.getFirst();
        else
            threadId = 0L;

        if (threadId > 0L) {
            SessionStore sessionStore = new TextSecureSessionStore(context, accountContext);
            sessionStore.deleteAllSessions(envelope.getSource());

            SecurityEvent.broadcastSecurityUpdateEvent(context);
        }
    }

    private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message) {
        ALog.i(TAG, "handleSynchronizeSentEndSessionMessage");
        PrivateChatRepo chatRepo = repository.getChatRepo();
        Recipient recipient = getSyncMessageDestination(message);
        OutgoingTextMessage outgoingTextMessage = new OutgoingTextMessage(recipient, "", -1);
        OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

        long threadId = repository.getThreadRepo().getThreadIdFor(recipient);

        if (!recipient.isGroupRecipient()) {
            SessionStore sessionStore = new TextSecureSessionStore(context, accountContext);
            sessionStore.deleteAllSessions(recipient.getAddress().serialize());

            SecurityEvent.broadcastSecurityUpdateEvent(context);

            chatRepo.insertOutgoingTextMessage(threadId, outgoingEndSessionMessage, message.getTimestamp(), null);
            chatRepo.setMessageSendSuccess(messageId);
        }

        return threadId;
    }

    private void handleExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                        @NonNull SignalServiceProtos.Envelope envelope,
                                        @NonNull SignalServiceDataMessage message) {
        ALog.i(TAG, "handleExpirationUpdate");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        Recipient recipient = getMessageDestination(envelope, message);
        IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret,
                Address.from(accountContext, envelope.getSource()),
                message.getTimestamp(), -1,
                message.getExpiresInSeconds() * 1000, true,
                Optional.fromNullable(envelope.getRelay()),
                Optional.absent(), message.getGroupInfo(),
                Optional.absent());


        chatRepo.insertIncomingMediaMessage(masterSecret.getMasterSecret().get(), mediaMessage);

        repository.getRecipientRepo().setExpireTime(recipient, message.getExpiresInSeconds());
    }

    private void handleSynchronizeVerifiedMessage(@NonNull MasterSecretUnion masterSecret,
                                                  @NonNull VerifiedMessage verifiedMessage) {
        ALog.i(TAG, "handleSynchronizeVerifiedMessage");
        IdentityUtil.processVerifiedMessage(context, accountContext, masterSecret, verifiedMessage);
    }

    private void handleSynchronizeSentMessage(@NonNull MasterSecretUnion masterSecret,
                                              @NonNull SignalServiceProtos.Envelope envelope,
                                              @NonNull SentTranscriptMessage message) {
        ALog.i(TAG, "handleSynchronizeSentMessage");
        long threadId;

        if (message.getMessage().isEndSession()) {
            threadId = handleSynchronizeSentEndSessionMessage(message);
        } else if (message.getMessage().isGroupUpdate()) {
            threadId = -1L;
        } else if (message.getMessage().isExpirationUpdate()) {
            threadId = handleSynchronizeSentExpirationUpdate(masterSecret, message);
        } else if (message.getMessage().getAttachments().isPresent()) {
            threadId = handleSynchronizeSentMediaMessage(masterSecret, message);
        } else {
            threadId = handleSynchronizeSentTextMessage(masterSecret, message);
        }

        if (message.getMessage().getProfileKey().isPresent()) {
            Recipient recipient = null;

            if (message.getDestination().isPresent()) {
                recipient = Recipient.from(Address.from(accountContext, message.getDestination().get()), false);
            } else if (message.getMessage().getGroupInfo().isPresent()) {
                recipient = Recipient.from(Address.from(accountContext, GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);
            }


            if (recipient != null && !recipient.isProfileSharing()) {
                repository.getRecipientRepo().setProfileSharing(recipient, true);
            }
        }

        if (threadId > 0L) {
            repository.getThreadRepo().setRead(threadId, true);
        }
    }

    private void handleSynchronizeRequestMessage(@NonNull RequestMessage message) {
        ALog.i(TAG, "handleSynchronizeRequestMessage");
    }

    private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages,
                                              long envelopeTimestamp) {
        ALog.i(TAG, "handleSynchronizeReadMessage");

        for (ReadMessage readMessage : readMessages) {
            List<kotlin.Pair<Long, Long>> expiring = repository.getChatRepo().setTimestampRead(readMessage.getSender(), readMessage.getTimestamp(), envelopeTimestamp);

            IExpiringScheduler manager = ExpirationManager.INSTANCE.get(accountContext);
            for (Pair<Long, Long> expiringMessage : expiring) {
                manager.scheduleDeletion(expiringMessage.getFirst(), false, envelopeTimestamp, expiringMessage.getSecond());
            }

        }
    }

    private void handleMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                    @NonNull SignalServiceProtos.Envelope envelope,
                                    @NonNull SignalServiceDataMessage message) {

        ALog.i(TAG, "handleMediaMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        Recipient recipient = getMessageDestination(envelope, message);
        long expiresIn = 0;
        if (recipient.getExpireMessages() > 0) {
            expiresIn = recipient.getExpireMessages() * 1000;
        }
        if (AmeConfigure.INSTANCE.isOutgoingAutoDelete()) {
            expiresIn = message.getExpiresInSeconds() * 1000;
        }

        IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret,
                Address.from(accountContext, envelope.getSource()),
                message.getTimestamp(), -1,
                expiresIn, false,
                Optional.fromNullable(envelope.getRelay()),
                message.getBody(),
                message.getGroupInfo(),
                message.getAttachments());

        if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
            handleExpirationUpdate(masterSecret, envelope, message);
        }

        if (recipient.getAddress().isIndividual()) {
            AmePushProcess.INSTANCE.processPush(accountContext, new AmePushProcess.BcmData(new AmePushProcess.BcmNotify(AmePushProcess.CHAT_NOTIFY, 0, new AmePushProcess.ChatNotifyData(recipient.getAddress().serialize()), null, null, null, null)));
        }

        kotlin.Pair<Long, Long> insertResult = chatRepo.insertIncomingMediaMessage(masterSecret.getMasterSecret().get(), mediaMessage);
        List<AttachmentRecord> attachments = repository.getAttachmentRepo().getAttachments(insertResult.getSecond());


        JobManager manager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
        if (manager != null) {
            for (AttachmentRecord attachment : attachments) {
                manager.add(new AttachmentDownloadJob(context, accountContext, insertResult.getSecond(), attachment.getId(), attachment.getUniqueId(), false));

                if (!masterSecret.getMasterSecret().isPresent()) {
                    manager.add(new AttachmentFileNameJob(context, accountContext, masterSecret.getAsymmetricMasterSecret().get(), attachment, mediaMessage));
                }
            }
        }
    }


    private long handleSynchronizeSentExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                                       @NonNull SentTranscriptMessage message) {
        ALog.i(TAG, "handleSynchronizeSentExpirationUpdate");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        Recipient recipient = getSyncMessageDestination(message);

        OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
                message.getTimestamp(),
                message.getMessage().getExpiresInSeconds() * 1000);

        long threadId = repository.getThreadRepo().getThreadIdFor(recipient);
        long messageId = chatRepo.insertOutgoingMediaMessage(threadId, masterSecret.getMasterSecret().get(), expirationUpdateMessage, null);

        chatRepo.setMessageSendSuccess(messageId);

        RecipientRepo recipientRepo = Repository.getRecipientRepo(accountContext);
        if (recipientRepo != null) {
            recipientRepo.setExpireTime(recipient, message.getMessage().getExpiresInSeconds());
        }
        return threadId;
    }

    private long handleSynchronizeSentMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                                   @NonNull SentTranscriptMessage message) {
        ALog.i(TAG, "handleSynchronizeSentMediaMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        Recipient recipients = getSyncMessageDestination(message);
        OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                PointerAttachment.forPointers(masterSecret, message.getMessage().getAttachments()),
                message.getTimestamp(), -1,
                message.getMessage().getExpiresInSeconds() * 1000,
                ThreadRepo.DistributionTypes.DEFAULT);

        mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

        if (recipients.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
            handleSynchronizeSentExpirationUpdate(masterSecret, message);
        }

        long threadId = repository.getThreadRepo().getThreadIdFor(recipients);
        long messageId = chatRepo.insertOutgoingMediaMessage(threadId, masterSecret.getMasterSecret().get(), mediaMessage, null);

        chatRepo.setMessageSendSuccess(messageId);

        for (AttachmentRecord attachment : repository.getAttachmentRepo().getAttachments(messageId)) {
            JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
            if (jobManager != null) {
                jobManager.add(new AttachmentDownloadJob(context, accountContext, messageId, attachment.getId(), attachment.getUniqueId(), false));
            }
        }

        if (message.getMessage().getExpiresInSeconds() > 0) {
            chatRepo.setMessageExpiresStart(messageId);

            IExpiringScheduler manager = ExpirationManager.INSTANCE.get(accountContext);
            manager.scheduleDeletion(messageId, true,
                    message.getExpirationStartTimestamp(),
                    message.getMessage().getExpiresInSeconds() * 1000);

        }

        return threadId;
    }


    private void handleLocationMessage(@NonNull MasterSecretUnion masterSecret,
                                       @NonNull SignalServiceProtos.Envelope envelope,
                                       @NonNull SignalServiceDataMessage message) {

        ALog.i(TAG, "handleLocationMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        ThreadRepo threadRepo = repository.getThreadRepo();
        Recipient recipient = getMessageDestination(envelope, message);

        String body = message.getBody().isPresent() ? message.getBody().get() : "";
        AmeGroupMessage newMessage = AmeGroupMessage.Companion.messageFromJson(body);
        if (newMessage.getType() == AmeGroupMessage.GROUP_KEY) {
            return;
        }

        long currentDateSentStamp = message.getTimestamp();
        if (newMessage.getType() == AmeGroupMessage.CONTROL_MESSAGE) {
            AmeGroupMessage.ControlContent controlContent = (AmeGroupMessage.ControlContent) newMessage.getContent();
            if (controlContent.getActionCode() == 0) {
                threadRepo.deleteConversationContent(threadRepo.getThreadIdFor(recipient));
                body = new AmeGroupMessage<>(AmeGroupMessage.CONTROL_MESSAGE, new AmeGroupMessage.ControlContent(AmeGroupMessage.ControlContent.ACTION_CLEAR_MESSAGE, recipient.getAddress().serialize(), "", 0L)).toString();
            } else if (controlContent.getActionCode() == 1) {
                // ignore
            } else if (controlContent.getActionCode() == AmeGroupMessage.ControlContent.ACTION_RECALL_MESSAGE) {
                long threadId = threadRepo.getThreadIdIfExist(recipient);
                Long recallMessageDateSent = controlContent.getMessageId();
                if (threadId >= 0) {
                    boolean hasMessage = false;
                    if (recallMessageDateSent != null) {
                        hasMessage = chatRepo.deleteIncomingMessageByDateSent(threadId, recallMessageDateSent);
                    }
                    if (!hasMessage) {
                        return;
                    }
                } else {
                    return;
                }
                body = new AmeGroupMessage<>(AmeGroupMessage.CONTROL_MESSAGE, new AmeGroupMessage.ControlContent(AmeGroupMessage.ControlContent.ACTION_RECALL_MESSAGE, recipient.getAddress().serialize(), "", recallMessageDateSent)).toString();
                currentDateSentStamp = recallMessageDateSent;
            }

        } else if (newMessage.getType() == AmeGroupMessage.SCREEN_SHOT_MESSAGE) {

            body = new AmeGroupMessage<>(AmeGroupMessage.SCREEN_SHOT_MESSAGE, new AmeGroupMessage.ScreenshotContent(recipient.getName())).toString();

        } else if (newMessage.getType() == AmeGroupMessage.EXCHANGE_PROFILE) {
            ALog.i(TAG, "Receive exchange profile message");
            AmeGroupMessage.ExchangeProfileContent content = (AmeGroupMessage.ExchangeProfileContent) newMessage.getContent();
            long threadId = threadRepo.getThreadIdFor(recipient);
            if (threadId > 0) {
                IContactModule contactModule = AmeModuleCenter.INSTANCE.contact(accountContext);
                if (contactModule == null) {
                    ALog.w(TAG, "Contact module is null!");
                    return;
                }
                switch (content.getType()) {
                    case AmeGroupMessage.ExchangeProfileContent.RESPONSE:
                        ALog.i(TAG, "Get response type, update profile keys");
                        contactModule.updateProfileKey(AppContextHolder.APP_CONTEXT, recipient,
                                new ProfileKeyModel(content.getNickName(), content.getAvatar(), content.getVersion()));

                        break;
                    case AmeGroupMessage.ExchangeProfileContent.REQUEST:
                        ALog.i(TAG, "Get request type, update profile keys and save request to thread");
                        contactModule.updateProfileKey(AppContextHolder.APP_CONTEXT, recipient,
                                new ProfileKeyModel(content.getNickName(), content.getAvatar(), content.getVersion()));
                    case AmeGroupMessage.ExchangeProfileContent.CHANGE:
                        ALog.i(TAG, "Get change type, save request to thread");
                        threadRepo.setProfileRequest(threadId, true);
                        RxBus.INSTANCE.post(ARouterConstants.PARAM.PARAM_HAS_REQUEST, recipient);
                        break;
                    default:
                        break;
                }
            }
            return;
        } else if (newMessage.getType() == AmeGroupMessage.RECEIPT) {
            // Receive a new failure receipt, set the message as decryption failure, Thread increases the failure flag, the receipt is not stored in the database, and is discarded after processing
            ALog.i(TAG, "Receive receipt message");
            long failMessageId = ((AmeGroupMessage.ReceiptContent) newMessage.getContent()).getMessageId();
            boolean success = chatRepo.setMessageCannotDecrypt(recipient.getAddress().serialize(), failMessageId);
            if (success) {
                ALog.i(TAG, "Mark decrypted failed success, id = " + failMessageId);
                String dataJson = threadRepo.getDecryptFailData(recipient.getAddress().serialize());
                DecryptFailData data;
                if (dataJson != null && !dataJson.isEmpty()) {
                    data = GsonUtils.INSTANCE.fromJson(dataJson, DecryptFailData.class);
                } else {
                    data = new DecryptFailData();
                }
                data.increaseFailCount();
                if (data.getFirstNotFoundMsgTime() == 0 || data.getFirstNotFoundMsgTime() > failMessageId) {
                    data.setFirstNotFoundMsgTime(failMessageId);
                }
                threadRepo.setDecryptFailData(recipient.getAddress().serialize(), data.toJson());
            }
            return;
        } else {
            if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
                handleExpirationUpdate(masterSecret, envelope, message);
            }
        }

        long expiresIn = 0;
        if (newMessage.getType() != AmeGroupMessage.CONTROL_MESSAGE && newMessage.getType() != AmeGroupMessage.SCREEN_SHOT_MESSAGE) {
            if (recipient.getExpireMessages() > 0) {
                expiresIn = recipient.getExpireMessages() * 1000;
            }
            if (AmeConfigure.INSTANCE.isOutgoingAutoDelete()) {
                expiresIn = message.getExpiresInSeconds() * 1000;
            }
        }

        IncomingTextMessage commonMessage = new IncomingTextMessage(Address.from(accountContext, envelope.getSource()),
                envelope.getSourceDevice(),
                currentDateSentStamp, body,
                message.getGroupInfo(),
                expiresIn);

        IncomingLocationMessage textMessage = new IncomingLocationMessage(commonMessage, body);

        chatRepo.insertIncomingTextMessage(textMessage);
    }

    private void handleTextMessage(@NonNull MasterSecretUnion masterSecret,
                                   @NonNull SignalServiceProtos.Envelope envelope,
                                   @NonNull SignalServiceDataMessage message) {

        ALog.i(TAG, "handleTextMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();
        String body = message.getBody().isPresent() ? message.getBody().get() : "";
        Recipient recipient = getMessageDestination(envelope, message);

        if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
            handleExpirationUpdate(masterSecret, envelope, message);
        }

        if (recipient.getAddress().isIndividual()) {
            AmePushProcess.INSTANCE.processPush(accountContext, new AmePushProcess.BcmData(new AmePushProcess.BcmNotify(AmePushProcess.CHAT_NOTIFY, 0, new AmePushProcess.ChatNotifyData(recipient.getAddress().serialize()), null, null, null, null)));
        }

        long expiresIn = 0;
        if (recipient.getExpireMessages() > 0) {
            expiresIn = recipient.getExpireMessages() * 1000;
        } else if (AmeConfigure.INSTANCE.isOutgoingAutoDelete()) {
            expiresIn = message.getExpiresInSeconds() * 1000;
        }

        IncomingTextMessage textMessage = new IncomingTextMessage(Address.from(accountContext, envelope.getSource()),
                envelope.getSourceDevice(),
                message.getTimestamp(), body,
                message.getGroupInfo(),
                expiresIn);

        textMessage = new IncomingEncryptedMessage(textMessage, body);

        chatRepo.insertIncomingTextMessage(textMessage);
    }


    private long handleSynchronizeSentTextMessage(@NonNull MasterSecretUnion masterSecret,
                                                  @NonNull SentTranscriptMessage message) {

        ALog.i(TAG, "handleSynchronizeSentTextMessage");

        Recipient recipient = getSyncMessageDestination(message);
        String body = message.getMessage().getBody().or("");
        long expiresInMillis = message.getMessage().getExpiresInSeconds() * 1000;

        if (recipient.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
            handleSynchronizeSentExpirationUpdate(masterSecret, message);
        }

        long threadId = repository.getThreadRepo().getThreadIdFor(recipient);

        PrivateChatRepo chatRepo = repository.getChatRepo();
        long messageId;

        if (recipient.getAddress().isGroup()) {
            OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient, new SlideDeck(), body, message.getTimestamp(), -1, expiresInMillis, ThreadRepo.DistributionTypes.DEFAULT);
            outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

            messageId = chatRepo.insertOutgoingMediaMessage(threadId, masterSecret.getMasterSecret().get(), outgoingMediaMessage, null);
        } else {
            OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

            messageId = chatRepo.insertOutgoingTextMessage(threadId, outgoingTextMessage, message.getTimestamp(), null);
        }

        chatRepo.setMessageSendSuccess(messageId);

        if (expiresInMillis > 0) {
            chatRepo.setMessageExpiresStart(messageId);
            chatRepo.setMessageExpiresStart(messageId);

            IExpiringScheduler manager = ExpirationManager.INSTANCE.get(accountContext);
            manager.scheduleDeletion(messageId, false, message.getExpirationStartTimestamp(), expiresInMillis);
        }

        return threadId;
    }

    private void handleInvalidVersionMessage(@NonNull SignalServiceProtos.Envelope envelope) {

        ALog.i(TAG, "handleInvalidVersionMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();

        kotlin.Pair<Long, Long> insertResult = insertPlaceholder(envelope);

        if (insertResult.getSecond() > 0) {
            chatRepo.setMessageInvalidVersionKeyExchange(insertResult.getSecond());
        }
    }

    private void handleCorruptMessage(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceProtos.Envelope envelope) {
        ALog.i(TAG, "handleCorruptMessage");

        ThreadRepo threadRepo = repository.getThreadRepo();
        String dataJson = threadRepo.getDecryptFailData(envelope.getSource());
        DecryptFailData data;
        if (dataJson != null && !dataJson.isEmpty()) {
            data = GsonUtils.INSTANCE.fromJson(dataJson, DecryptFailData.class);
        } else {
            data = new DecryptFailData();
        }
        if (data.getLastDeleteSessionTime() + 50000 < System.currentTimeMillis()) {
            ALog.i(TAG, "Last delete session time is 5s ago. Delete Session.");
            SignalProtocolStore store = new SignalProtocolStoreImpl(context, accountContext);
            SignalProtocolAddress address = new SignalProtocolAddress(envelope.getSource(), SignalServiceAddress.DEFAULT_DEVICE_ID);
            if (store.containsSession(address)) {
                store.deleteSession(address);
            }
            try {
                Optional<String> relay = Optional.absent();
                if (envelope.hasRelay()) {
                    relay = Optional.of(envelope.getRelay());
                }
                List<PreKeyBundle> preKeyBundles = ChatHttp.INSTANCE.get(accountContext).getPreKeys(new SignalServiceAddress(envelope.getSource(), relay), SignalServiceAddress.DEFAULT_DEVICE_ID);
                for (PreKeyBundle preKey : preKeyBundles) {

                    String identityKeyString = new String(EncryptUtils.base64Encode(preKey.getIdentityKey().serialize()));
                    if (!AddressUtil.INSTANCE.isValid(envelope.getSource(), identityKeyString)) {
                        ALog.e(TAG, "getPreKeys error identity key got");
                        continue;
                    }

                    SessionBuilder sessionBuilder = new SessionBuilder(store, new SignalProtocolAddress(envelope.getSource(), SignalServiceAddress.DEFAULT_DEVICE_ID));
                    sessionBuilder.process(preKey);

                }
            } catch (Throwable ex) {
                ALog.w(TAG, "Untrusted identity key from handleMismatchedDevices");
            }
            data.setLastDeleteSessionTime(System.currentTimeMillis());
            threadRepo.setDecryptFailData(envelope.getSource(), data.toJson());
        }

        //If the decryption fails, do not insert the library, and send a new receipt directly
        long messageId = envelope.getTimestamp();
        Recipient recipient = Recipient.from(accountContext, envelope.getSource(), false);
        String message = new AmeGroupMessage<>(AmeGroupMessage.RECEIPT, new AmeGroupMessage.ReceiptContent(messageId)).toString();
        OutgoingLocationMessage outgoingLocationMessage = new OutgoingLocationMessage(recipient, message, 0);
        if (masterSecret.getMasterSecret().isPresent()) {
            MessageSender.sendHideMessage(AppContextHolder.APP_CONTEXT, accountContext, outgoingLocationMessage);
        }
    }

    private void handleLegacyMessage(@NonNull SignalServiceProtos.Envelope envelope) {

        ALog.i(TAG, "handleLegacyMessage");

        PrivateChatRepo chatRepo = repository.getChatRepo();

        kotlin.Pair<Long, Long> insertResult = insertPlaceholder(envelope);

        if (insertResult.getSecond() > 0) {
            chatRepo.setMessageLegacyVersion(insertResult.getSecond());
        }
    }

    private void handleUntrustedIdentityMessage(@NonNull SignalServiceProtos.Envelope envelope) {
        ALog.i(TAG, "handleUntrustedIdentityMessage");
        try {
            PrivateChatRepo chatRepo = repository.getChatRepo();
            Address sourceAddress = Address.from(accountContext, envelope.getSource());
            byte[] serialized = envelope.hasLegacyMessage() ? envelope.getLegacyMessage().toByteArray() : envelope.getContent().toByteArray();
            PreKeySignalMessage whisperMessage = new PreKeySignalMessage(serialized);
            String encoded = Base64.encodeBytes(serialized);

            IncomingTextMessage textMessage = new IncomingTextMessage(sourceAddress,
                    envelope.getSourceDevice(),
                    envelope.getTimestamp(), encoded,
                    Optional.absent(), 0);

            IncomingPreKeyBundleMessage bundleMessage = new IncomingPreKeyBundleMessage(textMessage, encoded, envelope.hasLegacyMessage());

            chatRepo.insertIncomingTextMessage(bundleMessage);
        } catch (InvalidMessageException | InvalidVersionException e) {
            throw new AssertionError(e);
        }
    }

    private void handleProfileKey(@NonNull SignalServiceProtos.Envelope envelope,
                                  @NonNull SignalServiceDataMessage message) {
        ALog.i(TAG, "handleProfileKey");

        RecipientRepo recipientRepo = Repository.getRecipientRepo(accountContext);
        Recipient recipient = Recipient.from(accountContext, envelope.getSource(), false);

        if (recipient.getProfileKey() == null || !MessageDigest.isEqual(recipient.getProfileKey(), message.getProfileKey().get())) {
            if (recipientRepo != null) {
                recipientRepo.setProfileKey(recipient, message.getProfileKey().get());
            }

            JobManager manager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
            if (manager != null) {
                manager.add(new RetrieveProfileJob(context, accountContext, recipient));
            }
        }
    }

    private void handleDeliveryReceipt(@NonNull SignalServiceProtos.Envelope envelope,
                                       @NonNull SignalServiceReceiptMessage message) {
        ALog.i(TAG, "handleDeliveryReceipt");

        for (long timestamp : message.getTimestamps()) {
            Log.w(TAG, String.format("Received encrypted delivery receipt: (XXXXX, %d)", timestamp));
            repository.getChatRepo().incrementDeliveryReceiptCount(envelope.getSource(), timestamp);
        }
    }

    private void handleReadReceipt(@NonNull SignalServiceProtos.Envelope envelope,
                                   @NonNull SignalServiceReceiptMessage message) {
        ALog.i(TAG, "handleReadReceipt");

        if (TextSecurePreferences.isReadReceiptsEnabled(accountContext)) {
            for (long timestamp : message.getTimestamps()) {
                Log.w(TAG, String.format("Received encrypted read receipt: (XXXXX, %d)", timestamp));

                repository.getChatRepo().incrementReadReceiptCount(envelope.getSource(), timestamp);
            }
        }
    }

    private kotlin.Pair<Long, Long> insertPlaceholder(@NonNull SignalServiceProtos.Envelope envelope) {
        PrivateChatRepo chatRepo = repository.getChatRepo();
        IncomingTextMessage textMessage = new IncomingTextMessage(Address.from(accountContext, envelope.getSource()),
                envelope.getSourceDevice(),
                envelope.getTimestamp(), "",
                Optional.absent(), 0);

        return chatRepo.insertIncomingTextMessage(textMessage);
    }

    private @NonNull Recipient getSyncMessageDestination(SentTranscriptMessage message) {
        if (message.getMessage().getGroupInfo().isPresent()) {
            return Recipient.from(Address.from(accountContext, GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);
        } else {
            return Recipient.from(Address.from(accountContext, message.getDestination().get()), false);
        }
    }

    private @NonNull Recipient getMessageDestination(SignalServiceProtos.Envelope envelope, SignalServiceDataMessage message) {
        if (message.getGroupInfo().isPresent()) {
            return Recipient.from(Address.from(accountContext, GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false)), false);
        } else {
            return Recipient.from(Address.from(accountContext, envelope.getSource()), false);
        }
    }
}
