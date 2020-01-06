package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.BuildConfig;
import com.bcm.messenger.common.color.MaterialColor;
import com.bcm.messenger.common.config.BcmFeatureSupport;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.spongycastle.util.encoders.DecoderException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;

@Deprecated
public class RecipientDatabase extends Database {

    private static final String TAG = "RecipientDatabase";

    private static final String RECIPIENT_PREFERENCES_URI = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/recipients/";

    static final String TABLE_NAME = "recipient_preferences";
    public static final String ID = "_id";
    
    public static final String ADDRESS = "recipient_ids";
    public static final String BLOCK = "block";
    public static final String NOTIFICATION = "notification";
    public static final String VIBRATE = "vibrate";
    public static final String MUTE_UNTIL = "mute_until";
    public static final String COLOR = "color";
    public static final String SEEN_INVITE_REMINDER = "seen_invite_reminder";
    public static final String DEFAULT_SUBSCRIPTION_ID = "default_subscription_id";
    public static final String EXPIRE_MESSAGES = "expire_messages";
    public static final String REGISTERED = "registered";
    public static final String PROFILE_KEY = "profile_key";
    
    public static final String PROFILE_NAME = "signal_profile_name";
    
    public static final String PROFILE_AVATAR = "signal_profile_avatar";

    
    public static final String PROFILE_SHARING = "profile_sharing_approval";
    
    public static final String SYSTEM_CONTACT_ID = "system_contact_id";

    
    public static final String SYSTEM_NAME = "system_display_name";
    
    public static final String SYSTEM_AVATAR = "system_avatar";

    
    public static final String LOCAL_NAME = "local_name";
    
    public static final String LOCAL_AVATAR = "local_avatar";


    
    public static final String PHONE = "bind_phone";
    
    public static final String FRIEND_FLAG = "is_friend";
    
    public static final String PRIVACY_PROFILE = "privacy_profile";
    
    public static final String RELATIONSHIP = "relationship";
    
    public static final String SUPPORT_FEATURES = "support_features";

    private static final String[] RECIPIENT_PROJECTION = new String[]{
            BLOCK, NOTIFICATION, VIBRATE, MUTE_UNTIL, COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, EXPIRE_MESSAGES, REGISTERED,
            PROFILE_KEY, SYSTEM_CONTACT_ID, SYSTEM_NAME, SYSTEM_AVATAR, LOCAL_NAME, LOCAL_AVATAR, PROFILE_NAME, PROFILE_AVATAR, PROFILE_SHARING,
            PHONE, FRIEND_FLAG, PRIVACY_PROFILE, RELATIONSHIP,SUPPORT_FEATURES
    };

    static final List<String> TYPED_RECIPIENT_PROJECTION = Stream.of(RECIPIENT_PROJECTION)
            .map(columnName -> TABLE_NAME + "." + columnName)
            .toList();

    public enum VibrateState {
        DEFAULT(0), ENABLED(1), DISABLED(2);

        private final int id;

        VibrateState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static VibrateState fromId(int id) {
            return values()[id];
        }
    }

    public enum RegisteredState {
        UNKNOWN(0), REGISTERED(1), NOT_REGISTERED(2);

        private final int id;

        RegisteredState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static RegisteredState fromId(int id) {
            return values()[id];
        }
    }

   
    public enum Relationship {
        STRANGER(0), FRIEND(1), FOLLOW(2), REQUEST(3), FOLLOW_REQUEST(4), BREAK(5);

        private final int type;

