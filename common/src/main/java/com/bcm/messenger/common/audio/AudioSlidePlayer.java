package com.bcm.messenger.common.audio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.common.utils.log.ACLog;
import com.orhanobut.logger.Logger;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.attachments.AttachmentServer;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.AudioSlide;
import com.bcm.messenger.utility.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class AudioSlidePlayer implements SensorEventListener {

    private static final String TAG = AudioSlidePlayer.class.getSimpleName();

    private final @NonNull
    Context context;
    private final @Nullable
    MasterSecret masterSecret;
    private final @NonNull
    AudioSlide slide;
    private final @NonNull
    ProgressEventHandler progressEventHandler;
    private final @NonNull
    AudioManager audioManager;
    private final @NonNull
    SensorManager sensorManager;
    private final @NonNull
    Sensor proximitySensor;
    private final @Nullable
    WakeLock wakeLock;

    private @NonNull
    WeakReference<Listener> listener;
    private @Nullable
    MediaPlayerWrapper mediaPlayer;
    private @Nullable
    AttachmentServer audioAttachmentServer;
    private long startTime;

    private boolean forSensor = false;//

    public synchronized static AudioSlidePlayer createFor(@NonNull Context context,
                                                          @Nullable  MasterSecret masterSecret,
                                                          @NonNull AudioSlide slide,
                                                          @NonNull Listener listener) {
        return new AudioSlidePlayer(context, masterSecret, slide, listener);
    }

    private AudioSlidePlayer(@NonNull Context context,
                             @Nullable
                             MasterSecret masterSecret,
                             @NonNull AudioSlide slide,
                             @NonNull Listener listener) {
        this.context = context;
        this.masterSecret = masterSecret;
        this.slide = slide;
        this.listener = new WeakReference<>(listener);
        this.progressEventHandler = new ProgressEventHandler(this);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (Build.VERSION.SDK_INT >= 21) {
            this.wakeLock = AppUtil.INSTANCE.getPowerManager(context).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
        } else {
            this.wakeLock = null;
        }
    }

    public static long getDuration(Context context, MasterSecret masterSecret, Attachment attachment) {
        long duration = 0;
        try {
            MediaPlayerWrapper mediaPlayer = new MediaPlayerWrapper();
            AttachmentServer audioAttachmentServer = new AttachmentServer(context, masterSecret, attachment);
            audioAttachmentServer.start();
            mediaPlayer.setDataSource(context, audioAttachmentServer.getUri());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);


            mediaPlayer.setOnPreparedListener(mp -> {
            });

            mediaPlayer.prepare();
            duration = mediaPlayer.getDuration();
            audioAttachmentServer.stop();
        } catch (Throwable e) {
            ACLog.e(masterSecret.getAccountContext(), "AudioSlidePlayer", "getDuration", e);
        }
        return duration;
    }

    public synchronized void play(final double progress) throws IOException {
        play(progress, false);
        //play(0, false);
    }

    private synchronized void play(final double progress, boolean earpiece) throws IOException {
        if (this.mediaPlayer != null)
            return;

        this.mediaPlayer = new MediaPlayerWrapper();
        this.audioAttachmentServer = new AttachmentServer(context, masterSecret, slide.asAttachment());
        this.startTime = System.currentTimeMillis();

        audioAttachmentServer.start();

        mediaPlayer.setDataSource(context, audioAttachmentServer.getUri());
        mediaPlayer.setAudioStreamType(earpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.w(TAG, "onPrepared");
                synchronized (AudioSlidePlayer.this) {
                    if (mediaPlayer == null)
                        return;

                    if (progress > 0) {
                        mediaPlayer.seekTo((int) (mediaPlayer.getDuration() * progress));
                    }

                    sensorManager.registerListener(AudioSlidePlayer.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                    mediaPlayer.start();
                }

                notifyOnStart(mediaPlayer.getDuration());
                progressEventHandler.sendEmptyMessage(0);
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.w(TAG, "onComplete");
                synchronized (AudioSlidePlayer.this) {
                    mediaPlayer = null;

                    if (audioAttachmentServer != null) {
                        audioAttachmentServer.stop();
                        audioAttachmentServer = null;
                    }

                    sensorManager.unregisterListener(AudioSlidePlayer.this);

                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                    }
                }

                notifyOnStop();
                progressEventHandler.removeMessages(0);
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.w(TAG, "MediaPlayer Error: " + what + " , " + extra);

                Toast.makeText(context, R.string.common_playing_audio_error, Toast.LENGTH_SHORT).show();

                synchronized (AudioSlidePlayer.this) {
                    mediaPlayer = null;

                    if (audioAttachmentServer != null) {
                        audioAttachmentServer.stop();
                        audioAttachmentServer = null;
                    }

                    sensorManager.unregisterListener(AudioSlidePlayer.this);

                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                    }
                }

                notifyOnStop();
                progressEventHandler.removeMessages(0);
                return true;
            }
        });

        mediaPlayer.prepareAsync();
    }

    /**
     * 
     *
     * @param forSensor true，false
     */
    private synchronized void stop(boolean forSensor) {
        this.forSensor = forSensor;

        Log.w(TAG, "Stop called forSensor:" + forSensor);

        if (this.mediaPlayer != null) {
            this.mediaPlayer.stop();
            this.mediaPlayer.release();
        }

        if (this.audioAttachmentServer != null) {
            this.audioAttachmentServer.stop();
        }

        sensorManager.unregisterListener(AudioSlidePlayer.this);
        if (!forSensor && wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        }

        this.mediaPlayer = null;
        this.audioAttachmentServer = null;
    }

    public synchronized void stop() {
        stop(false);
        notifyOnStop();
    }

    public void setListener(@NonNull Listener listener) {
        this.listener = new WeakReference<>(listener);

        if (this.mediaPlayer != null && this.mediaPlayer.isPlaying()) {
            progressEventHandler.notifyProgress();
        }
    }

    public @NonNull
    AudioSlide getAudioSlide() {
        return slide;
    }


    private Pair<Double, Integer> getProgress() {
        if (mediaPlayer == null || mediaPlayer.getCurrentPosition() <= 0 || mediaPlayer.getDuration() <= 0) {
            return new Pair<>(0D, 0);
        } else {
            return new Pair<>((double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration(),
                    mediaPlayer.getCurrentPosition());
        }
    }

    private void notifyOnStart(long totalMills) {
        Util.runOnMain(new Runnable() {
            @Override
            public void run() {
                getListener().onStart(AudioSlidePlayer.this, totalMills);
            }
        });
    }

    private void notifyOnStop() {
        Util.runOnMain(new Runnable() {
            @Override
            public void run() {
                getListener().onStop(AudioSlidePlayer.this);
            }
        });
    }

    private void notifyOnProgress(final double progress, final long millis) {
        Util.runOnMain(new Runnable() {
            @Override
            public void run() {
                getListener().onProgress(AudioSlidePlayer.this, progress, millis);
            }
        });
    }

    private @NonNull
    Listener getListener() {
        Listener listener = this.listener.get();

        if (listener != null)
            return listener;
        else
            return new Listener() {

                @Override
                public void onPrepare(AudioSlidePlayer player, long totalMills) {

                }

                @Override
                public void onStart(AudioSlidePlayer player, long totalMills) {
                }

                @Override
                public void onStop(AudioSlidePlayer player) {
                }

                @Override
                public void onProgress(AudioSlidePlayer player, double progress, long millis) {
                }
            };
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY)
            return;
        if (mediaPlayer == null || !mediaPlayer.isPlaying())
            return;

        int streamType;

        if (event.values[0] < 5f && event.values[0] != proximitySensor.getMaximumRange()) {
            streamType = AudioManager.STREAM_VOICE_CALL;
        } else {
            streamType = AudioManager.STREAM_MUSIC;
        }

        if (streamType == AudioManager.STREAM_VOICE_CALL &&
                mediaPlayer.getAudioStreamType() != streamType &&
                !audioManager.isWiredHeadsetOn()) {
            double position = mediaPlayer.getCurrentPosition();
            double duration = mediaPlayer.getDuration();
            double progress = position / duration;

            Logger.d("AudioSlidePlayer onSensorChanged, replay");
            if (wakeLock != null)
                wakeLock.acquire();
            stop(true);
            try {
                if (forSensor) {
                    play(progress, true);
                } else if (wakeLock != null) {
                    wakeLock.release();
                }
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        } else if (streamType == AudioManager.STREAM_MUSIC &&
                mediaPlayer.getAudioStreamType() != streamType &&
                System.currentTimeMillis() - startTime > 500) {

            Logger.d("AudioSlidePlayer onSensorChanged, stop");
            if (wakeLock != null)
                wakeLock.release();
            stop();
            notifyOnStop();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public long progress() {
        return progressEventHandler.progress();
    }

    public interface Listener {
        void onPrepare(AudioSlidePlayer player, long totalMills);

        void onStart(AudioSlidePlayer player, long totalMills);

        void onStop(AudioSlidePlayer player);

        void onProgress(AudioSlidePlayer player, double progress, long millis);
    }

    private static class ProgressEventHandler extends Handler {

        private final WeakReference<AudioSlidePlayer> playerReference;

        private ProgressEventHandler(@NonNull AudioSlidePlayer player) {
            this.playerReference = new WeakReference<>(player);
        }

        public long progress() {
            AudioSlidePlayer player = playerReference.get();
            if (player == null) {
                return 0;
            }

            return player.getProgress().second;
        }

        @Override
        public void handleMessage(Message msg) {
            notifyProgress();
        }

        public void notifyProgress() {
            AudioSlidePlayer player = playerReference.get();
            if (player == null) {
                return;
            }
            synchronized (AudioSlidePlayer.class) {
                if (player.mediaPlayer == null || !player.mediaPlayer.isPlaying()) {
                    return;
                }
                Pair<Double, Integer> progress = player.getProgress();
                player.notifyOnProgress(progress.first, progress.second);
                sendEmptyMessageDelayed(0, 50);
            }
        }
    }

    private static class MediaPlayerWrapper extends MediaPlayer {

        private int streamType;

        @Override
        public void setAudioStreamType(int streamType) {
            this.streamType = streamType;
            super.setAudioStreamType(streamType);
        }

        public int getAudioStreamType() {
            return streamType;
        }
    }
}
