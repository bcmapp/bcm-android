package com.bcm.messenger.common.recipients;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.database.records.RecipientSettings;
import com.bcm.messenger.common.database.repositories.RecipientRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.utility.dispatcher.AmeDispatcher;
import com.bcm.messenger.utility.AppContextHolder;

import com.bcm.messenger.utility.concurrent.ListenableFutureTask;
import com.bcm.messenger.utility.Util;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import com.bcm.messenger.utility.logger.ALog;

/**
 * 联系人提供器
 */
class RecipientProvider {

    private static final String TAG = RecipientProvider.class.getSimpleName();

    private static RecipientCache reserveCache = null;//后备缓存（所有，包括群组里的陌生人, 目前考虑全部联系人都用这种缓存)

    private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

    public RecipientProvider() {
        reserveCache = new RecipientCache(new WeakHashMap<>(5000));
    }

    /**
     * 清理所有缓存
     */
    void clearCache() {
        reserveCache.reset();
    }


    /**
     * 从缓存读取联系人信息
     * @param address
     * @return
     */
    @Nullable Recipient findCache(@NonNull Address address) {
        if (address.isGroup()) {
            return reserveCache.get(address);
        }else {
            return reserveCache.get(address);
        }
    }

    /**
     * 更新缓存（主要几种情况，自身，陌生人，以及本身通讯录的联系人）
     * @param address
     * @param recipient
     */
    void updateCache(@NonNull Address address, @Nullable Recipient recipient) {
        Recipient r = recipient;
        if (address.isCurrentLogin() && null == r) {
            r = Recipient.from(AppContextHolder.APP_CONTEXT, address, true);
        }
        reserveCache.set(address, r);
    }

    /**
     * 检查是否使用缓存
     * @param cachedRecipient
     * @param asynchronous
     * @return
     */
    private boolean useCache(Recipient cachedRecipient, boolean asynchronous) {

        if (cachedRecipient == null) {
            ALog.d(TAG, "useCache fail, cacheRecipient is null");
            return false;
        } else if (cachedRecipient.isStale()) {
            return false;
        } else if (!asynchronous && cachedRecipient.isResolving()) {
            return false;
        }
//        else if (hasGroup && hasSettings && (cachedRecipient.isResolving() || !cachedRecipient.isGroupRecipient() && !cachedRecipient.isSystemContact())) {
//            return false;
//        }
        return true;

    }

    @NonNull Recipient getRecipient(Context context, Address address, @Nullable RecipientDetails details, boolean asynchronous) {
        RecipientDetails newDetail = details == null ? new RecipientDetails(null, null, null, null) : details;

        Recipient current = findCache(address);
        if (!useCache(current, asynchronous)) {//如果不用缓存的数据，则重新生成recipient，并从数据库加载setting（同步或异步由asynchronous来定）

            ListenableFutureTask<RecipientDetails> futureTask = null;
            if (asynchronous) {
                futureTask = getRecipientDetailsAsync(context, address, newDetail);
                current = new Recipient(address, current, newDetail, futureTask);
            } else {
                fetchRecipientDetails(context, address, newDetail, false);
                current = new Recipient(address, current, newDetail, null);
            }
            updateCache(address, current);


        } else {
            final Recipient target = current;
            if(target != null) {
                if (asynchronous) {
                    AmeDispatcher.INSTANCE.getIo().dispatch(new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            target.updateRecipientDetails(details, true);
                            return null;
                        }
                    });
                }else {
                    target.updateRecipientDetails(details, true);
                }
            }
        }

        return current;
    }

    private @NonNull
    ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context, final @NonNull Address address, final @NonNull RecipientDetails detail) {

        ListenableFutureTask<RecipientDetails> future =
                new ListenableFutureTask<>(() -> {
                    fetchRecipientDetails(context, address, detail, true);
                    return detail;
                });
        asyncRecipientResolver.submit(future);
        return future;
    }

    private void fetchRecipientDetails(Context context, @NonNull Address address, @NonNull RecipientDetails details, boolean nestedAsynchronous) {
        if (address.isGroup()) {
            fetchGroupRecipientDetails(context, address, details, nestedAsynchronous, true);
        } else {
            fetchIndividualRecipientDetails(context, address, details, true);
        }
    }

    private void fetchIndividualRecipientDetails(Context context, @NonNull Address address, @NonNull RecipientDetails details, boolean force) {
        RecipientRepo recipientRepo = Repository.getRecipientRepo();
        if (details.getSettings() == null && force && recipientRepo != null) {
            details.setSettings(recipientRepo.getRecipient(address.serialize()));
        }

    }

    private void fetchGroupRecipientDetails(Context context, Address groupId, @NonNull RecipientDetails details, boolean asynchronous, boolean force) {

        if ((details.participants == null || details.participants.isEmpty()) && force) {
//            Optional<GroupRecord> groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId.toGroupString());
            List<Recipient> members = null;
            String groupTitle = null;
//            if (groupRecord.isPresent()) {
//                groupTitle = groupRecord.get().getTitle();
//                List<Address> memberAddresses = groupRecord.get().getMembers();
//                members = new LinkedList<>();
//                for (Address memberAddress : memberAddresses) {
//                    members.add(getRecipient(context, memberAddress, details, asynchronous));
//                }
//                details.setCustomName(groupTitle);
//                details.setParticipants(members);
//            }
        }
        if (details.settings == null && force) {
            details.setSettings(Repository.getRecipientRepo().getRecipient(groupId.serialize()));
        }

    }

    /**
     * 联系人详情信息
     */
    public static class RecipientDetails {

        @Nullable
        String customName;//针对群组是群组名称，针对个人是手机通讯录名称
        @Nullable
        String customAvatar;//针对群组是群组头像，针对个人是手机通讯录名称
        @Nullable
        List<Recipient> participants;
        @Nullable
        RecipientSettings settings;

        RecipientDetails(@Nullable String name, @Nullable String avatar,
                         @Nullable RecipientSettings settings,
                         @Nullable List<Recipient> participants) {
            this.customName = name;
            this.customAvatar = avatar;
            this.participants = participants;
            this.settings = settings;

        }

        @Nullable
        public String getCustomName() {
            return customName;
        }

        public void setCustomName(@Nullable String customName) {
            this.customName = customName;
        }

        @Nullable
        public String getCustomAvatar() {
            return customAvatar;
        }

        public void setCustomAvatar(@Nullable String customAvatar) {
            this.customAvatar = customAvatar;
        }

        @Nullable
        public List<Recipient> getParticipants() {
            return participants;
        }

        public void setParticipants(@Nullable List<Recipient> participants) {
            this.participants = participants;
        }

        @Nullable
        public RecipientSettings getSettings() {
            return settings;
        }

        public void setSettings(@Nullable RecipientSettings settings) {
            this.settings = settings;
        }
    }

    /**
     * 联系人缓存信息
     */
    private static class RecipientCache {
        
        private Map<Address, Recipient> cache;

        RecipientCache(Map<Address, Recipient> cache) {
            this.cache = cache;
        }

        public synchronized @Nullable
        Recipient get(Address address) {
            return cache.get(address);
        }

        public synchronized void set(Address address, @Nullable Recipient recipient) {
            if (recipient != null) {
                cache.put(address, recipient);
            } else {

                Recipient temp = cache.get(address);
                if (temp != null) {
                    temp.setStale();
                }
                cache.remove(address);

            }
        }

        public synchronized void reset() {
            for (Recipient recipient : cache.values()) {
                recipient.setStale();
            }
            cache.clear();
        }

        synchronized void remove(Address address) {
            this.cache.remove(address);
        }

    }

}