package com.bcm.messenger.common.expiration;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;

public class ExpirationListener extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AccountContext accountContext = intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT);
        if (accountContext != null) {
            ExpirationManager.INSTANCE.scheduler(accountContext).checkSchedule();
        }
    }

    public static void setAlarm(Context context, AccountContext accountContext, long waitTimeMillis) {
        Intent intent = new Intent(context, ExpirationListener.class);
        intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmManager.cancel(pendingIntent);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + waitTimeMillis, pendingIntent);
    }
}
