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
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.utility.Base64;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

@Deprecated
public class IdentityDatabase extends Database {

    private static final String TAG = IdentityDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "identities";
    private static final String ID = "_id";
    private static final String ADDRESS = "address";
    private static final String IDENTITY_KEY = "key";
    private static final String TIMESTAMP = "timestamp";
    private static final String FIRST_USE = "first_use";
    private static final String NONBLOCKING_APPROVAL = "nonblocking_approval";
    private static final String VERIFIED = "verified";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID + " INTEGER PRIMARY KEY, " +
            ADDRESS + " TEXT UNIQUE, " +
            IDENTITY_KEY + " TEXT, " +
            FIRST_USE + " INTEGER DEFAULT 0, " +
            TIMESTAMP + " INTEGER DEFAULT 0, " +
            VERIFIED + " INTEGER DEFAULT 0, " +
            NONBLOCKING_APPROVAL + " INTEGER DEFAULT 0);";

  
    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    public enum VerifiedStatus {
        DEFAULT, VERIFIED, UNVERIFIED;

        public int toInt() {
            if (this == DEFAULT)
                return 0;
            else if (this == VERIFIED)
                return 1;
            else if (this == UNVERIFIED)
                return 2;
            else
                throw new AssertionError();
        }

        public static VerifiedStatus forState(int state) {
            if (state == 0)
                return DEFAULT;
            else if (state == 1)
                return VERIFIED;
            else if (state == 2)
                return UNVERIFIED;
            else
                throw new AssertionError("No such state: " + state);
        }
    }

    IdentityDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public AccountContext getAccountContext() {
        return accountContext;
    }

    public Cursor getIdentities() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        return database.query(TABLE_NAME, null, null, null, null, null, null);
    }

    public @Nullable
    IdentityReader readerFor(@Nullable Cursor cursor) {
        if (cursor == null)
            return null;
        return new IdentityReader(cursor);
    }

    public Optional<IdentityRecord> getIdentity(String uid) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?",
                    new String[]{uid}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return Optional.of(getIdentityRecord(cursor));
            }
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return Optional.absent();
    }

    private IdentityRecord getIdentityRecord(@NonNull Cursor cursor) throws IOException, InvalidKeyException {
        String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
        String serializedIdentity = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
        int verifiedStatus = cursor.getInt(cursor.getColumnIndexOrThrow(VERIFIED));
        boolean nonblockingApproval = cursor.getInt(cursor.getColumnIndexOrThrow(NONBLOCKING_APPROVAL)) == 1;
        boolean firstUse = cursor.getInt(cursor.getColumnIndexOrThrow(FIRST_USE)) == 1;
        IdentityKey identity = new IdentityKey(Base64.decode(serializedIdentity), 0);

        return new IdentityRecord(Address.from(accountContext, address), identity, VerifiedStatus.forState(verifiedStatus), firstUse, timestamp, nonblockingApproval);
    }

    public static class IdentityRecord {

        private final Address address;
        private final IdentityKey identitykey;
        private final VerifiedStatus verifiedStatus;
        private final boolean firstUse;
        private final long timestamp;
        private final boolean nonblockingApproval;

        private IdentityRecord(Address address,
                               IdentityKey identitykey, VerifiedStatus verifiedStatus,
                               boolean firstUse, long timestamp, boolean nonblockingApproval) {
            this.address = address;
            this.identitykey = identitykey;
            this.verifiedStatus = verifiedStatus;
            this.firstUse = firstUse;
            this.timestamp = timestamp;
            this.nonblockingApproval = nonblockingApproval;
        }

        public Address getAddress() {
            return address;
        }

        public IdentityKey getIdentityKey() {
            return identitykey;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public VerifiedStatus getVerifiedStatus() {
            return verifiedStatus;
        }

        public boolean isApprovedNonBlocking() {
            return nonblockingApproval;
        }

        public boolean isFirstUse() {
            return firstUse;
        }

        @Override
        public String toString() {
            return "{address: " + address + ", identityKey: " + identitykey + ", verifiedStatus: " + verifiedStatus + ", firstUse: " + firstUse + "}";
        }

    }

    public class IdentityReader {
        private final Cursor cursor;

        public IdentityReader(@NonNull Cursor cursor) {
            this.cursor = cursor;
        }

        public @Nullable
        IdentityRecord getNext() {
            if (cursor.moveToNext()) {
                try {
                    return getIdentityRecord(cursor);
                } catch (IOException | InvalidKeyException e) {
                    throw new AssertionError(e);
                }
            }

            return null;
        }

        public void close() {
            cursor.close();
        }
    }

}
