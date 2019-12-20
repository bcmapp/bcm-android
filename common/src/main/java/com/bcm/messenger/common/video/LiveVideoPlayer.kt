package com.bcm.messenger.common.video

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.bcm.messenger.common.utils.VolumeChangeObserver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.android.synthetic.main.common_live_video_player_layout.view.*
import com.bcm.messenger.common.R
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.mms.VideoSlide
import com.bcm.messenger.common.video.exo.AttachmentDataSourceFactory

/**
 * Created by Kin on 2019/1/7
 */
class LiveVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    private var audioVolume = 0

    private val exoPlayer: SimpleExoPlayer
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var videoStateChangeListener: ((Int) -> Unit)? = null
    private var volumeChangeListener: ((Int) -> Unit)? = null
    private var playPositionChangeListener: ((Long) -> Unit)? = null

    private var isMuted = false

    init {
        View.inflate(context, R.layout.common_live_video_player_layout, this)
        val bandwidthMeter = DefaultBandwidthMeter()
        val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)
        val loadControl = DefaultLoadControl()
        val renderersFactory = DefaultRenderersFactory(context)
        exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl)
        if (video_view != null) {
            video_view.player = exoPlayer
        }
        video_view.controllerShowTimeoutMs = 0
        video_view.hideController()
        video_view.setPlayPositionListener {
            playPositionChangeListener?.invoke(it)
        }
    }

    private val observerCallback: (Int) -> Unit = { volume: Int ->
        audioVolume = volume
        if (!isMuted) {
            exoPlayer.volume = volume.toFloat()
        }
        volumeChangeListener?.invoke(volume)
    }

    init {
        exoPlayer.addListener(ExoPlayerListener())
        audioVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        VolumeChangeObserver.addCallback(observerCallback)
    }

    fun setVideoStateChangeListener(listener: (Int) -> Unit) {
        this.videoStateChangeListener = listener
    }

    fun setVolumeChangeListener(listener: (Int) -> Unit) {
        this.volumeChangeListener = listener
    }

    fun setPlayPositionChangeListener(listener: (Long) -> Unit) {
        this.playPositionChangeListener = listener
    }

    fun setVideoSource(masterSecret: MasterSecret, videoSource: VideoSlide, playWhenReady: Boolean) {
        val defaultDataSourceFactory = DefaultDataSourceFactory(context, "GenericUserAgent", null)
        val attachmentDataSourceFactory = AttachmentDataSourceFactory(masterSecret, defaultDataSourceFactory, null)
        val mediaSource = ExtractorMediaSource.Factory(attachmentDataSourceFactory).createMediaSource(videoSource.uri)

        setExoViewSource(mediaSource, playWhenReady)
    }

    fun setStreamingVideoSource(streamUrl: String, playWhenReady: Boolean) {
        val defaultDataSourceFactory = DefaultDataSourceFactory(context, "GenericUserAgent", null)
        val mediaSource = ExtractorMediaSource.Factory(defaultDataSourceFactory).createMediaSource(Uri.parse(streamUrl))

        setExoViewSource(mediaSource, playWhenReady)
    }

    fun setM3U8VideoSource(streamUrl: String, playWhenReady: Boolean) {
        val defaultDataSourceFactory = DefaultDataSourceFactory(context, "GenericUserAgent", null)
        val mediaSource = HlsMediaSource.Factory(defaultDataSourceFactory).createMediaSource(Uri.parse(streamUrl))

        setExoViewSource(mediaSource, playWhenReady)
    }

    private fun setExoViewSource(mediaSource: MediaSource, playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare(mediaSource)
    }

    fun cleanup() {
        exoPlayer.release()
        VolumeChangeObserver.removeCallback(observerCallback)
        volumeChangeListener = null
        videoStateChangeListener = null
        playPositionChangeListener = null
    }

    fun playVideo() {
        exoPlayer.playWhenReady = true
    }

    fun stopVideo() {
        exoPlayer.playWhenReady = false
    }

    fun muteAudio() {
        exoPlayer.volume = 0f
        isMuted = true
    }

    fun unMuteAudio() {
        exoPlayer.volume = audioVolume.toFloat()
        isMuted = false
    }

    fun isMuted(): Boolean = isMuted

    fun seekTo(timeMillis: Long) {
        if (timeMillis >= 0) {
            exoPlayer.seekTo(timeMillis)
        }
    }

    fun getVideoFormat(): Format? = exoPlayer.videoFormat

    fun isPlaying(): Boolean = exoPlayer.playWhenReady

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    private inner class ExoPlayerListener : Player.EventListener {
        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {}

        override fun onRepeatModeChanged(repeatMode: Int) {}

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

        override fun onPositionDiscontinuity(reason: Int) {}

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {}

        override fun onSeekProcessed() {}

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            video_view.hideController()
            videoStateChangeListener?.invoke(playbackState)
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {}

        override fun onLoadingChanged(isLoading: Boolean) {}

        override fun onPlayerError(error: ExoPlaybackException?) {}
    }
}