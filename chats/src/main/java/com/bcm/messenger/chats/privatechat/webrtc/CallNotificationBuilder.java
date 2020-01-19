package com.bcm.messenger.chats.privatechat.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.bcm.messenger.chats.privatechat.ChatRtcCallActivity;
import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.AmeNotification;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.logger.ALog;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 */

public class CallNotificationBuilder {

    private static final String TAG = "CallNotificationBuilder";
    public static final int WEBRTC_NOTIFICATION = 313388;
    public static final int TYPE_CALL_DEFAULT = 0;
    public static final int TYPE_INCOMING_RINGING = 1;
    public static final int TYPE_OUTGOING_RINGING = 2;
    public static final int TYPE_ESTABLISHED = 3;
    public static final int REQUEST_SERVICE_TOUCH = 0;
    public static final int REQUEST_SERVICE_ACTION = 1;

    public static final String CALL_CHANNEL_DEFAULT = "CALL_UNKNOWN";

    public static Notification getCallDefaultNotification(Context context) {

        NotificationCompat.Builder builder = AmeNotification.INSTANCE.getCustomNotificationBuilder(context, CALL_CHANNEL_DEFAULT, "CALL",
                context.getString(R.string.common_webrtc_call_default_description))
                .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.common_webrtc_call_default_description));

        return builder.build();
    }

    public static Notification getCallInProgressNotification(Context context, AccountContext accountContext, int type, @NonNull Recipient recipient) {
        Intent contentIntent = new Intent(context, ChatRtcCallActivity.class);
        contentIntent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext);
//        contentIntent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, REQUEST_SERVICE_TOUCH, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = AmeNotification.INSTANCE.getCustomNotificationBuilder(context, CALL_CHANNEL_DEFAULT, "CALL",
                context.getString(R.string.common_webrtc_call_channel_description))
                .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setContentTitle(recipient.getName());

        ALog.i(TAG, "createCallInProgressNotification, type: " + type);
        if (type == TYPE_INCOMING_RINGING) {
            builder.setContentText(context.getString(R.string.common_webrtc_incoming_call));
            builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_DENY_CALL, R.drawable.ic_close_grey600_32dp, R.string.common_webrtc_deny_call));
            builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_ANSWER_CALL, R.drawable.ic_phone_grey600_32dp, R.string.common_webrtc_answer_call));
        } else if (type == TYPE_OUTGOING_RINGING) {
            builder.setContentText(context.getString(R.string.common_webrtc_establishing_call));
            builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.common_webrtc_cancel_call));
        } else {
            builder.setContentText(context.getString(R.string.common_webrtc_call_in_progress));
            builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.common_webrtc_end_call));
        }

        return builder.build();
    }


    private static NotificationCompat.Action getServiceNotificationAction(Context context, String action, int iconResId, int titleResId) {
        Intent intent = new Intent(context, WebRtcCallService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(context, REQUEST_SERVICE_ACTION, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
    }
}