        Relationship(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public static Relationship fromType(int type) {
            return values()[type];
        }
    }

    
    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME +
                    " (" + ID + " INTEGER PRIMARY KEY, " +
                    ADDRESS + " TEXT UNIQUE, " +
                    BLOCK + " INTEGER DEFAULT 0," +
                    NOTIFICATION + " TEXT DEFAULT NULL, " +
                    VIBRATE + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
                    MUTE_UNTIL + " INTEGER DEFAULT 0, " +
                    COLOR + " TEXT DEFAULT NULL, " +
                    SEEN_INVITE_REMINDER + " INTEGER DEFAULT 0, " +
                    DEFAULT_SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " +
                    EXPIRE_MESSAGES + " INTEGER DEFAULT 0, " +
                    REGISTERED + " INTEGER DEFAULT 0, " +
                    SYSTEM_CONTACT_ID + " TEXT DEFAULT NULL, " +
                    SYSTEM_NAME + " TEXT DEFAULT NULL, " +
                    SYSTEM_AVATAR + " TEXT DEFAULT NULL, " +
                    LOCAL_NAME + " TEXT DEFAULT NULL, " +
                    LOCAL_AVATAR + " TEXT DEFAULT NULL, " +
                    PROFILE_KEY + " TEXT DEFAULT NULL, " +
                    PROFILE_NAME + " TEXT DEFAULT NULL, " +
                    PROFILE_AVATAR + " TEXT DEFAULT NULL, " +
                    PROFILE_SHARING + " INTEGER DEFAULT 0, " +
                    PHONE + " TEXT DEFAULT NULL, " +
                    PRIVACY_PROFILE + " TEXT, " +
                    RELATIONSHIP + " INTEGER DEFAULT 0, " +
                    FRIEND_FLAG + " INTEGER DEFAULT 0, "  +
                    SUPPORT_FEATURES + " TEXT DEFAULT NULL);";

    
    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    public static final String ALTER_TABLE_ADD_PRIVACY = "ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + PRIVACY_PROFILE + " TEXT";
    public static final String ALTER_TABLE_ADD_RELATIONSHIP = "ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + RELATIONSHIP + " INTEGER";
    public static final String ALTER_TABLE_ADD_SUPPORT_FEATURES = "ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + SUPPORT_FEATURES + " TEXT DEFAULT NULL";

