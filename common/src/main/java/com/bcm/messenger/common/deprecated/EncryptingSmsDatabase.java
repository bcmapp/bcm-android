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
import android.database.sqlite.SQLiteOpenHelper;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.utility.LRUCache;
import org.whispersystems.libsignal.InvalidMessageException;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;

import com.bcm.messenger.utility.logger.ALog;

@Deprecated
public class EncryptingSmsDatabase extends SmsDatabase {

    private final PlaintextCache plaintextCache = new PlaintextCache();

    public EncryptingSmsDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public SmsMessageRecord getMessage(MasterSecret masterSecret, long messageId) throws NoSuchMessageException {
        Cursor cursor = super.getMessage(messageId);
        DecryptingReader reader = new DecryptingReader(masterSecret, cursor);
        SmsMessageRecord record = reader.getNext();

        reader.close();

        if (record == null) throw new NoSuchMessageException("No message for ID: " + messageId);
        else return record;
    }

    public Reader readerFor(MasterSecret masterSecret, Cursor cursor) {
        return new DecryptingReader(masterSecret, cursor);
    }

    public class DecryptingReader extends SmsDatabase.Reader {

        private final MasterCipher masterCipher;

        public DecryptingReader(MasterSecret masterSecret, Cursor cursor) {
            super(cursor);
            this.masterCipher = new MasterCipher(masterSecret);
        }

        @Override
        protected DisplayRecord.Body getBody(Cursor cursor) {
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
            String ciphertext = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

            if (ciphertext == null) {
                return new DisplayRecord.Body("", true);
            }

            try {
                if (SmsDatabase.Types.isSymmetricEncryption(type)) {
                    String plaintext = plaintextCache.get(ciphertext);

                    if (plaintext != null)
                        return new DisplayRecord.Body(plaintext, true);

                    plaintext = masterCipher.decryptBody(ciphertext);

                    ALog.i("EncryptingSmsDatabase", "cipher message type " + type);

                    plaintextCache.put(ciphertext, plaintext);
                    return new DisplayRecord.Body(plaintext, true);
                } else {
                    ALog.i("EncryptingSmsDatabase", "plain message type " + type);
                    return new DisplayRecord.Body(ciphertext, true);
                }
            } catch (InvalidMessageException e) {
                ALog.e("EncryptingSmsDatabase", e);
                return new DisplayRecord.Body(context.getString(R.string.EncryptingSmsDatabase_error_decrypting_message), true);
            }
        }
    }

    private static class PlaintextCache {
        private static final int MAX_CACHE_SIZE = 2000;
        private static final Map<String, SoftReference<String>> decryptedBodyCache =
                Collections.synchronizedMap(new LRUCache<String, SoftReference<String>>(MAX_CACHE_SIZE));

        public void put(String ciphertext, String plaintext) {
            decryptedBodyCache.put(ciphertext, new SoftReference<String>(plaintext));
        }

        public String get(String ciphertext) {
            SoftReference<String> plaintextReference = decryptedBodyCache.get(ciphertext);

            if (plaintextReference != null) {
                String plaintext = plaintextReference.get();

                if (plaintext != null) {
                    return plaintext;
                }
            }

            return null;
        }
    }
}
