package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.live.BaseLiveFlowWindow
import com.bcm.messenger.chats.group.live.LiveFlowController
import com.bcm.messenger.chats.group.live.SIZE_FULLSCREEN
import com.bcm.messenger.chats.group.live.SIZE_SMALL
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.VideoSlide
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.video.LiveVideoPlayer
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.Util
import org.greenrobot.eventbus.EventBus
import java.util.*

/**
 * Created by Kin on 2018/12/5
 */
class VideoFlowWindow private constructor(private val context: Context, private val profile: VideoSettingProfile) : BaseLiveFlowWindow(context) {
    companion object {
        private const val TAG = "VideoFlowWindow"

        const val MODE_LOCAL = 0
        const val MODE_STREAM = 1
        const val MODE_M3U8 = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var params: WindowManager.LayoutParams
    private var sizeMode = SIZE_FULLSCREEN
    private var showingIcons = true
    private var firstLoad = true
    private val formatBuilder = StringBuilder()
    private val formatter = Formatter(formatBuilder, Locale.getDefault())
    private var isShowing = true
    private var isMuted = false

    private val rootView = LayoutInflater.from(context).inflate(R.layout.chats_video_flow_window, null) as ConstraintLayout
    private val coverView = rootView.findViewById<FlowWindowCoverLayout>(R.id.flow_cover_view)
    private val videoPlayer = rootView.findViewById<LiveVideoPlayer>(R.id.flow_video_player)
    private val closeButton = rootView.findViewById<ImageView>(R.id.flow_close)
    private val smallButton = rootView.findViewById<ImageView>(R.id.flow_small)
    private val muteIcon = rootView.findViewById<ImageView>(R.id.flow_mute)
    private val largeMuteIcon = rootView.findViewById<ImageView>(R.id.flow_mute_large)
    private val loadingView = rootView.findViewById<ImageView>(R.id.flow_loading_view)
    private val positionView = rootView.findViewById<TextView>(R.id.flow_position)
    private val messageButton = rootView.findViewById<ImageView>(R.id.flow_message_switch)
    private val chatButton = rootView.findViewById<ImageView>(R.id.flow_chat)
    private val pauseLayout = rootView.findViewById<LinearLayout>(R.id.flow_pause_cover_layout)
    private val pauseText = rootView.findViewById<TextView>(R.id.flow_pause_text)
    private val pauseOwnerText = rootView.findViewById<TextView>(R.id.flow_pause_owner_text)
    private val chatsFlowLayout = rootView.findViewById<FlowChatLayout>(R.id.flow_chats_layout)
    private val removeButton = rootView.findViewById<ImageView>(R.id.flow_remove)
    private val unsupportedText = rootView.findViewById<TextView>(R.id.flow_unsupport_text)

    private val loadingAnim = ObjectAnimator.ofFloat(loadingView, "rotation", 0f, 360f).apply {
        duration = 500
        repeatCount = -1
    }
    private val pauseAnim = ObjectAnimator.ofFloat(pauseLayout, "alpha", 0f, 1f).apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                pauseLayout.visibility = View.VISIBLE
                if (!profile.isGroupOwner && sizeMode == SIZE_FULLSCREEN) {
                    pauseOwnerText.visibility = View.VISIBLE
                }
            }
        })
    }
    private val resumeAnim = ObjectAnimator.ofFloat(pauseLayout, "alpha", 1f, 0f).apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                pauseLayout?.visibility = View.GONE
                pauseOwnerText?.visibility = View.GONE
            }
        })
    }
    private val hideAnim = ObjectAnimator.ofFloat(rootView, "translationX", 0.0f, 500f).apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                hide()
            }
        })
    }
    private val hideAnimLeft = ObjectAnimator.ofFloat(rootView, "translationX", 0.0f, -500f).apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                hide()
            }
        })
    }

    private val hideRunnable = Runnable {
        hideIcons()
    }

    private val positionChangeListener = object : FlowWindowCoverLayout.OnViewPositionChangedListener {
        override fun onViewPositionChanged(x: Int, y: Int) {
            if (sizeMode == SIZE_SMALL) onDragSmallWindow(x, y)
            else if (sizeMode == SIZE_FULLSCREEN) onDragFullWindow(y)
        }

        override fun onViewPositionChangedEnd(x: Int, y: Int) {
            if (sizeMode == SIZE_SMALL) onDragSmallWindowEnded(x, y)
            else if (sizeMode == SIZE_FULLSCREEN) onDragFullWindowEnded(y)
        }

        private fun onDragSmallWindow(x: Int, y: Int) {
            params.x = x
            params.y = y
            manager.updateViewLayout(rootView, params)
        }

        private fun onDragFullWindow(y: Int) {
            val smoothY = (rootView.top + y).toFloat()
            setViewY(smoothY)

            val percentage = 1 - y.toFloat() / rootView.height
            var alpha = Math.min((255 * percentage).toInt(), 255)
            if (alpha < 50) alpha = 0
            rootView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            pauseLayout.setBackgroundColor(context.getColorCompat(R.color.common_color_transparent))
            if (showingIcons && (1 - percentage) > 0.02f) {
                hideIcons()
            }

            val maxScale = Math.min(percentage, 1f)
            val minScale = Math.max(0.2f, maxScale)
            setViewScale(minScale)

            rootView.invalidate()
        }

        private fun onDragFullWindowEnded(y: Int) {
            setViewY(0f)

            setViewScale(1f)
            rootView.setBackgroundColor(Color.argb(255, 0, 0, 0))
            pauseLayout.setBackgroundColor(context.getColorCompat(R.color.common_30_transparent))
            rootView.invalidate()
            if (y.toFloat() / rootView.height > 0.2f) {
                switchToSmallSize()
            } else {
                enterFullScreenMode()
            }
        }

        private fun onDragSmallWindowEnded(x: Int, y: Int) {
            when {
                x < -params.width * 1 / 3 -> hideAnimLeft.start()
                x > AppUtil.getRealScreenWidth() - params.width * 2 / 3 -> hideAnim.start()
                else -> stickyToCorner(rootView, x, y, params)
            }
        }

        private fun setViewY(currentY: Float) {
            coverView.y = currentY
            videoPlayer.y = currentY
            pauseLayout.y = currentY
            loadingView.y = (params.height - loadingView.height) / 2.toFloat() + currentY
        }

        private fun setViewScale(scale: Float) {
            coverView.scaleX = scale
            coverView.scaleY = scale
            videoPlayer.scaleX = scale
            videoPlayer.scaleY = scale
            pauseLayout.scaleX = scale
            pauseLayout.scaleY = scale
            loadingView.scaleX = scale
            loadingView.scaleY = scale
        }
    }

    init {
    }

    private fun initView() {
        ALog.i(TAG, "Init video floating window")
        coverView.setListener(positionChangeListener)
        closeButton.setOnClickListener {
            // Hide floating window and show icon floating window
            hide()
            startControllerCountdown()
        }
        smallButton.setOnClickListener {
            // Switch to small size and hide icons
            switchToSmallSize()
            startControllerCountdown()
        }
        muteIcon.setOnClickListener {
            muteOrUnMute()
        }
        largeMuteIcon.setOnClickListener {
            muteOrUnMute()
            startControllerCountdown()
        }
        removeButton.setOnClickListener {
            AmePopup.bottom.newBuilder()
                    .withTitle(context.getString(R.string.chats_live_flow_stop_title))
                    .withPopItem(AmeBottomPopup.PopupItem(context.getString(R.string.chats_live_flow_stop_stop)) {
                        profile.listener?.onStop()
                        hide(false)
                    })
                    .withDoneTitle(context.getString(R.string.chats_cancel))
                    .withDoneAction {
                        enterFullScreenMode()
                    }
                    .withCancelable(false)
                    .show(context as? FragmentActivity)

            startControllerCountdown()
        }

        if (profile.unsupported) {
            unsupportedText.visibility = View.VISIBLE
        } else {
            initVideoPlayer()
        }
        hideIcons()
        if (profile.windowSize == SIZE_FULLSCREEN) {
            sizeMode = SIZE_FULLSCREEN
            switchToFullScreenSize()
        } else {
            sizeMode = SIZE_SMALL
            switchToSmallSize()
        }
        muteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
        largeMuteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
        videoPlayer.muteAudio()
        messageButton.setOnClickListener {
            if (chatsFlowLayout.visibility == View.VISIBLE) {
                chatsFlowLayout.visibility = View.GONE
                messageButton.setImageResource(R.drawable.chats_flow_message_off)
            } else {
                chatsFlowLayout.visibility = View.VISIBLE
                messageButton.setImageResource(R.drawable.chats_flow_message_on)
            }
            startControllerCountdown()
        }
    }

    private fun initVideoPlayer() {
        when (profile.mode) {
            MODE_LOCAL -> {
                ALog.i(TAG, "Video floating window playing local video")
                if (profile.masterSecret != null && profile.uri != null) {
                    videoPlayer.setVideoSource(profile.masterSecret!!, VideoSlide(context, profile.uri, profile.size), profile.autoPlay)
                }
            }
            MODE_STREAM -> {
                ALog.i(TAG, "Video floating window playing stream video")
                if (profile.uri != null) {
                    videoPlayer.setStreamingVideoSource(profile.uri.toString(), profile.autoPlay)
                }
            }
            MODE_M3U8 -> {
                ALog.i(TAG, "Video floating window playing m3u8 video")
                if (profile.uri != null) {
                    videoPlayer.setM3U8VideoSource(profile.uri.toString(), profile.autoPlay)
                }
            }
        }
        videoPlayer.seekTo(profile.currentPos)
        videoPlayer.setVideoStateChangeListener {
            when (it) {
                Player.STATE_READY -> {
                    ALog.i(TAG, "Video ready to play")
                    if (firstLoad) {
                        ALog.i(TAG, "Video is first loaded, adjust window size")
                        firstLoad = false
                        val format = videoPlayer.getVideoFormat()
                        profile.width = format?.width ?: 640
                        profile.height = format?.height ?: 360
                        EventBus.getDefault().post(LiveFlowController.VideoSizeChangeEvent(profile.liveId, format?.width ?: 640, format?.height ?: 360))
                        if (sizeMode == SIZE_SMALL) {
                            switchToSmallSize()
                        }
                        if (profile.isGroupOwner) {
                            setPlayOnClick()
                        }
                        if (profile.autoPlay) {
                            hideIcons()
                        } else {
                            pauseVideo()
                        }
                    }
                    loadingView.visibility = View.GONE
                    loadingAnim.cancel()
                }
                Player.STATE_ENDED -> {
                    ALog.i(TAG, "Video ended")
                    if (isShowing) {
                        hide(true, true)
                    }
                }
                Player.STATE_BUFFERING -> {
                    ALog.i(TAG, "Video is buffering")
                    loadingView.visibility = View.VISIBLE
                    loadingAnim.start()
                }
                Player.STATE_IDLE -> {
                    ALog.i(TAG, "Video is idle")
                }
            }
        }
        videoPlayer.setPlayPositionChangeListener {
            positionView.text = Util.getStringForTime(formatBuilder, formatter, it)
        }
        loadingView.visibility = View.VISIBLE
        loadingAnim.start()
    }

    /**
     * Switch to small size floating window
     */
    fun switchToSmallSize() {
        ALog.i(TAG, "Window switch to small size")
        setActivityPortrait()
        exitFullScreenMode()
        hideIcons()

        params = switchToSmallSize(rootView, profile.width, profile.height)

        sizeMode = SIZE_SMALL
        muteIcon.visibility = View.VISIBLE

        coverView.enableDrag(true)
        coverView.setOnClickListener {
            switchToFullScreenSize()
        }

        pauseText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        pauseOwnerText.visibility = View.GONE
        chatsFlowLayout.clear()
        chatsFlowLayout.visibility = View.GONE
    }

    fun switchToFullScreenSize() {
        enterFullScreenMode()
        setActivityLandscape()

        params = switchToFullscreen(rootView)

        sizeMode = SIZE_FULLSCREEN
        muteIcon.visibility = View.GONE

        coverView.enableDrag(false)
        coverView.setOnClickListener {
            if (showingIcons) {
                hideIcons()
            } else {
                showIcons()
            }
        }

        pauseText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
        if (!profile.isGroupOwner) pauseOwnerText.visibility = View.VISIBLE
        chatsFlowLayout.visibility = View.VISIBLE
    }

    /**
     * Show operation icons
     */
    private fun showIcons() {
        ALog.i(TAG, "Window show icons")
        showingIcons = true
        smallButton.visibility = View.VISIBLE
        largeMuteIcon.visibility = View.VISIBLE
        positionView.visibility = View.VISIBLE
        if (profile.isGroupOwner) {
            removeButton.visibility = View.VISIBLE
        }
        messageButton.visibility = View.VISIBLE
        startControllerCountdown()
    }

    /**
     * Hide operation icons
     */
    private fun hideIcons() {
        ALog.i(TAG, "Window hide icons")
        showingIcons = false
//        closeButton.visibility = View.GONE
        smallButton.visibility = View.GONE
        largeMuteIcon.visibility = View.GONE
        positionView.visibility = View.GONE
        removeButton.visibility = View.GONE
        messageButton.visibility = View.GONE
//        chatButton.visibility = View.GONE
        stopControllerCountdown()
    }

    private fun startControllerCountdown() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 2000)
    }

    private fun stopControllerCountdown() {
        handler.removeCallbacks(hideRunnable)
    }

    /**
     * Mute or cancel mute
     */
    private fun muteOrUnMute() {
        if (videoPlayer.isMuted()) {
            ALog.i(TAG, "Unmute video")
            videoPlayer.unMuteAudio()
            muteIcon.setImageResource(R.drawable.chats_flow_unmute_icon)
            largeMuteIcon.setImageResource(R.drawable.chats_flow_unmute_icon)
            SuperPreferences.setFlowtingWindowMuted(context, false)
        } else {
            ALog.i(TAG, "Mute video")
            videoPlayer.muteAudio()
            muteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
            largeMuteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
            SuperPreferences.setFlowtingWindowMuted(context, true)
        }
    }

    private fun setPlayOnClick() {
        val pauseDrawable = context.getDrawable(R.drawable.chats_flow_pause_icon).apply {
            setBounds(0, 0, 24.dp2Px(), 24.dp2Px())
        }
        positionView.setCompoundDrawables(pauseDrawable, null, null, null)
        positionView.setOnClickListener {
            if (videoPlayer.isPlaying()) {
                AmePopup.bottom.newBuilder()
                        .withTitle(context.getString(R.string.chats_live_pause_live_title))
                        .withPopItem(AmeBottomPopup.PopupItem(context.getString(R.string.chats_live_pause_pause)) {
                            profile.listener?.onPause(videoPlayer.getCurrentPosition())
                            pauseVideo()
                        })
                        .withDoneTitle(context.getString(R.string.chats_cancel))
                        .withDismissListener {
                            enterFullScreenMode()
                        }
                        .show(context as? FragmentActivity)
            } else {
                profile.listener?.onResume()
                playVideo()
            }
            handler.removeCallbacks(hideRunnable)
        }
    }

    fun pauseVideo(currentPos: Long = -1L) {
        videoPlayer.stopVideo()
        if (currentPos != -1L) {
            videoPlayer.seekTo(currentPos)
        }
        if (profile.isGroupOwner) {
            val playDrawable = context.getDrawable(R.drawable.chats_flow_play_icon).apply {
                setBounds(0, 0, 24.dp2Px(), 24.dp2Px())
            }
            positionView.setCompoundDrawables(playDrawable, null, null, null)
        }
        pauseAnim.start()
    }

    fun pauseVideoNoAnim() {
        videoPlayer.stopVideo()
        if (profile.isGroupOwner) {
            val playDrawable = context.getDrawable(R.drawable.chats_flow_play_icon).apply {
                setBounds(0, 0, 24.dp2Px(), 24.dp2Px())
            }
            positionView.setCompoundDrawables(playDrawable, null, null, null)
        }
    }

    fun playVideo(currentPos: Long = -1L) {
        if (currentPos != -1L) {
            videoPlayer.seekTo(currentPos)
        }
        if (profile.isGroupOwner) {
            val pauseDrawable = context.getDrawable(R.drawable.chats_flow_pause_icon).apply {
                setBounds(0, 0, 24.dp2Px(), 24.dp2Px())
            }
            positionView.setCompoundDrawables(pauseDrawable, null, null, null)
        }
        resumeAnim.start()
        videoPlayer.playVideo()
    }

    fun mute() {
        isMuted = videoPlayer.isMuted()
        if (!isMuted) {
            muteOrUnMute()
        }
    }

    fun unMute() {
        if (!isMuted) {
            muteOrUnMute()
        }
    }

    fun getCurrentSizeMode() = sizeMode

    fun isVideoPlaying() = videoPlayer.isPlaying()

    /**
     * Show floating window
     */
    fun show() {
        initView()
    }

    /**
     * Hide floating window
     *
     * @param callback enable hide callback
     */
    fun hide(callback: Boolean = true, ended: Boolean = false) {
        ALog.i(TAG, "Hide video floating window")
        isShowing = false
//        VolumeChangeObserver.unregisterReceiver()
        exitFullScreenMode()
        setActivityPortrait()
        try {
            videoPlayer.stopVideo()
            videoPlayer.cleanup()
        } catch (e: Exception) {
            ALog.logForSecret(TAG, "Hide error", e)
        } finally {
            if (rootView.parent != null) {
                manager.removeViewImmediate(rootView)
            }
            if (callback) {
                profile.listener?.onHide(ended)
            }
        }
    }

    fun addChatFlowMessage(messageDetail: AmeGroupMessageDetail) {
        if (sizeMode == SIZE_FULLSCREEN) {
            chatsFlowLayout.addMessage(messageDetail)
        }
    }

    class Builder(private val activity: Activity) {
        private val profile = VideoSettingProfile()

        fun setMediaUri(uri: Uri): Builder {
            profile.uri = uri
            return this
        }

        fun setLiveID(liveId: Long): Builder {
            profile.liveId = liveId
            return this
        }

        fun setMode(mode: Int): Builder {
            profile.mode = mode
            return this
        }

        fun setCurrentPosition(position: Long): Builder {
            profile.currentPos = position
            return this
        }

        fun setMasterSecret(masterSecret: MasterSecret): Builder {
            profile.masterSecret = masterSecret
            return this
        }

        fun setVideoSize(size: Long): Builder {
            profile.size = size
            return this
        }

        fun setVideoResolution(width: Int, height: Int): Builder {
            profile.width = width
            profile.height = height
            return this
        }

        fun setIsGroupOwner(isGroupOwner: Boolean): Builder {
            profile.isGroupOwner = isGroupOwner
            return this
        }

        fun setWindowSizeMode(mode: Int): Builder {
            profile.windowSize = mode
            return this
        }

        fun setAutoPlay(autoPlay: Boolean): Builder {
            profile.autoPlay = autoPlay
            return this
        }

        fun setUnsupported(): Builder {
            profile.unsupported = true
            return this
        }

        fun setListener(listener: VideoFlowListener): Builder {
            profile.listener = listener
            return this
        }

        fun build(): VideoFlowWindow {
            return VideoFlowWindow(activity, profile)
        }
    }

    private class VideoSettingProfile {
        // Video mode
        var mode: Int = 0
        // Live ID
        var liveId = 0L
        // Video uri
        var uri: Uri? = null
        // Current play position
        var currentPos = 0L
        // MasterSecret
        var masterSecret: MasterSecret? = null
        // Video size
        var size = 0L
        // Video origin width
        var width = AppUtil.getScreenWidth(AppContextHolder.APP_CONTEXT)
        // Video origin height
        var height = AppUtil.dp2Px(AppContextHolder.APP_CONTEXT.resources, 202)
        // Is group owner
        var isGroupOwner = false
        // Auto play video
        var autoPlay = false
        // Window size
        var windowSize = SIZE_FULLSCREEN
        // Unsupported type
        var unsupported = false
        // Listener
        var listener: VideoFlowListener? = null
    }

    interface VideoFlowListener {
        fun onHide(ended: Boolean = false)
        fun onPause(timeMillis: Long)
        fun onResume()
        fun onStop()
    }
}