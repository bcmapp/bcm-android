package com.bcm.messenger.common.database;

import android.content.ContentValues;
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
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.crypto.MasterSecretUnion;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.OutgoingLocationMessage;
import com.bcm.messenger.common.sms.OutgoingTextMessage;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.common.utils.IdentityUtil;
import com.bcm.messenger.common.utils.RxBus;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.spongycastle.util.encoders.DecoderException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public RecipientDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public AccountContext getAccountContext() {
        return AMELogin.INSTANCE.getMajorContext();
    }
   
    public void setGroupNotification(@NonNull Cursor cursor) {
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(URI_GROUP));
    }

    public void setFriendNotification(@NonNull Cursor cursor) {
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(URI_FRIEND));
    }

    public void setStrangerNotification(@NonNull Cursor cursor) {
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(URI_STRANGER));
    }

    public Cursor getBlocked() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();

        Cursor cursor = database.query(TABLE_NAME, new String[]{ID, ADDRESS}, BLOCK + " = 1",
                null, null, null, null, null);
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(RECIPIENT_PREFERENCES_URI));

        return cursor;
    }

    public BlockedReader readerForBlocked(Cursor cursor) {
        return new BlockedReader(context, cursor);
    }

    @Nullable
    public RecipientSettings getRecipientSettings(@NonNull Address address) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();

        try (Cursor cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?", new String[]{address.serialize()}, null, null, null)) {

            if (cursor != null && cursor.moveToNext()) {
                return getRecipientSettings(cursor);
            }
            return null;
        }
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
    public static RecipientSettings getRecipientSettings(@NonNull Cursor cursor) {

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
                    cursor.getInt(cursor.getColumnIndex(FRIEND_FLAG)) == 1, featureSupport);

        }catch (Exception ex) {
            return null;
        }
    }

    public BulkOperationsHandle resetAllDisplayNames() {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        return new BulkOperationsHandle(database);
    }

    public void setColor(@NonNull Recipient recipient, @NonNull MaterialColor color) {
        ContentValues values = new ContentValues();
        values.put(COLOR, color.serialize());
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setColor(color);
    }

    public void setSupportFeatures(@NotNull Recipient recipient, @Nullable String supportFeatures) {
        ContentValues values = new ContentValues();
        values.put(SUPPORT_FEATURES, supportFeatures);
        updateOrInsert(recipient.getAddress(), values);
        BcmFeatureSupport featureSupport = null;
        if (!TextUtils.isEmpty(supportFeatures)) {
            try {
                featureSupport = new BcmFeatureSupport(supportFeatures);
            } catch (DecoderException exception){
                exception.printStackTrace();
            }
        }
        recipient.resolve().setFeatureSupport(featureSupport);
    }

    public void setDefaultSubscriptionId(@NonNull Recipient recipient, int defaultSubscriptionId) {
        ContentValues values = new ContentValues();
        values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setDefaultSubscriptionId(Optional.of(defaultSubscriptionId));
    }

    public void setBlocked(@NonNull Recipient recipient, boolean blocked) {
        ContentValues values = new ContentValues();
        values.put(BLOCK, blocked ? 1 : 0);
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setBlocked(blocked);

        try {
        
            createBlockMessage(recipient, blocked);
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    
    private void createBlockMessage(@NonNull Recipient recipient, boolean blocked) {

        if (recipient.isLogin()) {
            return;
        }
        String blockMessage;
        if (!blocked) {
            blockMessage = new AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO,
                    new AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_UNBLOCK, recipient.getAddress().serialize(), new ArrayList(), "")).toString();
        } else {
            blockMessage = new AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO,
                    new AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_BLOCK, recipient.getAddress().serialize(), new ArrayList(), "")).toString();
        }

        long threadId = DatabaseFactory.getThreadDatabase(AppContextHolder.APP_CONTEXT).getThreadIdFor(recipient);
        OutgoingLocationMessage message = new OutgoingLocationMessage(recipient, blockMessage, (recipient.getExpireMessages() * 1000));
        long messageId = DatabaseFactory.getEncryptingSmsDatabase(AppContextHolder.APP_CONTEXT)
                .insertMessageOutbox(new MasterSecretUnion(BCMEncryptUtils.INSTANCE.getMasterSecret(AMELogin.INSTANCE.getMajorContext())),
                threadId, message, false, AmeTimeUtil.INSTANCE.getMessageSendTime(), null);

        DatabaseFactory.getEncryptingSmsDatabase(AppContextHolder.APP_CONTEXT).markAsSent(messageId, true);

        
        RxBus.INSTANCE.post(recipient.getAddress().serialize(), threadId);

    }

    public void setRingtone(@NonNull Recipient recipient, @Nullable Uri notification) {
        ContentValues values = new ContentValues();
        values.put(NOTIFICATION, notification == null ? null : notification.toString());
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setRingtone(notification);
    }

    public void setVibrate(@NonNull Recipient recipient, @NonNull VibrateState enabled) {
        ContentValues values = new ContentValues();
        values.put(VIBRATE, enabled.getId());
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setVibrate(enabled);
    }

    public void setMuted(@NonNull Recipient recipient, long until) {
        ContentValues values = new ContentValues();
        values.put(MUTE_UNTIL, until);
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setMuted(until);
    }

    public void setSeenInviteReminder(@NonNull Recipient recipient, boolean seen) {
        ContentValues values = new ContentValues(1);
        values.put(SEEN_INVITE_REMINDER, seen ? 1 : 0);
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setHasSeenInviteReminder(seen);
        recipient.resolve().notifyListeners();
    }

    public void setExpireMessages(@NonNull Recipient recipient, int expiration) {

        ContentValues values = new ContentValues(1);
        values.put(EXPIRE_MESSAGES, expiration);
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setExpireMessages(expiration);
        recipient.resolve().notifyListeners();
    }

    public void setProfileKey(@NonNull Recipient recipient, @Nullable byte[] profileKey) {
        ContentValues values = new ContentValues(1);
        values.put(PROFILE_KEY, profileKey == null ? null : Base64.encodeBytes(profileKey));
        updateOrInsert(recipient.getAddress(), values);
        recipient.resolve().setProfileKey(profileKey);
        recipient.resolve().notifyListeners();
    }

    public void setProfileName(@NonNull Recipient recipient, @Nullable String profileName) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PROFILE_NAME, profileName);
        updateOrInsert(recipient.getAddress(), contentValues);
        recipient.resolve().setProfileName(profileName);
        recipient.resolve().notifyListeners();
    }

    public void setProfileAvatar(@NonNull Recipient recipient, @Nullable String profileAvatar) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PROFILE_AVATAR, profileAvatar);
        updateOrInsert(recipient.getAddress(), contentValues);
        recipient.resolve().setProfileAvatar(profileAvatar);
        recipient.resolve().notifyListeners();
    }

    public void setProfileSharing(@NonNull Recipient recipient, boolean enabled) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PROFILE_SHARING, enabled ? 1 : 0);
        updateOrInsert(recipient.getAddress(), contentValues);
        recipient.resolve().setProfileSharing(enabled);
    }

 
    public void setProfile(@NonNull Recipient recipient, @Nullable byte[] profileKey, @Nullable String profileName, @Nullable String profileAvatar, @NonNull RegisteredState registeredState) {
        ContentValues params = new ContentValues();
        params.put(PROFILE_KEY, profileKey == null ? null : Base64.encodeBytes(profileKey));
        params.put(PROFILE_NAME, profileName);
        params.put(PROFILE_AVATAR, profileAvatar);
        params.put(REGISTERED, registeredState.getId());
        updateOrInsert(recipient.getAddress(), params);
        setIdentityKey(recipient.getAddress(), recipient.getIdentityKey());
        recipient.resolve().setProfile(profileKey, profileName, profileAvatar, registeredState);
    }


    public void updateProfileName(@NonNull Recipient recipient, String name) {
        if (null == name) {
            return;
        }

        ContentValues params = new ContentValues();
        params.put(PROFILE_NAME, name);
        updateOrInsert(recipient.getAddress(), params);
        setIdentityKey(recipient.getAddress(), recipient.getIdentityKey());
        recipient.resolve().updateName(name);
    }

  
    public void setLocalProfile(@NonNull Recipient recipient, @Nullable String profileName, @Nullable String profileAvatar) {
        ContentValues params = new ContentValues(2);
        params.put(LOCAL_NAME, profileName);
        params.put(LOCAL_AVATAR, profileAvatar);
        updateOrInsert(recipient.getAddress(), params);
        recipient.resolve().setLocalProfile(profileName, profileAvatar);
        notifyFriendChanged();
    }

   
    private void setIdentityKey(Address address, String identityKeyValue) {
        try {
            if (TextUtils.isEmpty(identityKeyValue)) {
                Log.w(TAG, "Identity key is missing on profile!");
                return;
            }

            IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyValue), 0);
            if (!DatabaseFactory.getIdentityDatabase(context)
                    .getIdentity(address)
                    .isPresent()) {
                Log.w(TAG, "identityKey first use...");
                return;
            }

            Log.d(TAG, "identityKey save");
            IdentityUtil.saveIdentity(context, getAccountContext(), address.serialize(), identityKey, true);

        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

  
    @NonNull
    public Cursor getFriendsFromContact() {
        return getRecipients(2);
    }

    @NonNull
    public Cursor getContactsFromOneSide() {
        return getRecipients(5);
    }


    @NonNull
    public Cursor getAllBcmUser() {
        return getRecipients(3);
    }


    @NonNull
    public Cursor getBlockedUser() {
        return getRecipients(4);
    }

    public @NonNull
    Cursor getRecipients(int filter, @Nullable String... addresses) {
        String localNumber = AMELogin.INSTANCE.getMajorUid();
        String sql = "select * from " + TABLE_NAME + " where " + ADDRESS + " != '" + localNumber + "' ";
        if (addresses != null && addresses.length > 0) {
            StringBuilder selectionBuilder = new StringBuilder();
            for (int i = 0; i < addresses.length; i++) {

                if (addresses[i] != null && !addresses[i].isEmpty()) {
                    selectionBuilder.append("'");
                    selectionBuilder.append(addresses[i]);
                    selectionBuilder.append("'");
                }

                if (i < addresses.length - 1) {
                    selectionBuilder.append(",");
                }
            }
            sql += " and " + ADDRESS + " in (" + selectionBuilder.toString() + ") ";
        } else {
            sql += " and " + ADDRESS + " not like '" + GroupUtil.ENCODED_TT_GROUP_PREFIX + "%' and " + ADDRESS +
                    " not like '" + GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX + "%' and " + ADDRESS + " not like '" + GroupUtil.ENCODED_MMS_GROUP_PREFIX + "%'";
        }
        switch (filter) {
            case 1: 
                sql += " and " + RELATIONSHIP + " == " + Relationship.FRIEND.type + "";
                break;
            case 2: 
                sql += " and (" + RELATIONSHIP + " == " + Relationship.FRIEND.type +
                        " or " + RELATIONSHIP + " == " + Relationship.FOLLOW.type +
                        " or " + RELATIONSHIP + " == " + Relationship.FOLLOW_REQUEST.type +
                        " or " + RELATIONSHIP + " == " + Relationship.BREAK.type +
                        " or 1 == " + FRIEND_FLAG + ")";
                break;
            case 3: 
                sql += " and " + REGISTERED + " == " + RegisteredState.REGISTERED.id;
                break;
            case 4: 
                sql += " and " + REGISTERED + " == " + RegisteredState.REGISTERED.id + " and " + BLOCK + " = 1 ";
                break;
            case 5:
                sql += " and (" + RELATIONSHIP + " != " + Relationship.STRANGER.type +
                        " or 1 == " + FRIEND_FLAG + ")";
            default:
                break;
        }
        final SQLiteDatabase db = databaseHelper.getReadableDatabase();
        return db.rawQuery(sql, null);
    }

    public Set<Recipient> getAllRecipients() {
        final SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Set<Recipient> results = new HashSet<>();

        try (Cursor cursor = db.query(TABLE_NAME, new String[] { ADDRESS }, null, null, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                results.add(Recipient.from(Address.from(AMELogin.INSTANCE.getMajorContext(), cursor.getString(0)), true));
            }
        }

        return results;
    }

   
    public @NonNull
    Cursor getGroupRecipients() {
        String sql = "select * from " + TABLE_NAME + " where " + ADDRESS + " like '" + GroupUtil.ENCODED_TT_GROUP_PREFIX + "%' or " + ADDRESS +
                " like '" + GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX + "%' or " + ADDRESS + " like '" + GroupUtil.ENCODED_MMS_GROUP_PREFIX + "%'";
        final SQLiteDatabase db = databaseHelper.getReadableDatabase();
        return db.rawQuery(sql, null);
    }

    public void setRegistered(@NonNull Recipient recipient, RegisteredState registeredState) {
        Log.d(TAG, recipient.getAddress().serialize() + " setRegistered:" + registeredState);
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(REGISTERED, registeredState.getId());
        updateOrInsert(recipient.getAddress(), contentValues);
        recipient.resolve().setRegistered(registeredState);
    }

    @Deprecated
    public void setRegistered(@NonNull List<Recipient> activeRecipients,
                              @NonNull List<Recipient> inactiveRecipients) {

    }

    public List<Address> getRegistered() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        List<Address> results = new LinkedList<>();

        try (Cursor cursor = db.query(TABLE_NAME, new String[]{ADDRESS}, REGISTERED + " = ?", new String[]{"1"}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                results.add(Address.from(cursor.getString(0)));
            }
        }

        return results;
    }

   
    public void leaveGroup(long... groupIds) {
        List<String> addressList = new ArrayList<>(groupIds.length);
        for (long groupId : groupIds) {
            addressList.add(GroupUtil.addressFromGid(AMELogin.INSTANCE.getMajorContext(), groupId).serialize());
        }
        delete(addressList);
    }

    public void setPrivacyProfile(@NonNull Recipient recipient, @NonNull PrivacyProfile profile) {
        ContentValues contentValues = new ContentValues(2);
        contentValues.put(PRIVACY_PROFILE, profile.toString());
        contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());
        updateOrInsert(recipient.getAddress(), contentValues);
