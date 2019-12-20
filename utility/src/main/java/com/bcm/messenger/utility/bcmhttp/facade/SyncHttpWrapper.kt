package com.bcm.messenger.utility.bcmhttp.facade

import android.net.Uri
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder.FileCallBuilder
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type

open class SyncHttpWrapper(val impl: BaseHttp) {
    fun setClient(client: OkHttpClient) {
        impl.setClient(client)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> get(url: String, params: Map<String, String>?, typeOfT: Type): T {
        var requestUrl = url
        if (params != null && params.isNotEmpty()) {
            requestUrl = appendParams(url, params)
        }

        val response = impl.get()
                .url(requestUrl)
                .addHeader("content-type", "application/json")
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .buildCall().execute()
        return parseResponse(response, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> post(url: String, params: Map<String, Any>?, typeOfT: Type): T {
        val content = if (params == null || params.isEmpty()) {
            ""
        } else {
            GsonUtils.toJson(params)
        }
        return post(url, content, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> post(url: String, body: String, typeOfT: Type): T {
        val response = impl.postString()
                .url(url)
                .addHeader("content-type", "application/json")
                .content(body)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .buildCall().execute()
        return parseResponse(response, typeOfT)
    }


    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> put(url: String, params: Map<String, String>?,
                body: String, typeOfT: Type): T {

        var requestUrl = url
        if (params != null && params.isNotEmpty()) {
            requestUrl = appendParams(url, params)
        }

        return put(requestUrl, body, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> put(url: String, body: String, typeOfT: Type): T {
        val response = impl.putString()
                .url(url)
                .addHeader("content-type", "application/json")
                .content(body)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .buildCall()
                .execute()

        return parseResponse(response, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> postForm(url: String
                     , key: String
                     , filename: String?
                     , body: RequestBody
                     , typeOfT: Type
                     , paramsProvider: ((builder: FileCallBuilder.PostFormBuilder) -> Unit)? = null): T {


        val builder = impl.postForm()
        paramsProvider?.invoke(builder)
        val call = builder.addFormPart(key, filename, body)
                .url(url)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .buildCall()

        val response = call.execute()
        return parseResponse(response, typeOfT)
    }


    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun <T> delete(url: String, params: Map<String, String>?,
                   body: String?, typeOfT: Type): T {

        var requestUrl = url
        if (params != null && params.isNotEmpty()) {
            requestUrl = appendParams(url, params)
        }

        val builder = impl.deleteString()
                .url(requestUrl)
                .addHeader("content-type", "application/json")
                .content(body ?: "")

        val response = builder.build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .buildCall()
                .execute()

        return parseResponse(response, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, BaseHttp.HttpErrorException::class)
    fun getFile(url: String, params: Map<String, String>?): NetInputStreamWrapper {
        var requestUrl = url
        if (params != null && params.isNotEmpty()) {
            requestUrl = appendParams(url, params)
        }

        val response = impl.get()
                .url(requestUrl)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .buildCall().execute()

        if (response.isSuccessful) {
            val stream = response.body()?.byteStream()?:throw NoContentException()
            return NetInputStreamWrapper(response.body()?.contentLength()
                    ?: 0, stream)
        } else {
            throw BaseHttp.HttpErrorException(response.code(), response.message()?:"")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parseResponse(response: Response?, typeOfT: Type): T {
        if (response?.isSuccessful == true) {
            val resp = response.body()?.string()
            return when {
                typeOfT == AmeEmpty::class.java -> AmeEmpty() as T
                resp.isNullOrEmpty() -> throw NoContentException()
                typeOfT == String::class.java -> resp as T
                else -> try {
                    GsonUtils.fromJson<T>(resp, typeOfT) ?: throw (BaseHttp.HttpErrorException(0, "parse error"))
                } catch (e: Throwable) {
                    throw BaseHttp.HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "")
                }
            }
        } else {
            throw BaseHttp.HttpErrorException(response?.code()
                    ?: -200, response?.message() ?: "")
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

    data class NetInputStreamWrapper(val contentLength:Long, val stream:InputStream)
}