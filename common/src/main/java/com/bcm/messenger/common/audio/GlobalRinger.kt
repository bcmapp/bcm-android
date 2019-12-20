package com.bcm.messenger.common.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.IOException

class GlobalRinger(private val context: Context) {
    private val TAG = "GlobalRinger"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    enum class Type {
        SONAR,
        RINGING,
        BUSY
    }

    private var mediaPlayer: MediaPlayer? = null

    fun start(resId:Int) {
        val soundId: Int = resId

        if (mediaPlayer != null) {
            mediaPlayer!!.release()
        }

        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            // 手机当前是震动or静音，不播放音频
            return
        }

        mediaPlayer = MediaPlayer()
        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_ALARM)
        mediaPlayer?.isLooping = false

        val packageName = context.packageName
        val dataUri = Uri.parse("android.resource://$packageName/$soundId")

        try {
            mediaPlayer?.setDataSource(context, dataUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, e)
        } catch (e: SecurityException) {
            Log.w(TAG, e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, e)
        } catch (e: IOException) {
            Log.w(TAG, e)
        }

    }

    fun stop() {
        if (mediaPlayer == null) return
        mediaPlayer?.release()
        mediaPlayer = null
    }
}