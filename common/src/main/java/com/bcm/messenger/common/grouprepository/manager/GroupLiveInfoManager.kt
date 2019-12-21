package com.bcm.messenger.common.grouprepository.manager

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.text.TextUtils

import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.GroupLiveInfoDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo
import com.bcm.messenger.utility.AppContextHolder
import org.greenrobot.eventbus.EventBus

class GroupLiveInfoManager internal constructor() {

    private val mHandlerThread: HandlerThread = HandlerThread("GroupLive_Manager")
    private val mHandler: Handler

    private val GID = "gid"

    // gid
    private var currentPlayingGid: Long = -1

    companion object {
        @Volatile
        private var liveManagerInstance: GroupLiveInfoManager? = null

        fun getInstance(): GroupLiveInfoManager {
            if (liveManagerInstance == null) {
                synchronized(GroupLiveInfoManager::class.java) {
                    if (liveManagerInstance == null)
                        liveManagerInstance = GroupLiveInfoManager()
                }
            }
            return liveManagerInstance!!
        }
    }

    fun registerCurrentPlayingGid(gid: Long) {
        currentPlayingGid = gid
    }

    fun unRegisterCurrentPlayingGid(gid: Long) {
        if (gid == currentPlayingGid)
            currentPlayingGid = -1
    }


    private val dao: GroupLiveInfoDao
        get() = UserDatabase.getDatabase().groupLiveInfoDao()

