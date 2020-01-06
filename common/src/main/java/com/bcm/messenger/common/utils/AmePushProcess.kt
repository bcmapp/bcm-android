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
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
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
    private var mUnreadCountMap = mutableMapOf<AccountContext, MutableSet<String>>()
    private var mAdHocUnreadCountMap = mutableMapOf<AccountContext, MutableSet<String>>()
    private var mFriendReqUnreadCountMap = mutableMapOf<AccountContext, Int>()

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
                    @SerializedName("targetid") val targetHash: Long,
                    @SerializedName("contactchat") val contactChat: ChatNotifyData?,
                    @SerializedName("groupchat") val groupChat: GroupNotifyData?,
                    @SerializedName("friendmsg") val friendMsg: FriendNotifyData?,
                    @SerializedName("adhocchat") val adhocChat: AdHocNotifyData? = null,
                    @SerializedName("systemmsg") val systemChat: SystemNotifyData? = null) : NotGuard

    class BcmData(val bcmdata: BcmNotify?): NotGuard

    @SuppressLint("CheckResult")
    fun processPush(accountContext: AccountContext, notify: BcmData?) {

        if (AMELogin.isLogin && null != notify) {
            if (TextSecurePreferences.isNotificationsEnabled(accountContext) &&
                    TextSecurePreferences.isDatabaseMigrated(accountContext)) {
                Observable.create<Unit> {
                    if (needShowOffline()) {
                        incrementOfflineUnreadCount(accountContext, notify)
                        handleNotify(accountContext, notify.bcmdata?.contactChat)
                        handleNotify(accountContext, notify.bcmdata?.groupChat)
                        handleNotify(accountContext, notify.bcmdata?.friendMsg)
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

    /**
     * from offline push
     */
    fun processPush(pushContent: String) {
        try {
            if (!AMELogin.isLogin) {
                ALog.i(TAG, "processPush Current is not login")
                return
            }
            ALog.d(TAG, "processPush: $pushContent")
            val notify = Gson().fromJson(pushContent, BcmData::class.java)
            if (notify.bcmdata != null) {
                val accountContext = findAccountContext(notify.bcmdata.targetHash)
                if (accountContext == null) {
                    ALog.w(TAG, "processPush fail, find accountContext null")
                    return
                }
                notify.bcmdata.contactChat?.uid?.let {
                    try {
                        notify.bcmdata.contactChat.uid = BCMEncryptUtils.decryptSource(accountContext, it.toByteArray())

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
            val lastMsg = TextSecurePreferences.getStringPreference(AMELogin.majorContext, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + AMELogin.majorUid + "_" + SystemNotifyData.TYPE_BANNER, "")
            val msg = GsonUtils.fromJson(lastMsg, SystemNotifyData::class.java)
            if (lastMsg.isNotEmpty() && msg.type == SystemNotifyData.TYPE_BANNER) {
                val data = BcmData(BcmNotify(SYSTEM_NOTIFY, 0, null, null, null, null, msg))
                processPush(AMELogin.majorContext, data)
            }
        }
    }

    /**
     *
     */
    private fun incrementOfflineUnreadCount(accountContext: AccountContext, data: BcmData) {
        if (!AppForeground.foreground()) {
            val privateChat = data.bcmdata?.contactChat
            val groupChat = data.bcmdata?.groupChat
            val friendRequest = data.bcmdata?.friendMsg
            val adhocChat = data.bcmdata?.adhocChat

            if (privateChat != null || groupChat != null || friendRequest != null) {

                var unreadSet = mUnreadCountMap[accountContext]
                if (unreadSet == null) {
                    unreadSet = mutableSetOf()
                    mUnreadCountMap[accountContext] = unreadSet
                }
                unreadSet.clear()
                unreadSet.addAll(Repository.getThreadRepo(accountContext)?.getAllThreadsWithRecipientReady()?.mapNotNull {
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
                } ?: setOf())

                val groupId = groupChat?.gid
                if (groupId != null) {
                    val mute = GroupInfoDataManager.queryOneAmeGroupInfo(accountContext, groupId)?.mute ?: true
                    if (!mute) {
                        unreadSet.add(groupId.toString())
                    }
                }

                val privateUid = privateChat?.uid
                if (privateUid != null) {
                    val recipient = Recipient.from(accountContext, privateUid, false)
                    if (!recipient.isMuted) {
                        unreadSet.add(privateUid)
                    }
                }

                var friendReqCount = mFriendReqUnreadCountMap[accountContext] ?: 0
                friendReqCount = Repository.getFriendRequestRepo(accountContext)?.queryUnreadCount() ?: 0
                mFriendReqUnreadCountMap[accountContext] = friendReqCount
            }

            if (adhocChat != null) {

                val dao = Repository.getAdHocSessionRepo(accountContext)
                var unreadSet = mAdHocUnreadCountMap[accountContext]
                if (unreadSet == null) {
                    unreadSet = mutableSetOf()
                    mAdHocUnreadCountMap[accountContext] = unreadSet
                }
                unreadSet.clear()
                unreadSet.addAll(dao?.loadAllUnreadSession()?.map {
                    it.sessionId
                } ?: setOf())
                if (dao?.querySession(data.bcmdata.adhocChat.session)?.mute != true) {
                    unreadSet.add(data.bcmdata.adhocChat.session)
                }

            }

        }
    }

    private fun canNotifySystemMsg(current: Long, start: Long, end: Long): Boolean {
        return start == -1L || start == 0L || current in start..end
    }

    private fun handleNotify(accountContext: AccountContext, notifyData: SystemNotifyData?) {
        if(notifyData == null) {
            return
        }

        val oldJsonData = TextSecurePreferences.getStringPreference(accountContext, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + accountContext.uid + "_" + notifyData.type, "")
        if (!oldJsonData.isNullOrEmpty()) {
            val oldData = GsonUtils.fromJson(oldJsonData, SystemNotifyData::class.java)
            if (oldData.id >= notifyData.id) { //
                ALog.i(TAG, "oldData.id = ${oldData.id} ,newNotifyData.id = ${notifyData.id}")
                return
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
                setAlarm(accountContext, builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifySystemMessageBar(accountContext, builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_HOME)
            }
            PushUtil.confirmSystemMessages(accountContext, notifyData.id).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.i(TAG, "confirm system message maxMid ${notifyData.id}")
                    }, {
                        ALog.e(TAG, it.localizedMessage)
                    })

        }catch (ex: Exception) {
            ALog.e(TAG, "handleNotify for system chat fail", ex)
        }finally {
            TextSecurePreferences.setStringPreference(accountContext, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + accountContext.uid + "_" + notifyData.type, GsonUtils.toJson(notifyData))
        }
    }

    private fun setAlarm(accountContext: AccountContext, builder: NotificationCompat.Builder, ringtone: Uri?, vibrate: RecipientDatabase.VibrateState) {
        if (System.currentTimeMillis() - lastNotifyTime > 6000) {
            lastNotifyTime = System.currentTimeMillis()

            val defaultRingtone = TextSecurePreferences.getNotificationRingtone(accountContext)
            if (ringtone != null) {
                builder.setSound(ringtone)
            } else if (!defaultRingtone.isNullOrEmpty()) {
                builder.setSound(Uri.parse(defaultRingtone))
            }

            val defaultVibrate = TextSecurePreferences.isNotificationVibrateEnabled(accountContext)
            if ((vibrate == RecipientDatabase.VibrateState.ENABLED || vibrate == RecipientDatabase.VibrateState.DEFAULT) && defaultVibrate) {
                builder.setDefaults(Notification.DEFAULT_VIBRATE)
            }
        }
    }

    private fun handleNotify(accountContext: AccountContext, notifyData: ChatNotifyData?) {
        if (null != notifyData) {
            ALog.i(TAG, "receive push group data -- background!!!")
            notifyData.uid?.let {uid ->
                val builder = AmeNotification.getDefaultNotificationBuilder(AppContextHolder.APP_CONTEXT)
                val recipient = Recipient.from(accountContext, uid, false)

                if (recipient.isMuted) {
                    return
                }

                if (TextSecurePreferences.isNotificationsEnabled(accountContext)) {
                    setAlarm(accountContext, builder, recipient.ringtone, recipient.vibrate ?:RecipientDatabase.VibrateState.DEFAULT)
                }

                notifyBar(accountContext, builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_CHAT)
            }
        }
    }

    /**
     *
     */
    private fun handleNotify(accountContext: AccountContext, notifyData: GroupNotifyData?) {
        if (null != notifyData && notifyData.gid ?: 0 > 0) {
            val builder = AmeNotification.getDefaultNotificationBuilder(AppContextHolder.APP_CONTEXT)

            var mute = false
            mute = GroupInfoDataManager.queryOneAmeGroupInfo(accountContext,notifyData.gid
                    ?: throw Exception("group message id is null"))?.mute
                    ?: true

            if (notifyData.isAt == true || !mute) {
                setAlarm(accountContext, builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifyBar(accountContext, builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_GROUP)
            }
        }
    }

    private fun handleNotify(accountContext: AccountContext, notifyData: FriendNotifyData?) {
        if (notifyData == null) return
        ALog.i(TAG, "handle friend notify")
        val friendReqCount = getFriendRequestUnreadCount()
        val content = if (friendReqCount <= 1) {
            AppContextHolder.APP_CONTEXT.getString(R.string.common_notification_friend_single)
        } else {
            AppContextHolder.APP_CONTEXT.getString(R.string.common_notification_friend_multiple, friendReqCount)
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
        builder.setContentIntent(AmeNotificationService.getIntent(accountContext,null, AmeNotificationService.ACTION_FRIEND_REQ, notificationId))
        val notification = builder.build()

        AmeDispatcher.mainThread.dispatch({
            mFriendReqNotification = notification
            updateAppBadge(AppContextHolder.APP_CONTEXT, getChatUnreadCount(), friendReqCount)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(friendNotificationId, notification)
            ALog.i(TAG, "notify bar start end $notificationId")

        },500)
    }

    /**
     *
     */
    private fun handleNotify(accountContext: AccountContext, notifyData: AdHocNotifyData?) {
        if (notifyData != null && accountContext != null) {
            val builder = AmeNotification.getAdHocNotificationBuilder(AppContextHolder.APP_CONTEXT)
            val enable = Repository.getAdHocSessionRepo(accountContext)?.querySession(notifyData.session)?.mute != true
            if (notifyData.isAt || enable) {
                setAlarm(accountContext, builder, null, RecipientDatabase.VibrateState.ENABLED)
                notifyBar(accountContext, builder, notifyData, AppContextHolder.APP_CONTEXT, System.currentTimeMillis().toInt(),
                        AmeNotificationService.ACTION_ADHOC)
            }else {
                ALog.w(TAG, "handleAdHocNotify isAt: ${notifyData.isAt}, enable: $enable")
            }
        }
    }

    /**
     *
     */
    private fun notifyBar(accountContext: AccountContext, builder: NotificationCompat.Builder, msg: Parcelable, context: Context?, notificationId: Int, action: Int) {
        try {
            if (null != context) {
                val pendingIntent = AmeNotificationService.getIntent(accountContext, msg, action, notificationId)
                val appName = context.resources.getString(R.string.app_name)

                var chatCount = 0
                var friendReqCount = 0
                if (msg is AdHocNotifyData) {
                    chatCount = getAdHocUnreadCount()
                }else if (msg is GroupNotifyData || msg is ChatNotifyData) {
                    chatCount = getChatUnreadCount()
                    friendReqCount = getFriendRequestUnreadCount()
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

    private fun notifySystemMessageBar(accountContext: AccountContext, builder: NotificationCompat.Builder, msg: Parcelable, context: Context?, notificationId: Int, action: Int) {
        try {
            if (null != context) {
                val pendingIntent = AmeNotificationService.getIntent(accountContext, msg, action, notificationId)
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
        mUnreadCountMap.clear()
        mAdHocUnreadCountMap.clear()
        mFriendReqUnreadCountMap.clear()
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
        mFriendReqUnreadCountMap.clear()
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

    /**
     * find offline push target account context
     */
    fun findAccountContext(targetHash: Long): AccountContext? {
        try {
            val accountContextList = AmeModuleCenter.login().getLoginAccountContextList()
            for (ac in accountContextList) {
                if (BcmHash.hash(ac.uid.toByteArray()) == targetHash) {
                    return ac
                }
            }

        }catch (ex: Exception) {
            ALog.e(TAG, "findAccountContext error", ex)
        }
        return null
    }

    private fun getChatUnreadCount(): Int {
        var unreadCount = 0
        for ((ac, set) in mUnreadCountMap) {
            unreadCount += set.size
        }
        return unreadCount
    }

    private fun getFriendRequestUnreadCount(): Int {
        var friendReqCount = 0
        for ((ac, count) in mFriendReqUnreadCountMap) {
            friendReqCount += count
        }
        return friendReqCount
    }

    private fun getAdHocUnreadCount(): Int {
        var unreadCount = 0
        for ((ac, set) in mAdHocUnreadCountMap) {
            unreadCount += set.size
        }
        return unreadCount
    }
}