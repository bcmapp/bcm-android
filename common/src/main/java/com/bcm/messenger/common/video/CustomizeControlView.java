package com.bcm.messenger.common.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import com.google.android.exoplayer2.util.Util;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.mms.GlideRequests;

import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;

import com.bcm.messenger.utility.logger.ALog;

/**
 * UIExoPlayer
 * ，
 * Line 137 layout
 * Line 178 Controller
 * <p>
 * Created by Kin on 2018/7/21
 */
public class CustomizeControlView extends FrameLayout {

    public static final String TAG = "CustomizeControlView";

    static {

        ExoPlayerLibraryInfo.registerModule("goog.exo.ui");
    }

    public interface VisibilityListener {
        void onVisibilityChange(int visibility);
    }

    public interface PlayPositionListener {
        void onPositionChanged(long position);
    }

    public interface PlayIconOnclickListener {
        // True！！！
        boolean playIconClicked();
    }

    public static final int DEFAULT_FAST_FORWARD_MS = 15000;
    public static final int DEFAULT_REWIND_MS = 5000;
    public static final int DEFAULT_SHOW_TIMEOUT_MS = 5000;
    public static final @RepeatModeUtil.RepeatToggleModes
    int DEFAULT_REPEAT_TOGGLE_MODES =
            RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE;

    public static final int MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR = 100;

    private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

    private final ComponentListener componentListener;
    private final View previousButton;
    private final View nextButton;
    private final View playButton;
    private final View centerPlayButton;
    private final View pauseButton;
    private final View fastForwardButton;
    private final View rewindButton;
    private final ImageView repeatToggleButton;
    private final View shuffleButton;
    private final TextView durationView;
    private final TextView positionView;
    private final ImageView thumbnailView;
    private final View controlLayout;
    private final TimeBar timeBar;
    private final StringBuilder formatBuilder;
    private final Formatter formatter;
    private final Timeline.Period period;
    private final Timeline.Window window;

    private final Drawable repeatOffButtonDrawable;
    private final Drawable repeatOneButtonDrawable;
    private final Drawable repeatAllButtonDrawable;
    private final String repeatOffButtonContentDescription;
    private final String repeatOneButtonContentDescription;
    private final String repeatAllButtonContentDescription;

    private Player player;
    private com.google.android.exoplayer2.ControlDispatcher controlDispatcher;
    private VisibilityListener visibilityListener;
    private @Nullable
    PlaybackPreparer playbackPreparer;
    private PlayIconOnclickListener playIconOnclickListener;
    private PlayPositionListener playPositionListener;

    private boolean isAttachedToWindow;
    private boolean showMultiWindowTimeBar;
    private boolean multiWindowTimeBar;
    private boolean scrubbing;
    private int rewindMs;
    private int fastForwardMs;
    private int showTimeoutMs;
    private @RepeatModeUtil.RepeatToggleModes
    int repeatToggleModes;
    private boolean showShuffleButton;
    private long hideAtMs;
    private long[] adGroupTimesMs;
    private boolean[] playedAdGroups;
    private long[] extraAdGroupTimesMs;
    private boolean[] extraPlayedAdGroups;

    public boolean showCenterPlay = true;

    private boolean isThumbnailMode = false;

    private final Runnable updateProgressAction =
            () -> updateProgress();

    private final Runnable hideAction =
            () -> hide();

    public CustomizeControlView(Context context) {
        this(context, null);
    }

