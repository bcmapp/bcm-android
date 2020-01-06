package com.bcm.messenger.chats.privatechat.logic;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.bcm.messenger.chats.privatechat.jobs.SendReadReceiptJob;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.MessagingDatabase.ExpirationInfo;
import com.bcm.messenger.common.database.MessagingDatabase.MarkedMessageInfo;
import com.bcm.messenger.common.database.MessagingDatabase.SyncMessageId;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.expiration.ExpirationManager;
import com.bcm.messenger.common.expiration.IExpiringScheduler;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.login.jobs.MultiDeviceReadUpdateJob;
import com.bcm.messenger.utility.dispatcher.AmeDispatcher;
import com.bcm.messenger.utility.logger.ALog;

import org.whispersystems.jobqueue.JobManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import kotlin.Unit;

public class MarkReadReceiver extends MasterSecretBroadcastReceiver {

    private static final String TAG = MarkReadReceiver.class.getSimpleName();
    public static final String CLEAR_ACTION = "com.bcm.messenger.common.notifications.CLEAR";
    public static final String THREAD_IDS_EXTRA = "thread_ids";
    public static final String NOTIFICATION_ID_EXTRA = "notification_id";

    @Override
    protected void onReceive(final Context context, Intent intent, @Nullable final MasterSecret masterSecret) {
        if (!CLEAR_ACTION.equals(intent.getAction()))
            return;

        final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

        if (threadIds != null) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));
            AmeDispatcher.INSTANCE.getIo().dispatch(() -> {
                List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();
                for (long threadId : threadIds) {
                    ALog.d(TAG, "Marking as read: " + threadId);
                    List<MarkedMessageInfo> messageIds = Repository.getThreadRepo(AMELogin.INSTANCE.getMajorContext()).setRead(threadId, true);
                    messageIdsCollection.addAll(messageIds);
                }
                process(context, AMELogin.INSTANCE.getMajorContext(), messageIdsCollection);
                return Unit.INSTANCE;
            });
        }
    }

    public static void process(@NonNull Context context,
                               @NonNull AccountContext accountContext,
                               @NonNull List<MarkedMessageInfo> markedReadMessages) {
        if (markedReadMessages.isEmpty()) return;

        List<SyncMessageId> syncMessageIds = new LinkedList<>();

        for (MarkedMessageInfo messageInfo : markedReadMessages) {
            scheduleDeletion(accountContext, messageInfo.getExpirationInfo());
            syncMessageIds.add(messageInfo.getSyncMessageId());
        }

        JobManager mgr = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
        if (null != mgr) {
            mgr.add(new MultiDeviceReadUpdateJob(context, syncMessageIds));
        }


        Map<Address, List<SyncMessageId>> addressMap = Stream.of(markedReadMessages)
                .map(MarkedMessageInfo::getSyncMessageId)
                .collect(Collectors.groupingBy(SyncMessageId::getAddress));

        for (Address address : addressMap.keySet()) {
            List<Long> timestamps = Stream.of(addressMap.get(address)).map(SyncMessageId::getTimetamp).toList();
            if (null != mgr) {
                mgr.add(new SendReadReceiptJob(context, accountContext, address, timestamps));
            }
        }
    }

    private static void scheduleDeletion(AccountContext accountContext, ExpirationInfo expirationInfo) {
        if (expirationInfo.getExpiresIn() > 0 && expirationInfo.getExpireStarted() <= 0) {
            IExpiringScheduler expirationManager = ExpirationManager.INSTANCE.scheduler(accountContext);

            PrivateChatRepo chatRepo = Repository.getChatRepo(accountContext);
            if (chatRepo == null) {
                return;
            }
            chatRepo.setMessageExpiresStart(expirationInfo.getId());

            expirationManager.scheduleDeletion(expirationInfo.getId(), expirationInfo.isMms(), expirationInfo.getExpiresIn());
        }
    }
}
