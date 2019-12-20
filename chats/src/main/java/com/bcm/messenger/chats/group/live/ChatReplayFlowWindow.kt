package com.bcm.messenger.chats.group.live

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.common.video.VideoPlayer
import com.bcm.messenger.utility.logger.ALog
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Player

/**
 * Created by Kin on 2019/1/8
 */
class ChatReplayFlowWindow(private val context: Context, private val url: String, private val isGroupOwner: Boolean) : BaseLiveFlowWindow(context) {
    private val TAG = "ChatReplayFlowWindow"

    interface ChatReplayFlowListener {
        fun onHide()
        fun onRemove()
    }

    private var listener: ChatReplayFlowListener? = null
    private var params = WindowManager.LayoutParams()
    private var format: Format? = null
    private var sizeMode = SIZE_SMALL
    private var showingIcons = false
    private var firstLoad = true
    private var dp50 = 50.dp2Px()
    private var isMuted = false

    private val rootView = LayoutInflater.from(context).inflate(R.layout.chats_replay_flow_window, null) as ConstraintLayout
    private val videoPlayer = rootView.findViewById<VideoPlayer>(R.id.replay_video)
    private val smallButton = rootView.findViewById<ImageView>(R.id.replay_small)
    private val loadingView = rootView.findViewById<ImageView>(R.id.replay_loading_view)
    private val coverView = rootView.findViewById<ChatReplayCoverLayout>(R.id.replay_cover)
    private val replayText = rootView.findViewById<TextView>(R.id.replay_text)
    private val removeButton = rootView.findViewById<ImageView>(R.id.replay_remove)
    private val centerPlayButton = rootView.findViewById<ImageView>(R.id.replay_center_play)
    private val muteIcon = rootView.findViewById<ImageView>(R.id.replay_mute)

    private val loadingAnim = ObjectAnimator.ofFloat(loadingView, "rotation", 0f, 360f).apply {
        duration = 500
        repeatCount = -1
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

    private val positionChangeListener = object : ChatReplayCoverLayout.OnViewPositionChangedListener {
        override fun onViewPositionChanged(x: Int, y: Int) {
            if (sizeMode == SIZE_SMALL) onDragSmallWindow(x, y)
            else if (sizeMode == SIZE_FULLSCREEN) onDragFullWindow(y)
        }

        override fun onViewPositionChangedEnd(x: Int, y: Int) {
            if (sizeMode == SIZE_SMALL) onDragSmallWindowEnded(x, y)
            else if (sizeMode == SIZE_FULLSCREEN) onDragFullWindowEnded(y)
        }

        override fun onClick(event: MotionEvent?): Boolean {
            return if (sizeMode == SIZE_SMALL) {
                switchToFullscreen()
                true
            } else {
                if (event != null) {
                    videoPlayer.sendMotionEvent(MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_DOWN, event.x, event.y, event.metaState))
                }
                false
            }
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
            if (videoPlayer.isPlaying && (1 - percentage) > 0.02f) {
                videoPlayer.hideController()
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
                x > context.getScreenWidth() - params.width * 2 / 3 -> hideAnim.start()
//                x > AppUtil.getRealScreenWidth() - params.width || x < 0 -> doResetAnim(rootView, x, params)
                else -> stickyToCorner(rootView, x, y, params)
            }
        }

        private fun setViewY(currentY: Float) {
            videoPlayer.y = currentY
            replayText.y = currentY
            removeButton.y = currentY
            smallButton.y = currentY
            loadingView.y = (params.height - loadingView.height) / 2.toFloat() + currentY
        }

        private fun setViewScale(scale: Float) {
            videoPlayer.scaleX = scale
            videoPlayer.scaleY = scale
            loadingView.scaleX = scale
            loadingView.scaleY = scale
        }
    }

    init {
        initViews()
        initVideoPlayer()
    }

    private fun initViews() {
        ALog.i(TAG, "Init views")
        switchToSmallSize()

        coverView.setListener(positionChangeListener)
        smallButton.setOnClickListener {
            switchToSmallSize()
        }
        centerPlayButton.setOnClickListener {
            videoPlayer.playVideo()
        }
        removeButton.setOnClickListener {
            AmePopup.bottom.newBuilder()
                    .withTitle(context.getString(R.string.chats_live_flow_remove_title))
                    .withPopItem(AmeBottomPopup.PopupItem(context.getString(R.string.chats_live_flow_remove_remove)) {
                        listener?.onRemove()
                        hide(false)
                    })
                    .withDoneTitle(context.getString(R.string.chats_cancel))
                    .withDoneAction {
                        enterFullScreenMode()
                    }
                    .withCancelable(false)
                    .show(context as? FragmentActivity)
        }
        muteIcon.setOnClickListener {
            muteOrUnMute()
        }
    }

