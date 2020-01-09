package com.bcm.messenger.common.service;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.utility.logger.ALog;

import java.io.Serializable;

public abstract class PersistentAlarmManagerListener extends BroadcastReceiver {

    private static final String TAG = "PersistentAlarmManagerListener";

    protected abstract long getNextScheduledExecutionTime(Context context, AccountContext accountContext);

    protected abstract long onAlarm(Context context, AccountContext accountContext, long scheduledTime);

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.hasExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)) {
                Serializable obj = intent.getSerializableExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT);
                if (obj instanceof AccountContext) {
                    AccountContext accountContext = (AccountContext)obj;
                    if (!accountContext.isLogin()) {
                        ALog.w(TAG, "onReceive account must login");
                        return;
                    }
                    long scheduledTime = getNextScheduledExecutionTime(context, accountContext);
                    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    Intent alarmIntent = new Intent(context, getClass());

                    alarmIntent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);

                    if (System.currentTimeMillis() >= scheduledTime) {
                        scheduledTime = onAlarm(context, accountContext, scheduledTime);
                    }

                    ALog.w(TAG, getClass() + " scheduling for: " + scheduledTime);

                    alarmManager.cancel(pendingIntent);
                    if (accountContext.isLogin()) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent);
                    }
                }
            }
        } catch (Throwable e) {
            ALog.e(TAG, "onReceive", e);
        }
    }
}
