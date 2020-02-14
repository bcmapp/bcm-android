package com.bcm.messenger.common.audio

import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.mms.AudioSlide
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck

class ChatsAudioPlayer {
    companion object {
        val instance: ChatsAudioPlayer by lazy {
            ChatsAudioPlayer()
        }

        fun getDuration(masterSecret: MasterSecret?, slide: AudioSlide):Long {
            if (slide.uri == null) {
                return 0
            }

            return AudioSlidePlayer.getDuration(AppContextHolder.APP_CONTEXT, masterSecret, slide.asAttachment())
        }

        private val defaultListener = object :AudioSlidePlayer.Listener {
            override fun onPrepare(player: AudioSlidePlayer?, totalMills: Long) {
            }

            override fun onStart(player: AudioSlidePlayer?, totalMills: Long) {
            }

            override fun onStop(player: AudioSlidePlayer?) {
            }

            override fun onProgress(player: AudioSlidePlayer?, progress: Double, millis: Long) {
            }
        }
    }

    private var player: AudioSlidePlayer? = null
    private var playingSlide: AudioSlide? = null

    fun play(masterSecret: MasterSecret?,
             slide: AudioSlide,
             listener: AudioSlidePlayer.Listener) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }
        stop()

        playingSlide = slide
        player = AudioSlidePlayer.createFor(AppContextHolder.APP_CONTEXT, masterSecret, slide, listener)
        player?.play(0.0)
    }

    fun setEventListener(listener: AudioSlidePlayer.Listener?) {
        player?.setListener(listener?:defaultListener)
    }

    fun pause() {
        player?.stop()
    }

    fun resume(process: Double) {
        player?.play(process)
    }

    fun isPlaying(slide: AudioSlide): Boolean {
        return slide.uri != null && slide.uri == playingSlide?.uri
    }

    fun progress(): Long {
        return player?.progress() ?: 0
    }

    fun stop() {
        val player = this.player
        this.player = null
        playingSlide = null
        player?.stop()
    }
}