    public CustomizeControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomizeControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, attrs);
    }

    public CustomizeControlView(
            Context context, AttributeSet attrs, int defStyleAttr, AttributeSet playbackAttrs) {
        super(context, attrs, defStyleAttr);
        // controllerLayoutIdUI layout ID
        int controllerLayoutId = R.layout.video_player_controller;
        rewindMs = DEFAULT_REWIND_MS;
        fastForwardMs = DEFAULT_FAST_FORWARD_MS;
        showTimeoutMs = DEFAULT_SHOW_TIMEOUT_MS;
        repeatToggleModes = DEFAULT_REPEAT_TOGGLE_MODES;
        hideAtMs = C.TIME_UNSET;
        showShuffleButton = false;
        if (playbackAttrs != null) {
            TypedArray a =
                    context
                            .getTheme()
                            .obtainStyledAttributes(playbackAttrs, com.google.android.exoplayer2.R.styleable.PlayerControlView, 0, 0);
            try {
                rewindMs = a.getInt(com.google.android.exoplayer2.R.styleable.PlayerControlView_rewind_increment, rewindMs);
                fastForwardMs =
                        a.getInt(com.google.android.exoplayer2.R.styleable.PlayerControlView_fastforward_increment, fastForwardMs);
                showTimeoutMs = a.getInt(com.google.android.exoplayer2.R.styleable.PlayerControlView_show_timeout, showTimeoutMs);
                controllerLayoutId =
                        a.getResourceId(com.google.android.exoplayer2.R.styleable.PlayerControlView_controller_layout_id, controllerLayoutId);
                repeatToggleModes = getRepeatToggleModes(a, repeatToggleModes);
                showShuffleButton =
                        a.getBoolean(com.google.android.exoplayer2.R.styleable.PlayerControlView_show_shuffle_button, showShuffleButton);
            } finally {
                a.recycle();
            }
        }
        period = new Timeline.Period();
        window = new Timeline.Window();
        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());
        adGroupTimesMs = new long[0];
        playedAdGroups = new boolean[0];
        extraAdGroupTimesMs = new long[0];
        extraPlayedAdGroups = new boolean[0];
        componentListener = new ComponentListener();
        controlDispatcher = new com.google.android.exoplayer2.DefaultControlDispatcher();

        LayoutInflater.from(context).inflate(controllerLayoutId, this);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

        // 
        durationView = findViewById(R.id.exo_duration);
        positionView = findViewById(R.id.exo_position);
        timeBar = findViewById(R.id.exo_progress);
        if (timeBar != null) {
            timeBar.addListener(componentListener);
        }
        playButton = findViewById(R.id.exo_play);
        if (playButton != null) {
            playButton.setOnClickListener(componentListener);
        }
        centerPlayButton = findViewById(R.id.center_play_icon);
        if (centerPlayButton != null) {
            centerPlayButton.setOnClickListener(componentListener);
        }
        pauseButton = findViewById(R.id.exo_pause);
        if (pauseButton != null) {
            pauseButton.setOnClickListener(componentListener);
        }
        previousButton = findViewById(R.id.exo_prev);
        if (previousButton != null) {
            previousButton.setOnClickListener(componentListener);
        }
        nextButton = findViewById(R.id.exo_next);
        if (nextButton != null) {
            nextButton.setOnClickListener(componentListener);
        }
        rewindButton = findViewById(R.id.exo_rew);
        if (rewindButton != null) {
            rewindButton.setOnClickListener(componentListener);
        }
        fastForwardButton = findViewById(R.id.exo_ffwd);
        if (fastForwardButton != null) {
            fastForwardButton.setOnClickListener(componentListener);
        }
        repeatToggleButton = findViewById(R.id.exo_repeat_toggle);
        if (repeatToggleButton != null) {
            repeatToggleButton.setOnClickListener(componentListener);
        }
        shuffleButton = findViewById(R.id.exo_shuffle);
        if (shuffleButton != null) {
            shuffleButton.setOnClickListener(componentListener);
        }
        thumbnailView = findViewById(R.id.exo_thumbnail);
        if (thumbnailView != null) {
            thumbnailView.setOnClickListener(componentListener);
        }
        controlLayout = findViewById(R.id.center_control_layout);
        Resources resources = context.getResources();
        repeatOffButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.R.drawable.exo_controls_repeat_off);
        repeatOneButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.R.drawable.exo_controls_repeat_one);
        repeatAllButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.R.drawable.exo_controls_repeat_all);
        repeatOffButtonContentDescription =
                resources.getString(com.google.android.exoplayer2.R.string.exo_controls_repeat_off_description);
        repeatOneButtonContentDescription =
                resources.getString(com.google.android.exoplayer2.R.string.exo_controls_repeat_one_description);
        repeatAllButtonContentDescription =
                resources.getString(com.google.android.exoplayer2.R.string.exo_controls_repeat_all_description);
    }

    @SuppressWarnings("ResourceType")
    private static @RepeatModeUtil.RepeatToggleModes
    int getRepeatToggleModes(
            TypedArray a, @RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
        return a.getInt(com.google.android.exoplayer2.R.styleable.PlayerControlView_repeat_toggle_modes, repeatToggleModes);
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        if (this.player == player) {
            return;
        }
        if (this.player != null) {
            this.player.removeListener(componentListener);
        }
        this.player = player;
        if (player != null) {
            player.addListener(componentListener);
        }
        updateAll();
    }

    public void setShowMultiWindowTimeBar(boolean showMultiWindowTimeBar) {
        this.showMultiWindowTimeBar = showMultiWindowTimeBar;
        updateTimeBarMode();
    }

    public void setExtraAdGroupMarkers(
            @Nullable long[] extraAdGroupTimesMs, @Nullable boolean[] extraPlayedAdGroups) {
        if (extraAdGroupTimesMs == null) {
            this.extraAdGroupTimesMs = new long[0];
            this.extraPlayedAdGroups = new boolean[0];
        } else {
            Assertions.checkArgument(extraAdGroupTimesMs.length == extraPlayedAdGroups.length);
            this.extraAdGroupTimesMs = extraAdGroupTimesMs;
            this.extraPlayedAdGroups = extraPlayedAdGroups;
        }
        updateProgress();
    }

    public void setVisibilityListener(VisibilityListener listener) {
        this.visibilityListener = listener;
    }

    public void setPlaybackPreparer(@Nullable PlaybackPreparer playbackPreparer) {
        this.playbackPreparer = playbackPreparer;
    }

    public void setControlDispatcher(
            @Nullable com.google.android.exoplayer2.ControlDispatcher controlDispatcher) {
        this.controlDispatcher =
                controlDispatcher == null
                        ? new com.google.android.exoplayer2.DefaultControlDispatcher()
                        : controlDispatcher;
    }

    public void setRewindIncrementMs(int rewindMs) {
        this.rewindMs = rewindMs;
        updateNavigation();
    }

    public void setFastForwardIncrementMs(int fastForwardMs) {
        this.fastForwardMs = fastForwardMs;
        updateNavigation();
    }

    public int getShowTimeoutMs() {
        return showTimeoutMs;
    }

    public void setShowTimeoutMs(int showTimeoutMs) {
        this.showTimeoutMs = showTimeoutMs;
        if (isVisible()) {
            // Reset the timeout.
            hideAfterTimeout();
        }
    }

    public @RepeatModeUtil.RepeatToggleModes
    int getRepeatToggleModes() {
        return repeatToggleModes;
    }

    public void setRepeatToggleModes(@RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
        this.repeatToggleModes = repeatToggleModes;
        if (player != null) {
            @Player.RepeatMode int currentMode = player.getRepeatMode();
            if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE
                    && currentMode != Player.REPEAT_MODE_OFF) {
                controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_OFF);
            } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE
                    && currentMode == Player.REPEAT_MODE_ALL) {
                controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_ONE);
            } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
                    && currentMode == Player.REPEAT_MODE_ONE) {
                controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_ALL);
            }
        }
    }

    public boolean getShowShuffleButton() {
        return showShuffleButton;
    }

    public void setShowShuffleButton(boolean showShuffleButton) {
        this.showShuffleButton = showShuffleButton;
        updateShuffleButton();
    }

    public void show() {
        if (!isVisible()) {
//            setVisibility(VISIBLE);
            centerPlayButton.setVisibility(VISIBLE);
            controlLayout.setVisibility(VISIBLE);
            if (visibilityListener != null) {
                visibilityListener.onVisibilityChange(getVisibility());
            }
            updateAll();
            requestPlayPauseFocus();
        }
        // Call hideAfterTimeout even if already visible to reset the timeout.
        hideAfterTimeout();
    }

    public void hide() {
        if (isVisible()) {
//            setVisibility(GONE);
            centerPlayButton.setVisibility(GONE);
            controlLayout.setVisibility(GONE);
            if (visibilityListener != null) {
                visibilityListener.onVisibilityChange(getVisibility());
            }
            removeCallbacks(updateProgressAction);
            removeCallbacks(hideAction);
            hideAtMs = C.TIME_UNSET;
        }
    }

    public boolean isVisible() {
        return controlLayout.getVisibility() == VISIBLE;
    }

    private void hideAfterTimeout() {
        removeCallbacks(hideAction);
        if (showTimeoutMs > 0) {
            hideAtMs = SystemClock.uptimeMillis() + showTimeoutMs;
            if (isAttachedToWindow) {
                postDelayed(hideAction, showTimeoutMs);
            }
        } else {
            hideAtMs = C.TIME_UNSET;
        }
    }

    private void updateAll() {
        updatePlayPauseButton();
        updateNavigation();
        updateRepeatModeButton();
        updateShuffleButton();
        updateProgress();
    }

    private void updatePlayPauseButton() {
        if (!isVisible() || !isAttachedToWindow) {
            return;
        }
        boolean requestPlayPauseFocus = false;
        boolean playing = isPlaying();
        if (playButton != null) {
            requestPlayPauseFocus |= playing && playButton.isFocused();
            playButton.setVisibility(playing ? View.GONE : View.VISIBLE);
        }
        if (centerPlayButton != null) {
            if (showCenterPlay) {
                centerPlayButton.setVisibility(playing ? View.GONE : View.VISIBLE);
            }
        }
        if (pauseButton != null) {
            requestPlayPauseFocus |= !playing && pauseButton.isFocused();
            pauseButton.setVisibility(!playing ? View.GONE : View.VISIBLE);
        }
        if (requestPlayPauseFocus) {
            requestPlayPauseFocus();
        }
    }

    private void updateNavigation() {
        if (!isVisible() || !isAttachedToWindow) {
            return;
        }
        Timeline timeline = player != null ? player.getCurrentTimeline() : null;
        boolean haveNonEmptyTimeline = timeline != null && !timeline.isEmpty();
        boolean isSeekable = false;
        boolean enablePrevious = false;
        boolean enableNext = false;
        if (haveNonEmptyTimeline && !player.isPlayingAd()) {
            int windowIndex = player.getCurrentWindowIndex();
            timeline.getWindow(windowIndex, window);
            isSeekable = window.isSeekable;
            enablePrevious =
                    isSeekable || !window.isDynamic || player.getPreviousWindowIndex() != C.INDEX_UNSET;
            enableNext = window.isDynamic || player.getNextWindowIndex() != C.INDEX_UNSET;
        }
        setButtonEnabled(enablePrevious, previousButton);
        setButtonEnabled(enableNext, nextButton);
        setButtonEnabled(fastForwardMs > 0 && isSeekable, fastForwardButton);
        setButtonEnabled(rewindMs > 0 && isSeekable, rewindButton);
        if (timeBar != null) {
            timeBar.setEnabled(isSeekable);
        }
    }

    private void updateRepeatModeButton() {
        if (!isVisible() || !isAttachedToWindow || repeatToggleButton == null) {
            return;
        }
        if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
            repeatToggleButton.setVisibility(View.GONE);
            return;
        }
        if (player == null) {
            setButtonEnabled(false, repeatToggleButton);
            return;
        }
        setButtonEnabled(true, repeatToggleButton);
        switch (player.getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                repeatToggleButton.setImageDrawable(repeatOffButtonDrawable);
                repeatToggleButton.setContentDescription(repeatOffButtonContentDescription);
                break;
            case Player.REPEAT_MODE_ONE:
                repeatToggleButton.setImageDrawable(repeatOneButtonDrawable);
                repeatToggleButton.setContentDescription(repeatOneButtonContentDescription);
                break;
            case Player.REPEAT_MODE_ALL:
                repeatToggleButton.setImageDrawable(repeatAllButtonDrawable);
                repeatToggleButton.setContentDescription(repeatAllButtonContentDescription);
                break;
            default:
                // Never happens.
        }
        repeatToggleButton.setVisibility(View.VISIBLE);
    }

    private void updateShuffleButton() {
        if (!isVisible() || !isAttachedToWindow || shuffleButton == null) {
            return;
        }
        if (!showShuffleButton) {
            shuffleButton.setVisibility(View.GONE);
        } else if (player == null) {
            setButtonEnabled(false, shuffleButton);
        } else {
            shuffleButton.setAlpha(player.getShuffleModeEnabled() ? 1f : 0.3f);
            shuffleButton.setEnabled(true);
            shuffleButton.setVisibility(View.VISIBLE);
        }
    }

    private void updateTimeBarMode() {
        if (player == null) {
            return;
        }
        multiWindowTimeBar =
                showMultiWindowTimeBar && canShowMultiWindowTimeBar(player.getCurrentTimeline(), window);
    }

    private void updateProgress() {
//        if (!isVisible() || !isAttachedToWindow) {
//            return;
//        }
        if (!isAttachedToWindow) {
            return;
        }

        long position = 0;
        long bufferedPosition = 0;
        long duration = 0;
        if (player != null) {
            long currentWindowTimeBarOffsetUs = 0;
            long durationUs = 0;
            int adGroupCount = 0;
            Timeline timeline = player.getCurrentTimeline();
            if (!timeline.isEmpty()) {
                int currentWindowIndex = player.getCurrentWindowIndex();
                int firstWindowIndex = multiWindowTimeBar ? 0 : currentWindowIndex;
                int lastWindowIndex =
                        multiWindowTimeBar ? timeline.getWindowCount() - 1 : currentWindowIndex;
                for (int i = firstWindowIndex; i <= lastWindowIndex; i++) {
                    if (i == currentWindowIndex) {
                        currentWindowTimeBarOffsetUs = durationUs;
                    }
                    timeline.getWindow(i, window);
                    if (window.durationUs == C.TIME_UNSET) {
                        Assertions.checkState(!multiWindowTimeBar);
                        break;
                    }
                    for (int j = window.firstPeriodIndex; j <= window.lastPeriodIndex; j++) {
                        timeline.getPeriod(j, period);
                        int periodAdGroupCount = period.getAdGroupCount();
                        for (int adGroupIndex = 0; adGroupIndex < periodAdGroupCount; adGroupIndex++) {
                            long adGroupTimeInPeriodUs = period.getAdGroupTimeUs(adGroupIndex);
                            if (adGroupTimeInPeriodUs == C.TIME_END_OF_SOURCE) {
                                if (period.durationUs == C.TIME_UNSET) {
                                    // Don't show ad markers for postrolls in periods with unknown duration.
                                    continue;
                                }
                                adGroupTimeInPeriodUs = period.durationUs;
                            }
                            long adGroupTimeInWindowUs = adGroupTimeInPeriodUs + period.getPositionInWindowUs();
                            if (adGroupTimeInWindowUs >= 0 && adGroupTimeInWindowUs <= window.durationUs) {
                                if (adGroupCount == adGroupTimesMs.length) {
                                    int newLength = adGroupTimesMs.length == 0 ? 1 : adGroupTimesMs.length * 2;
                                    adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, newLength);
                                    playedAdGroups = Arrays.copyOf(playedAdGroups, newLength);
                                }
                                adGroupTimesMs[adGroupCount] = C.usToMs(durationUs + adGroupTimeInWindowUs);
                                playedAdGroups[adGroupCount] = period.hasPlayedAdGroup(adGroupIndex);
                                adGroupCount++;
                            }
                        }
                    }
                    durationUs += window.durationUs;
                }
            }
            duration = C.usToMs(durationUs);
            position = C.usToMs(currentWindowTimeBarOffsetUs);
            bufferedPosition = position;
            if (player.isPlayingAd()) {
                position += player.getContentPosition();
                bufferedPosition = position;
            } else {
                position += player.getCurrentPosition();
                bufferedPosition += player.getBufferedPosition();
            }
            if (timeBar != null) {
                int extraAdGroupCount = extraAdGroupTimesMs.length;
                int totalAdGroupCount = adGroupCount + extraAdGroupCount;
                if (totalAdGroupCount > adGroupTimesMs.length) {
                    adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, totalAdGroupCount);
                    playedAdGroups = Arrays.copyOf(playedAdGroups, totalAdGroupCount);
                }
                System.arraycopy(extraAdGroupTimesMs, 0, adGroupTimesMs, adGroupCount, extraAdGroupCount);
                System.arraycopy(extraPlayedAdGroups, 0, playedAdGroups, adGroupCount, extraAdGroupCount);
                timeBar.setAdGroupTimesMs(adGroupTimesMs, playedAdGroups, totalAdGroupCount);
            }
        }
        if (durationView != null) {
            durationView.setText(Util.getStringForTime(formatBuilder, formatter, duration));
        }
        if (positionView != null && !scrubbing) {
            positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
        }
        if (playPositionListener != null) {
            playPositionListener.onPositionChanged(position);
        }
        if (timeBar != null) {
            timeBar.setPosition(position);
            timeBar.setBufferedPosition(bufferedPosition);
            timeBar.setDuration(duration);
        }

        // Cancel any pending updates and schedule a new one if necessary.
        removeCallbacks(updateProgressAction);
        int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            long delayMs;
            if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                float playbackSpeed = player.getPlaybackParameters().speed;
                if (playbackSpeed <= 0.1f) {
                    delayMs = 1000;
                } else if (playbackSpeed <= 5f) {
                    long mediaTimeUpdatePeriodMs = 1000 / Math.max(1, Math.round(1 / playbackSpeed));
                    long mediaTimeDelayMs = mediaTimeUpdatePeriodMs - (position % mediaTimeUpdatePeriodMs);
                    if (mediaTimeDelayMs < (mediaTimeUpdatePeriodMs / 5)) {
                        mediaTimeDelayMs += mediaTimeUpdatePeriodMs;
                    }
                    delayMs =
                            playbackSpeed == 1 ? mediaTimeDelayMs : (long) (mediaTimeDelayMs / playbackSpeed);
                } else {
                    delayMs = 200;
                }
            } else {
                delayMs = 1000;
            }
            postDelayed(updateProgressAction, delayMs);
        }
    }

    private void requestPlayPauseFocus() {
        boolean playing = isPlaying();
        if (!playing && playButton != null) {
            playButton.requestFocus();
        } else if (playing && pauseButton != null) {
            pauseButton.requestFocus();
        }
    }

    private void setButtonEnabled(boolean enabled, View view) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.3f);
        view.setVisibility(VISIBLE);
    }

    private void previous() {
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty()) {
            return;
        }
        int windowIndex = player.getCurrentWindowIndex();
        timeline.getWindow(windowIndex, window);
        int previousWindowIndex = player.getPreviousWindowIndex();
        if (previousWindowIndex != C.INDEX_UNSET
                && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
                || (window.isDynamic && !window.isSeekable))) {
            seekTo(previousWindowIndex, C.TIME_UNSET);
        } else {
            seekTo(0);
        }
    }

    private void next() {
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty()) {
            return;
        }
        int windowIndex = player.getCurrentWindowIndex();
        int nextWindowIndex = player.getNextWindowIndex();
        if (nextWindowIndex != C.INDEX_UNSET) {
            seekTo(nextWindowIndex, C.TIME_UNSET);
        } else if (timeline.getWindow(windowIndex, window, false).isDynamic) {
            seekTo(windowIndex, C.TIME_UNSET);
        }
    }

    private void rewind() {
        if (rewindMs <= 0) {
            return;
        }
        seekTo(Math.max(player.getCurrentPosition() - rewindMs, 0));
    }

    private void fastForward() {
        if (fastForwardMs <= 0) {
            return;
        }
        long durationMs = player.getDuration();
        long seekPositionMs = player.getCurrentPosition() + fastForwardMs;
        if (durationMs != C.TIME_UNSET) {
            seekPositionMs = Math.min(seekPositionMs, durationMs);
        }
        seekTo(seekPositionMs);
    }

    private void seekTo(long positionMs) {
        seekTo(player.getCurrentWindowIndex(), positionMs);
    }

    private void seekTo(int windowIndex, long positionMs) {
        boolean dispatched = controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
        if (!dispatched) {
            // The seek wasn't dispatched. If the progress bar was dragged by the user to perform the
            // seek then it'll now be in the wrong position. Trigger a progress update to snap it back.
            updateProgress();
        }
    }

    private void seekToTimeBarPosition(long positionMs) {
        int windowIndex;
        Timeline timeline = player.getCurrentTimeline();
        if (multiWindowTimeBar && !timeline.isEmpty()) {
            int windowCount = timeline.getWindowCount();
            windowIndex = 0;
            while (true) {
                long windowDurationMs = timeline.getWindow(windowIndex, window).getDurationMs();
                if (positionMs < windowDurationMs) {
                    break;
                } else if (windowIndex == windowCount - 1) {
                    // Seeking past the end of the last window should seek to the end of the timeline.
                    positionMs = windowDurationMs;
                    break;
                }
                positionMs -= windowDurationMs;
                windowIndex++;
            }
        } else {
            windowIndex = player.getCurrentWindowIndex();
        }
        seekTo(windowIndex, positionMs);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
        if (hideAtMs != C.TIME_UNSET) {
            long delayMs = hideAtMs - SystemClock.uptimeMillis();
            if (delayMs <= 0) {
                hide();
            } else {
                postDelayed(hideAction, delayMs);
            }
        } else if (isVisible()) {
            hideAfterTimeout();
        }
        updateAll();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedToWindow = false;
        removeCallbacks(updateProgressAction);
        removeCallbacks(hideAction);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    public boolean dispatchMediaKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (player == null || !isHandledMediaKey(keyCode)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                fastForward();
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                rewind();
            } else if (event.getRepeatCount() == 0) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        controlDispatcher.dispatchSetPlayWhenReady(player, !player.getPlayWhenReady());
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        controlDispatcher.dispatchSetPlayWhenReady(player, true);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        controlDispatcher.dispatchSetPlayWhenReady(player, false);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        next();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        previous();
                        break;
                    default:
                        break;
                }
            }
        }
        return true;
    }

    private boolean isPlaying() {
        return player != null
                && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlayWhenReady();
    }

    @SuppressLint("InlinedApi")
    private static boolean isHandledMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    public void setVideoThumbnail(Object uri, GlideRequests glide) {
        ALog.i(TAG, "setVideoThumbnail");
        if (thumbnailView != null) {
            isThumbnailMode = true;
            show();
            removeCallbacks(hideAction);
            centerPlayButton.setVisibility(View.GONE);
            thumbnailView.setVisibility(View.VISIBLE);
            glide.asBitmap().load(uri)
                    .error(R.drawable.common_image_broken_img)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(thumbnailView);
        }
    }

    public void hideVideoThumbnail() {
        ALog.i(TAG, "hideVideoThumbnail");
        if (thumbnailView != null) {
            isThumbnailMode = false;
            centerPlayButton.setVisibility(View.VISIBLE);
            thumbnailView.setVisibility(View.GONE);
        }
        hideAfterTimeout();
    }

    public void setPlayIconOnclickListener(PlayIconOnclickListener listener) {
        this.playIconOnclickListener = listener;
    }

    public void setPositionChangedListener(PlayPositionListener listener) {
        this.playPositionListener = listener;
    }

    public void removeListeners() {
        playIconOnclickListener = null;
        playPositionListener = null;
    }

    private static boolean canShowMultiWindowTimeBar(Timeline timeline, Timeline.Window window) {
        if (timeline.getWindowCount() > MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR) {
            return false;
        }
        int windowCount = timeline.getWindowCount();
        for (int i = 0; i < windowCount; i++) {
            if (timeline.getWindow(i, window).durationUs == C.TIME_UNSET) {
                return false;
            }
        }
        return true;
    }

    public void setNotShowCenterPlay() {
        showCenterPlay = false;
        centerPlayButton.setVisibility(GONE);
    }

    private final class ComponentListener extends Player.DefaultEventListener
            implements TimeBar.OnScrubListener, OnClickListener {

        @Override
        public void onScrubStart(TimeBar timeBar, long position) {
            removeCallbacks(hideAction);
            scrubbing = true;
        }

        @Override
        public void onScrubMove(TimeBar timeBar, long position) {
            if (positionView != null) {
                positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
            }
            if (playPositionListener != null) {
                playPositionListener.onPositionChanged(position);
            }
        }

        @Override
        public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
            scrubbing = false;
            if (!canceled && player != null) {
                seekToTimeBarPosition(position);
            }
            hideAfterTimeout();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            updatePlayPauseButton();
            updateProgress();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            updateRepeatModeButton();
            updateNavigation();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            updateShuffleButton();
            updateNavigation();
        }

        @Override
        public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
            updateNavigation();
            updateProgress();
        }

        @Override
        public void onTimelineChanged(
                Timeline timeline, Object manifest, @Player.TimelineChangeReason int reason) {
            updateNavigation();
            updateTimeBarMode();
            updateProgress();
        }

        @Override
        public void onClick(View view) {
            if (player != null) {
                if (nextButton == view) {
                    next();
                } else if (previousButton == view) {
                    previous();
                } else if (fastForwardButton == view) {
                    fastForward();
                } else if (rewindButton == view) {
                    rewind();
                } else if (playButton == view || centerPlayButton == view) {
                    if (playIconOnclickListener == null || !playIconOnclickListener.playIconClicked()) {
                        if (player.getPlaybackState() == Player.STATE_IDLE) {
                            if (playbackPreparer != null) {
                                playbackPreparer.preparePlayback();
                            }
                        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
                            controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
                        }
                        controlDispatcher.dispatchSetPlayWhenReady(player, true);
                    }
                } else if (pauseButton == view) {
                    controlDispatcher.dispatchSetPlayWhenReady(player, false);
                } else if (repeatToggleButton == view) {
                    controlDispatcher.dispatchSetRepeatMode(
                            player, RepeatModeUtil.getNextRepeatMode(player.getRepeatMode(), repeatToggleModes));
                } else if (shuffleButton == view) {
                    controlDispatcher.dispatchSetShuffleModeEnabled(player, !player.getShuffleModeEnabled());
                }
            }
            hideAfterTimeout();
        }
    }
}
