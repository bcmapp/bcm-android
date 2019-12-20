package com.bcm.messenger.chats.bean

import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by Kin on 2019/1/2
 */
class YoutubeInfo: NotGuard {
    val playabilityStatus: Status? = null
    val streamingData: StreamingData? = null
}

class StreamingData: NotGuard {
    val expiresInSeconds = ""
    val formats = listOf<Formats>()
}

class Status: NotGuard {
    val status = ""
    val playableInEmbed = true
}

class Formats: NotGuard {
    val itag = 0
    val url = ""
    val mimeType = ""
    val bitrate = 0
    val width = 0
    val height = 0
    val lastModified = ""
    val contentLength = ""
    val quality = ""
    var qualityLabel = ""
    val projectionType = ""
    val averageBitrate = 0
    val audioQuality = ""
    val approxDurationMs = ""
    val audioSampleRate = ""
}