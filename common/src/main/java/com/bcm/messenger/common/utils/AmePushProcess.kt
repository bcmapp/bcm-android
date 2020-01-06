package com.bcm.messenger.common.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AmeNotification
import com.bcm.messenger.common.R
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.RecipientDatabase
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.push.AmeNotificationService
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.leolin.shortcutbadger.ShortcutBadger
import org.greenrobot.eventbus.EventBus


/**
 *
 * Created by bcm.social.01 on 2018/6/28.
 */
object AmePushProcess {

    private const val TAG = "AmePushProcess"

    const val CHAT_NOTIFY = 1
    const val GROUP_NOTIFY = 2
    const val SYSTEM_NOTIFY = 3
    const val FRIEND_NOTIFY = 4
    const val ADHOC_NOTIFY = 5

    private const val chatNotificationId = 0
    private const val friendNotificationId = 2
    private const val downloadNotificationId = 3

    private var pushInitTime = 0L
    private var offlineUnreadSet = mutableSetOf<String>()
    private var adhocOfflineUnreadSet = mutableSetOf<String>()
    private var friendReqUnreadCount = 0

    private var lastNotifyTime = 0L

    private var mChatNotification: Notification? = null
    private var mFriendReqNotification: Notification? = null

    /**
     *
     */
    class SystemNotifyData(val type: String, val id: Long, val activity_id: Long, val content: String) : Parcelable, NotGuard {

        companion object {
            const val TYPE_BANNER = "banner"
            const val TYPE_ALERT_WEB = "webalert"
            const val TYPE_ALERT_TEXT = "textalert"

            @JvmField
            val CREATOR : Parcelable.Creator<SystemNotifyData> = object : Parcelable.Creator<SystemNotifyData> {
                override fun createFromParcel(parcel: Parcel): SystemNotifyData {
                    return SystemNotifyData(parcel)
                }

                override fun newArray(size: Int): Array<SystemNotifyData?> {
                    return arrayOfNulls(size)
                }
            }
        }

        constructor(parcel: Parcel) : this(
                parcel.readString(),
                parcel.readLong(),
                parcel.readLong(),
                parcel.readString())

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            dest?.writeString(type)
            dest?.writeLong(id)
            dest?.writeLong(activity_id)
            dest?.writeString(content)
        }

        override fun describeContents(): Int {
            return 0
        }

        class AlertButton(val title: String, val action: String) : NotGuard

        class WebAlertData(val url: String, @SerializedName("starttime") val start: Long, @SerializedName("endtime") val end: Long) : NotGuard

        class TextAlertData(val title: String, val content: String, val buttons: Array<AlertButton>?,
                            @SerializedName("starttime") val start: Long, @SerializedName("endtime") val end: Long) : NotGuard

