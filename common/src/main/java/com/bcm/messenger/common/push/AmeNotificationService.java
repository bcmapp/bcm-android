package com.bcm.messenger.common.push;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.BuildConfig;
import com.bcm.messenger.common.utils.AmePushProcess;
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.common.utils.BcmFileUtils;
import com.bcm.messenger.common.utils.BcmHash;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.route.api.BcmRouter;
import com.google.gson.Gson;

import java.io.File;
import java.io.Serializable;

/**
 * Created by bcm.social.01 on 2018/6/28.
 */
public class AmeNotificationService extends Service {

    protected String TAG = "AmeNotificationService";
    private Handler handler;
    public final static int ACTION_CHAT = 0;
    public final static int ACTION_GROUP = 1;
    public final static int ACTION_HOME = 2;
    public final static int ACTION_INSTALL = 3;
    public final static int SYSTEM_WEB_URL = 4;
    public final static int ACTION_FRIEND_REQ = 5;
    public final static int ACTION_ADHOC = 6;

    public final static String ACTION = "PUSH_ACTION";
    public final static String ACTION_DATA = "PUSH_ACTION_DATA";
    public final static String ACTION_CONTEXT = "PUSH_ACTION_CONTEXT";

    public AmeNotificationService() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new NotificationServiceBinder();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        ALog.i(TAG, "onStartCommand");
        if (null != intent) {
            handler.post(() -> {
                try {
                    int action = intent.getIntExtra(ACTION, -1);
                    switch (action) {
                        case ACTION_CHAT:
                            toChat(intent);
                            break;
                        case ACTION_GROUP:
                            toGroup(intent);
                            break;
                        case ACTION_HOME:
                            toHome();
                            break;
                        case ACTION_INSTALL:
                            toInstall(intent);
                            break;
                        case SYSTEM_WEB_URL:
                            toWeb(intent);
                            break;
                        case ACTION_FRIEND_REQ:
                            toFriendRequest(intent);
                            break;
                        case ACTION_ADHOC:
                            toAdHoc(intent);
                            break;
                        default:
                            break;
                    }
                } catch (Throwable e) {
                    ALog.e(TAG, "onStartCommand", e);
                }

                stopSelf();
            });

        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void toHome() {
        BcmRouter.getInstance()
                .get(ARouterConstants.Activity.APP_LAUNCH_PATH)
                .navigation();
    }

    private void toChat(Intent intent) {
        AmePushProcess.ChatNotifyData data = intent.getParcelableExtra(ACTION_DATA);
        AccountContext accountContext = getAccountContext(intent);
        if (data != null && data.getUid() != null) {
            long targetHash = 0L;
            if (accountContext != null) {
                targetHash = BcmHash.hash(accountContext.getUid().getBytes());
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_LAUNCH_PATH)
                    .putString("bcmdata", new Gson().toJson(new AmePushProcess.BcmNotify(AmePushProcess.CHAT_NOTIFY, targetHash, data, null, null, null, null)))
                    .navigation();

        } else {
            ALog.e(TAG, "chat - unknown push data");
        }

    }

    private void toGroup(Intent intent){
        AmePushProcess.GroupNotifyData data = intent.getParcelableExtra(ACTION_DATA);
        AccountContext accountContext = getAccountContext(intent);
        if (data != null && data.getGid() != null && data.getMid() != null) {
            long targetHash = 0L;
            if (accountContext != null) {
                targetHash = BcmHash.hash(accountContext.getUid().getBytes());
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_LAUNCH_PATH)
                    .putString("bcmdata", new Gson().toJson(new AmePushProcess.BcmNotify(AmePushProcess.GROUP_NOTIFY, targetHash,null, data, null, null, null)))
                    .navigation();
        } else {
            ALog.e(TAG, "group - unknown push data");
        }
    }

    private AccountContext getAccountContext(Intent intent) {
        Serializable context = intent.getSerializableExtra(ACTION_CONTEXT);
        if (context instanceof AccountContext) {
            return (AccountContext) context;
        }
        return null;
    }

    private void toInstall(Intent intent) {
        String filePath = intent.getStringExtra(ACTION_DATA);
        if (BcmFileUtils.INSTANCE.isExist(filePath)) {
            if (AppUtil.INSTANCE.checkInstallPermission(AppContextHolder.APP_CONTEXT)) {
                Intent intent2 = new Intent(Intent.ACTION_VIEW);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri uri = FileProvider.getUriForFile(AppContextHolder.APP_CONTEXT, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", new File(filePath));
                    intent2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent2.setDataAndType(uri, "application/vnd.android.package-archive");
                } else {
                    intent2.setDataAndType(BcmFileUtils.INSTANCE.getFileUri(filePath), "application/vnd.android.package-archive");
                }
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                AppContextHolder.APP_CONTEXT.startActivity(intent2);
            } else {
                Uri packageUri = Uri.parse("package:" + getPackageName());
                Intent intent2 = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri);
                intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent2);
            }
        }
    }

    private void toWeb(Intent intent) {
        AmePushProcess.SystemNotifyData data = intent.getParcelableExtra(ACTION_DATA);
        if (data != null && data.getType().equals(AmePushProcess.SystemNotifyData.TYPE_ALERT_WEB)) {
            AmePushProcess.SystemNotifyData.WebAlertData urlData = new Gson().fromJson(data.getContent(), AmePushProcess.SystemNotifyData.WebAlertData.class);
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.WEB)
                    .putString(ARouterConstants.PARAM.WEB_URL, urlData.getUrl())
                    .navigation();
        } else {
            ALog.e(TAG, "group - unknown push data");
        }
    }

    private void toFriendRequest(Intent intent) {
        AmePushProcess.FriendNotifyData data = intent.getParcelableExtra(ACTION_DATA);
        if (data == null) {
            data = new AmePushProcess.FriendNotifyData("", 1, "");
        }
        AccountContext accountContext = getAccountContext(intent);
        long targetHash = 0L;
        if (accountContext != null) {
            targetHash = BcmHash.hash(accountContext.getUid().getBytes());
        }
        BcmRouter.getInstance()
                .get(ARouterConstants.Activity.APP_LAUNCH_PATH)
                .putString("bcmdata", new Gson().toJson(new AmePushProcess.BcmNotify(AmePushProcess.FRIEND_NOTIFY, targetHash,null, null, data, null, null)))
                .navigation();
    }

    private void toAdHoc(Intent intent) {
        AmePushProcess.AdHocNotifyData data = intent.getParcelableExtra(ACTION_DATA);
        AccountContext accountContext = getAccountContext(intent);
        if (data != null && !TextUtils.isEmpty(data.getSession())) {
            long targetHash = 0L;
            if (accountContext != null) {
                targetHash = BcmHash.hash(accountContext.getUid().getBytes());
            }
            BcmRouter.getInstance()
                    .get(ARouterConstants.Activity.APP_LAUNCH_PATH)
                    .putString("bcmdata", new Gson().toJson(new AmePushProcess.BcmNotify(AmePushProcess.ADHOC_NOTIFY, targetHash,null, null, null,  data, null)))
                    .navigation();
        }else {
            ALog.w(TAG, "adhoc -- unknown push data");
        }
    }

    public class NotificationServiceBinder extends Binder {
        public AmeNotificationService getService() {
            return AmeNotificationService.this;
        }
    }


    public static PendingIntent getIntent(@Nullable AccountContext accountContext, @Nullable Parcelable data, int action, int id){
        Intent notificationIntent = new Intent();
        notificationIntent.putExtra(ACTION, action);
        if (data != null) {
            notificationIntent.putExtra(ACTION_DATA, data);
            notificationIntent.putExtra(ACTION_CONTEXT, accountContext);
        }
        notificationIntent.setClass(AppContextHolder.APP_CONTEXT, AmeNotificationService.class);

        return PendingIntent.getService(AppContextHolder.APP_CONTEXT, (1 << 24) ^ id, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static PendingIntent getIntentData(@Nullable String data, int action, int id) {
        Intent notificationIntent = new Intent();
        notificationIntent.putExtra(ACTION, action);
        notificationIntent.putExtra(ACTION_DATA, data);
        notificationIntent.setClass(AppContextHolder.APP_CONTEXT, AmeNotificationService.class);

        return PendingIntent.getService(AppContextHolder.APP_CONTEXT, (1<<24)^id, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

}
