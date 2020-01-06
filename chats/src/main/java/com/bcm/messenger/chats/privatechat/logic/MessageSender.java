/**
 * Copyright (C) 2011 Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.chats.privatechat.logic;

import android.content.Context;

import com.bcm.messenger.chats.privatechat.jobs.PushControlMessageSendJob;
import com.bcm.messenger.chats.privatechat.jobs.PushHideMessageSendJob;
import com.bcm.messenger.chats.privatechat.jobs.PushMediaSendJob;
import com.bcm.messenger.chats.privatechat.jobs.PushTextSendJob;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.expiration.IExpiringScheduler;
import com.bcm.messenger.common.grouprepository.room.dao.ChatHideMessageDao;
import com.bcm.messenger.common.grouprepository.room.entity.ChatHideMessage;
import com.bcm.messenger.common.mms.OutgoingMediaMessage;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.OutgoingLocationMessage;
import com.bcm.messenger.common.sms.OutgoingTextMessage;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.logger.ALog;

import org.whispersystems.jobqueue.JobManager;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MessageSender {

    private static final String TAG = MessageSender.class.getSimpleName();

    /**
     * 
     * @param context Context.
     * @param message Message to sent.
     * @param threadId Current thread id.
     * @param insertListener Listener to be called after inserting to database.
     * @return Thread id if current thread id is larger than 0 or allocated a new one.
     */
    public static long send(final Context context,
                            final OutgoingTextMessage message,
                            final long threadId,
                            final Function1<Long, Unit> insertListener) {
        PrivateChatRepo chatRepo = Repository.getChatRepo();
        Recipient recipient = message.getRecipient();

        long allocatedThreadId;

        if (threadId <= 0) {
            allocatedThreadId = Repository.getThreadRepo().getThreadIdFor(recipient);
        } else {
            allocatedThreadId = threadId;
        }

        //
        long messageId = chatRepo.insertOutgoingTextMessage(allocatedThreadId,
                message, AmeTimeUtil.INSTANCE.getMessageSendTime(), insertListener);

        // 
        if (!recipient.isFriend() && !recipient.isAllowStranger()) {
            chatRepo.setMessageSendFail(messageId);
        }else {
            sendTextMessage(context, recipient, messageId, message.getExpiresIn(), false);
        }

        return allocatedThreadId;
    }

    // 
    public static long sendSilently(final Context context,
                                    final OutgoingTextMessage message,
                                    final long threadId,
                                    final Function1<Long, Unit> insertListener) {
        PrivateChatRepo chatRepo = Repository.getChatRepo();
        Recipient recipient = message.getRecipient();

        long allocatedThreadId;

        if (threadId <= 0) {
            allocatedThreadId = Repository.getThreadRepo().getThreadIdFor(recipient);
        } else {
            allocatedThreadId = threadId;
        }

        //
        long messageId = chatRepo.insertOutgoingTextMessage(allocatedThreadId,
                message, AmeTimeUtil.INSTANCE.getMessageSendTime(), insertListener);

        sendTextMessage(context, recipient, messageId, message.getExpiresIn(), true);

        return allocatedThreadId;
    }

    /**
     * 
     * @param context Context
     * @param message 
     */
    public static void sendHideMessage(final Context context, final OutgoingLocationMessage message) {
        ALog.i(TAG, "Send hide message");

        if (isSelfSend(context, message.getRecipient())) {
            // Do nothing if message send to major
            return;
        }

        ChatHideMessageDao dao = UserDatabase.Companion.getDatabase().chatControlMessageDao();
        ChatHideMessage controlMessage = new ChatHideMessage();
        controlMessage.setContent(message.getMessageBody());
        controlMessage.setDestinationAddress(message.getRecipient().getAddress().toString());
        controlMessage.setSendTime(AmeTimeUtil.INSTANCE.getMessageSendTime());
        long messageId = dao.saveHideMessage(controlMessage);

        sendHideMessagePush(context, message.getRecipient(), messageId);
    }

    /**
     * send message
     * @param context Context.
     * @param masterSecret Current user's master secret to encrypt attachments.
     * @param message Message to send.
     * @param threadId Current thread id.
     * @param insertListener Listener to be called after inserting to database.
     * @return Thread id if current thread id is larger than 0 or allocated a new one.
     */
    public static long send(final Context context,
                            final MasterSecret masterSecret,
                            final OutgoingMediaMessage message,
                            final long threadId,
                            final Function1<Long, Unit> insertListener) {
        try {
            PrivateChatRepo chatRepo = Repository.getChatRepo();

            long allocatedThreadId;

            if (threadId <= 0) {
                allocatedThreadId = Repository.getThreadRepo().getThreadIdFor(message.getRecipient());
            } else {
                allocatedThreadId = threadId;
            }

            Recipient recipient = message.getRecipient();
            long messageId = chatRepo.insertOutgoingMediaMessage(allocatedThreadId, masterSecret, message, insertListener);

            
            if (!recipient.isFriend() && !recipient.isAllowStranger()) {
                chatRepo.setMessageSendFail(messageId);
            }else {
                sendMediaMessage(context, recipient, messageId, message.getExpiresIn());
            }

            return allocatedThreadId;
        } catch (Exception e) {
            ALog.e(TAG, "send MediaMessage error", e);
            return threadId;
        }
    }

    /**
     * resend message
     * @param context Context.
     * @param masterSecret Current user's master secret to encrypt or decrypt.
     * @param messageRecord Message to resend.
     */
    public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
        try {
            long messageId = messageRecord.getId();
            long expiresIn = messageRecord.getExpiresTime();
            Recipient recipient = messageRecord.getRecipient();

            // 
            if (recipient.isFriend() || recipient.isAllowStranger()) {
                Repository.getChatRepo().updateDateSentForResending(messageRecord.getId(), AmeTimeUtil.INSTANCE.getMessageSendTime());
                if (messageRecord.isMediaMessage()) {
                    sendMediaMessage(context, recipient, messageId, expiresIn);
                } else {
                    sendTextMessage(context, recipient, messageId, expiresIn, false);
                }
            }

        } catch (Exception e) {
            ALog.e(TAG, "resend message error", e);
        }
    }

    /**
     * Real send media messages.
     * @param context Context.
     * @param recipient The User who message will be sent to.
     * @param messageId Message id.
     * @param expiresIn Expires time.
     */
    private static void sendMediaMessage(Context context, Recipient recipient, long messageId, long expiresIn) {
        if (isSelfSend(context, recipient)) {
            sendMediaSelf(messageId, expiresIn);
        } else if (isGroupPushSend(recipient)) {
//            sendGroupPush(context, recipient, messageId, null);
        } else {
            sendMediaPush(context, recipient, messageId);
        }
    }

    /**
     * Real send text messages.
     * @param context Context.
     * @param recipient The User who message will be sent to.
     * @param messageId Message id.
     * @param expiresIn Expires time.
     * @param isSilent True means the message does not need to push by FCM/APNS
     */
    private static void sendTextMessage(Context context, Recipient recipient,
                                        long messageId, long expiresIn, boolean isSilent) {
        if (isSelfSend(context, recipient)) {
            sendTextSelf(messageId, expiresIn);
        } else {
            sendTextPush(context, recipient, messageId, isSilent);
        }
    }


    private static void sendTextSelf(long messageId, long expiresIn) {
        PrivateChatRepo chatRepo = Repository.getChatRepo();

        chatRepo.setMessageSendSuccess(messageId);

        if (expiresIn > 0) {
            IExpiringScheduler expiringScheduler = ExpirationManager.INSTANCE.scheduler();

            chatRepo.setMessageExpiresStart(messageId);
            expiringScheduler.scheduleDeletion(messageId, false, expiresIn);
        }
    }

    private static void sendMediaSelf(long messageId, long expiresIn) {
        PrivateChatRepo chatRepo = Repository.getChatRepo();

        chatRepo.setMessageSendSuccess(messageId);
        try {
            MessageRecord record = chatRepo.getMessage(messageId);
            if (record != null) {
                Repository.getAttachmentRepo().setAttachmentUploaded(record.getAttachments());
            }
        } catch (Exception e) {
            // Do nothing
        }

        if (expiresIn > 0) {
            IExpiringScheduler expiringScheduler = ExpirationManager.INSTANCE.scheduler();

            chatRepo.setMessageExpiresStart(messageId);
            expiringScheduler.scheduleDeletion(messageId, true, expiresIn);
        }
    }

    private static void sendTextPush(Context context, Recipient recipient, long messageId, boolean isSilent) {
        JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr();
        if (jobManager == null) {
            return;
        }
        if (isSilent) {
            jobManager.add(new PushTextSendJob(context, messageId, recipient.getAddress(), true));
        } else  {
            jobManager.add(new PushTextSendJob(context, messageId, recipient.getAddress()));
        }
    }

    private static void sendHideMessagePush(Context context, Recipient recipient, long messageId) {
        JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr();
        if (jobManager != null) {
            jobManager.add(new PushHideMessageSendJob(context, messageId, recipient.getAddress()));
        }
    }

    private static void sendMediaPush(Context context, Recipient recipient, long messageId) {
        JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr();
        if (jobManager != null) {
            jobManager.add(new PushMediaSendJob(context, messageId, recipient.getAddress()));
        }
    }

    private static boolean isGroupPushSend(Recipient recipient) {
        return recipient.getAddress().isGroup() &&
                !recipient.getAddress().isMmsGroup();
    }

    private static boolean isSelfSend(Context context, Recipient recipient) {
        if (!AMELogin.INSTANCE.isPushRegistered()) {
            return false;
        }

        if (recipient.isGroupRecipient()) {
            return false;
        }

        return recipient.isLogin();
    }

    public static void recall(MessageRecord messageRecord, MasterSecret masterSecret, boolean isMMS) throws Exception {
        if (messageRecord == null) {
            return;
        }

        if (messageRecord.getRecipient().getAddress().toString().equals(AMELogin.INSTANCE.getUid())) {
            // Self message cannot recall
            return;
        }

        JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr();
        if (jobManager != null) {
            jobManager.add(new PushControlMessageSendJob(AppContextHolder.APP_CONTEXT, messageRecord.getId(), isMMS, messageRecord.getRecipient().getAddress()));
        }
    }

}