        class BannerData(val type: String, val content: String, val action: String,
                         @SerializedName("starttime") val start: Long, @SerializedName("endtime") val end: Long) : NotGuard

    }

    /**
     *
     */
    class ChatNotifyData(var uid: String?) : Parcelable, NotGuard {
        constructor(parcel: Parcel) : this(parcel.readString())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(uid)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<ChatNotifyData> {
            override fun createFromParcel(parcel: Parcel): ChatNotifyData {
                return ChatNotifyData(parcel)
            }

            override fun newArray(size: Int): Array<ChatNotifyData?> {
                return arrayOfNulls(size)
            }
        }

    }

    /**
     *
     */
    class GroupNotifyData(val mid: Long?, val gid: Long?, var isAt: Boolean? = false) : Parcelable, NotGuard {

        constructor(parcel: Parcel) : this(
                parcel.readValue(Long::class.java.classLoader) as? Long,
                parcel.readValue(Long::class.java.classLoader) as? Long,
                parcel.readValue(Boolean::class.java.classLoader) as? Boolean)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeValue(mid)
            parcel.writeValue(gid)
            parcel.writeValue(isAt)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<GroupNotifyData> {
            override fun createFromParcel(parcel: Parcel): GroupNotifyData {
                return GroupNotifyData(parcel)
            }

            override fun newArray(size: Int): Array<GroupNotifyData?> {
                return arrayOfNulls(size)
            }
        }
    }

    class FriendNotifyData(var uid: String?, var type: Int = 1, var payload: String? = null) : Parcelable, NotGuard {
        constructor(parcel: Parcel?) : this(
                parcel?.readString(),
                parcel?.readInt() ?: 1,
                parcel?.readString()
        )

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            if (dest == null) return
            dest.writeString(uid)
            dest.writeInt(type)
            dest.writeString(payload)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<FriendNotifyData> {
            override fun createFromParcel(source: Parcel?): FriendNotifyData {
                return FriendNotifyData(source)
            }

            override fun newArray(size: Int): Array<FriendNotifyData?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     *
     */
    class AdHocNotifyData(val session: String, var isAt: Boolean = false) : Parcelable, NotGuard {
        constructor(parcel: Parcel) : this(parcel.readString(), parcel.readInt() == 1)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(session)
            parcel.writeInt(if (isAt) 1 else 0)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<AdHocNotifyData> {
            override fun createFromParcel(parcel: Parcel): AdHocNotifyData {
                return AdHocNotifyData(parcel)
            }

            override fun newArray(size: Int): Array<AdHocNotifyData?> {
                return arrayOfNulls(size)
            }
        }

    }

    class BcmNotify(@SerializedName("notifytype") val notifyType: Int,
                    @SerializedName("contactchat") val contactChat: ChatNotifyData?,
                    @SerializedName("groupchat") val groupChat: GroupNotifyData?,
                    @SerializedName("friendmsg") val friendMsg: FriendNotifyData?,
                    @SerializedName("adhocchat") val adhocChat: AdHocNotifyData? = null,
                    @SerializedName("systemmsg") val systemChat: SystemNotifyData? = null) : NotGuard

    class BcmData(val bcmdata: BcmNotify?): NotGuard

    @SuppressLint("CheckResult")
    fun processPush(accountContext: AccountContext?, notify: BcmData?) {

        if (AMELogin.isLogin && null != notify) {
            if (TextSecurePreferences.isNotificationsEnabled(AppContextHolder.APP_CONTEXT) &&
                    TextSecurePreferences.isDatabaseMigrated(AppContextHolder.APP_CONTEXT)) { //
                Observable.create<Unit> {
                    if (needShowOffline()) {
                        incrementOfflineUnreadCount(accountContext, notify)
                        handleNotify(accountContext, notify.bcmdata?.contactChat)
                        handleNotify(accountContext, notify.bcmdata?.groupChat)
                        handleNotify(notify.bcmdata?.friendMsg)
                        handleNotify(accountContext, notify.bcmdata?.adhocChat)

                    } else {
                        ALog.e(TAG, "receive push data -- not showOffline")
                    }
                    handleNotify(accountContext, notify.bcmdata?.systemChat)
                    it.onComplete()
                }.subscribeOn(AmeDispatcher.singleScheduler)
                        .observeOn(AmeDispatcher.singleScheduler)
                        .doOnError {
                            ALog.e(TAG, "processPush", it)
                        }.subscribe {

                        }
            }else {
                ALog.w(TAG, "receive push data, -- no notification enable")
            }

        } else {
            ALog.e(TAG, "receive push data -- no login state!!!")
        }

    }

    fun processPush(accountContext: AccountContext?, pushContent: String) {
        try {
            if (!AMELogin.isLogin) {
                ALog.i(TAG, "processPush Current is not login")
                return
            }
            ALog.d(TAG, "processPush: $pushContent")
            val notify = Gson().fromJson(pushContent, BcmData::class.java)
            if (notify.bcmdata != null) {
                notify.bcmdata.contactChat?.uid?.let {
                    try {
                        notify.bcmdata.contactChat.uid = if(null != accountContext) {
                            BCMEncryptUtils.decryptSource(accountContext, it.toByteArray())
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        ALog.e(TAG, "Uid decrypted failed!")
                        return
                    }
                }

                notify.bcmdata.groupChat?.isAt = true
                processPush(accountContext, notify)

            } else {
                ALog.e(TAG, "PushContent is not support")
            }
        } catch (e: Exception) {
            ALog.e(TAG, e)
        }
    }

    /**
     * Banner
     */
    fun checkSystemBannerNotice() {
        AmeDispatcher.io.dispatch {
            val lastMsg = TextSecurePreferences.getStringPreference(AppContextHolder.APP_CONTEXT, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + AMELogin.majorUid + "_" + SystemNotifyData.TYPE_BANNER, "")
            val msg = GsonUtils.fromJson(lastMsg, SystemNotifyData::class.java)
            if (lastMsg.isNotEmpty() && msg.type == SystemNotifyData.TYPE_BANNER) {
                val data = BcmData(BcmNotify(SYSTEM_NOTIFY, null, null, null, null, msg))
                processPush(AMELogin.majorContext, data)
            }
        }
    }

    /**
     *
     */
    private fun incrementOfflineUnreadCount(accountContext: AccountContext?, data: BcmData) {
        if (null == accountContext) {
            return
        }

        if (!AppForeground.foreground()) {
            val privateChat = data.bcmdata?.contactChat
            val groupChat = data.bcmdata?.groupChat
            val friendRequest = data.bcmdata?.friendMsg
            val adhocChat = data.bcmdata?.adhocChat

            if (privateChat != null || groupChat != null || friendRequest != null) {

                offlineUnreadSet.clear()
                offlineUnreadSet.addAll(Repository.getThreadRepo(accountContext).getAllThreadsWithRecipientReady().mapNotNull {
                    val r = it.getRecipient(accountContext)
                    if (r.isMuted || it.unreadCount <= 0) {
                        null
                    }else {
                        if (r.isGroupRecipient) {
                            GroupUtil.gidFromUid(it.uid).toString()
                        } else {
                            it.uid
                        }
                    }
                })

                val groupId = groupChat?.gid
                if (groupId != null) {
                    val mute = GroupInfoDataManager.queryOneAmeGroupInfo(accountContext, groupId)?.mute
                            ?: true
                    if (!mute) {
                        offlineUnreadSet.add(groupId.toString())
                    }
                }

                val privateUid = privateChat?.uid
                if (privateUid != null) {
                    val recipient = Recipient.from(accountContext, privateUid, false)
                    if (!recipient.isMuted) {
                        offlineUnreadSet.add(privateUid)
                    }
                }

                friendReqUnreadCount = UserDatabase.getDatabase(accountContext).friendRequestDao().queryUnreadCount()

            }

            if (adhocChat != null) {
                val dao = UserDatabase.getDatabase(accountContext).adHocSessionDao()

                adhocOfflineUnreadSet.clear()
                adhocOfflineUnreadSet.addAll(dao.loadAllUnreadSession().map {
                    it.sessionId
                })
                if (dao.querySession(data.bcmdata.adhocChat.session)?.mute != true) {
                    adhocOfflineUnreadSet.add(data.bcmdata.adhocChat.session)
                }

            }

        }
    }

    private fun canNotifySystemMsg(current: Long, start: Long, end: Long): Boolean {
        return start == -1L || start == 0L || current in start..end
    }

    private fun handleNotify(accountContext: AccountContext?, notifyData: SystemNotifyData?) {
        if(notifyData == null) {
            return
        }

        if (null != accountContext) {
            val oldJsonData = TextSecurePreferences.getStringPreference(AppContextHolder.APP_CONTEXT, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + accountContext.uid + "_" + notifyData.type, "")
            if (!oldJsonData.isNullOrEmpty()) {
                val oldData = GsonUtils.fromJson(oldJsonData, SystemNotifyData::class.java)
                if (oldData.id >= notifyData.id) { //
                    ALog.i(TAG, "oldData.id = ${oldData.id} ,newNotifyData.id = ${notifyData.id}")
                    return
                }
            }
        }

        try {
            ALog.i(TAG, "handleNotify type: ${notifyData.type}, content: ${notifyData.content}")
            val current = AmeTimeUtil.serverTimeMillis() / 1000
            when(notifyData.type) {
                SystemNotifyData.TYPE_ALERT_WEB -> {
                    val data = GsonUtils.fromJson(notifyData.content, SystemNotifyData.WebAlertData::class.java)
                    if (canNotifySystemMsg(current, data.start, data.end)) {
                        val topActivity = AmeAppLifecycle.current() ?: return
                        if (!topActivity.isFinishing && !topActivity.isDestroyed) {
                            EventBus.getDefault().post(data)
                        } else {
                            ALog.w(TAG, "activity is finishing or destroyed, can not handle webalert")
                        }
                    }
                }
                SystemNotifyData.TYPE_BANNER -> {
                    val data = GsonUtils.fromJson(notifyData.content, SystemNotifyData.BannerData::class.java)
                    if (canNotifySystemMsg(current, data.start, data.end)) {

                        EventBus.getDefault().postSticky(data)
                    }
                }
            }
            if (!AppForeground.foreground()) { //Umeng，App，Umeng
                val builder = AmeNotification.getDefaultNotificationBuilder(AppContextHolder.APP_CONTEXT)
                setAlarm(builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifySystemMessageBar(builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_HOME)
            }
            //
            if (null != accountContext) {
                PushUtil.confirmSystemMessages(accountContext, notifyData.id).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            ALog.i(TAG, "confirm system message maxMid ${notifyData.id}")
                        }, {
                            ALog.e(TAG, it.localizedMessage)
                        })
            }


        }catch (ex: Exception) {
            ALog.e(TAG, "handleNotify for system chat fail", ex)
        }finally {
            if (null != accountContext) {
                TextSecurePreferences.setStringPreference(AppContextHolder.APP_CONTEXT, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + accountContext.uid + "_" + notifyData.type, GsonUtils.toJson(notifyData))
            }
        }
    }

    private fun setAlarm(builder: NotificationCompat.Builder, ringtone: Uri?, vibrate: RecipientDatabase.VibrateState) {
        if (System.currentTimeMillis() - lastNotifyTime > 6000) {
            lastNotifyTime = System.currentTimeMillis()

            val defaultRingtone = TextSecurePreferences.getNotificationRingtone(AppContextHolder.APP_CONTEXT)
            if (ringtone != null) {
                builder.setSound(ringtone)
            } else if (!defaultRingtone.isNullOrEmpty()) {
                builder.setSound(Uri.parse(defaultRingtone))
            }

            val defaultVibrate = TextSecurePreferences.isNotificationVibrateEnabled(AppContextHolder.APP_CONTEXT)
            if ((vibrate == RecipientDatabase.VibrateState.ENABLED || vibrate == RecipientDatabase.VibrateState.DEFAULT) && defaultVibrate) {
                builder.setDefaults(Notification.DEFAULT_VIBRATE)
            }
        }
    }

    private fun handleNotify(accountContext: AccountContext?, notifyData: ChatNotifyData?) {
        if (null != notifyData) {
            ALog.i(TAG, "receive push group data -- background!!!")

            notifyData.uid?.let {uid ->
                val builder = AmeNotification.getDefaultNotificationBuilder(AppContextHolder.APP_CONTEXT)
                val recipient = if(null != accountContext) {
                    Recipient.from(accountContext, uid, false)
                } else {
                    null
                }

                if (recipient?.isMuted == true) {
                    return
                }

                if (TextSecurePreferences.isNotificationsEnabled(AppContextHolder.APP_CONTEXT)) {
                    setAlarm(builder, recipient?.ringtone, recipient?.vibrate?:RecipientDatabase.VibrateState.DEFAULT)
                }

                notifyBar(builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_CHAT)
            }

        }
    }

    /**
     *
     */
    private fun handleNotify(accountContext: AccountContext?, notifyData: GroupNotifyData?) {
        if (null != notifyData && notifyData.gid ?: 0 > 0) {
            val builder = AmeNotification.getDefaultNotificationBuilder(AppContextHolder.APP_CONTEXT)

            var mute = false
            if (accountContext != null) {
                mute = GroupInfoDataManager.queryOneAmeGroupInfo(accountContext,notifyData.gid
                        ?: throw Exception("group message id is null"))?.mute
                        ?: true
            }

            if (notifyData.isAt == true || !mute) {
                setAlarm(builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifyBar(builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_GROUP)
            }
        }
    }

    private fun handleNotify(notifyData: FriendNotifyData?) {
        if (notifyData == null) return
        ALog.i(TAG, "handle friend notify")

        val content = if (friendReqUnreadCount <= 1) {
            AppContextHolder.APP_CONTEXT.getString(R.string.common_notification_friend_single)
        } else {
            AppContextHolder.APP_CONTEXT.getString(R.string.common_notification_friend_multiple, friendReqUnreadCount)
        }

        val context = AppContextHolder.APP_CONTEXT
        val builder = AmeNotification.getFriendNotificationBuilder(AppContextHolder.APP_CONTEXT)
        builder.setContentTitle(context.getString(R.string.app_name))
        builder.setContentText(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.icon_notification_alpha)
            builder.color = context.getColor(R.color.common_color_black)
        } else {
            builder.setSmallIcon(R.mipmap.ic_launcher)
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }
        builder.setAutoCancel(true)
        val notificationId = System.currentTimeMillis().toInt()
        builder.setContentIntent(AmeNotificationService.getIntent(null, AmeNotificationService.ACTION_FRIEND_REQ, notificationId))
        val notification = builder.build()

        AmeDispatcher.mainThread.dispatch({
            mFriendReqNotification = notification
            updateAppBadge(AppContextHolder.APP_CONTEXT, offlineUnreadSet.size, friendReqUnreadCount)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(friendNotificationId, notification)
            ALog.i(TAG, "notify bar start end $notificationId")

        },500)
    }

    /**
     *
     */
    private fun handleNotify(accountContext: AccountContext?, notifyData: AdHocNotifyData?) {
        if (notifyData != null && accountContext != null) {
            val builder = AmeNotification.getAdHocNotificationBuilder(AppContextHolder.APP_CONTEXT)
            val enable = UserDatabase.getDatabase(accountContext).adHocSessionDao().querySession(notifyData.session)?.mute != true
            if (notifyData.isAt || enable) {
                setAlarm(builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifyBar(builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_ADHOC)
            }else {
                ALog.w(TAG, "handleAdHocNotify isAt: ${notifyData.isAt}, enable: $enable")
            }
        }
    }

    /**
     *
     */
    private fun notifyBar(builder: NotificationCompat.Builder, msg: Parcelable, context: Context?, notificationId: Int, action: Int) {
        try {
            if (null != context) {
                val pendingIntent = AmeNotificationService.getIntent(msg, action, notificationId)
                val appName = context.resources.getString(R.string.app_name)

                var chatCount = 0
                var friendReqCount = 0
                if (msg is AdHocNotifyData) {
                    chatCount = adhocOfflineUnreadSet.size
                }else if (msg is GroupNotifyData || msg is ChatNotifyData) {
                    chatCount = offlineUnreadSet.size
                    friendReqCount = friendReqUnreadCount
                }

                if (chatCount <= 0) {
                    ALog.i(TAG, "notify bar start $notificationId $chatCount but empty message thread list")
                    return
                }else {
                    ALog.i(TAG, "notify bar start $notificationId")
                }

                val content = if (chatCount > 1) {
                    context.getString(R.string.common_notification_message_contents_format, chatCount)
                } else {
                    context.getString(R.string.common_notification_message_content_format)
                }
                builder.setContentIntent(pendingIntent)
                        .setContentTitle(appName)
                        .setContentText(content)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.setSmallIcon(R.drawable.icon_notification_alpha).setColor(AppUtil.getColor(context.resources, R.color.common_color_black))
                }

                val notification = builder.build()
                AmeDispatcher.mainThread.dispatch({
                    mChatNotification = notification
                    updateAppBadge(AppContextHolder.APP_CONTEXT, chatCount, friendReqCount)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(chatNotificationId, notification)
                    ALog.i(TAG, "notify bar start end $notificationId")

                },500)

            } else {
                ALog.i(TAG, "notify bar end context is null $notificationId")
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "notifyBar error", ex)
        }
    }

    private fun notifySystemMessageBar(builder: NotificationCompat.Builder, msg: Parcelable, context: Context?, notificationId: Int, action: Int) {
        try {
            if (null != context) {
                val pendingIntent = AmeNotificationService.getIntent(msg, action, notificationId)
                val appName = context.resources.getString(R.string.app_name)

                ALog.i(TAG, "notify bar start $notificationId")

                val content = context.getString(R.string.common_notification_message_contents_format, 1)
                builder.setContentIntent(pendingIntent)
                        .setContentTitle(appName)
                        .setContentText(content)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.setSmallIcon(R.drawable.icon_notification_alpha).color = AppUtil.getColor(context.resources, R.color.common_color_black)
                }
                val notification = builder.build()

                AmeDispatcher.mainThread.dispatch({
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(0, notification)
                    ALog.i(TAG, "notify bar start end $notificationId")
                }, 1000)

            } else {
                ALog.i(TAG, "notify bar end context is null $notificationId")
            }
        } catch (ex: Exception) {
            ALog.e("AmePushProcess", "notifyBar error", ex)
        }
    }


    fun reset() {
        ALog.i(TAG, "reset")
        pushInitTime = System.currentTimeMillis()
        clearNotificationCenter()
    }

    /**
     *
     */
    private fun needShowOffline(): Boolean {
        if (System.currentTimeMillis() - pushInitTime > 5000 && pushInitTime != 0L) {
            if (!AppForeground.foreground()) {
                return true
            }
        }
        return false
    }

    /**
     *
     */
    fun clearNotificationCenter() {
        ALog.i(TAG, "clearNotificationCenter")
        val notificationManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancelAll()
        offlineUnreadSet.clear()
        adhocOfflineUnreadSet.clear()
        friendReqUnreadCount = 0
        mChatNotification = null
        mFriendReqNotification = null

    }

    /**
     *
     */
    fun clearFriendRequestNotification() {
        ALog.i(TAG, "clearFriendRequestNotification")

        val notificationManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(friendNotificationId)
        friendReqUnreadCount = 0
        mFriendReqNotification = null
    }

    /**
     *
     */
    fun notifyDownloadApkNotification(contentText: String, isCompleted: Boolean, installPath: String) {
        ALog.i(TAG, "notifyDownloadApkNotification isComplete: $isCompleted")
        val notificationManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val builder = AmeNotification.getUpdateNotificationBuilder(AppContextHolder.APP_CONTEXT)
        builder.setContentTitle(AppContextHolder.APP_CONTEXT.getApplicationName())
                ?.setContentText(contentText)
                ?.setSmallIcon(R.mipmap.ic_launcher)
                ?.setAutoCancel(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(R.drawable.icon_notification_alpha)
                    .color = getColor(R.color.common_color_black)
        }
        if (isCompleted) {
            builder.setContentIntent(AmeNotificationService.getIntentData(installPath,
                    AmeNotificationService.ACTION_INSTALL, System.currentTimeMillis().toInt()))
        }
        notificationManager?.notify(downloadNotificationId, builder.build())
    }

    /**
     *
     */
    fun updateAppBadge(context: Context, count: Int) {
        try {
            ALog.i(TAG, "updateAppBadge with totalCount: $count")
            if (RomUtil.isMiui()) {
                ALog.i(TAG, "xiaomi updateAppBadge count: $count")
                ShortcutBadger.applyNotification(context, mChatNotification ?: mFriendReqNotification, count)
            } else {
                if (count <= 0) {
                    ShortcutBadger.removeCount(context)
                } else {
                    ShortcutBadger.applyCount(context, count)
                }
            }

        } catch (t: Throwable) {
            // NOTE :: I don't totally trust this thing, so I'm catching
            // everything.
            ALog.e(TAG, "updateAppBadge error", t)
        }
    }

    /**
     *
     */
    private fun updateAppBadge(context: Context, chatCount: Int, friendReqCount: Int) {
        try {

            if (RomUtil.isMiui()) {
                ALog.i(TAG, "xiaomi updateAppBadge chatCount: $chatCount, chatNotification: ${mChatNotification != null}, " +
                        "friendReqCount: $friendReqCount, friendNotification: ${mFriendReqNotification != null}")
                if (mChatNotification == null && mFriendReqNotification != null) {
                    ShortcutBadger.applyNotification(context, mFriendReqNotification, chatCount + friendReqCount)
                }else if (mChatNotification != null && mFriendReqNotification == null) {
                    ShortcutBadger.applyNotification(context, mChatNotification, chatCount + friendReqCount)
                }else {
                    ShortcutBadger.applyNotification(context, mChatNotification, chatCount)
                    ShortcutBadger.applyNotification(context, mFriendReqNotification, friendReqCount)
                }

            } else {
                val count = chatCount + friendReqCount
                ALog.i(TAG, "updateAppBadge with chatCount: $chatCount, friendReqCount: $friendReqCount")
                if (count <= 0) {
                    ShortcutBadger.removeCount(context)
                } else {
                    ShortcutBadger.applyCount(context, count)
                }
            }

        } catch (t: Throwable) {
            // NOTE :: I don't totally trust this thing, so I'm catching
            // everything.
            ALog.e(TAG, "updateAppBadge error", t)
        }
    }

}