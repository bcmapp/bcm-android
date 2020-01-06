package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import org.whispersystems.libsignal.InvalidMessageException;
import com.bcm.messenger.common.crypto.MasterCipher;

import java.util.LinkedList;
import java.util.List;

@Deprecated
public class DraftDatabase extends Database {

  public static final String TABLE_NAME  = "drafts";
  public  static final String ID          = "_id";
  public  static final String THREAD_ID   = "thread_id";
  public  static final String DRAFT_TYPE  = "type";
  public  static final String DRAFT_VALUE = "value";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
                                            THREAD_ID + " INTEGER, " + DRAFT_TYPE + " TEXT, " + DRAFT_VALUE + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS draft_thread_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
  };

  
  public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;
  public DraftDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
    super(context, accountContext, databaseHelper);
  }

  public List<Draft> getDrafts(MasterCipher masterCipher, long threadId) {
    SQLiteDatabase db   = databaseHelper.getReadableDatabase();
    List<Draft> results = new LinkedList<Draft>();
    Cursor cursor       = null;

    try {
      cursor = db.query(TABLE_NAME, null, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        try {
            if (masterCipher == null) {
                results.add(new Draft(cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_TYPE)), cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_VALUE))));
            } else {
                String encryptedType = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_TYPE));
                String encryptedValue = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_VALUE));

                results.add(new Draft(masterCipher.decryptBody(encryptedType),
                        masterCipher.decryptBody(encryptedValue)));
            }

        } catch (InvalidMessageException ime) {
          Log.w("DraftDatabase", ime);
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static class Draft {
    public static final String TEXT     = "text";
    public static final String IMAGE    = "image";
    public static final String VIDEO    = "video";
    public static final String AUDIO    = "audio";
    public static final String LOCATION = "location";

    private final String type;
    private final String value;

    public Draft(String type, String value) {
      this.type  = type;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }

    public String getSnippet(Context context) {
      switch (type) {
      case TEXT:     return value;
      case IMAGE:    return context.getString(R.string.common_draft_image_snippet);
      case VIDEO:    return context.getString(R.string.common_draft_video_snippet);
      case AUDIO:    return context.getString(R.string.common_draft_audio_snippet);
      case LOCATION: return context.getString(R.string.common_draft_location_snippet);
      default:       return null;
      }
    }
  }
}
