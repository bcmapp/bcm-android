package com.bcm.messenger.wallet.utils

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bcm.messenger.wallet.R
import com.bcm.messenger.utility.AppContextHolder

/**
 * 钱包通知管理器
 * Created by wjh on 2018/6/10
 */
class WalletNotificationManager(private val context: Context, private val channel: String) {

    companion object {
        const val CHANNEL_ACTIVATE = "wallet_activate"
        const val CHANNEL_TRANSFER = "wallet_transfer"
        const val CHANNEL_SYNC = "wallet_block_sync"

        /**
         * 钱包的基础通知ID
         */
        const val NOTIFICATION_BASE = 1000

        /**
         * 用于标识当前使用的通知数
         */
        private var mCurrentNo = 0
    }

    private var mBuilder: NotificationCompat.Builder? = null

    /**
     * 用户是否可以手动划掉通知栏
     */
    var cancelable: Boolean = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationChannel = NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_LOW)
            notificationChannel.lightColor = Color.RED
            manager?.createNotificationChannel(notificationChannel)
        }
    }

    /**
     * 基于现有的ID创建通知ID
     */
    fun createNotifyId(baseNotifyId: Int): Int {
        val newNotifyId = baseNotifyId + mCurrentNo
        mCurrentNo++
        return newNotifyId
    }

    /**
     * 展示通知
     */
    fun notify(id: Int, coinBase: String? = null, title: CharSequence, content: CharSequence, progress: Int? = null, pendingIntent: PendingIntent? = null) {
        if (mBuilder == null) {
            mBuilder = NotificationCompat.Builder(context, channel)
        }
        mBuilder?.apply {
            val logo = when (coinBase) {
                WalletSettings.BTC -> R.drawable.wallet_btc_white_icon
                WalletSettings.ETH -> R.drawable.wallet_eth_white_icon
                else -> R.mipmap.ic_launcher
            }
            this.setSmallIcon(logo)
                    .setLargeIcon(BitmapFactory.decodeResource(AppContextHolder.APP_CONTEXT.resources, logo))
                    .setTicker(title)
                    .setContentTitle(title)
                    .setContentText(content)
            if (cancelable) {
                this.setOngoing(false)
            } else {
                this.setOngoing(true)
            }
            if (progress != null) {
                this.setProgress(100, progress, false)
            } else {
                this.setProgress(0, 0, true)
            }
            this.setContentIntent(pendingIntent)
            val mNotifyMgr = context.getSystemService(IntentService.NOTIFICATION_SERVICE) as NotificationManager
            mNotifyMgr.notify(id, this.build())

        }

    }
}