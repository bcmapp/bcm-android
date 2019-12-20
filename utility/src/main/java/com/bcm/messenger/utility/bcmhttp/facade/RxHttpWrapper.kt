package com.bcm.messenger.utility.bcmhttp.facade

import android.net.Uri
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.callback.StringCallback
import com.bcm.messenger.utility.bcmhttp.exception.ConnectionException
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import io.reactivex.Emitter
import io.reactivex.Observable
import okhttp3.Call
import okhttp3.OkHttpClient
import java.lang.reflect.Type
import java.net.ConnectException
import java.net.SocketTimeoutException

open class RxHttpWrapper(val impl: BaseHttp) {
    fun setClient(client: OkHttpClient) {
        impl.setClient(client)
    }

    fun <T> get(url: String, params: Map<String, String>?, typeOfT: Type): Observable<T> {
        return Observable.create<T> { emit ->
            var requestUrl = url
            if (params != null && !params.isEmpty()) {
                requestUrl = appendParams(url, params)
            }

            impl.get()
                    .url(requestUrl)
                    .addHeader("content-type", "application/json")
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .enqueue(GsonSerializeHelper<T>(emit, typeOfT))
        }
    }

    fun <T> post(url: String, params: Map<String, Any>?, typeOfT: Type): Observable<T> {
        val content = if (params == null || params.isEmpty()) {
            ""
        } else {
            GsonUtils.toJson(params)
        }
        return post(url, content, typeOfT)
    }

    fun <T> post(url: String, body: String, typeOfT: Type): Observable<T> {
        return Observable.create<T> { emit ->
            impl.postString()
                    .url(url)
                    .addHeader("content-type", "application/json")
                    .content(body)
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .enqueue(GsonSerializeHelper<T>(emit, typeOfT))
        }
    }


    fun <T> put(url: String, params: Map<String, String>?,
                body: String, typeOfT: Type): Observable<T> {

        var requestUrl = url
        if (params != null && params.isNotEmpty()) {
            requestUrl = appendParams(url, params)
        }

        return put(requestUrl, body, typeOfT)
    }

    fun <T> put(url: String, body: String, typeOfT: Type): Observable<T> {
        return Observable.create<T> { emit ->
            impl.putString()
                    .url(url)
                    .addHeader("content-type", "application/json")
                    .content(body)
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .enqueue(GsonSerializeHelper<T>(emit, typeOfT))
        }
    }

    fun <T> delete(url: String, params: Map<String, String>?,
                   body: String?, typeOfT: Type): Observable<T> {

        return Observable.create<T> { emit ->
            var requestUrl = url
            if (params != null && params.isNotEmpty()) {
                requestUrl = appendParams(url, params)
            }

            val builder = impl.deleteString()
                    .url(requestUrl)
                    .addHeader("content-type", "application/json")
                    .content(body ?: "")

            builder.build()
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .enqueue(GsonSerializeHelper<T>(emit, typeOfT))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class GsonSerializeHelper<T>(private val emitter: Emitter<T>, private val typeOfT: Type) : StringCallback() {
        override fun onError(call: Call?, e: Exception?, id: Long) {
            emitter.onError(e?:BaseHttp.HttpErrorException(-200,""))
        }

        override fun onResponse(response: String?, id: Long) {
            when {
                typeOfT == AmeEmpty::class.java -> emitter.onNext(AmeEmpty() as T)
                response.isNullOrEmpty() -> {
                    emitter.onError(NoContentException())
                    return
                }
                typeOfT == String::class.java -> emitter.onNext(response as T)
                else -> try {
                    emitter.onNext(GsonUtils.fromJson(response, typeOfT))
                } catch (e: Throwable) {
                    emitter.onError(e)
                    return
                }
            }
            emitter.onComplete()
        }
    }

    private fun appendParams(url: String, params: Map<String, String>?): String {
        if (params == null || params.isEmpty()) {
            return url
        }
        val builder = Uri.parse(url).buildUpon()
        for ((key, value) in params) {
            builder.appendQueryParameter(key, value)
        }
        return builder.build().toString()
    }
}