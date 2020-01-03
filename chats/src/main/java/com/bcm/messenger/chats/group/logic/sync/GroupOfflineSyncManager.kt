package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.core.group.GroupMessageEntity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.server.ConnectState
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.foreground.AppForeground
import java.io.*
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.max

/**
 * bcm.social.01 2019/3/19.
 */
class GroupOfflineSyncManager(private val syncCallback: OfflineSyncCallback) {
    companion object {
        private const val QUERY_ONE_TIME = 4
        private const val PAGE_SIZE = 500L
        private const val SERIALIZABLE_FILE_NAME = "group_offline_sync_tasks"
        private const val TAG = "GroupOfflineSyncManager"
    }

    private val messageSyncTaskList = ArrayList<GroupOfflineMessageSyncTask>()//for taskExecutor

    private val joinReqOwnerTaskList = ArrayList<GroupOfflineJoinReqMessageSyncTask>()//for taskExecutor
    private var joinReqOwnerSyncRunning = false
    private val joinReqMemberTaskList = ArrayList<GroupOfflineJoinReqMessageSyncTask>()//for taskExecutor
    private var joinReqMemberSyncRunning = false

    private val taskExecutor = Executors.newSingleThreadExecutor() //task handled by this executor
    private var taskRunningCount = 0

    fun init() {
        taskExecutor.execute {
            messageSyncTaskList.clear()
            joinReqOwnerTaskList.clear()
            joinReqMemberTaskList.clear()

            loadTasks()
        }
    }

    fun unInit() {
        taskExecutor.execute {
            messageSyncTaskList.clear()
            joinReqOwnerTaskList.clear()
            joinReqMemberTaskList.clear()
        }
    }


    fun sync() {
        taskExecutor.execute {
            doSync()

            if (joinReqOwnerTaskList.isNotEmpty() && !joinReqOwnerSyncRunning) {
                joinReqOwnerSyncRunning = true
                syncOwnerJoinReqTask(joinReqOwnerTaskList.first())
            }

            if (joinReqMemberTaskList.isNotEmpty() && !joinReqMemberSyncRunning) {
                joinReqMemberSyncRunning = true
                syncMemberJoinReqTask(joinReqMemberTaskList.first())
            }
        }
    }

    fun sync(gid: Long, fromMid: Long, toMid: Long) {
        if (gid == 0L) {
            return
        }
        taskExecutor.execute {
            val tasks = LinkedList<GroupOfflineMessageSyncTask>()

            val maxSyncingMid = getMaxSyncMid(gid)
            val minSyncingMid = getMinSyncMid(gid)
            if (maxSyncingMid >= toMid && minSyncingMid <= fromMid) {
                ALog.i(TAG, "sync begin $gid  all syncing")
                return@execute
            }

            var taskBegin = max(fromMid, maxSyncingMid)

            ALog.i(TAG, "sync begin $gid  from $taskBegin to $toMid")

            do {
                val taskEnd = taskBegin + Math.min(PAGE_SIZE, toMid - taskBegin)
                val task = GroupOfflineMessageSyncTask(gid, taskBegin, taskEnd)
                if (!isExist(task)) {
                    tasks.add(task)
                }

                taskBegin = taskEnd + 1
            } while (taskBegin <= toMid)

            if (tasks.isNotEmpty()) {
                val last = tasks.removeLast()
                this.messageSyncTaskList.add(0, last)
                this.messageSyncTaskList.addAll(tasks)

                sortTaskList()
            }
            saveTasks()
            doSync()
        }
    }

    fun resetDelay() {
        taskExecutor.execute {
            messageSyncTaskList.forEach {
                it.delay = 0
            }
            doSync()
        }
    }

    private fun getMaxSyncMid(gid:Long) :Long {
        val tl = messageSyncTaskList.filter { it.gid == gid }
        var maxMid = 0L
        for (i in tl) {
            if (i.toMid > maxMid) {
                maxMid = i.toMid
            }
        }
        return maxMid
    }

    private fun getMinSyncMid(gid:Long) :Long {
        val tl = messageSyncTaskList.filter { it.gid == gid }
        var minId = 100000000L
        for (i in tl) {
            if (i.fromMid < minId) {
                minId = i.fromMid
            }
        }
        return minId
    }

    private fun doSync() {
        if (messageSyncTaskList.isEmpty()) {
            return
        }

        while (taskRunningCount < QUERY_ONE_TIME) {
            if (syncNextTask(true)) {
                ++taskRunningCount
            } else {
                break
            }
        }
    }

    private fun isExist(task: GroupOfflineMessageSyncTask): Boolean {
        for (i in messageSyncTaskList) {
            if (i.isSame(task)) {
                return true
            }
        }
        return false
    }

