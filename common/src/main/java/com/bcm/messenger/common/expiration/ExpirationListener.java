package com.bcm.messenger.common.expiration;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ExpirationListener extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
      ExpirationManager.INSTANCE.scheduler().checkSchedule();
  }

  public static void setAlarm(Context context, long waitTimeMillis) {
    Intent        intent        = new Intent(context, ExpirationListener.class);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    AlarmManager  alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

    alarmManager.cancel(pendingIntent);
    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + waitTimeMillis, pendingIntent);
  }
}
