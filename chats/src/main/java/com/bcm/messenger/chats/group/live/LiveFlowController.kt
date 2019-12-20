package com.bcm.messenger.chats.group.live

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.LiveIconDeleteWindow
import com.bcm.messenger.chats.components.LiveIconFlowWindow
import com.bcm.messenger.chats.components.VideoFlowWindow
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.chats.util.analyseYoutubeUrl
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference

/**
 * Live floating windows management controller
 *
 * Created by Kin on 2018/12/19
 */
class LiveFlowController(activity: Activity, private val gid: Long, private val isGroupOwner: Boolean) {
    private val TAG = "LiveFlowController"

    private val activityRef = WeakReference(activity)
    private var videoFlowWindow: VideoFlowWindow? = null
    private var iconFlowWindow: LiveIconFlowWindow? = null
    private var iconDeleteWindow: LiveIconDeleteWindow? = null
    private var replayFlowWindow: ChatReplayFlowWindow? = null

    // Countdown to hide floating windows
    private val countdownRunnable = Runnable {
        ALog.i(TAG, "Countdown runnable run")
        AmeDispatcher.io.dispatch {
            GroupMessageLogic.systemNotice(gid, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_LIVE_END))
        }
        videoFlowWindow?.hide(false)
        iconFlowWindow?.setReplayMode()
        liveStatus = GroupLiveInfo.LiveStatus.STOPED.value
        isReplay = true
    }
    private val cellularRunnable = Runnable {
        ToastUtil.show(AppContextHolder.APP_CONTEXT, AppContextHolder.APP_CONTEXT.getString(R.string.chats_live_using_mobile_network_toast))
    }

    private var liveInfo = GroupLiveInfo()
    private var playUrl = ""
    private var liveStatus = GroupLiveInfo.LiveStatus.EMPTY.value
    private var seekTime = 0L
    private var lastActionTime = 0L
    private var sizeEvent: VideoSizeChangeEvent? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isReplay = false

    data class VideoSizeChangeEvent(val liveId: Long, val width: Int, val height: Int)

    init {
        EventBus.getDefault().register(this)
        GroupLiveInfoManager.getInstance().registerCurrentPlayingGid(gid)
    }

    /**
     * Show video floating window to play videos
     *
     * @param windowSize Specify floating window size, default is small size
     */
    private fun showVideoFlowWindow(windowSize: Int = SIZE_SMALL, unsupported: Boolean = false) {
        ALog.i(TAG, "Show video floating window")
        activityRef.get()?.let {
            val builder = VideoFlowWindow.Builder(it)
                    .setMediaUri(Uri.parse(playUrl))
                    .setLiveID(liveInfo.liveId)
                    .setCurrentPosition(if (liveStatus == GroupLiveInfo.LiveStatus.PAUSE.value)
                        seekTime else AmeTimeUtil.serverTimeMillis() - lastActionTime + seekTime)
                    .setMode(VideoFlowWindow.MODE_STREAM)
                    .setIsGroupOwner(isGroupOwner)
                    .setListener(videoFlowWindowListener)
                    .setWindowSizeMode(windowSize)
                    .setAutoPlay(liveStatus == GroupLiveInfo.LiveStatus.LIVING.value)
            sizeEvent?.let { event ->
                builder.setVideoResolution(event.width, event.height)
            }
            if (unsupported) {
                builder.setUnsupported()
            }
            videoFlowWindow = builder.build()
            videoFlowWindow?.show()
            if (AppUtil.isMobileNetwork(AppContextHolder.APP_CONTEXT)) {
                handler.postDelayed(cellularRunnable, 3000)
            }
        }
    }

    /**
     * Show streaming icon floating window
     */
    private fun showIconFlowWindow() {
        ALog.i(TAG, "Show icon floating window")
        if (iconFlowWindow != null) return
        activityRef.get()?.let {
            iconFlowWindow?.hide(false)
            iconDeleteWindow?.dismiss(false)
            iconFlowWindow = LiveIconFlowWindow(it, isGroupOwner, liveStatus == GroupLiveInfo.LiveStatus.STOPED.value)
            iconFlowWindow?.setListener(iconFlowWindowListener)
            if (liveStatus == GroupLiveInfo.LiveStatus.LIVING.value) {
                startCountdown()
            }
            if (isGroupOwner) {
                addDeleteWindow()
            }
        }
    }

    private fun showReplayFlowWindow() {
        ALog.i(TAG, "Show replay floating window")
        activityRef.get()?.let {
            replayFlowWindow = ChatReplayFlowWindow(it, playUrl, isGroupOwner)
            replayFlowWindow?.setReplayListener(replayFlowListener)
        }
    }

    private fun addDeleteWindow() {
        ALog.i(TAG, "Add icon delete window")
        activityRef.get()?.let {
            iconDeleteWindow = LiveIconDeleteWindow(it)
        }
    }

    private fun showDeleteWindow() {
        ALog.i(TAG, "Show icon delete window")
        iconDeleteWindow?.show()
    }

    private fun hideDeleteWindow() {
        ALog.i(TAG, "Hide icon delete window")
        iconDeleteWindow?.hide()
    }

    private val videoFlowWindowListener = object : VideoFlowWindow.VideoFlowListener {
        override fun onHide(ended: Boolean) {
            // Video floating window hided, show icon floating window
            ALog.i(TAG, "Video floating window callback on hide")
            isReplay = ended
            videoFlowWindow = null
            if (ended) {
                liveStatus = GroupLiveInfo.LiveStatus.STOPED.value
                AmeDispatcher.io.dispatch {
                    GroupMessageLogic.systemNotice(gid, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_LIVE_END))
                }
            }
            showIconFlowWindow()
        }

        override fun onPause(timeMillis: Long) {
            ALog.i(TAG, "Video floating window callback on pause, pause position is $timeMillis")
            seekTime = timeMillis
            lastActionTime = AmeTimeUtil.serverTimeMillis()
            liveStatus = GroupLiveInfo.LiveStatus.PAUSE.value
            GroupMessageLogic.messageSender.sendPauseLiveMessage(gid, playUrl, AmeGroupMessage.LiveContent.PlaySource(liveInfo.source_type, liveInfo.source_url), liveInfo.liveId, liveInfo.duration, timeMillis, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                        AmeDispatcher.io.dispatch {
                            GroupLiveInfoManager.getInstance().updateWhenSendLiveMessage(liveInfo.id, messageDetail.message.content as AmeGroupMessage.LiveContent)
                        }
                    }
                }
            })
        }

        override fun onResume() {
            ALog.i(TAG, "Video floating window callback on resume")
            lastActionTime = AmeTimeUtil.serverTimeMillis()
            liveStatus = GroupLiveInfo.LiveStatus.LIVING.value
            GroupMessageLogic.messageSender.sendRestartLiveMessage(gid, playUrl, AmeGroupMessage.LiveContent.PlaySource(liveInfo.source_type, liveInfo.source_url), liveInfo.liveId, liveInfo.duration, seekTime, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                        AmeDispatcher.io.dispatch {
                            GroupLiveInfoManager.getInstance().updateWhenSendLiveMessage(liveInfo.id, messageDetail.message.content as AmeGroupMessage.LiveContent)
                        }
                    }
                }
            })
        }

        override fun onStop() {
            ALog.i(TAG, "Video floating window callback on stop")
            iconDeleteWindow?.dismiss(false)
            iconFlowWindow?.hide(false)
            stopCountdown()
            GroupMessageLogic.messageSender.sendEndLiveMessage(true, gid, playUrl, AmeGroupMessage.LiveContent.PlaySource(liveInfo.source_type, liveInfo.source_url), liveInfo.liveId, liveInfo.duration, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    if (isSuccess) {
                        AmeDispatcher.io.dispatch {
                            if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                                GroupLiveInfoManager.getInstance().updateWhenSendLiveMessage(liveInfo.id, messageDetail.message.content as AmeGroupMessage.LiveContent)
                            }
                        }
                    }
                }
            })
        }
    }

    private val iconFlowWindowListener = object : LiveIconFlowWindow.LiveIconFlowListener {
        override fun onHide() {
            // Icon floating window hided, show video play window and stop count down
            ALog.i(TAG, "Icon floating window callback on hide")
            iconDeleteWindow?.dismiss(false)
            iconFlowWindow = null
            iconDeleteWindow = null
            showVideoFlowWindow(SIZE_SMALL)
            stopCountdown()
        }

        override fun onReplayHide() {
            ALog.i(TAG, "Icon floating window callback on replay hide")
            showReplayFlowWindow()
            iconDeleteWindow?.dismiss(false)
            iconFlowWindow = null
            iconDeleteWindow = null
        }

        override fun onMove(isMoving: Boolean) {
            ALog.i(TAG, "Icon floating window callback on move")
            if (isGroupOwner) {
                if (isMoving) {
                    showDeleteWindow()
                } else {
                    hideDeleteWindow()
                }
            }
        }

        override fun onDelete() {
            ALog.i(TAG, "Icon floating window callback on delete")
            iconDeleteWindow?.dismiss(false)
            videoFlowWindow?.hide(false)
            stopCountdown()
            GroupMessageLogic.messageSender.sendEndLiveMessage(!isReplay, gid, playUrl, AmeGroupMessage.LiveContent.PlaySource(liveInfo.source_type, liveInfo.source_url), liveInfo.liveId, liveInfo.duration, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    if (isSuccess) {
                        AmeDispatcher.io.dispatch {
                            if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                                GroupLiveInfoManager.getInstance().updateWhenSendLiveMessage(liveInfo.id, messageDetail.message.content as AmeGroupMessage.LiveContent)
                            }
                        }
                    }
                }
            })
        }
    }

    private val replayFlowListener = object : ChatReplayFlowWindow.ChatReplayFlowListener {
        override fun onHide() {
            ALog.i(TAG, "Replay window callback on hide")
            showIconFlowWindow()
            replayFlowWindow = null
        }

        override fun onRemove() {
            ALog.i(TAG, "Replay window callback on remove")
            GroupMessageLogic.messageSender.sendEndLiveMessage(false, gid, playUrl, AmeGroupMessage.LiveContent.PlaySource(liveInfo.source_type, liveInfo.source_url), liveInfo.liveId, liveInfo.duration, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    if (isSuccess) {
                        AmeDispatcher.io.dispatch {
                            if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                                GroupLiveInfoManager.getInstance().updateWhenSendLiveMessage(liveInfo.id, messageDetail.message.content as AmeGroupMessage.LiveContent)
                            }
                        }
                    }
                }
            })
        }
    }


    /**
     * Handle GroupLiveEvent
     */
    private fun handleEventChanged(info: GroupLiveInfo) {
        if (info.gid == gid) {
            if (GroupLogic.getGroupInfo(gid)?.role == AmeGroupMemberInfo.VISITOR) {
                return
            }
            if (info.liveId != liveInfo.liveId) {
                ALog.i(TAG, "Receive a new live, url is ${info.source_url}, duration is ${liveInfo.duration}, live status is ${liveInfo.liveStatus}")
                liveInfo = info
                liveStatus = info.liveStatus
                lastActionTime = liveInfo.currentActionTime
                seekTime = info.currentSeekTime

                videoFlowWindow?.hide(false)
                iconFlowWindow?.hide(false)
                replayFlowWindow?.hide(false)
                iconDeleteWindow?.dismiss(false)
                videoFlowWindow = null
                replayFlowWindow = null
                iconFlowWindow = null

                when (info.liveStatus) {
                    GroupLiveInfo.LiveStatus.LIVING.value -> {
                        ALog.i(TAG, "New live is living")
                        // Start playing video
                        showVideoFlowWindow(SIZE_SMALL, info.source_type == GroupLiveInfo.LiveSourceType.Unsupported.value)
                        isReplay = false
                    }
                    GroupLiveInfo.LiveStatus.PAUSE.value -> {
                        ALog.i(TAG, "New live is pause")
                        showVideoFlowWindow(SIZE_SMALL)
                        isReplay = false
                    }
                    GroupLiveInfo.LiveStatus.STOPED.value -> {
                        ALog.i(TAG, "New live is stopped")
                        showIconFlowWindow()
                        iconFlowWindow?.setReplayMode()
                        isReplay = true
                    }
                }
            } else {
                ALog.i(TAG, "Receive a same live, url is ${info.source_url}, duration is ${liveInfo.duration}, live status is ${liveInfo.liveStatus}")
                liveInfo = info
                liveStatus = info.liveStatus
                lastActionTime = liveInfo.currentActionTime
                seekTime = info.currentSeekTime

                when (info.liveStatus) {
                    GroupLiveInfo.LiveStatus.LIVING.value -> {
                        ALog.i(TAG, "Old live is living")

                        if (videoFlowWindow != null) {
                            val time = AmeTimeUtil.serverTimeMillis() - lastActionTime + seekTime
                            videoFlowWindow?.playVideo(time)
                        } else {
                            startCountdown()
                        }
                        isReplay = false
                    }
                    GroupLiveInfo.LiveStatus.PAUSE.value -> {
                        ALog.i(TAG, "Old live is paused")

                        videoFlowWindow?.pauseVideo(info.currentSeekTime)
                        stopCountdown()
                        isReplay = false
                    }
                    GroupLiveInfo.LiveStatus.STOPED.value -> {
                        ALog.i(TAG, "Old live is stop")
                        videoFlowWindow?.hide(false)
                        if (replayFlowWindow != null ) {
                            replayFlowWindow?.playVideo()
                        } else {
                            showIconFlowWindow()
                            iconFlowWindow?.setReplayMode()
                        }
                        isReplay = true
                    }
                    GroupLiveInfo.LiveStatus.REMOVED.value -> {
                        ALog.i(TAG, "Old live is removed")

                        videoFlowWindow?.hide(false)
                        iconFlowWindow?.hide(false)
                        iconDeleteWindow?.dismiss(false)
                        replayFlowWindow?.videoRemoved()
                        isReplay = false
                    }
                }
            }
        }
    }

    fun muteVideo() {
        if (isReplay) {
            replayFlowWindow?.mute()
        } else {
            videoFlowWindow?.mute()
        }
    }

    fun unMuteVideo() {
        if (isReplay) {
            replayFlowWindow?.unMute()
        } else {
            videoFlowWindow?.unMute()
        }
    }

    fun isLiveInFullScreen() = videoFlowWindow?.isVideoPlaying() == true && videoFlowWindow?.getCurrentSizeMode() == SIZE_FULLSCREEN

    fun switchToSmallSize() {
        videoFlowWindow?.switchToSmallSize()
        replayFlowWindow?.switchToSmallSize()
    }

    /**
     * Start countdown, when time goes to 0, hide all floating windows
     */
    private fun startCountdown() {
        val time = liveInfo.duration - (AmeTimeUtil.serverTimeMillis() - lastActionTime) - seekTime
        handler.postDelayed(countdownRunnable, time)
    }

    /**
     * Stop countdown
     */
    private fun stopCountdown() {
        handler.removeCallbacks(countdownRunnable)
    }

    private fun loadLiveInfo() {
        Observable.create<GroupLiveInfo> {
            var liveInfo = GroupLiveInfoManager.getInstance().getCurrentPlaybackInfo(gid)
            if (liveInfo != null) {
                it.onNext(liveInfo)
            } else {
                liveInfo = GroupLiveInfoManager.getInstance().getCurrentLiveInfo(gid)
                if (liveInfo != null) {
                    it.onNext(liveInfo)
                }
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    AmeDispatcher.mainThread.dispatch({
                        if (it.source_type == GroupLiveInfo.LiveSourceType.Youtube.value) {
                            ALog.i(TAG, "Live source is a YouTube link, link is ${it.source_url}")
                            analyseYoutubeUrl(it.source_url) { _, realUrl, _ ->
                                playUrl = realUrl
                                handleEventChanged(it)
                            }
                        } else {
                            ALog.i(TAG, "Live source is a normal link, link is ${it.source_url}")
                            playUrl = it.source_url
                            handleEventChanged(it)
                        }
                    }, 300)
                }, {
                    it.printStackTrace()
                })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupLiveInfo) {
        ALog.i(TAG, "Receive live event from EventBus")
        if (!isGroupOwner) {
            if (event.source_type == GroupLiveInfo.LiveSourceType.Youtube.value) {
                analyseYoutubeUrl(event.source_url) { _, realUrl, _ ->
                    playUrl = realUrl
                    handleEventChanged(event)
                }
            } else {
                handleEventChanged(event)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: VideoSizeChangeEvent) {
        sizeEvent = event
    }

    fun addChatFlowMessage(messageDetail: AmeGroupMessageDetail) {
        videoFlowWindow?.addChatFlowMessage(messageDetail)
    }

    /**
     * Invoke when activity onPause
     */
    fun onPause() {

    }

    /**
     * Invoke when activity onResume
     */
    fun onResume() {
        loadLiveInfo()
    }

    /**
     * Invoke when activity onStop
     */
    fun onStop() {
        ALog.i(TAG, "Controller on stop")
        if (!AppForeground.foreground()) {
            lastActionTime = AmeTimeUtil.serverTimeMillis()
            videoFlowWindow?.pauseVideoNoAnim()
            replayFlowWindow?.pauseVideo()
        }
    }

    /**
     * Invoke when activity onDestroy
     */
    fun onDestroy() {
        ALog.i(TAG, "Controller on clear")
        EventBus.getDefault().unregister(this)
        GroupLiveInfoManager.getInstance().unRegisterCurrentPlayingGid(gid)
        if (liveStatus == GroupLiveInfo.LiveStatus.LIVING.value) {
            AmeDispatcher.io.dispatch {
                val info = GroupLiveInfoManager.getInstance().getCurrentLiveInfo(gid)
                if (info?.liveStatus == GroupLiveInfo.LiveStatus.STOPED.value) {
                    GroupMessageLogic.systemNotice(gid, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_LIVE_END))
                }
            }
        }
        handler.removeCallbacks(cellularRunnable)
        stopCountdown()
        activityRef.clear()
        videoFlowWindow?.hide(false)
        replayFlowWindow?.hide(false)
        iconFlowWindow?.hide(false)
        iconDeleteWindow?.dismiss(false)
        videoFlowWindow = null
        replayFlowWindow = null
        iconFlowWindow = null
        iconDeleteWindow = null
    }

    fun onConfigurationChanged(newConfig: Configuration?) {
        if (newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE || newConfig?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isReplay) {
                if (replayFlowWindow?.getCurrentSizeMode() == SIZE_FULLSCREEN) {
                    replayFlowWindow?.switchToFullscreen()
                }
            } else {
                if (videoFlowWindow?.getCurrentSizeMode() == SIZE_FULLSCREEN) {
                    videoFlowWindow?.switchToFullScreenSize()
                }
            }
        }
    }
}