    init {
        mHandlerThread.start()
        mHandler = object : Handler(mHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                val gid = msg.data.getLong(GID)
                executeAutoStopLiveTask(getCurrentLiveInfo(gid))
            }
        }
    }


    fun deleteLiveInfoWhenLeaveGroup(gid: Long) {
        dao.deleteLiveInfoByGid(gid)
        updateThreadLiveState(gid, GroupLiveInfo.LiveStatus.EMPTY.value)
    }


    //
    fun stashLiveInfo(gid: Long, liveId: Long, sourceType: GroupLiveInfo.LiveSourceType, sourceUrl: String, actionTime: Long, duration: Long): Long {
        val groupLiveInfo = GroupLiveInfo()
        groupLiveInfo.gid = gid
        groupLiveInfo.liveId = liveId
        groupLiveInfo.start_time = actionTime
        groupLiveInfo.currentActionTime = actionTime
        groupLiveInfo.currentSeekTime = 0
        groupLiveInfo.source_url = sourceUrl
        groupLiveInfo.source_type = sourceType.value
        groupLiveInfo.duration = duration
        groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.STASH.value
        groupLiveInfo.isConfirmed = false
        return dao.insert(groupLiveInfo)
    }

    //
    fun clearStashLiveInfo(index: Long) {
        dao.delete(dao.loadLiveInfoByIndexId(index))
    }

    //
    fun updateWhenSendLiveMessage(index: Long, message: AmeGroupMessage.LiveContent) {
        val groupLiveInfo = dao.loadLiveInfoByIndexId(index)
        if (groupLiveInfo != null) {
            when {
                message.isStartLive() -> {
                    groupLiveInfo.isConfirmed = true
                    groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
                    groupLiveInfo.currentSeekTime = 0
                    groupLiveInfo.currentActionTime = message.actionTime
                }
                message.isRemoveLive() -> {
                    groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
                    groupLiveInfo.currentActionTime = message.actionTime
                    groupLiveInfo.currentSeekTime = message.duration
                }
                message.isPauseLive() -> {
                    groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.PAUSE.value
                    groupLiveInfo.currentSeekTime = message.currentSeekTime
                    groupLiveInfo.currentActionTime = message.actionTime
                }
                message.isRestartLive() -> {
                    groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
                    groupLiveInfo.currentSeekTime = message.currentSeekTime
                    groupLiveInfo.currentActionTime = message.actionTime
                }

                message.isRemovePlayback() -> {
                    groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
                    groupLiveInfo.currentActionTime = message.actionTime
                    groupLiveInfo.currentSeekTime = message.duration
                }
            }
            dao.update(groupLiveInfo)
            updateLiveModel(groupLiveInfo)
            updateThreadLiveState(groupLiveInfo.gid, groupLiveInfo.liveStatus)
            executeAutoStopLiveTask(groupLiveInfo)
        }

    }

    //=================================== ，  ================================================
    //
    fun handleReceiveLiveMessage(gid: Long, message: AmeGroupMessage.LiveContent, isOfflineMessage: Boolean) {
        //，，，，。
        val groupLiveInfo = dao.loadLatestLiveInfoByGid(gid)
        if (groupLiveInfo == null) {
            createAndChangeLiveStateByMessage(gid, message, isOfflineMessage)
        } else if (groupLiveInfo.liveId > message.id) {
            //,
            return
        } else if (groupLiveInfo.liveId == message.id) {//
            if (message.actionTime <= groupLiveInfo.currentActionTime) {//
                return
            } else {//
                changeLiveStateByMessage(groupLiveInfo, message)
            }
        } else if (message.id > groupLiveInfo.liveId) {
            //TODO:,
            createAndChangeLiveStateByMessage(gid, message, isOfflineMessage)
        }
    }


    //，，
    private fun createAndChangeLiveStateByMessage(gid: Long, message: AmeGroupMessage.LiveContent, isOfflineMessage: Boolean) {
        val groupLiveInfo = GroupLiveInfo()
        groupLiveInfo.gid = gid
        groupLiveInfo.liveId = message.id
        if (message.playSource != null && !TextUtils.isEmpty(message.playSource.url)) {
            groupLiveInfo.source_type = message.playSource.type
            groupLiveInfo.source_url = message.playSource.url
        } else {
            groupLiveInfo.source_type = GroupLiveInfo.LiveSourceType.Deprecated.value
            groupLiveInfo.source_url = message.sourceUrl
        }
        groupLiveInfo.duration = message.duration
        groupLiveInfo.currentSeekTime = message.currentSeekTime
        groupLiveInfo.currentActionTime = message.actionTime
        groupLiveInfo.isConfirmed = true
        when {
            message.isStartLive() -> {
                groupLiveInfo.start_time = message.actionTime
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
            }
            message.isRestartLive() -> {
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
            }
            message.isPauseLive() -> {
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.PAUSE.value
            }
            message.isRemoveLive() -> {
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
            }
            message.isRemovePlayback() -> {
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
            }
        }
        dao.insert(groupLiveInfo)
        //
        updateLiveModel(groupLiveInfo)
        //Thread 
        updateThreadLiveState(gid, groupLiveInfo.liveStatus)
        executeAutoStopLiveTask(groupLiveInfo, isOfflineMessage)

    }


    //
    private fun changeLiveStateByMessage(groupLiveInfo: GroupLiveInfo, message: AmeGroupMessage.LiveContent) {
        when {
            message.isRemoveLive() -> {//
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
                groupLiveInfo.currentActionTime = message.actionTime
            }
            message.isPauseLive() -> {
                groupLiveInfo.currentActionTime = message.actionTime
                groupLiveInfo.currentSeekTime = message.currentSeekTime
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.PAUSE.value
            }
            message.isRestartLive() -> {
                groupLiveInfo.currentActionTime = message.actionTime
                groupLiveInfo.currentSeekTime = message.currentSeekTime
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
            }
            message.isStartLive() -> {
                //
            }
            message.isRemovePlayback() -> {
                groupLiveInfo.currentActionTime = message.actionTime
                groupLiveInfo.currentSeekTime = message.currentSeekTime
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.REMOVED.value
            }
        }
        dao.update(groupLiveInfo)
        executeAutoStopLiveTask(groupLiveInfo)
        updateLiveModel(groupLiveInfo)
        updateThreadLiveState(groupLiveInfo.gid, groupLiveInfo.liveStatus)
    }

    //
    fun getCurrentPlaybackInfo(gid: Long): GroupLiveInfo? {
        val groupLiveInfo = getCurrentLiveInfo(gid)
        if (groupLiveInfo == null
                || groupLiveInfo.liveStatus == GroupLiveInfo.LiveStatus.REMOVED.value
                || groupLiveInfo.liveStatus == GroupLiveInfo.LiveStatus.EMPTY.value) {
            return null
        } else {
            return groupLiveInfo
        }

    }

    //
    fun getCurrentLiveInfo(gid: Long): GroupLiveInfo? {
        val groupLiveInfo = dao.loadLatestLiveInfoByGid(gid)
        if (groupLiveInfo == null) return groupLiveInfo
        //
        if (groupLiveInfo.isLiveStatus && groupLiveInfo.livePlayHasDone()) {
            groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.STOPED.value
            updateLiveStopToDbAndInsertMessage(groupLiveInfo)
            updateThreadLiveState(gid, groupLiveInfo.liveStatus)
        }
        return groupLiveInfo
    }


    // ThreadDataBase 
    fun loadAndUpdateThreadLiveinfo() {

    }

    private fun updateThreadLiveState(gid: Long, status: Int = getCurrentLiveInfo(gid)?.liveStatus ?: 0) {
        Repository.getThreadRepo().updateLiveState(gid, status)
    }

    private fun updateLiveModel(groupLiveInfo: GroupLiveInfo) {
        EventBus.getDefault().post(groupLiveInfo)
    }


    private fun executeAutoStopLiveTask(groupLiveInfo: GroupLiveInfo?, isOfflineMessage: Boolean = false) {
        if (groupLiveInfo == null) return
        if (groupLiveInfo.isLiveStatus) {
            val finishTimeDelay = groupLiveInfo.computeLiveFinishTime()
            if (finishTimeDelay <= 0) {
                groupLiveInfo.liveStatus = GroupLiveInfo.LiveStatus.STOPED.value
                updateLiveStopToDbAndInsertMessage(groupLiveInfo, isOfflineMessage)
                updateThreadLiveState(groupLiveInfo.gid, groupLiveInfo.liveStatus)
            } else {
                val message = Message.obtain()
                message.data.putLong(GID, groupLiveInfo.gid)
                mHandler.sendMessageDelayed(message, finishTimeDelay)
            }
        }
    }

    //stop
    private fun updateLiveStopToDbAndInsertMessage(groupLiveInfo: GroupLiveInfo?, isOfflineMessage: Boolean = false) {
        groupLiveInfo?.let {
            if (it.liveStatus != GroupLiveInfo.LiveStatus.STOPED.value) {
                it.liveStatus = GroupLiveInfo.LiveStatus.STOPED.value
            }
            dao.update(groupLiveInfo)
            if (groupLiveInfo.gid != currentPlayingGid && !isOfflineMessage) {
                MessageDataManager.systemNotice(it.gid, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_LIVE_END))
            }
        }
    }

}
