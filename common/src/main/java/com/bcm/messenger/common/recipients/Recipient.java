package com.bcm.messenger.common.recipients;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.color.MaterialColor;
import com.bcm.messenger.common.config.BcmFeatureSupport;
import com.bcm.messenger.common.contacts.avatars.ContactColors;
import com.bcm.messenger.common.contacts.avatars.ContactPhoto;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.BcmHttpApiHelper;
import com.bcm.messenger.common.core.corebean.AmeGroupInfo;
import com.bcm.messenger.common.database.GroupDatabase;
import com.bcm.messenger.common.database.RecipientDatabase.RegisteredState;
import com.bcm.messenger.common.database.RecipientDatabase.VibrateState;
import com.bcm.messenger.common.database.records.PrivacyProfile;
import com.bcm.messenger.common.database.records.RecipientSettings;
import com.bcm.messenger.common.database.repositories.RecipientRepo;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.provider.IContactModule;
import com.bcm.messenger.common.provider.IGroupModule;
import com.bcm.messenger.common.recipients.RecipientProvider.RecipientDetails;
import com.bcm.messenger.common.ui.IndividualAvatarView;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Conversions;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.bcm.messenger.utility.Util;
import com.bcm.messenger.utility.concurrent.FutureTaskListener;
import com.bcm.messenger.utility.concurrent.ListenableFutureTask;
import com.bcm.messenger.utility.dispatcher.AmeDispatcher;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.bcm.route.api.BcmRouter;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * AME系统联系人类，管理所有联系人相关信息
 *
 * @author wjh
 */
public class Recipient implements RecipientModifiedListener, NotGuard {

    public static class RecipientQR implements NotGuard {

        public static final int QR_VERSION = 3;

        @NonNull
        public final String uid;
        @Nullable
        @SerializedName("name")
        public String name = null;
        @Nullable
        public String phone = null;

        public int version = QR_VERSION;

        public RecipientQR(@NonNull String uid) {
            this.uid = uid;
        }

        @Override
        public String toString() {
            try{
                JSONObject qrJson = new JSONObject();
                qrJson.put("uid", uid);
                qrJson.put("name", name);
                qrJson.put("version", version);
                return qrJson.toString();

            }catch (Exception ex) {
                ex.printStackTrace();
            }
            return "";
        }

        public String toSchemeUri() {
            try {
                return "bcm://scheme.bcm-im.com/addfriend/new_chat_page?uid=" + uid + "&name=" + URLEncoder.encode(name, "UTF-8");
            }catch (Exception ex) {
                ex.printStackTrace();
            }
            return "";
        }

        @Nullable
        public static RecipientQR fromJson(String jsonString) {
            try {
                JSONObject json = new JSONObject(jsonString);
                RecipientQR qr = new RecipientQR(json.optString("uid"));
                qr.version = json.optInt("version", 1);
                String name = json.optString("profileName");
                if (TextUtils.isEmpty(name)) {
                    name = json.optString("name");
                }
                qr.name = name;
                return qr;

            }catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }
    }

    private static final String TAG = "Recipient";

    public static final String UNKNOWN_LETTER = "#";

    public static final String[] LETTERS = new String[]{
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };

    /**
     * 获取默认的联系人排序器
     * @return
     */
    public static Comparator<Recipient> getRecipientComparator() {
        return new Comparator<Recipient>() {
            private Map<Address, Integer> map = new HashMap<>();

            @Override
            public int compare(Recipient o1, Recipient o2) {
                Integer one = this.map.get(o1.address);
                if (one == null) {
                    one = o1.getCharacterLetterIndex();
                    this.map.put(o1.address, one);
                }
                Integer two = this.map.get(o2.address);
                if (two == null) {
                    two = o2.getCharacterLetterIndex();
                    this.map.put(o2.address, two);
                }
                return one.compareTo(two);
            }
        };
    }

    /**
     * 判断是否匹配keyword（用于搜索匹配）
     * @param keyword
     * @param recipient
     * @return
     */
    public static boolean match(String keyword, Recipient recipient) {

        ALog.i(TAG, "match keyword: " + keyword + "， name: " + recipient.getName());
        return StringAppearanceUtil.INSTANCE.containIgnore(recipient.getName(), keyword);

    }

