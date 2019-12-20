package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.interceptor.metrics.FileMetricsInterceptor
import com.bcm.messenger.common.glide.OkHttpUrlLoader
import com.bcm.messenger.utility.bcmhttp.call.RequestCall
import com.bcm.messenger.utility.bcmhttp.callback.Callback
import com.bcm.messenger.utility.bcmhttp.interceptor.ProgressInterceptor
import com.bcm.messenger.utility.bcmhttp.utils.progress.ProgressManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object FileHttp: BcmBaseHttp() {
    init {
        val client = OkHttpClient.Builder()
                .addInterceptor(FileMetricsInterceptor())
                .addInterceptor(ProgressInterceptor())
                .readTimeout(DEFAULT_FILE_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_FILE_MILLISECONDS, TimeUnit.MILLISECONDS)
                .build()
        setClient(client)
    }

    fun getOkHttpFactory():OkHttpUrlLoader.Factory {
        return OkHttpUrlLoader.Factory(getClient())
    }

    override fun post(requestCall: RequestCall, callback: Callback<*>?) {
        val innerCallback = callback ?: Callback.CALLBACK_DEFAULT

        val id = requestCall.id
        if (requestCall.enableUploadProgress()) {
            ProgressManager.getInstance().registerRequestListener(id) { progressInfo -> broadcastProgressCallback(callback, id, progressInfo) }
        } else if (requestCall.enableDownloadProgress()) {
            ProgressManager.getInstance().registerResponseListener(id) { progressInfo -> broadcastProgressCallback(callback, id, progressInfo) }
        }

        requestCall.call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                ProgressManager.getInstance().unregisterProgressListener(id)
                sendFailResultCallback(call, e, callback, id)
            }

            override fun onResponse(call: Call, response: Response) {
                ProgressManager.getInstance().unregisterProgressListener(id)
                try {
                    if (call.isCanceled) {
                        sendFailResultCallback(call, HttpErrorException(0, ""), callback, id)
                        return
                    }

                    if (!innerCallback.validateResponse(response, id)) {
                        val error = HttpErrorException(response.code(), response.message())
                        sendFailResultCallback(call, error, callback, id)
                        return
                    }

                    val o = innerCallback.parseNetworkResponse(response, id)
                    sendSuccessResultCallback(o, callback, id)
                } catch (e: Exception) {
                    sendFailResultCallback(call, e, callback, id)
                } finally {
                    if (response.body() != null)
                        response.body()!!.close()
                }
            }
        })
    }
}