package com.bcm.messenger.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by bcm.social.01 on 2018/6/27.
 */
object AmeNotification {
    private val notificationManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var CHANNEL_CHAT = ""//
    private var CHANNEL_UPDATE = ""//
    private var CHANNEL_FRIEND = ""
    private var CHANNEL_ADHOC = ""
    private var CHANNEL_WALLET = ""//

    /**
     * 
     */
    fun getDefaultChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (CHANNEL_CHAT.isEmpty()) {
                removeOldChatChannel(context)
                CHANNEL_CHAT = context.getString(R.string.common_notification_default_id)
                val channel = NotificationChannel(CHANNEL_CHAT, context.getString(R.string.common_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = context.getString(R.string.common_notification_channel_description)
                notificationManager.createNotificationChannel(channel)
            }
        }
        return CHANNEL_CHAT
    }

    private fun getUpdateChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && CHANNEL_UPDATE.isEmpty()) {
            CHANNEL_UPDATE = context.getString(R.string.common_notification_update_id)
            val channel = NotificationChannel(CHANNEL_UPDATE, "Update", NotificationManager.IMPORTANCE_LOW)
            channel.description = "BCM update notification."
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_UPDATE
    }

    private fun getFriendChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && CHANNEL_FRIEND.isEmpty()) {
            CHANNEL_FRIEND = context.getString(R.string.common_notification_friend_id)
            val channel = NotificationChannel(CHANNEL_FRIEND, "Friend", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "BCM friend request notification."
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_FRIEND
    }

    private fun getAdHocChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && CHANNEL_ADHOC.isEmpty()) {
            CHANNEL_ADHOC = context.getString(R.string.common_notification_adhoc_id)
            val channel = NotificationChannel(CHANNEL_ADHOC, "AdHoc", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "BCM Air Chat request notification."
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_ADHOC
    }

    private fun getWalletChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && CHANNEL_WALLET.isEmpty()) {
            CHANNEL_WALLET = context.getString(R.string.common_notification_wallet_id)
            val channel = NotificationChannel(CHANNEL_WALLET, "Wallet", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "BCM Wallet request notification."
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_WALLET
    }

    private fun getCustomChannel(context: Context, chanelId: String, channelName: String, description: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(chanelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
        return chanelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun removeOldChatChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(context.packageName)
        if (channel != null) {
            manager.deleteNotificationChannel(context.packageName)
        }
    }

    /**
     */
    fun getDefaultNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getDefaultChannel(context))
    }

    fun getUpdateNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getUpdateChannel(context))
    }

    fun getFriendNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getFriendChannel(context))
    }

    fun getCustomNotificationBuilder(context: Context, chanelId: String, channelName: String, description: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getCustomChannel(context, chanelId, channelName, description))
    }

    fun getAdHocNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getAdHocChannel(context))
    }

    fun getWalletNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, getWalletChannel(context))
    }
}