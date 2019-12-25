package com.bcm.messenger.common.service;


import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;

import java.util.concurrent.TimeUnit;

public class RotateSignedPreKeyListener extends PersistentAlarmManagerListener {

    private static final long INTERVAL = TimeUnit.DAYS.toMillis(2);

    @Override
    protected long getNextScheduledExecutionTime(Context context) {
        return AMELogin.INSTANCE.getSignedPreKeyRotationTime();
    }

    @Override
    protected long onAlarm(Context context, long scheduledTime) {
        if (scheduledTime != 0 && TextSecurePreferences.isPushRegistered(context)) {
            AmeModuleCenter.INSTANCE.login().rotateSignedPrekey();
        }
        long nextTime = System.currentTimeMillis() + INTERVAL;
        AMELogin.INSTANCE.setSignedPreKeyRotationTime(nextTime);

        return nextTime;
    }

    public static void schedule(Context context) {
        new RotateSignedPreKeyListener().onReceive(context, new Intent());
    }
}
