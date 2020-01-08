package com.bcm.messenger.common.recipients;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.color.MaterialColor;
import com.bcm.messenger.common.config.BcmFeatureSupport;
import com.bcm.messenger.common.contacts.avatars.ContactColors;
import com.bcm.messenger.common.contacts.avatars.ContactPhoto;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.BcmHttpApiHelper;
import com.bcm.messenger.common.core.corebean.AmeGroupInfo;
import com.bcm.messenger.common.database.records.PrivacyProfile;
import com.bcm.messenger.common.database.records.RecipientSettings;
import com.bcm.messenger.common.database.repositories.RecipientRepo;
import com.bcm.messenger.common.deprecated.RecipientDatabase.RegisteredState;
import com.bcm.messenger.common.deprecated.RecipientDatabase.VibrateState;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.provider.accountmodule.IContactModule;
import com.bcm.messenger.common.provider.accountmodule.IGroupModule;
import com.bcm.messenger.common.recipients.RecipientProvider.RecipientDetails;
import com.bcm.messenger.common.ui.IndividualAvatarView;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Conversions;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.bcm.messenger.utility.Util;
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
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
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
     * 
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
     * keyword（）
     * @param keyword
     * @param recipient
     * @return
     */
    public static boolean match(String keyword, Recipient recipient) {

        ALog.i(TAG, "match keyword: " + keyword + "， name: " + recipient.getName());
        return StringAppearanceUtil.INSTANCE.containIgnore(recipient.getName(), keyword);

    }

    /**
     * action
     */
    public static final String RECIPIENT_CLEAR_ACTION = "com.bcm.messenger.common.database.RecipientFactory.CLEAR";

    private static final Map<AccountContext, RecipientProvider> sProviderMap = new HashMap<AccountContext, RecipientProvider>();

    private static synchronized RecipientProvider getProvider(AccountContext accountContext) {
        RecipientProvider provider = sProviderMap.get(accountContext);
        if (provider == null) {
            provider = new RecipientProvider(accountContext);
            sProviderMap.put(accountContext, provider);
        }
        return provider;
    }

    /**
     * json
     */
    public static String toQRCode(Recipient recipient) {
        try {
            /**
             * 1：phone，，
             * 2：uid，
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
     * 
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

    @NonNull
    public static Recipient login(@NonNull AccountContext context) throws Exception {
        String loginUid = context.getUid();
        ALog.d(TAG, "loginUid:" + loginUid);
        if (TextUtils.isEmpty(loginUid)) {
            throw new Exception(("login uid is null"));
        }
        return getProvider(context).getRecipient(AppContextHolder.APP_CONTEXT, Address.from(context, loginUid), null, true);
    }

    @NonNull
    public static Recipient major() throws Exception {
        String majorUid = AMELogin.INSTANCE.getMajorUid();
        ALog.d(TAG, "majorUid:" + majorUid);
        if (TextUtils.isEmpty(majorUid)) {
            throw new Exception(("major uid is null"));
        }
        return getProvider(AMELogin.INSTANCE.getMajorContext()).getRecipient(AppContextHolder.APP_CONTEXT, Address.from(AMELogin.INSTANCE.getMajorContext(), majorUid), null, true);
    }

    @NonNull
    public static Recipient from(@NonNull Address address, boolean asynchronous) {
        ALog.d(TAG, "from accountContext:" + address.context().getUid() + ", from(uid: " + address.serialize() + ", asynchronous:" + asynchronous + ")");
        return getProvider(address.context()).getRecipient(AppContextHolder.APP_CONTEXT, address, null, asynchronous);
    }


    @NonNull
    public static Recipient from(@NonNull AccountContext context, @NonNull String uid, boolean asynchronous) {
        ALog.d(TAG, "from accountContext:" + context.getUid() + ", from(uid: " + uid + ", (asynchronous:" + asynchronous + ")");
        return getProvider(context).getRecipient(AppContextHolder.APP_CONTEXT, Address.from(context, uid), null, asynchronous);
    }


    @NonNull
    public static Recipient fromSnapshot(@NonNull AccountContext context, @NonNull String uid, @NonNull RecipientSettings settings) {
        ALog.d(TAG, "from accountContext:" + context.getUid() + ", fromSnapshot(uid: " + uid + ")");
        RecipientDetails details = new RecipientDetails(uid, null, null, settings, null);
        return getProvider(context).getRecipient(AppContextHolder.APP_CONTEXT, Address.from(context, uid), details, false);
    }

    public static void clearCache(Context context) {
        RecipientProvider.Companion.clearCache();
        context.sendBroadcast(new Intent(RECIPIENT_CLEAR_ACTION));
    }


    public static void clearCache(Context context, Address address) {
        RecipientProvider.Companion.deleteCache(address);
    }


    @NonNull
    public static Recipient recipientFromNewGroupId(@NonNull AccountContext context, long groupId) {
        return Recipient.from(context, GroupUtil.uidFromGid(groupId), false);
    }

    @NonNull
    public static Recipient recipientFromNewGroupIdAsync(@NonNull AccountContext context, long groupId) {
        return Recipient.from(context, GroupUtil.uidFromGid(groupId), true);
    }

    @NonNull
    public static Recipient recipientFromNewGroup(@NonNull AccountContext context, @NonNull AmeGroupInfo groupInfo) {
        String uid = GroupUtil.uidFromGid(groupInfo.getGid());
        RecipientSettings settings = new RecipientSettings();
        settings.setUid(uid);
        settings.setProfileName(groupInfo.getName());
        settings.setProfileAvatar(groupInfo.getIconUrl());
        if (groupInfo.getMute()) {
            settings.setMuteUntil(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10));
        }else {
            settings.setMuteUntil(0);
        }
        return Recipient.fromSnapshot(context, uid, settings);
    }

    private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

    /**
     * 
     */
    private boolean mNeedRefreshProfile = true;
    /**
     * profile
     */
    public synchronized boolean needRefreshProfile() {
        return mNeedRefreshProfile;
    }

    public synchronized void setNeedRefreshProfile(boolean need) {
        mNeedRefreshProfile = need;
    }

    @NonNull
    private final Address address;
    @NonNull
    private final List<Recipient> participants = new LinkedList<>();
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
    private String localName;
    @Nullable
    private String localAvatar;

    @NonNull
    private PrivacyProfile privacyProfile = new PrivacyProfile();
    @NonNull
    private RecipientRepo.Relationship mRelationship = RecipientRepo.Relationship.STRANGER;
    @Nullable
    private BcmFeatureSupport featureSupport;

    private boolean backgroundRequestAddFriendFlag = false;
    /**
     *  addressID，adapterStableId
     */
    private long uniquenessId;

    private long groupId = -1L;

    public long getUniquenessId() {
        return uniquenessId;
    }

    public long getGroupId() {
        return groupId;
    }

    /**
     * constructor
     */
    Recipient(@NonNull Address address,
              @Nullable Recipient stale) {

        this.address = address;
        this.color = null;
        this.resolving = true;
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


    void updateRecipientDetails(@Nullable RecipientDetails details) {
        updateRecipientDetails(details, true);
    }

    /**
     * 
     * @param details 
     * @param notify 
     */
    void updateRecipientDetails(@Nullable RecipientDetails details, boolean notify) {

        if (details == null || (details.getCustomName() == null && details.getCustomAvatar() == null && details.getParticipants() == null && details.getSettings() == null)) {
            return;
        }

        synchronized (Recipient.this) {

            boolean changed = false;
            if (isGroupRecipient()) {
                if (details.getCustomName() != null) {
                    Recipient.this.groupTitle = details.getCustomName();
                    changed = true;
                }
                if (details.getCustomAvatar() != null) {
                    Recipient.this.customLabel = details.getCustomAvatar();
                    changed = true;
                }
            }
            if (details.getParticipants() != null) {
                Recipient.this.participants.clear();
                Recipient.this.participants.addAll(details.getParticipants());
                changed = true;
            }

            if (Recipient.this.fillSettings(details.getSettings())) {
                changed = true;
            }

            this.resolving = false;

            if (!listeners.isEmpty()) {
                for (Recipient recipient : Recipient.this.participants) {
                    recipient.addListener(Recipient.this);
                }
            }
            if (notify && changed) {
                notifyListeners();
            }
        }

        RecipientProvider.Companion.updateCache(this);

        //check FOLLOW relationship
        IContactModule module = AmeModuleCenter.INSTANCE.contact(address.context());
        if (module != null) {
            module.checkNeedRequestAddFriend(AppContextHolder.APP_CONTEXT, Recipient.this);
        }
    }

    /**
     * 
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
     * 
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

            return true;
        }
        return false;
    }

    public boolean isLogin() {
        return address.isCurrentLogin();
    }

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

    public synchronized boolean checkNeedProfileKey() {
        boolean need = (TextUtils.isEmpty(privacyProfile.getName()) && TextUtils.isEmpty(privacyProfile.getNameKey())) ||
                (TextUtils.isEmpty(privacyProfile.getAvatarHD()) && TextUtils.isEmpty(privacyProfile.getAvatarKey())) ||
                (TextUtils.isEmpty(privacyProfile.getAvatarLD()) && TextUtils.isEmpty(privacyProfile.getAvatarKey()));
        ALog.i(TAG, "checkNeedProfileKey: $need");
        return need;
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
     * profile
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
     * AMEprofile
     *
     * @param profileName
     * @param profileAvatar
     */
    public synchronized void setProfile(String profileName, String profileAvatar) {
        setProfile(this.profileKey, profileName, profileAvatar, this.registered);
    }

    /**
     * AMEprofile
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
     * 
     *
     * @return
     */
    @NonNull
    public synchronized String getCharacter() {
        return StringAppearanceUtil.INSTANCE.getFirstCharacter(getName());
    }

    /**
     * 
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
     * 
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
     * BCM（， null）
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
     * BCM(，null)
     *
     * @return
     */
    @Nullable
    public synchronized String getBcmName() {
        if (isGroupRecipient()) {
            if (!TextUtils.isEmpty(groupTitle)) {
                return groupTitle;
            }
            IGroupModule groupProvider = AmeModuleCenter.INSTANCE.group(address.context());
            AmeGroupInfo groupInfo;
            if (groupProvider == null) {
                groupInfo = null;
            } else {
                groupInfo = groupProvider.getGroupInfo(groupId);
            }
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
     * 
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
     * avatar
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
     * avatar
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
     * 
     * @return
     */
    public synchronized boolean isAllowStranger() {
        return privacyProfile.getAllowStranger();
    }

    /**
     * 
     * @return
     */
    public synchronized boolean isFriend() {
        return mRelationship == RecipientRepo.Relationship.FRIEND || isLogin();
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
     * （，）
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
     * ，
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
