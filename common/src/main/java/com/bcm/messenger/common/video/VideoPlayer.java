package com.bcm.messenger.common.video;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.GlideRequests;
import com.bcm.messenger.common.mms.VideoSlide;
import com.bcm.messenger.common.video.exo.AttachmentDataSourceFactory;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;


public class VideoPlayer extends FrameLayout {

    private static final String TAG = VideoPlayer.class.getName();

    private AudioManager audioManager;

    @Nullable
    private final CustomizePlayerView exoView;

    @Nullable
    private SimpleExoPlayer exoPlayer;
    private ImageView audioView;
    private int audioVolume = 5;
    private boolean disableCallback = false;
    private boolean isFlowWindow = false;
    private boolean isMuted = false;

    // Listeners
    private VideoReadyListener videoReadyListener;
    private VideoStateChangeListener videoStateChangeListener;
    private ControllerVisibleListener controllerVisibleListener;
    private VolumeChangeListener volumeChangeListener;

    public static abstract class VideoStateChangeListener {
        public abstract void onChanged(int state);
        public void onPlayStateChanged(boolean playWhenReady) {}
    }

    public interface VideoReadyListener {
        void onReady();
    }

    public interface ControllerVisibleListener {
        void onVisibleChanged(boolean isVisible);
    }

    public interface VolumeChangeListener {
        void onVolumeChanged(int volume);
    }

    public VideoPlayer(Context context) {
        this(context, null);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflate(context, R.layout.video_player_layout, this);
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        LoadControl loadControl = new DefaultLoadControl();
        RenderersFactory renderersFactory = new DefaultRenderersFactory(getContext());
        this.exoView = findViewById(R.id.video_view);
        this.exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
        this.exoPlayer.addListener(new ExoPlayerListener());
        if (exoView != null) {
            exoView.setPlayer(exoPlayer);
        }
        this.audioView = findViewById(R.id.audio_btn);

        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        //        VolumeChangeObserver.INSTANCE.registerReceiver();
        //        VolumeChangeObserver.INSTANCE.addCallback(observerCallback);
        audioVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        exoPlayer.setVolume(audioVolume);

        if (audioVolume == 0) {
            audioView.setImageResource(R.drawable.ic_mute_16);
            isMuted = true;
        }
    }

    /**
     * Set chat video source
     *
     * @param masterSecret MasterSecret
     * @param uri Video source uri
     * @param playWhenReady Auto play when video is ready
     */
    public void setVideoSource(@NonNull MasterSecret masterSecret, @NonNull Uri uri, boolean playWhenReady) {
        DefaultDataSourceFactory defaultDataSourceFactory = new DefaultDataSourceFactory(getContext(), "GenericUserAgent", null);
        AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(masterSecret, defaultDataSourceFactory, null);
        MediaSource mediaSource = new ExtractorMediaSource.Factory(attachmentDataSourceFactory).createMediaSource(uri);

        setExoViewSource(mediaSource, playWhenReady);
    }

    /**
     * Set online streaming video source
     *
     * @param streamUrl Online video url, MUST be the real video url, MUST NOT be the website url
     * @param playWhenReady Auto play when video is ready
     */
    public void setStreamingVideoSource(String streamUrl, boolean playWhenReady) {
        DefaultDataSourceFactory defaultDataSourceFactory = new DefaultDataSourceFactory(getContext(), "GenericUserAgent", null);
        MediaSource mediaSource = new ExtractorMediaSource.Factory(defaultDataSourceFactory).createMediaSource(Uri.parse(streamUrl));

        setExoViewSource(mediaSource, playWhenReady);
    }

    /**
     * Set m3u8 video source
     *
     * @param streamUrl M3U8 video url
     * @param playWhenReady Auto play when video is ready
     */
    public void setM3U8VideoSource(String streamUrl, boolean playWhenReady) {
        DefaultDataSourceFactory defaultDataSourceFactory = new DefaultDataSourceFactory(getContext(), "GenericUserAgent", null);
        MediaSource mediaSource = new HlsMediaSource.Factory(defaultDataSourceFactory).createMediaSource(Uri.parse(streamUrl));

        setExoViewSource(mediaSource, playWhenReady);
    }

    /**
     * Continue playing video
     */
    public void playVideo() {
        if (exoPlayer != null)
            exoPlayer.setPlayWhenReady(true);
    }

    /**
     * Cleanup resources when activity on destroy
     */
    public void cleanup() {
        if (this.exoPlayer != null) {
            this.exoPlayer.release();
        }
        //        VolumeChangeObserver.INSTANCE.removeCallback(observerCallback);
        //        VolumeChangeObserver.INSTANCE.unregisterReceiver();
    }

    /**
     * Stop playing video
     */
    public void stopVideo() {
        if (exoPlayer != null && exoPlayer.getPlayWhenReady()) {  //播放为true，暂停为false
            exoPlayer.setPlayWhenReady(false);
        }
    }

    /**
     * Get video duration
     *
     * @return duration time, ms
     */
    public long getDuration() {
        return this.exoPlayer == null ? 0 : this.exoPlayer.getDuration();
    }