    public RecipientDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public Cursor getAllDatabaseRecipients() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try {
            Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
            return cursor;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static RecipientSettings getRecipientSettings(@NonNull Cursor cursor, IdentityDatabase identityDatabase) {

        try {
            String id = cursor.getString(cursor.getColumnIndex(ADDRESS));
            boolean blocked = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK)) == 1;
            String notification = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
            int vibrateState = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
            long muteUntil = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
            String serializedColor = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
            Uri notificationUri = notification == null ? null : Uri.parse(notification);
            boolean seenInviteReminder = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_INVITE_REMINDER)) == 1;
            int defaultSubscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
            int expireMessages = cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRE_MESSAGES));
            int registeredState = cursor.getInt(cursor.getColumnIndexOrThrow(REGISTERED));
            String profileKeyString = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY));
            String signalProfileName = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_NAME));
            String signalProfileAvatar = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_AVATAR));
            boolean profileSharing = cursor.getInt(cursor.getColumnIndexOrThrow(PROFILE_SHARING)) == 1;
            String privacyProfileString = cursor.getString(cursor.getColumnIndex(PRIVACY_PROFILE));
            int relationshipType = cursor.getInt(cursor.getColumnIndex(RELATIONSHIP));

            String features = cursor.getString(cursor.getColumnIndex(SUPPORT_FEATURES));
            BcmFeatureSupport featureSupport = null;
            if (!TextUtils.isEmpty(features)) {
                try {
                    featureSupport = new BcmFeatureSupport(features);
                } catch (DecoderException e) {
                    ALog.e(TAG, "parse feature failed", e);
                }

            }


            MaterialColor color;
            byte[] profileKey = null;

            try {
                color = serializedColor == null ? null : MaterialColor.fromSerialized(serializedColor);
            } catch (MaterialColor.UnknownColorException e) {
                Log.w(TAG, e);
                color = null;
            }

            if (profileKeyString != null) {
                try {
                    profileKey = Base64.decode(profileKeyString);
                } catch (IOException e) {
                    Log.w(TAG, e);
                    profileKey = null;
                }
            }

            return new RecipientSettings(id, blocked, muteUntil,
                    VibrateState.fromId(vibrateState),
                    notificationUri, color, seenInviteReminder,
                    defaultSubscriptionId, expireMessages,
                    RegisteredState.fromId(registeredState),
                    profileKey,
                    cursor.getString(cursor.getColumnIndex(SYSTEM_CONTACT_ID)), cursor.getString(cursor.getColumnIndex(SYSTEM_NAME)), cursor.getString(cursor.getColumnIndex(SYSTEM_AVATAR)),
                    cursor.getString(cursor.getColumnIndex(LOCAL_NAME)), cursor.getString(cursor.getColumnIndex(LOCAL_AVATAR)),
                    signalProfileName, signalProfileAvatar, profileSharing,
                    cursor.getString(cursor.getColumnIndex(PHONE)),
                    PrivacyProfile.fromString(privacyProfileString),
                    Relationship.fromType(relationshipType),
                    cursor.getInt(cursor.getColumnIndex(FRIEND_FLAG)) == 1, featureSupport, identityDatabase);

        } catch (Exception ex) {
            return null;
        }
    }
   
    public static class RecipientSettings {

        @Expose
        @SerializedName("uid")
        private @NonNull
        String uid;
        private boolean blocked;
        private long muteUntil;
        private VibrateState vibrateState;
        private Uri notification;
        private MaterialColor color;
        private boolean seenInviteReminder;
        private int defaultSubscriptionId;
        private int expireMessages;
        private @NonNull
        RegisteredState registered;
        private byte[] profileKey;
        private String systemContactId;
        private String systemName;
        private String systemAvatar;

        @SerializedName("localName")
        private String localName;

        @SerializedName("localAvatar")
        private String localAvatar;

        @SerializedName("profileName")
        private String signalProfileName;

        @SerializedName("profileAvatar")
        private String signalProfileAvatar;

        private boolean profileSharing;

        @SerializedName("phone")
        private String phone;

        @Deprecated
        @SerializedName("friendFlag")
        private boolean friendFlag;

        @SerializedName("identityKey")
        private @Nullable
        String identityKey;

        @SerializedName("privacyProfile")
        private @NonNull PrivacyProfile privacyProfile;

        @SerializedName("featureSupport")
        private BcmFeatureSupport featureSupport;

        @SerializedName("relationship")
        @NonNull
        private Relationship relationship;

        @Nullable
        private String contactPartKey;

        public RecipientSettings(@NonNull String uid) {
            this.uid = uid;
            this.registered = RegisteredState.UNKNOWN;
            this.privacyProfile = new PrivacyProfile();
            this.relationship = Relationship.STRANGER;
        }

        public RecipientSettings(@NonNull String uid, boolean blocked, long muteUntil,
                                 @NonNull VibrateState vibrateState,
                                 @Nullable Uri notification,
                                 @Nullable MaterialColor color,
                                 boolean seenInviteReminder,
                                 int defaultSubscriptionId,
                                 int expireMessages,
                                 @NonNull RegisteredState registered,
                                 @Nullable byte[] profileKey,
                                 @Nullable String systemContactId,
                                 @Nullable String systemName,
                                 @Nullable String systemAvatar,
                                 @Nullable String localName,
                                 @Nullable String localAvatar,
                                 @Nullable String signalProfileName,
                                 @Nullable String signalProfileAvatar,
                                 boolean profileSharing, String phone,
                                 @NonNull PrivacyProfile privacyProfile,
                                 @NonNull Relationship relationship,
                                 boolean friendFlag,
                                 BcmFeatureSupport featureSupport,
                                 IdentityDatabase identityDatabase) {
            this.uid = uid;
            this.blocked = blocked;
            this.muteUntil = muteUntil;
            this.vibrateState = vibrateState;
            this.notification = notification;
            this.color = color;
            this.seenInviteReminder = seenInviteReminder;
            this.defaultSubscriptionId = defaultSubscriptionId;
            this.expireMessages = expireMessages;
            this.registered = registered;
            this.profileKey = profileKey;
            this.systemContactId = systemContactId;
            this.systemName = systemName;
            this.systemAvatar = systemAvatar;
            this.localName = localName;
            this.localAvatar = localAvatar;
            this.signalProfileName = signalProfileName;
            this.signalProfileAvatar = signalProfileAvatar;
            this.profileSharing = profileSharing;
            this.phone = phone;
            this.privacyProfile = privacyProfile;
            this.relationship = relationship;
            this.featureSupport = featureSupport;
            this.friendFlag = friendFlag;

            
            if (this.friendFlag && this.relationship == Relationship.STRANGER) {
                this.relationship = Relationship.FOLLOW;
            }

            try {
                IdentityDatabase.IdentityRecord record = identityDatabase.getIdentity(Address.from(uid)).orNull();
                if (record != null) {
                    this.identityKey = Base64.encodeBytes(record.getIdentityKey().serialize());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public @NonNull
        String getUid() {
            return uid;
        }

        public @Nullable
        MaterialColor getColor() {
            return color;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public void setMuteUntil(long muteUntil) {
            this.muteUntil = muteUntil;
        }

        public long getMuteUntil() {
            return muteUntil;
        }

        public @NonNull
        VibrateState getVibrateState() {
            return vibrateState;
        }

        public @Nullable
        Uri getRingtone() {
            return notification;
        }

        public boolean hasSeenInviteReminder() {
            return seenInviteReminder;
        }

        public Optional<Integer> getDefaultSubscriptionId() {
            return defaultSubscriptionId != -1 ? Optional.of(defaultSubscriptionId) : Optional.absent();
        }

        public int getExpireMessages() {
            return expireMessages;
        }

        @NonNull
        public RegisteredState getRegistered() {
            return registered;
        }

        public byte[] getProfileKey() {
            return profileKey;
        }

        public @Nullable
        String getLocalName() {
            return localName;
        }

        public @Nullable
        String getProfileName() {
            return signalProfileName;
        }

        public @Nullable
        String getProfileAvatar() {
            return signalProfileAvatar;
        }

        public String getSystemContactId() {
            return systemContactId;
        }

        public boolean isProfileSharing() {
            return profileSharing;
        }

        public @Nullable
        String getLocalAvatar() {
            return localAvatar;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }


        public String getSystemName() {
            return systemName;
        }

        public String getSystemAvatar() {
            return systemAvatar;
        }

        @NonNull
        public PrivacyProfile getPrivacyProfile() {
            return privacyProfile;
        }

        public void setPrivacyProfile(@NonNull PrivacyProfile privacyProfile) {
            this.privacyProfile = privacyProfile;
        }

        @NonNull
        public Relationship getRelationship() {
            return relationship;
        }

        public void setRelationship(@NonNull Relationship relationship) {
            this.relationship = relationship;
        }

        public BcmFeatureSupport getFeatureSupport() {
            return featureSupport;
        }

       
        public void setSystemProfile(@Nullable String contactId, @Nullable String systemName, @Nullable String systemAvatar) {
            this.systemContactId = contactId;
            this.systemName = systemName;
            this.systemAvatar = systemAvatar;
        }

      
        public void setSignalProfile(@Nullable byte[] profileKey, @Nullable String name, @Nullable String avatar) {
            this.profileKey = profileKey;
            this.signalProfileName = name;
            this.signalProfileAvatar = avatar;
        }

    
        public void setLocalProfile(@Nullable String name, @Nullable String avatar) {
            this.localName = name;
            this.localAvatar = avatar;
        }

        public void setTemporaryProfile(@Nullable String name, @Nullable String avatar) {
            if (TextUtils.isEmpty(signalProfileName)) {
                signalProfileName = name;
            }
            if (TextUtils.isEmpty(signalProfileAvatar)) {
                signalProfileAvatar = avatar;
            }
        }

        public void setRegistered(@NonNull RegisteredState registered) {
            this.registered = registered;
        }

        @Nullable
        public String getIdentityKey() {
            return identityKey;
        }

        public void setIdentityKey(@Nullable String identityKey) {
            this.identityKey = identityKey;
        }

        @Nullable
        public String getContactPartKey() {
            return contactPartKey;
        }

        public void setContactPartKey(@Nullable String contactPartKey) {
            this.contactPartKey = contactPartKey;
        }

        @Deprecated
        public boolean isFriendFlag() {
            return friendFlag;
        }

        @Deprecated
        public void setFriendFlag(boolean friendFlag) {
            this.friendFlag = friendFlag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final RecipientSettings settings = (RecipientSettings) o;

            return uid.equals(settings.uid);
        }

        @Override
        public int hashCode() {
            return uid.hashCode();
        }

    }

    public static class BlockedReader {

        private final Context context;
        private final Cursor cursor;

        BlockedReader(Context context, Cursor cursor) {
            this.context = context;
            this.cursor = cursor;
        }

        public @NonNull
        Address getCurrentAddress() {
            String serialized = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
            return Address.from(AMELogin.INSTANCE.getMajorContext(), serialized);
        }

        public @NonNull
        Recipient getCurrent() {
            String serialized = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
            return Recipient.from(AMELogin.INSTANCE.getMajorContext(), serialized, false);
        }

        public @Nullable
        Recipient getNext() {
            if (!cursor.moveToNext()) {
                return null;
            }

            return getCurrent();
        }
    }

    public static class PrivacyProfile implements NotGuard {

        public static final int CURRENT_VERSION = 1;
        public static final int MAX_LD_AVATAR_SIZE = AppUtilKotlinKt.dp2Px(55);

        private static final String SHARE_SHORT_LINK_PRE =
//                "http://39.108.124.60:9200/member/"; 
                "https://s.bcm.social/member/";

       
        public static Boolean isShortLink(@Nullable String url) {
            if (url == null) {
                return false;
            }
            return url.startsWith(SHARE_SHORT_LINK_PRE);
        }

        @Nullable
        private String encryptedName;
        @Nullable
        private String name;
        @Nullable
        private String encryptedAvatarLD;
        @Nullable
        private String avatarLD;
        @Nullable
        private String avatarLDUri;
        private boolean isAvatarLdOld;
        @Nullable
        private String encryptedAvatarHD;
        @Nullable
        private String avatarHD;
        @Nullable
        private String avatarHDUri;
        private boolean isAvatarHdOld;
        @Nullable
        private String namePubKey;
        @Nullable
        private String nameKey;
        @Nullable
        private String avatarPubKey;
        @Nullable
        private String avatarKey;
        @Nullable
        private String shortLink;

        private boolean allowStranger = true;

        private int version = CURRENT_VERSION;

        private boolean needKeys = false;

        public PrivacyProfile() {
            this.version = CURRENT_VERSION;
        }

        @Nullable
        public String getEncryptedName() {
            return encryptedName;
        }

        public void setEncryptedName(@Nullable String encryptedName) {
            this.encryptedName = encryptedName;
        }

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(@Nullable String name) {
            this.name = name;
        }

        @Nullable
        public String getEncryptedAvatarLD() {
            return encryptedAvatarLD;
        }

        public void setEncryptedAvatarLD(@Nullable String encryptedAvatarLD) {
            this.encryptedAvatarLD = encryptedAvatarLD;
        }

        @Nullable
        public String getAvatarLD() {
            return avatarLD;
        }

        public void setAvatarLD(@Nullable String avatarLD) {
            this.avatarLD = avatarLD;
        }

        @Nullable
        public String getAvatarLDUri() {
            return avatarLDUri;
        }

        public void setAvatarLDUri(@Nullable String avatarLDUri) {
            this.avatarLDUri = avatarLDUri;
        }

        public boolean isAvatarLdOld() {
            return isAvatarLdOld;
        }

        public void setAvatarLdOld(boolean avatarLdOld) {
            isAvatarLdOld = avatarLdOld;
        }

        @Nullable
        public String getEncryptedAvatarHD() {
            return encryptedAvatarHD;
        }

        public void setEncryptedAvatarHD(@Nullable String encryptedAvatarHD) {
            this.encryptedAvatarHD = encryptedAvatarHD;
        }

        @Nullable
        public String getAvatarHD() {
            return avatarHD;
        }

        public void setAvatarHD(@Nullable String avatarHD) {
            this.avatarHD = avatarHD;
        }

        @Nullable
        public String getAvatarHDUri() {
            return avatarHDUri;
        }

        public void setAvatarHDUri(@Nullable String avatarHDUri) {
            this.avatarHDUri = avatarHDUri;
        }

        public boolean isAvatarHdOld() {
            return isAvatarHdOld;
        }

        public void setAvatarHdOld(boolean avatarHdOld) {
            isAvatarHdOld = avatarHdOld;
        }

        @Nullable
        public String getNamePubKey() {
            return namePubKey;
        }

        public void setNamePubKey(@Nullable String namePubKey) {
            this.namePubKey = namePubKey;
        }

        @Nullable
        public String getNameKey() {
            return nameKey;
        }

        public void setNameKey(@Nullable String nameKey) {
            this.nameKey = nameKey;
        }

        @Nullable
        public String getAvatarPubKey() {
            return avatarPubKey;
        }

        public void setAvatarPubKey(@Nullable String avatarPubKey) {
            this.avatarPubKey = avatarPubKey;
        }

        @Nullable
        public String getAvatarKey() {
            return avatarKey;
        }

        public void setAvatarKey(@Nullable String avatarKey) {
            this.avatarKey = avatarKey;
        }

        public boolean isAllowStranger() {
            return allowStranger;
        }

        public void setAllowStranger(boolean allowStranger) {
            this.allowStranger = allowStranger;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public boolean isNeedKeys() {
            return needKeys;
        }

        public void setNeedKeys(boolean needKeys) {
            this.needKeys = needKeys;
        }

        @Nullable
        public String getShortLink() {
            return shortLink;
        }

        public void setShortLink(String shortIndex, String hashBase62) {
            this.shortLink = SHARE_SHORT_LINK_PRE + shortIndex + "#" + hashBase62;
        }

        public String getUploadPubKeys() {
            try {
                JSONObject json = new JSONObject();
                json.put("namePubKey", this.namePubKey);
                json.put("avatarPubKey", this.avatarPubKey);
                json.put("version", this.version);
                JSONObject result = new JSONObject();
                result.put("encrypt", json.toString());
                return result.toString();

            }catch (Exception ex) {
                ALog.e(TAG, "getUploadPubKeys error", ex);
            }
            return "";
        }

      
        public RecipientDatabase.PrivacyProfile copy() {
            PrivacyProfile newProfile = new PrivacyProfile();
            newProfile.name = name;
            newProfile.encryptedName = encryptedName;
            newProfile.namePubKey = namePubKey;
            newProfile.nameKey = nameKey;
            newProfile.avatarHD = avatarHD;
            newProfile.avatarHDUri = avatarHDUri;
            newProfile.avatarLD = avatarLD;
            newProfile.avatarLDUri = avatarLDUri;
            newProfile.avatarKey = avatarKey;
            newProfile.avatarPubKey = avatarPubKey;
            newProfile.isAvatarHdOld = isAvatarHdOld;
            newProfile.isAvatarLdOld = isAvatarLdOld;
            newProfile.allowStranger = allowStranger;
            newProfile.needKeys = needKeys;
            newProfile.version = version;
            return newProfile;
        }

        public void setPrivacyProfile(PrivacyProfile profile) {
            this.name = profile.name;
            this.encryptedName = profile.encryptedName;
            this.namePubKey = profile.namePubKey;
            this.nameKey = profile.nameKey;
            this.avatarHD = profile.avatarHD;
            this.avatarHDUri = profile.avatarHDUri;
            this.avatarLD = profile.avatarLD;
            this.avatarLDUri = profile.avatarLDUri;
            this.avatarKey = profile.avatarKey;
            this.avatarPubKey = profile.avatarPubKey;
            this.isAvatarHdOld = profile.isAvatarHdOld;
            this.isAvatarLdOld = profile.isAvatarLdOld;
            this.allowStranger = profile.allowStranger;
            this.needKeys = profile.needKeys;
            this.version = profile.version;
        }

        @Override
        public String toString() {
            return GsonUtils.INSTANCE.toJson(this);
        }

        public static PrivacyProfile fromString(String json) {
            if (TextUtils.isEmpty(json)) {
                return new PrivacyProfile();
            }
            try{
                return GsonUtils.INSTANCE.fromJson(json, new TypeToken<PrivacyProfile>(){}.getType());
            }catch (Exception ex) {
                return new PrivacyProfile();
            }
        }
    }
}