    private fun initVideoPlayer() {
        ALog.i(TAG, "Init video player")
        videoPlayer.setStreamingVideoSource(url, true)
        videoPlayer.setControllerTimeoutMs(2000)
        videoPlayer.hideController()
        videoPlayer.setControllerVisibleListener {
            if (it && sizeMode == SIZE_FULLSCREEN) {
                showIcons()
            } else {
                hideIcons()
            }
        }
        videoPlayer.setNotShowCenterPlay()
        videoPlayer.setVideoStateChangeListener(object : VideoPlayer.VideoStateChangeListener() {
            override fun onChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        ALog.i(TAG, "Video ready")
                        if (firstLoad) {
                            ALog.i(TAG, "Video first loaded")
                            firstLoad = false
                            format = videoPlayer.videoFormat
                            if (sizeMode == SIZE_SMALL) {
                                switchToSmallSize()
                            }
                        }
                        if (sizeMode == SIZE_SMALL) {
                            videoPlayer.hideController()
                        }
                        loadingAnim.cancel()
                        loadingView.visibility = View.GONE
                    }
                    Player.STATE_BUFFERING -> {
                        ALog.i(TAG, "Video buffering")
                        loadingView.visibility = View.VISIBLE
                        loadingAnim.start()
                        videoPlayer.hideController()
                    }
                    Player.STATE_ENDED -> {
                        ALog.i(TAG, "Video ended")
                        hide(true)
                    }
                    Player.STATE_IDLE -> {
                        ALog.i(TAG, "Video idle")
                        videoPlayer.hideController()
                    }
                }
            }

            override fun onPlayStateChanged(playWhenReady: Boolean) {
                centerPlayButton.visibility = if (showingIcons) {
                    if (playWhenReady) View.GONE else View.VISIBLE
                } else {
                    View.GONE
                }
            }
        })
        videoPlayer.setVolumeChangeListener {
            if (it == 0) {
                muteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
            } else {
                muteIcon.setImageResource(R.drawable.chats_flow_unmute_icon)
            }
        }
    }

    fun switchToSmallSize() {
        ALog.i(TAG, "Switch to small size")
        sizeMode = SIZE_SMALL
        coverView.enableDrag(true)
        muteIcon.visibility = View.VISIBLE
        videoPlayer.hideController()
        exitFullScreenMode()
        setActivityPortrait()

        params = switchToSmallSize(rootView, format?.width ?: context.getScreenWidth(), format?.height ?: 202.dp2Px())
    }

    fun switchToFullscreen() {
        ALog.i(TAG, "Switch to fullscreen size")
        sizeMode = SIZE_FULLSCREEN
        coverView.enableDrag(false)
        muteIcon.visibility = View.GONE
        enterFullScreenMode()
        setActivityLandscape()
        videoPlayer.showController()

        params = switchToFullscreen(rootView)
    }

    private fun showIcons() {
        ALog.i(TAG, "Show icons")
        smallButton.visibility = View.VISIBLE
        replayText.visibility = View.VISIBLE
        if (isGroupOwner) {
            removeButton.visibility = View.VISIBLE
        }
        if (!videoPlayer.isPlaying) {
            centerPlayButton.visibility = View.VISIBLE
        }
        coverView.layoutParams = (coverView.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(marginStart, topMargin, marginEnd, dp50)
        }
        showingIcons = true
    }

    private fun hideIcons() {
        ALog.i(TAG, "Hide icons")
        smallButton.visibility = View.GONE
        replayText.visibility = View.GONE
        removeButton.visibility = View.GONE
        centerPlayButton.visibility = View.GONE
        coverView.layoutParams = (coverView.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(marginStart, topMargin, marginEnd, 0)
        }
        showingIcons = false
    }

    /**
     * Mute or cancel mute
     */
    private fun muteOrUnMute() {
        if (videoPlayer.isMuted) {
            videoPlayer.unMuteAudio()
            muteIcon.setImageResource(R.drawable.chats_flow_unmute_icon)
        } else {
            videoPlayer.muteAudio()
            muteIcon.setImageResource(R.drawable.chats_flow_mute_icon)
        }
    }

    fun mute() {
        isMuted = videoPlayer.isMuted
        if (!isMuted) {
            muteOrUnMute()
        }
    }

    fun unMute() {
        if (!isMuted) {
            muteOrUnMute()
        }
    }

    fun videoRemoved() {
        ALog.i(TAG, "Replay video removed")
        videoPlayer.stopVideo()
        if (context is FragmentActivity) {
            AmePopup.result.notice(context, context.getString(R.string.chats_live_replay_link_removed))
        }
        hide(false)
    }

    fun pauseVideo() {
        videoPlayer.stopVideo()
        videoPlayer.hideController()
    }

    fun playVideo() {
        videoPlayer.playVideo()
    }

    fun getCurrentSizeMode(): Int = sizeMode

    fun hide(callback: Boolean = true) {
        ALog.i(TAG, "Replay window hide")
        try {
            exitFullScreenMode()
            setActivityPortrait()
            videoPlayer.stopVideo()
            videoPlayer.cleanup()
            if (rootView.parent != null) {
                manager.removeViewImmediate(rootView)
            }
            if (callback) {
                listener?.onHide()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setReplayListener(listener: ChatReplayFlowListener) {
        this.listener = listener
    }
}