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
import android.net.Uri;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.common.BuildConfig;

import java.util.Set;

@Deprecated
public abstract class Database {

    protected static final String ID_WHERE = "_id = ?";
    private static final String CONVERSATION_URI = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/thread/";
    private static final String CONVERSATION_LIST_URI = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/conversation-list";

    static final String URI_FRIEND = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/friends"; 
    static final String URI_STRANGER = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/strangers"; 
    static final String URI_GROUP = "content://"+ BuildConfig.BCM_APPLICATION_ID + "_secure/group";

    protected SQLiteOpenHelper databaseHelper;
    protected AccountContext accountContext;
    protected final Context context;

    public Database(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        this.context = context;
        this.accountContext = accountContext;
        this.databaseHelper = databaseHelper;
    }

    protected void notifyConversationListeners(Set<Long> threadIds) {
        for (long threadId : threadIds) {
            notifyConversationListeners(threadId);
        }
    }

    protected void notifyConversationListeners(long threadId) {
        context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_URI + threadId), null);
    }

    protected void notifyConversationListListeners() {
        context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_LIST_URI), null);
    }

    protected void setNotifyConversationListeners(Cursor cursor, long threadId) {
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_URI + threadId));
    }

    protected void setNotifyConversationListListeners(Cursor cursor) {
        cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_LIST_URI));
    }

    public void reset(SQLiteOpenHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

 
    public static void notifyFriendChanged() {
        AppContextHolder.APP_CONTEXT.getContentResolver().notifyChange(Uri.parse(URI_FRIEND), null);
    }


    public static void notifyStrangerChanged() {
        AppContextHolder.APP_CONTEXT.getContentResolver().notifyChange(Uri.parse(URI_STRANGER), null);
    }

   
    public static void notifyGroupChanged() {
        AppContextHolder.APP_CONTEXT.getContentResolver().notifyChange(Uri.parse(URI_GROUP), null);
    }
}