    private fun finishTask(task: GroupOfflineMessageSyncTask, messageList: List<GroupMessageEntity>?) {
        taskExecutor.execute {
            if (!task.isSucceed || null == messageList) {
                if (messageSyncTaskList.remove(task)) {
                    task.executing = false
                    addTask(task)
                }

                syncNextTask()
            } else {
                if (existTask(task)) {
                    if (syncCallback.onOfflineMessageFetched(task.gid, messageList)) {
                        removeTask(task)
                        saveTasks()

                        val groupTaskClear = !isExistGroupTask(task.gid)
                        if (groupTaskClear) {
                            syncCallback.onOfflineMessageSyncFinished(task.gid)
                        }
                    } else {
                        task.parseFail()
                        removeTask(task)
                        addTask(task)
                    }

                    syncNextTask()
                } else {
                    syncNextTask()
                }
            }
        }
    }

    private fun existTask(task: GroupOfflineMessageSyncTask): Boolean {
        return messageSyncTaskList.contains(task)
    }

    private fun addTask(task: GroupOfflineMessageSyncTask) {
        messageSyncTaskList.add(task)
        sortTaskList()
    }

    private fun removeTask(task: GroupOfflineMessageSyncTask): Boolean {
        if (messageSyncTaskList.remove(task)) {
            sortTaskList()
            return true
        }
        return false
    }

    private fun sortTaskList() {
        messageSyncTaskList.sortWith(kotlin.Comparator { o1, o2 ->
            if (o1.executing && !o2.executing) {
                return@Comparator -1
            } else if (o2.executing && !o1.executing) {
                return@Comparator 1
            }
            return@Comparator (o1.delay - o2.delay).toInt()
        })
    }

    private fun endTask() {
        taskRunningCount = --taskRunningCount
        ALog.i(TAG, "end task $taskRunningCount, taskCount:${messageSyncTaskList.count()} canSync:${canSync()}")
    }

    private fun isExistGroupTask(gid: Long): Boolean {
        for (i in messageSyncTaskList) {
            if (i.gid == gid) {
                return true
            }
        }
        return false
    }

    private fun syncNextTask(fromInit: Boolean = false): Boolean {
        if (messageSyncTaskList.isNotEmpty() && canSync()) {
            for (i in messageSyncTaskList) {
                if (!i.executing && !i.isDead()) {
                    i.execute { task, messageList ->
                        finishTask(task, messageList)
                    }
                    return true
                }
            }
        }
        if (!fromInit) {
            endTask()
        }
        return false
    }

    fun doOnLogin() {
    }

    private fun saveTasks() {
        var output: FileOutputStream? = null
        var objStream: ObjectOutputStream? = null

        try {
            output = FileOutputStream(File(AMELogin.accountDir, SERIALIZABLE_FILE_NAME))
            objStream = ObjectOutputStream(output)
            objStream.writeObject(messageSyncTaskList)
        } catch (e: Exception) {
            ALog.e(TAG, "saveTasks 1", e)
        } finally {
            try {
                output?.close()
                objStream?.close()
            } catch (e: Exception) {
                ALog.e(TAG, "saveTasks 2", e)
            }
        }
    }

    private fun loadTasks() {
        var input: FileInputStream? = null
        var objStream: ObjectInputStream? = null

        try {
            input = FileInputStream(File(AMELogin.accountDir, SERIALIZABLE_FILE_NAME))
            objStream = ObjectInputStream(input)
            val taskList = objStream.readObject() as? ArrayList<*>
            if (null != taskList) {
                this.messageSyncTaskList.addAll(taskList.map {
                    val task = it as GroupOfflineMessageSyncTask
                    task.delay = 0
                    task.executing = false
                    task
                })
            }
        } catch (e: Exception) {
            ALog.e(TAG, "loadTasks 1", e)
        } finally {
            try {
                input?.close()
                objStream?.close()
            } catch (e: Exception) {
                ALog.e(TAG, "loadTasks 2", e)
            }
        }
    }