    /**
     * Get video playing position
     *
     * @return Current playing position, ms
     */
    public long getCurrentPosition() {
        long position = 0;
        try {
            position = exoPlayer != null ? exoPlayer.getContentPosition() : 0;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return position;
    }

    /**
     * Seek to specific time
     *
     * @param timeMillis Seek time
     */
    public void seekTo(long timeMillis) {
        long seekPosition = 0;
        try {
            if (timeMillis > 0) {
                seekPosition = timeMillis;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        if (exoPlayer != null) {
            exoPlayer.seekTo(seekPosition);
        }
    }

    /**
     * Set video state change listener
     *
     * @param l listener
     */
    public void setVideoStateChangeListener(VideoStateChangeListener l) {
        videoStateChangeListener = l;
    }

    public void removeListeners() {
        this.videoReadyListener = null;
        this.videoStateChangeListener = null;
        this.controllerVisibleListener = null;
        this.volumeChangeListener = null;
        if (exoView != null) {
            exoView.removeListeners();
        }
    }

    /**
     * @return If video is playing.
     */
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.getPlayWhenReady();
    }

    public void showControllerWithoutCallback() {
        if (exoView != null) {
            disableCallback = true;
            exoView.showController();
        }
    }

    public void showController() {
        if (exoView != null) {
            exoView.showController();
        }
    }

    public void hideControllerWithoutCallback() {
        if (exoView != null) {
            disableCallback = true;
            exoView.hideController();
        }
    }

    public void hideController() {
        if (exoView != null) {
            exoView.hideController();
        }
    }

    public void setVideoThumbnail(Uri uri, GlideRequests glide) {
        if (exoView != null) {
            exoView.setPlayer(exoPlayer);
            exoView.setVideoThumbnail(uri, glide);
        }
    }

    public void hideVideoThumbnail() {
        if (exoView != null) {
            exoView.hideVideoThumbnail();
        }
    }

    public void setPlayIconListener(CustomizeControlView.PlayIconOnclickListener listener) {
        if (exoView != null) {
            exoView.setPlayIconListener(listener);
        }
    }

    private void setControllerListener() {
        if (exoView != null) {
            exoView.setControllerVisibilityListener(visibility -> {
                if (visibility == View.VISIBLE && !isFlowWindow) {
                    audioView.setVisibility(View.VISIBLE);
                } else {
                    audioView.setVisibility(View.GONE);
                }
                if (controllerVisibleListener != null) {
                    if (disableCallback) {
                        disableCallback = false;
                    } else {
                        controllerVisibleListener.onVisibleChanged(visibility == View.VISIBLE);
                    }
                }
            });
        }
    }

    public void setControllerVisibleListener(ControllerVisibleListener listener) {
        this.controllerVisibleListener = listener;
    }

    public void setVolumeChangeListener(VolumeChangeListener listener) {
        this.volumeChangeListener = listener;
    }

    /**
     * Set video source to player
     *
     * @param mediaSource Video source
     * @param playWhenReady Auto play when video is ready
     */
    private void setExoViewSource(@NonNull MediaSource mediaSource, boolean playWhenReady) {
        if (exoPlayer == null) return;
        exoPlayer.setPlayWhenReady(playWhenReady);
        exoPlayer.prepare(mediaSource);
        if (videoReadyListener != null) {
            videoReadyListener.onReady();
        }
        audioView.setOnClickListener(v -> {
            if (isMuted) {
                unMuteAudio();
            } else {
                muteAudio();
            }
        });
        setControllerListener();
    }

    public void muteAudio() {
        if (exoPlayer != null) {
            exoPlayer.setVolume(0);
        }
        audioView.setImageResource(R.drawable.ic_mute_16);
        if (volumeChangeListener != null) {
            volumeChangeListener.onVolumeChanged(0);
        }
        isMuted = true;
    }

    public void unMuteAudio() {
        if (exoPlayer != null) {
            exoPlayer.setVolume(audioVolume);
        }
        audioView.setImageResource(R.drawable.ic_unmute_16);
        if (volumeChangeListener != null) {
            volumeChangeListener.onVolumeChanged(5);
        }
        isMuted = false;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public Format getVideoFormat() {
        if (exoPlayer == null) return null;
        return exoPlayer.getVideoFormat();
    }

    public void resetVideoState() {
        audioView.setVisibility(View.VISIBLE);
        if (exoView != null) {
            exoView.showController();
        }
    }

    public void sendMotionEvent(MotionEvent event) {
        if (exoView != null) {
            exoView.onTouchEvent(event);
        }
    }

    public void setNotShowCenterPlay() {
        if (exoView != null) {
            exoView.setNotShowCenterPlay();
        }
    }

    public void setControllerTimeoutMs(int timeoutMs) {
        if (exoView != null) {
            exoView.setControllerShowTimeoutMs(timeoutMs);
        }
    }

    private class ExoPlayerListener implements Player.EventListener {
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest, int reason) { }

        @Override
        public void onRepeatModeChanged(int repeatMode) { }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) { }

        @Override
        public void onPositionDiscontinuity(int reason) { }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) { }

        @Override
        public void onSeekProcessed() { }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (videoStateChangeListener != null) {
                videoStateChangeListener.onChanged(playbackState);
                videoStateChangeListener.onPlayStateChanged(playWhenReady);
            }
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) { }

        @Override
        public void onLoadingChanged(boolean isLoading) { }

        @Override
        public void onPlayerError(ExoPlaybackException error) { }
    }

    private Function1<Integer, Unit> observerCallback = (Integer volume) -> {
//        if (volume == 0) {
//            audioView.setImageResource(R.drawable.ic_mute_16);
//        } else {
//            audioView.setImageResource(R.drawable.ic_unmute_16);
//        }
        audioVolume = volume;
        if (exoPlayer != null && !isMuted) {
            exoPlayer.setVolume(volume);
        }
//        if (volumeChangeListener != null) {
//            volumeChangeListener.onVolumeChanged(volume);
//        }
        return Unit.INSTANCE;
    };
}
