package com.bcm.messenger.chats.util

import android.net.Uri
import com.bcm.messenger.chats.bean.YoutubeInfo
import com.bcm.messenger.utility.GsonUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import com.bcm.messenger.utility.logger.ALog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeLinkUtil"
private val client by lazy { OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build() }

fun analyseYoutubeUrl(url: String, result: (success: Boolean, realUrl: String, duration: Long) -> Unit) {
    Observable.create<Pair<String, Long>> {
        try {
            val uri = Uri.parse(url)
            val videoId = if (uri.host == "youtu.be") {
                uri.path.substring(1)
            } else {
                uri.getQueryParameter("v")
            }
            val reqUrl = "https://www.youtube.com/get_video_info?video_id=$videoId"
            val req = Request.Builder().url(reqUrl).get().build()
            val res = client.newCall(req).execute()
            val resStr = res.body()?.string() ?: ""
            val decodeStr = URLDecoder.decode(resStr, "utf-8")
            val jsonStr = decodeStr.substring(decodeStr.indexOf("{\""), decodeStr.indexOf(",\"playbackTracking\"")) + "}"
            val info = GsonUtils.gson.fromJson(jsonStr, YoutubeInfo::class.java)
            val data = info.streamingData
            var pair: Pair<String, Long>? = null
            var quality = "720p"
            var duration = 0L
            loop@while (pair == null && quality != "144p") {
                if (data?.formats != null) {
                    for (formats in data.formats) {
                        if (duration == 0L && formats.approxDurationMs.isNotBlank()) {
                            duration = formats.approxDurationMs.toLong()
                        }
                        if (formats.qualityLabel == quality) {
                            pair = Pair(formats.url, duration)
                            break@loop
                        }
                    }
                }
                quality = when (quality) {
                    "720p" -> "480p"
                    "480p" -> "360p"
                    "360p" -> "240p"
                    else -> "144p"
                }
            }
            if (pair != null) {
                it.onNext(pair)
            } else {
                it.onNext(Pair("", 0L))
            }
        } catch (e: Exception) {
            it.onError(e)
        } finally {
            it.onComplete()
        }
    }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it.first.isNotBlank()) {
                    result(true, it.first, it.second)
                } else {
                    result(false, "", 0L)
                }
            }, {
                ALog.logForSecret(TAG, "Analyse Youtube link failed, reason is ${it.message}", it)
                result(false, "", 0L)
            })
}