    fun syncJoinReq(gidList: List<Long>) {
        taskExecutor.execute {
            ALog.w(TAG, "syncJoinReq begin sync ${gidList.size}")

            if (joinReqOwnerTaskList.isEmpty() || joinReqMemberTaskList.isEmpty()) {
                val ownerListEmpty = joinReqOwnerTaskList.isEmpty()
                val memberListEmpty = joinReqMemberTaskList.isEmpty()

                for (gid in gidList) {
                    val groupInfo = GroupInfoDataManager.getGroupInfo(gid) ?: continue
                    if (!groupInfo.newGroup || groupInfo.owner == AMELogin.uid) {
                        if (groupInfo.role == AmeGroupMemberInfo.OWNER && groupInfo.needConfirm == true) {
                            if (ownerListEmpty) {
                                joinReqOwnerTaskList.add(GroupOfflineJoinReqMessageSyncTask(gid, 1000, true))
                            }
                        } else if (memberListEmpty) {
                            joinReqMemberTaskList.add(GroupOfflineJoinReqMessageSyncTask(gid, 10000, false))
                        }
                    }
                }
            } else {
                ALog.w(TAG, "syncJoinReq repeat request")
            }

            //I am the owner and I have opened the sync chain of the audit switch
            if (!joinReqOwnerSyncRunning && joinReqOwnerTaskList.isNotEmpty()) {
                joinReqOwnerSyncRunning = true
                syncOwnerJoinReqTask(joinReqOwnerTaskList.first())
            }

            //Synchronization chain without audit switch
            if (!joinReqMemberSyncRunning && joinReqMemberTaskList.isNotEmpty()) {
                joinReqMemberSyncRunning = true
                syncMemberJoinReqTask(joinReqMemberTaskList.first())
            }
        }
    }

    fun syncJoinReq(gid: Long) {
        taskExecutor.execute {
            ALog.w(TAG, "syncJoinReq begin sync $gid")

            val groupInfo = GroupInfoDataManager.getGroupInfo(gid) ?: return@execute
            if (!groupInfo.newGroup || groupInfo.owner == AMELogin.uid) {
                val needConfirm = groupInfo.role == AmeGroupMemberInfo.OWNER && groupInfo.needConfirm == true
                if (needConfirm) {
                    GroupOfflineJoinReqMessageSyncTask(gid, 300, needConfirm).execute {
                        ALog.i(TAG, "syncJoinReq  owner $gid finished")

                        if (it) {
                            syncCallback.onJoinRequestMessageSyncFinished(gid)
                        }
                    }
                } else {
                    GroupOfflineJoinReqMessageSyncTask(gid, 3000, needConfirm).execute {
                        ALog.i(TAG, "syncJoinReq member $gid finished")

                        if (it) {
                            syncCallback.onJoinRequestMessageSyncFinished(gid)
                        }
                    }
                }
            }
        }
    }

    private fun syncOwnerJoinReqTask(task: GroupOfflineJoinReqMessageSyncTask) {
        task.execute {
            syncOwnerJoinReqTaskFinish(task)
        }
    }

    private fun syncOwnerJoinReqTaskFinish(task: GroupOfflineJoinReqMessageSyncTask) {
        taskExecutor.execute {
            if (joinReqOwnerTaskList.remove(task)) {
                if (joinReqOwnerTaskList.isNotEmpty() && canSync()) {
                    syncOwnerJoinReqTask(joinReqOwnerTaskList.first())
                    ALog.i(TAG, "syncOwnerJoinReqTaskFinish left ${joinReqOwnerTaskList.size}")
                } else {
                    joinReqOwnerSyncRunning = false
                    ALog.i(TAG, "syncOwnerJoinReqTaskFinish all finished")
                }

                syncCallback.onJoinRequestMessageSyncFinished(task.gid)
            }
        }
    }

    private fun syncMemberJoinReqTask(task: GroupOfflineJoinReqMessageSyncTask) {
        task.execute {
            syncMemberJoinReqTaskFinish(task)
        }
    }

    private fun syncMemberJoinReqTaskFinish(task: GroupOfflineJoinReqMessageSyncTask) {
        taskExecutor.execute {
            if (joinReqMemberTaskList.remove(task)) {
                if (joinReqMemberTaskList.isNotEmpty() && canSync()) {
                    syncMemberJoinReqTask(joinReqMemberTaskList.first())
                    ALog.i(TAG, "syncMemberJoinReqTaskFinish left ${joinReqMemberTaskList.size}")
                } else {
                    joinReqMemberSyncRunning = false
                    ALog.i(TAG, "syncMemberJoinReqTaskFinish all finished")
                }
            }

            syncCallback.onJoinRequestMessageSyncFinished(task.gid)
        }
    }


    private fun canSync(): Boolean {
        return AmeModuleCenter.serverDaemon().state() == ConnectState.CONNECTED && AppForeground.foreground()
    }

    interface OfflineSyncCallback {
        fun onOfflineMessageFetched(gid: Long, messageList: List<GroupMessageEntity>): Boolean
        fun onOfflineMessageSyncFinished(gid: Long)
        fun onJoinRequestMessageSyncFinished(gid: Long)
    }
}