//        recipient.resolve().setPrivacyProfile(profile);
    }

    public void setBcmContacts(@NonNull List<RecipientSettings> settingList) {
        setBcmContacts(settingList, false);
    }

    
    public void setBcmContacts(@NonNull List<RecipientSettings> settingList, boolean notifyRecipient) {

        class UpdateSettings {
            public RecipientSettings settings;
            public boolean createFriendMsg;
            public boolean createBlockMsg;
        }

        if (settingList.isEmpty()) {
            return;
        }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        Map<String, UpdateSettings> updateMap = new HashMap<>(settingList.size());
        String sql = "select * from " + TABLE_NAME;
        UpdateSettings updateSettings;
        RecipientSettings s = null;
        StringBuilder selectionBuilder = new StringBuilder();
        for (int i = 0; i < settingList.size(); i++) {
            s = settingList.get(i);
            selectionBuilder.append("'");
            selectionBuilder.append(s.uid);
            selectionBuilder.append("'");

            if (i < settingList.size() - 1) {
                selectionBuilder.append(",");
            }
            ALog.d(TAG, "setUpdateMap uid: " + s.uid + ", relation: " + s.getRelationship());
            updateSettings = new UpdateSettings();
            updateSettings.settings = s;
            updateSettings.createFriendMsg = false;
            updateSettings.createBlockMsg = false;
            updateMap.put(s.uid, updateSettings);
        }
        sql += " WHERE " + ADDRESS + " in (" + selectionBuilder.toString() + ") ";
        Cursor cursor = db.rawQuery(sql, null);
        RecipientSettings ns = null;
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ns = getRecipientSettings(cursor);
                if (ns != null) {
                    updateSettings = updateMap.get(ns.uid);
                    s = updateSettings.settings;
                    if (s != null) {
                        ns.setRegistered(RegisteredState.REGISTERED);
                        if (TextUtils.isEmpty(ns.signalProfileName)) {
                            ns.setSignalProfile(ns.getProfileKey(), s.getProfileName(), ns.getProfileAvatar());
                        }
                        if (s.getRelationship() == Relationship.STRANGER) {
                            ns.setLocalProfile("", "");

                            if (!TextUtils.equals(ns.uid, AMELogin.INSTANCE.getMajorUid())) {
                                PrivacyProfile privacyProfile = ns.getPrivacyProfile();
                                privacyProfile.setName("");
                                privacyProfile.setNameKey("");
                                privacyProfile.setAvatarHDUri("");
                                privacyProfile.setAvatarLDUri("");
                                privacyProfile.setAvatarKey("");
                                if (!TextUtils.isEmpty(privacyProfile.encryptedName) || !TextUtils.isEmpty(privacyProfile.encryptedAvatarLD) || !TextUtils.isEmpty(privacyProfile.encryptedAvatarHD)) {
                                    privacyProfile.needKeys = true;
                                }
                                ns.setPrivacyProfile(privacyProfile);
                                if (TextUtils.isEmpty(s.getProfileName())) {
                                    ns.setSignalProfile(null, "", "");
                                }else {
                                    ns.setSignalProfile(null, ns.getProfileName(), "");
                                }
                            }

                            if (ns.relationship == Relationship.FRIEND) {
                                updateSettings.createBlockMsg = true;
                            }

                        }else if (s.getRelationship() == Relationship.FRIEND) {
                            
                            if (ns.getRelationship() == Relationship.REQUEST) {
                                updateSettings.createFriendMsg = true;
                            }
                            if (ns.blocked) {
                                updateSettings.createBlockMsg = true;
                            }
                        }
                        ns.setRelationship(s.getRelationship());
                    }
                    updateSettings.settings = ns;
                    ns.setFriendFlag(false);
                }
            }
            cursor.close();
        }

        ContentValues params;
        try {
            db.beginTransaction();

            for (Map.Entry<String, UpdateSettings> entry : updateMap.entrySet()) {
                updateSettings = entry.getValue();
                ns = updateSettings.settings;
                ALog.d(TAG, "setDatabase uid: " + ns.uid + ", relation: " + ns.getRelationship());
                params = new ContentValues();
                params.put(FRIEND_FLAG, ns.isFriendFlag() ? 1: 0);
                params.put(RELATIONSHIP, ns.getRelationship().type);
                params.put(REGISTERED, ns.getRegistered().id);
                params.put(PROFILE_NAME, ns.getProfileName());
                params.put(PROFILE_AVATAR, ns.getProfileAvatar());
                params.put(LOCAL_NAME, ns.getLocalName());
                params.put(LOCAL_AVATAR, ns.getLocalAvatar());
                params.put(PRIVACY_PROFILE, ns.getPrivacyProfile().toString());
                Address address = Address.from(ns.uid);
                updateOrInsert(db, address, params);

            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();

            if (notifyRecipient) {
                ALog.d(TAG, "updateBcmContacts notifyRecipient");
                Recipient recipient;
                for (Map.Entry<String, UpdateSettings> entry : updateMap.entrySet()) {
                    updateSettings = entry.getValue();
                    ns = updateSettings.settings;
                    recipient = Recipient.from(Address.from(ns.uid), true);
//                    recipient = Recipient.fromSnapshot(context, Address.fromSerialized(ns.uid), ns);
                    if (updateSettings.createFriendMsg) {
                        
                        if (ns.getRelationship() == Relationship.FRIEND) {
                            createFriendMessage(recipient, true);
                        } else if (ns.getRelationship() == Relationship.STRANGER) {
                            createFriendMessage(recipient, false);
                        } else {
                            ALog.d(TAG, "notifyRecipient uid: " + ns.uid + ", relation: " + ns.getRelationship());
                        }
                    }
                    
                    if (updateSettings.createBlockMsg) {
                        if (ns.getRelationship() == Relationship.FRIEND) {
                            setBlocked(recipient, false);
                        } else if (ns.getRelationship() == Relationship.STRANGER) {
                            setBlocked(recipient, true);
                        }
                    }
                }
                notifyFriendChanged();
            }
        }
    }

    /**
     * @param newContactList
     */
    public void updateLocalContacts(@NonNull List<RecipientSettings> newContactList) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = null;
        HashSet<String> removeSet = new HashSet<>();
        List<RecipientSettings> updateList = new ArrayList<>();
        RecipientSettings find = null;
        try {
            
            String localNumber = AMELogin.INSTANCE.getMajorUid();
            String sql = "select * from " + TABLE_NAME + " where " + ADDRESS + " <> '" + localNumber + "' ";
            sql += " and " + ADDRESS + " not like '" + GroupUtil.ENCODED_TT_GROUP_PREFIX + "%' and " + ADDRESS +
                    " not like '" + GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX + "%' and " + ADDRESS + " not like '" + GroupUtil.ENCODED_MMS_GROUP_PREFIX + "%'";
            sql += " and (1 == " + FRIEND_FLAG + " or " + SYSTEM_CONTACT_ID + " is not null and " + SYSTEM_CONTACT_ID + " != '') ";

            cursor = database.rawQuery(sql, null);
            //            cursor = database.query(TABLE_NAME, null, null, null, null, null, null);

            while(cursor.moveToNext()) {
                find = null;
                RecipientSettings setting = getRecipientSettings(cursor);
                if (setting == null) {
                    continue;
                }
                
                int index = newContactList.indexOf(setting);
                if(index >= 0) {
                    find = newContactList.get(index);
                }
                if(find == null) {
                    if(setting.isFriendFlag()) {
                        setting.setSystemProfile("", "", "");
                        setting.setPhone("");
                        updateList.add(setting);
                    }else {
                        removeSet.add(setting.uid);
                    }
                }else {
                    setting.setSystemProfile(find.systemContactId, find.systemName, find.systemAvatar);
                    setting.setPhone(find.phone);
                    setting.setRegistered(find.registered);
                    updateList.add(setting);
                }
            }

            
            for(RecipientSettings ns : newContactList) {
                if(!updateList.contains(ns)) {
                    updateList.add(ns);
                }

                if (ns.phone != null && !ns.phone.isEmpty()) {
                    removeSet.add(ns.phone);
                }
            }

            if (!removeSet.isEmpty()) {
                List<String> removeList = new ArrayList<>();
                removeList.addAll(removeSet);
                delete(removeList);
            }

            setLocalContacts(updateList);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

 
    public void savePhoneContacts(List<RecipientSettings> phoneContactList) {
        ALog.i(TAG, "save phone Contacts list");
        if (null != phoneContactList && !phoneContactList.isEmpty()) {
            ALog.i(TAG, "save phone Contacts list size:" + phoneContactList.size());
            setLocalContacts(phoneContactList);
        }
    }

    private void setLocalContacts(@NonNull List<RecipientSettings> settingList) {
        ContentValues params;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        try {
            db.beginTransaction();
            for (RecipientSettings setting : settingList) {

                String localId = setting.getSystemContactId();
                String systemName = setting.getSystemName();
                String systemAvatar = setting.getSystemAvatar();
                String phone = setting.getPhone();
                RegisteredState registeredState = setting.getRegistered();

                params = new ContentValues();
                params.put(PROFILE_SHARING, 1);
                if(localId != null) {
                    params.put(SYSTEM_CONTACT_ID, localId);
                }
                if (systemName != null) {
                    params.put(SYSTEM_NAME, systemName);
                }
                if (systemAvatar != null) {
                    params.put(SYSTEM_AVATAR, systemAvatar);
                }
                if(phone != null) {
                    params.put(PHONE, phone);
                }
                params.put(REGISTERED, registeredState.getId());

                Address address = Address.from(AMELogin.INSTANCE.getMajorContext(), setting.uid);
                updateOrInsert(db, address, params);

                
//                Recipient.clearCache(AppContextHolder.APP_CONTEXT, address);
            }
            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    
    private void createFriendMessage(@NonNull Recipient recipient, boolean isFriend) {

        
        if (recipient.isLogin()) {
            ALog.d(TAG, "address: " + recipient.getAddress().serialize() + " set major friend: " + isFriend);
            ALog.i(TAG, "set major friend: " + isFriend);
        }else {
            ALog.d(TAG, "setFriend uid: " + recipient.getAddress().serialize() + ", isFriend: " + isFriend);
            ALog.i(TAG, "setFriend isFriend: " + isFriend);
            
            final AmeGroupMessage.FriendContent content = new AmeGroupMessage.FriendContent(isFriend ? AmeGroupMessage.FriendContent.ADD : AmeGroupMessage.FriendContent.DELETE,
                    recipient.getAddress().serialize());
            final AmeGroupMessage messageBody = new AmeGroupMessage<AmeGroupMessage.FriendContent>(AmeGroupMessage.FRIEND, content);

            long expiresIn = 0;
            if (recipient.getExpireMessages() > 0) {
                expiresIn = recipient.getExpireMessages() * 1000;
            }

            final OutgoingTextMessage textMessage = new OutgoingLocationMessage(recipient, messageBody.toString(), expiresIn);

            long threadId = DatabaseFactory.getThreadDatabase(AppContextHolder.APP_CONTEXT).getThreadIdFor(recipient);
            long messageId = DatabaseFactory.getEncryptingSmsDatabase(AppContextHolder.APP_CONTEXT)
                    .insertMessageOutbox(new MasterSecretUnion(BCMEncryptUtils.INSTANCE.getMasterSecret(AMELogin.INSTANCE.getMajorContext())), threadId,
                            textMessage, false, AmeTimeUtil.INSTANCE.getMessageSendTime(), null);

            DatabaseFactory.getEncryptingSmsDatabase(AppContextHolder.APP_CONTEXT).markAsSent(messageId, true);

            
            RxBus.INSTANCE.post(recipient.getAddress().serialize(), threadId);
        }
    }

    private void delete(List<String> addressList) {
        String localNumber = AMELogin.INSTANCE.getMajorUid();
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE " + ADDRESS + " <> '" + localNumber + "' ";
        if (addressList != null && addressList.size() > 0) {
            StringBuilder selectionBuilder = new StringBuilder();
            for (int i = 0; i < addressList.size(); i++) {

                if (addressList.get(i) != null && !addressList.get(i).isEmpty()) {
                    selectionBuilder.append("'");
                    selectionBuilder.append(addressList.get(i));
                    selectionBuilder.append("'");

                    Recipient.clearCache(AppContextHolder.APP_CONTEXT, Address.from(AMELogin.INSTANCE.getMajorContext(), addressList.get(i)));
                }

                if (i < addressList.size() - 1) {
                    selectionBuilder.append(",");
                }
            }
            sql += " and (" + ADDRESS + " IN (" + selectionBuilder.toString() + ")) ";
        }else {
            return;
        }

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.execSQL(sql);
    }

    private void updateOrInsert(Address address, ContentValues contentValues) {

        SQLiteDatabase database = databaseHelper.getWritableDatabase();

        database.beginTransaction();
        boolean isUpdate = updateOrInsert(database, address, contentValues);
        database.setTransactionSuccessful();
        database.endTransaction();

        if (!isUpdate) {
        
            if (address.isGroup() || address.isMmsGroup()) {
                notifyGroupChanged();
            }
        }

        context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
    }

    private boolean updateOrInsert(SQLiteDatabase database, Address address, ContentValues contentValues) {
        int updated = database.update(TABLE_NAME, contentValues, ADDRESS + " = ?", new String[]{address.serialize()});
        if (updated < 1) {
            contentValues.put(ADDRESS, address.serialize());
            database.insert(TABLE_NAME, null, contentValues);
            return false;
        }
        return true;
    }

    public class BulkOperationsHandle {

        private final SQLiteDatabase database;

        private final List<Pair<Recipient, String>> pendingDisplayNames = new LinkedList<>();

        BulkOperationsHandle(SQLiteDatabase database) {
            this.database = database;
        }

        public void setDisplayName(@NonNull Recipient recipient, @Nullable String displayName) {
            
//            ContentValues contentValues = new ContentValues(1);
//            contentValues.put(LOCAL_NAME, displayName);
//            updateOrInsert(recipient.getAddress(), contentValues);
//            pendingDisplayNames.add(new Pair<>(recipient, displayName));
        }

        public void finish() {
            database.setTransactionSuccessful();
            database.endTransaction();

//            Stream.of(pendingDisplayNames).forEach(pair -> pair.first().resolve().setLocalName(pair.second()));
            context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
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
                                 boolean friendFlag, BcmFeatureSupport featureSupport) {
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
                IdentityDatabase.IdentityRecord record = DatabaseFactory.getIdentityDatabase(AppContextHolder.APP_CONTEXT).getIdentity(Address.from(uid)).orNull();
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

