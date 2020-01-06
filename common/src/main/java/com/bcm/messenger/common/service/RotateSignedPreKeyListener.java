package com.bcm.messenger.common.service;


import android.content.Context;
import android.content.Intent;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.provider.AmeModuleCenter;

import java.util.concurrent.TimeUnit;

public class RotateSignedPreKeyListener extends PersistentAlarmManagerListener {

    private static final long INTERVAL = TimeUnit.DAYS.toMillis(2);

    @Override
    protected long getNextScheduledExecutionTime(Context context, AccountContext accountContext) {
        return accountContext.getSignedPreKeyRotationTime();
    }

    @Override
    protected long onAlarm(Context context, AccountContext accountContext, long scheduledTime) {
        if (scheduledTime != 0) {
            AmeModuleCenter.INSTANCE.login().rotateSignedPrekey(accountContext);
        }
        long nextTime = System.currentTimeMillis() + INTERVAL;
        accountContext.setSignedPreKeyRotationTime(nextTime);

        return nextTime;
    }

    public static void schedule(Context context, AccountContext accountContext) {
        Intent intent = new Intent();
        intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
        new RotateSignedPreKeyListener().onReceive(context, intent);
    }
}