    /**
     * 缓存删除触发的action
     */
    public static final String RECIPIENT_CLEAR_ACTION = "com.bcm.messenger.common.database.RecipientFactory.CLEAR";
    /**
     * 联系人profile更新触发的action
     */
    public static final String RECIPIENT_PROFILE_UPDATE = "com.bcm.messenger.common.database.RecipientFactory.PROFILE";

    private static final RecipientProvider PROVIDER = new RecipientProvider();


    /**
     * 将跟人信息转成json信息
     */
    public static String toQRCode(Recipient recipient) {
        try {
            /**
             * 版本1：个人二维码是传phone，之前传的版本号有问题，是传的应用版本号
             * 版本2：个人二维码是传uid，现在版本号统一是独立的
             */
            if (!recipient.isGroupRecipient()) {
                RecipientQR qr = new RecipientQR(recipient.address.serialize());
                qr.name = recipient.getName();
                return qr.toString();
            }

        } catch (Exception ex) {
            ALog.e(TAG, "toQRCode fail", ex);
        }
        return "";
    }

    /**
     * 转成配置对象
     */
    @Nullable
    public static RecipientSettings fromJson(@Nullable String jsonString) {
        try {
            if (jsonString != null) {
                RecipientQR recipientQR = RecipientQR.fromJson(jsonString);
                if (recipientQR != null) {
                    RecipientSettings settings = new RecipientSettings();
                    settings.setUid(recipientQR.uid);
                    settings.setProfileName(recipientQR.name);
                    return settings;
                }
            }
        } catch (Exception ex) {
            ALog.e(TAG, "fromJson fail", ex);
        }
        return null;
    }

    /**
     * 获取本系统登录联系人信息
     *
     * @param context
     * @param asynchronous
     * @return
     */
    @NonNull
    public static Recipient fromSelf(@NonNull Context context, boolean asynchronous) throws Exception {
        String localNumber = AMESelfData.INSTANCE.getUid();
        ALog.d(TAG, "localNumber:" + localNumber);
        if (TextUtils.isEmpty(localNumber)) {
            throw new Exception(("self number is null"));
        }
        return PROVIDER.getRecipient(context, Address.fromSerialized(localNumber), null, asynchronous);
    }

    @NonNull
    public static Recipient self() throws Exception {
        String localNumber = AMESelfData.INSTANCE.getUid();
        ALog.d(TAG, "localNumber:" + localNumber);
        if (TextUtils.isEmpty(localNumber)) {
            throw new Exception(("self number is null 1"));
        }
        return PROVIDER.getRecipient(AppContextHolder.APP_CONTEXT, Address.fromSerialized(localNumber), null, true);
    }

    /**
     * 根据address号码查询联系人信息（可能从缓存直接读取）
     *
     * @param context
     * @param address
     * @param asynchronous false表示直接从数据库加载，true表示启动后台线程加载，后续会通过回调通知
     * @return
     */
    @NonNull
    public static Recipient from(@NonNull Context context, @NonNull Address address, boolean asynchronous) {
        return PROVIDER.getRecipient(context, address, null, asynchronous);
    }

    /**
     * 根据address号码和其他配置来查询联系人信息（可能从缓存直接读取）
     * @param context
     * @param address
     * @param settings
     * @param groupRecord
     * @param asynchronous
     * @return
     */
    @Deprecated
    @NonNull
    public static Recipient from(@NonNull Context context, @NonNull Address address, @NonNull Optional<RecipientSettings> settings, @NonNull Optional<GroupDatabase.GroupRecord> groupRecord, boolean asynchronous) {
        RecipientDetails details = new RecipientDetails(null, null, null, null);
        return PROVIDER.getRecipient(context, address, details, asynchronous);
    }

    /**
     * 读取当前的联系人信息（可以从缓存），并设置最新的配置信息
     *
     * @param context
     * @param address
     * @param settings
     * @return
     */
    @NonNull
    public static Recipient fromSnapshot(@NonNull Context context, @NonNull Address address, @NonNull RecipientSettings settings) {
        RecipientDetails details = new RecipientDetails(null, null, settings, null);
        return PROVIDER.getRecipient(context, address, details, false);
    }

    /**
     * 清理所有联系人缓存
     * @param context
     */
    public static void clearCache(Context context) {
        PROVIDER.clearCache();
        context.sendBroadcast(new Intent(RECIPIENT_CLEAR_ACTION));
    }

