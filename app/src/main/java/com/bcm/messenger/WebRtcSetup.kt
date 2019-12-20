package com.bcm.messenger

import android.os.Build
import com.bcm.messenger.chats.components.ChatPinDisplayView.Companion.TAG
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import org.webrtc.PeerConnectionFactory
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioUtils

object WebRtcSetup {
    fun setup() {
        try {
            val hardwareBlackList = listOf("Pixel", "Pixel XL", "Moto G5")
            val opensslESWhiteList = listOf("Pixel", "Pixel XL")

            if (hardwareBlackList.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
            }

            if (!opensslESWhiteList.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true)
            }

            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(AppContextHolder.APP_CONTEXT).createInitializationOptions())

        } catch (e: UnsatisfiedLinkError) {
            ALog.e(TAG, "initializeWebRtc", e)
        }
    }
}