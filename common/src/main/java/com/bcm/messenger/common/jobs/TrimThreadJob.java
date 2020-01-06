/**
 * Copyright (C) 2014 Open Whisper Systems
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
package com.bcm.messenger.common.jobs;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.database.DatabaseFactory;
import com.bcm.messenger.common.preferences.TextSecurePreferences;

import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;

public class TrimThreadJob extends Job {

    private static final String TAG = TrimThreadJob.class.getSimpleName();

    private final Context context;
    private final long threadId;
    private final AccountContext accountContext;

    public TrimThreadJob(Context context, AccountContext accountContext, long threadId) {
        super(JobParameters.newBuilder().withGroupId(TrimThreadJob.class.getSimpleName()).create());
        this.accountContext = accountContext;
        this.context = context;
        this.threadId = threadId;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() {
        boolean trimmingEnabled = TextSecurePreferences.isThreadLengthTrimmingEnabled(accountContext);
        int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(accountContext);

        if (!trimmingEnabled)
            return;

        DatabaseFactory.getThreadDatabase(context).trimThread(threadId, threadLengthLimit);
    }

    @Override
    public boolean onShouldRetry(Exception exception) {
        return false;
    }

    @Override
    public void onCanceled() {
        Log.w(TAG, "Canceling trim attempt: " + threadId);
    }
}