    /**
     * 清理自身的缓存
     * @param context
     * @param address
     */
    public static void clearCache(Context context, Address address) {
        PROVIDER.updateCache(address, null);
    }

    /**
     * 新groupId 转换成 recipient
     *
     * @param context
     * @param groupId
     * @return
     */
    @NonNull
    public static Recipient recipientFromNewGroupId(Context context, long groupId) {
        return Recipient.from(context, GroupUtil.addressFromGid(groupId), false);
    }

    /**
     * 新群id转成recipient（profile信息异步同步）
     * @param context
     * @param groupId
     * @return
     */
    @NonNull
    public static Recipient recipientFromNewGroupIdAsync(Context context, long groupId) {
        return Recipient.from(context, GroupUtil.addressFromGid(groupId), true);
    }

    /**
     * 通过群info信息转换成recipient(由于提供了groupInfo，所以会出发onModify，请注意)
     * @param context
     * @param groupInfo
     * @return
     */
    @NonNull
    public static Recipient recipientFromNewGroup(Context context, @NonNull AmeGroupInfo groupInfo) {
        Address address = GroupUtil.addressFromGid(groupInfo.getGid());
        RecipientSettings settings = new RecipientSettings();
        settings.setUid(address.serialize());
        settings.setProfileName(groupInfo.getName());
        settings.setProfileAvatar(groupInfo.getIconUrl());
        if (groupInfo.getMute()) {
            settings.setMuteUntil(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10));
        }else {
            settings.setMuteUntil(0);
        }
        return Recipient.fromSnapshot(context, address, settings);
    }

    private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

    /**
     * 是否需要刷新资料
     */
    private boolean mNeedRefreshProfile = true;
    /**
     * 是否需要刷新profile信息
     */
    public synchronized boolean needRefreshProfile() {
        return mNeedRefreshProfile;
    }

    public synchronized void setNeedRefreshProfile(boolean need) {
        mNeedRefreshProfile = need;
    }

    @NonNull
    private final Address address;//唯一身份标识
    @NonNull
    private final List<Recipient> participants = new LinkedList<>();//关联的联系人（用于群聊）
    @Nullable
    private String groupTitle;
    @Nullable
    private String customLabel;
    private boolean stale;
    private boolean resolving;
    @Nullable
    private Uri ringtone = null;
    private long mutedUntil = 0;
    private boolean blocked = false;
    private VibrateState vibrate = VibrateState.DEFAULT;
    private int expireMessages = 0;
    private Optional<Integer> defaultSubscriptionId = Optional.absent();
    @NonNull
    private RegisteredState registered = RegisteredState.UNKNOWN;
    @Nullable
    private MaterialColor color;
    private boolean seenInviteReminder;
    @Nullable
    private String identityKey;
    @Nullable
    private byte[] profileKey;
    @Nullable
    private String profileName;
    @Nullable
    private String profileAvatar;
    private boolean profileSharing;
    @Nullable
    private String localName;//本地备注名
    @Nullable
    private String localAvatar;//本地备注头像

    @NonNull
    private PrivacyProfile privacyProfile = new PrivacyProfile();//隐私资料（昵称和头像等信息）
    @NonNull
    private RecipientRepo.Relationship mRelationship = RecipientRepo.Relationship.STRANGER;//与联系人的关系
    @Nullable
    private BcmFeatureSupport featureSupport;

    private boolean backgroundRequestAddFriendFlag = false;//后台请求加好友标识
    /**
     *  根据address生成的唯一ID，可以用于作为adapter的StableId
     */
    private long uniquenessId;

    private long groupId = -1L;//群ID，只有是群组类型的时候才有值，否则是-1L

    private IGroupModule mGroupProvider;

    public long getUniquenessId() {
        return uniquenessId;
    }

    public long getGroupId() {
        return groupId;
    }

    /**
     * 构造函数
     * @param address
     * @param stale
     * @param details
     * @param future
     */
    Recipient(@NonNull Address address,
              @Nullable Recipient stale,
              @Nullable RecipientDetails details,
              @Nullable ListenableFutureTask<RecipientDetails> future) {

        this.address = address;
        this.color = null;
        this.resolving = future != null;
        if (stale != null) {

            this.color = stale.color;
            this.customLabel = stale.customLabel;
            this.ringtone = stale.ringtone;
            this.mutedUntil = stale.mutedUntil;
            this.blocked = stale.blocked;
            this.vibrate = stale.vibrate;
            this.expireMessages = stale.expireMessages;
            this.seenInviteReminder = stale.seenInviteReminder;
            this.defaultSubscriptionId = stale.defaultSubscriptionId;
            this.registered = stale.registered;
            this.profileKey = stale.profileKey;
            this.profileName = stale.profileName;
            this.profileAvatar = stale.profileAvatar;
            this.profileSharing = stale.profileSharing;
            this.localName = stale.localName;
            this.localAvatar = stale.localAvatar;
            this.privacyProfile = stale.privacyProfile;
            this.mRelationship = stale.mRelationship;
            this.participants.clear();
            this.participants.addAll(stale.participants);
            this.listeners.addAll(stale.listeners);
            this.featureSupport = stale.featureSupport;

        }

        updateRecipientDetails(details, future);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            this.uniquenessId = Conversions.byteArrayToLong(digest.digest(address.serialize().getBytes()));
            if (isGroupRecipient()) {
                this.groupId = GroupUtil.gidFromAddress(address);
            }else {
                this.groupId = -1L;
            }
        }catch (Exception ex) {
            ALog.e(TAG, "recipient constructor error");
        }
    }

    /**
     * 设置后台任务
     * @param current 当前详情
     * @param future 异步处理回调
     */
    private void updateRecipientDetails(@Nullable RecipientDetails current, @Nullable ListenableFutureTask<RecipientDetails> future) {

        updateRecipientDetails(current, future == null);
        if (future != null) {
            future.addListener(new FutureTaskListener<RecipientDetails>() {
                @Override
                public void onSuccess(RecipientDetails result) {
                    ALog.d(TAG, "updateRecipientDetails address: " + Recipient.this.address);
                    updateRecipientDetails(result, true);
                    synchronized (Recipient.this) {
                        Recipient.this.resolving = false;
                        Recipient.this.notifyAll();
                    }

                    //对于FOLLOW关系的联系人，都判断下是否需要发送后台好友请求
                    IContactModule provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast();
                    provider.checkNeedRequestAddFriend(AppContextHolder.APP_CONTEXT, Recipient.this);

                }

                @Override
                public void onFailure(ExecutionException error) {
                    Log.w(TAG, error);
                    synchronized (Recipient.this) {
                        Recipient.this.resolving = false;
                        Recipient.this.notifyAll();
                    }
                }

            });

        }else {
            //对于FOLLOW关系的联系人，都判断下是否需要发送后台好友请求
            IContactModule provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast();
            provider.checkNeedRequestAddFriend(AppContextHolder.APP_CONTEXT, Recipient.this);

        }
    }

    /**
     * 更新详细信息
     * @param details 详情
     * @param notify 是否通知
     */
    void updateRecipientDetails(@Nullable RecipientDetails details, boolean notify) {

        if (details == null || (details.customName == null && details.customAvatar == null && details.participants == null && details.settings == null)) {
            return;
        }

        //有可能已经更新了一些信息，所以缓存也要更新下
        PROVIDER.updateCache(address, this);

        synchronized (Recipient.this) {

            boolean changed = false;
            if (isGroupRecipient()) {
                if (details.customName != null) {
                    Recipient.this.groupTitle = details.customName;
                    changed = true;
                }
                if (details.customAvatar != null) {
                    Recipient.this.customLabel = details.customAvatar;
                    changed = true;
                }
            }
            if (details.participants != null) {
                Recipient.this.participants.clear();
                Recipient.this.participants.addAll(details.participants);
                changed = true;
            }

            if (Recipient.this.fillSettings(details.settings)) {
                changed = true;
            }

            if (!listeners.isEmpty()) {
                for (Recipient recipient : Recipient.this.participants) {
                    recipient.addListener(Recipient.this);
                }
            }
            if (notify && changed) {
                notifyListeners();
            }
        }
    }

    /**
     * 添加回调
     * @param listener
     */
    public synchronized void addListener(RecipientModifiedListener listener) {
        if (listeners.isEmpty()) {
            for (Recipient recipient : participants) {
                recipient.addListener(this);
            }
        }
        listeners.add(listener);
    }

    /**
     * 删除回调
     *
     * @param listener
     */
    public synchronized void removeListener(RecipientModifiedListener listener) {
        ALog.logForSecret(TAG, "removeListener address: " + address.serialize());
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            for (Recipient recipient : participants) {
                recipient.removeListener(this);
            }
        }
    }

    /**
     * 填充联系人详细设置
     * @param settings 配置
     */
    private boolean fillSettings(@Nullable RecipientSettings settings) {
        if (settings != null) {
            this.mutedUntil = settings.getMuteUntil();
            this.blocked = settings.isBlocked();
            this.expireMessages = (int) settings.getExpiresTime();
            this.profileKey = settings.getProfileKeyByteArray();
            this.profileName = settings.getProfileName();
            this.profileAvatar = settings.getProfileAvatar();
            this.profileSharing = settings.isProfileSharing();
            this.localName = settings.getLocalName();
            this.localAvatar = settings.getLocalAvatar();
            this.privacyProfile = settings.getPrivacyProfile();
            this.identityKey = settings.getIdentityKey();
            this.mRelationship = RecipientRepo.Relationship.values()[settings.getRelationship()];
            this.featureSupport = settings.getFeatureSupport();

            //老版本的兼容，因为老版本只有单向好友的概念，存在friend字段，而新版本是通过relationship字段表示关系，所以这里要做迁移
            // FIXME: Remove or not
//            if (this.mRelationship == RecipientRepo.Relationship.STRANGER && settings.isFriendFlag()) {
//                this.mRelationship = RecipientRepo.Relationship.FOLLOW;
//            }
            return true;
        }
        return false;
    }

    /**
     * 判断此联系人是否自己
     * @return
     */
    public boolean isSelf() {
        return address.isCurrentLogin();
    }

    /**
     * 返回联系人对应的配置信息
     * @return
     */
    @NonNull
    public synchronized
    RecipientSettings getSettings() {
        return new RecipientSettings(this.getAddress().serialize(), this.blocked, this.mutedUntil,
                this.expireMessages, this.localName, this.localAvatar, this.profileKey, this.profileName, this.profileAvatar,
                this.profileSharing, this.privacyProfile, this.mRelationship.getType(), this.featureSupport);
    }

    @NonNull
    public Address getAddress() {
        return address;
    }

    public synchronized boolean isBackgroundRequestAddFriendFlag() {
        return backgroundRequestAddFriendFlag;
    }

    public synchronized void setBackgroundRequestAddFriendFlag(boolean backgroundRequestAddFriendFlag) {
        this.backgroundRequestAddFriendFlag = backgroundRequestAddFriendFlag;
    }

    @NonNull
    public synchronized PrivacyProfile getPrivacyProfile() {
        return privacyProfile;
    }

    public synchronized void setPrivacyProfile(@NonNull PrivacyProfile privacyProfile) {
        this.privacyProfile.setPrivacyProfile(privacyProfile);
        this.registered = RegisteredState.REGISTERED;
        notifyListeners();
    }

    @NonNull
    public synchronized RecipientRepo.Relationship getRelationship() {
        return mRelationship;
    }

    public synchronized void setRelationship(@NonNull RecipientRepo.Relationship relationship) {
        this.mRelationship = relationship;
    }

    /**
     * 设置本地备注profile
     * @param name
     * @param avatar
     */
    public synchronized void setLocalProfile(String name, String avatar) {
        boolean isChanged = false;
        if(!TextUtils.equals(this.localName, name)) {
            this.localName = name;
            isChanged = true;
        }
        if(!TextUtils.equals(this.localAvatar, avatar)) {
            this.localAvatar = avatar;
            isChanged = true;
        }
        if(isChanged) {
            notifyListeners();
        }

    }

    /**
     * 设置AME系统profile信息
     *
     * @param profileName
     * @param profileAvatar
     */
    public synchronized void setProfile(String profileName, String profileAvatar) {
        setProfile(this.profileKey, profileName, profileAvatar, this.registered);
    }

    /**
     * 设置AME系统profile信息
     *
     * @param profileKey
     * @param profileName
     * @param profileAvatar
     * @param registeredState
     */
    public synchronized void setProfile(@Nullable byte[] profileKey, @Nullable String profileName, @Nullable String profileAvatar, @NonNull RegisteredState registeredState) {
        boolean isChanged = false;
        if (!Arrays.equals(this.profileKey, profileKey)) {
            this.profileKey = profileKey;
            isChanged = true;
        }
        if (!TextUtils.equals(this.profileName, profileName)) {
            this.profileName = profileName;
            isChanged = true;
        }
        if (!TextUtils.equals(this.profileAvatar, profileAvatar)) {
            this.profileAvatar = profileAvatar;
            isChanged = true;
        }
        if (registered.getId() != registeredState.getId()) {
            this.registered = registeredState;
            isChanged = true;
        }
        if (isChanged) {
            notifyListeners();
        }
    }

    public synchronized void setProfile(@Nullable byte[] profileKey, @Nullable String profileName, @Nullable String profileAvatar) {
        boolean isChanged = false;
        if (!Arrays.equals(this.profileKey, profileKey)) {
            this.profileKey = profileKey;
            isChanged = true;
        }
        if (!TextUtils.equals(this.profileName, profileName)) {
            this.profileName = profileName;
            isChanged = true;
        }
        if (!TextUtils.equals(this.profileAvatar, profileAvatar)) {
            this.profileAvatar = profileAvatar;
            isChanged = true;
        }
        if (isChanged) {
            notifyListeners();
        }
    }

    public synchronized void updateName(String name) {
        if (!TextUtils.equals(this.profileName, name)) {
            this.profileName = name;
            notifyListeners();
        }
    }

    /**
     * 获取名字首字
     *
     * @return
     */
    @NonNull
    public synchronized String getCharacter() {
        return StringAppearanceUtil.INSTANCE.getFirstCharacter(getName());
    }

    /**
     * 获取名字首字母索引
     * @return
     */
    public synchronized int getCharacterLetterIndex() {
        String name = getName();
        if (!TextUtils.isEmpty(name)) {
            name = StringAppearanceUtil.INSTANCE.getFirstCharacterLetter(name);
            for (int i = 0; i < LETTERS.length; i++) {
                if(name.equals(LETTERS[i])) {
                    return i;
                }
            }
        }
        return LETTERS.length -1;
    }

    /**
     * 获取名字首字母
     *
     * @return
     */
    @NonNull
    public synchronized String getCharacterLetter() {
        String name = getName();
        if (!TextUtils.isEmpty(name)) {
            name = StringAppearanceUtil.INSTANCE.getFirstCharacterLetter(name);
            if (LETTERS[LETTERS.length - 2].compareTo(name) >= 0 && LETTERS[0].compareTo(name) <= 0) {
                return name;
            }
        }
        return UNKNOWN_LETTER;
    }

    /**
     * 获取联系人在BCM系统的头像（私密或明文， 可能为null）
     * @return
     */
    @Nullable
    public synchronized String getBcmAvatar() {
        if (isGroupRecipient()) {
            if (!TextUtils.isEmpty(profileAvatar)) {
                return profileAvatar;
            }
        } else if (!TextUtils.isEmpty(privacyProfile.getAvatarHDUri())) {
            return privacyProfile.getAvatarHDUri();
        } else if (!TextUtils.isEmpty(privacyProfile.getAvatarLDUri())) {
            return privacyProfile.getAvatarLDUri();
        } else if (!TextUtils.isEmpty(profileAvatar)) {
            String avatar = profileAvatar;
            if (avatar != null && !avatar.startsWith(ContentResolver.SCHEME_FILE)) {
                avatar = BcmHttpApiHelper.INSTANCE.getDownloadApi("/avatar/" + avatar);
                int size = AppUtilKotlinKt.dp2Px(50);
                return IndividualAvatarView.Companion.getAvatarThumbnailUrl(avatar, size, size);
            }else {
                return avatar;
            }
        }
        return null;
    }

    /**
     * 获取联系人在BCM系统的昵称(私密或明文，可能为null)
     *
     * @return
     */
    @Nullable
    public synchronized String getBcmName() {
        if (isGroupRecipient()) {
            if (!TextUtils.isEmpty(groupTitle)) {
                return groupTitle;
            }
            if (mGroupProvider == null) {
                mGroupProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_GROUP_BASE).navigationWithCast();
            }
            AmeGroupInfo groupInfo = mGroupProvider.getGroupInfo(groupId);
            if (groupInfo != null && !TextUtils.isEmpty(groupInfo.getDisplayName())) {
                return groupInfo.getDisplayName();
            }
            if (!TextUtils.isEmpty(profileName)) {
                return profileName;
            }
            return null;
        }
        else if(!TextUtils.isEmpty(privacyProfile.getName())) {
            return privacyProfile.getName();
        }
        else if(!TextUtils.isEmpty(profileName)) {
            return profileName;
        }
        return null;
    }

    /**
     * 获取当前优先展示的名称
     * @return
     */
    @NonNull
    public synchronized String getName() {
        String name = null;
        if (isGroupRecipient()) {
            name = getBcmName();
            if (name == null) {
                List<String> names = new ArrayList<>();
                for (Recipient recipient : participants) {
                    names.add(recipient.toShortString());
                }
                name = StringAppearanceUtil.INSTANCE.join(names, ", ");
            }
        } else {
            if (!TextUtils.isEmpty(localName)) {
                name = localName;
            } else {
                name = getBcmName();
            }
            if (name == null) {
                name = address.format();
            }
        }
        return name;
    }

    /**
     * 返回通用的avatar
     * @return
     */
    @NonNull
    public synchronized String getAvatar() {
        String avatar = null;
        if (isGroupRecipient()) {
            avatar = getBcmAvatar();
        } else {
            if (!TextUtils.isEmpty(localAvatar)) {
                avatar = localAvatar;
            } else {
                avatar = getBcmAvatar();
            }
        }
        if (avatar == null) {
            avatar = "";
        }
        return avatar;
    }

    /**
     * 返回可用的隐私avatar
     * @return
     */
    @Nullable
    public synchronized String getPrivacyAvatar() {
        if (!TextUtils.isEmpty(privacyProfile.getAvatarLDUri())) {
            return privacyProfile.getAvatarLDUri();
        }
        else if (!TextUtils.isEmpty(privacyProfile.getAvatarHDUri())) {
            return privacyProfile.getAvatarHDUri();
        }
        return null;
    }

    @Nullable
    public synchronized String getPrivacyAvatar(boolean isHd) {
        if (isHd) {
            if (privacyProfile.getAvatarHDUri() == null) {
                return null;
            }else {
                return privacyProfile.getAvatarHDUri();
            }
        }else {
            if (privacyProfile.getAvatarLDUri() == null) {
                return null;
            }else {
                return privacyProfile.getAvatarLDUri();
            }
        }
    }

    /**
     * 是否允许陌生人聊天
     * @return
     */
    public synchronized boolean isAllowStranger() {
        return privacyProfile.getAllowStranger();
    }

    /**
     * 是否好友关系
     * @return
     */
    public synchronized boolean isFriend() {
        return mRelationship == RecipientRepo.Relationship.FRIEND || isSelf();
    }

    @Nullable
    public synchronized BcmFeatureSupport getFeatureSupport() {
        return featureSupport;
    }

    public synchronized void setFeatureSupport(@Nullable BcmFeatureSupport featureSupport) {
        this.featureSupport = featureSupport;
    }

    @Nullable
    public synchronized String getProfileName() {
        return profileName;
    }

    public synchronized void setProfileName(@Nullable String profileName) {
        this.profileName = profileName;
    }

    @Nullable
    public synchronized String getProfileAvatar() {
        return profileAvatar;
    }

    public synchronized void setProfileAvatar(@Nullable String profileAvatar) {
        this.profileAvatar = profileAvatar;
    }

    @Nullable
    public synchronized String getLocalName() {
        return localName;
    }

    public synchronized void setLocalName(@Nullable String localName) {
        this.localName = localName;
    }

    @Nullable
    public synchronized String getLocalAvatar() {
        return localAvatar;
    }

    public synchronized void setLocalAvatar(@Nullable String localAvatar) {
        this.localAvatar = localAvatar;
    }

    @NonNull
    public synchronized MaterialColor getColor() {
        if (isGroupRecipient()) {
            return MaterialColor.GROUP;
        } else if (color != null) {
            return color;
        } else if (localName != null) {
            return ContactColors.generateFor(localName);
        } else {
            return ContactColors.UNKNOWN_COLOR;
        }
    }

    public void setColor(@NonNull MaterialColor color) {
        synchronized (this) {
            this.color = color;
        }
        notifyListeners();
    }

    @Nullable
    public String getCustomLabel() {
        return customLabel;
    }

    public synchronized Optional<Integer> getDefaultSubscriptionId() {
        return defaultSubscriptionId;
    }

    public void setDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
        synchronized (this) {
            this.defaultSubscriptionId = defaultSubscriptionId;
        }
        notifyListeners();
    }

    @Nullable
    public synchronized String getIdentityKey() {
        return identityKey;
    }

    public synchronized void setIdentityKey(@Nullable String identityKey) {
        this.identityKey = identityKey;
    }

    public synchronized boolean isProfileSharing() {
        return profileSharing;
    }

    public void setProfileSharing(boolean value) {
        synchronized (this) {
            this.profileSharing = value;
        }
        notifyListeners();
    }

    public boolean isGroupRecipient() {
        return address.isGroup();
    }

    public boolean isMmsGroupRecipient() {
        return address.isMmsGroup();
    }

    public boolean isPushGroupRecipient() {
        return address.isGroup() && !address.isMmsGroup();
    }

    @NonNull
    public List<Recipient> getParticipants() {
        return participants;
    }

    public synchronized String toShortString() {
        return getName();
    }

    @Nullable
    public synchronized Uri getRingtone() {
        return ringtone;
    }

    public void setRingtone(@Nullable Uri ringtone) {
        synchronized (this) {
            this.ringtone = ringtone;
        }
        notifyListeners();
    }

    public synchronized long getMutedUntil() {
        return mutedUntil;
    }

    public synchronized boolean isMuted() {
        return System.currentTimeMillis() <= mutedUntil;
    }

    public void setMuted(long mutedUntil) {
        synchronized (this) {
            this.mutedUntil = mutedUntil;
        }
        notifyListeners();
    }

    public synchronized boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        synchronized (this) {
            this.blocked = blocked;
        }
        notifyListeners();
    }

    public synchronized VibrateState getVibrate() {
        return vibrate;
    }

    public void setVibrate(VibrateState vibrate) {
        synchronized (this) {
            this.vibrate = vibrate;
        }
        notifyListeners();
    }

    public synchronized int getExpireMessages() {
        return expireMessages;
    }

    public synchronized void setExpireMessages(int expireMessages) {

        if (this.expireMessages != expireMessages) {
            this.expireMessages = expireMessages;
        }
    }

    public synchronized boolean hasSeenInviteReminder() {
        return seenInviteReminder;
    }

    public synchronized void setHasSeenInviteReminder(boolean value) {
        this.seenInviteReminder = value;
    }

    public synchronized RegisteredState getRegistered() {
        if (isPushGroupRecipient()) {
            return RegisteredState.REGISTERED;
        } else if (isMmsGroupRecipient()) {
            return RegisteredState.NOT_REGISTERED;
        }
        return registered;
    }

    public synchronized void setRegistered(@NonNull RegisteredState value) {
        if(this.registered != value) {
            this.registered = value;
            notifyListeners();
        }
    }

    @Nullable
    public synchronized byte[] getProfileKey() {
        return profileKey;
    }

    public synchronized void setProfileKey(@Nullable byte[] profileKey) {
        this.profileKey = profileKey;
    }

    /**
     * 广播所有监听者有更新（注意不要滥用，触发回调在主线程执行）
     */
    public void notifyListeners() {
        Set<RecipientModifiedListener> localListeners;

        synchronized (this) {
            localListeners = new HashSet<>(listeners);
        }

        AmeDispatcher.INSTANCE.getMainThread().dispatch(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                for (RecipientModifiedListener listener : localListeners) {
                    if(listener != null) {
                        listener.onModified(Recipient.this);
                    }
                }
                return Unit.INSTANCE;
            }
        });

    }

    @Override
    public void onModified(Recipient recipient) {
        notifyListeners();
    }

    /**
     * 是否陈腐的，不新鲜的
     *
     * @return
     */
    boolean isStale() {
        return stale;
    }

    void setStale() {
        this.stale = true;
    }

    public synchronized Recipient resolve() {
        while (resolving) {
            try {
                Util.wait(this, 0);
            } catch (Exception e) {
                resolving = false;
            }
        }
        return this;
    }

    public synchronized boolean isResolving() {
        return resolving;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Recipient)) {
            return false;
        }

        Recipient that = (Recipient) o;

        return this.address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return this.address.hashCode();
    }


    @Deprecated
    @Nullable
    public synchronized Uri getContactUri() {
        return null;
    }

    @Deprecated
    @Nullable
    public synchronized ContactPhoto getContactPhoto() {
        return null;
    }


}
