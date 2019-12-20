package com.bcm.messenger.common.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.bcm.messenger.utility.AppContextHolder
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2018/12/26
 */
private const val VOLUME_ACTION = "android.media.VOLUME_CHANGED_ACTION"
private const val VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

object VolumeChangeObserver {
    private var registered = false
    private var receiver: VolumeBroadcastReceiver? = null
    private val audioManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val callbackList = mutableListOf<(Int) -> Unit>()

    fun registerReceiver() {
        receiver = VolumeBroadcastReceiver(this)
        AppContextHolder.APP_CONTEXT.registerReceiver(receiver, IntentFilter().apply {
            addAction(VOLUME_ACTION)
        })
        registered = true
    }

    fun unregisterReceiver() {
        try {
            AppContextHolder.APP_CONTEXT.unregisterReceiver(receiver)
            registered = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCallback(callback: (Int) -> Unit) {
        this.callbackList.add(callback)
    }

    fun removeCallback(callback: (Int) -> Unit) {
        this.callbackList.remove(callback)
    }

    private fun getCurrentVolume() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private class VolumeBroadcastReceiver(volumeChangeObserver: VolumeChangeObserver) : BroadcastReceiver() {
        private val weakObserver = WeakReference(volumeChangeObserver)

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_ACTION && intent.getIntExtra(VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC) {
                val observer = weakObserver.get()
                observer?.let {
                    it.callbackList.forEach { callback ->
                        callback(it.getCurrentVolume())
                    }
                }
            }
        }
    }
}