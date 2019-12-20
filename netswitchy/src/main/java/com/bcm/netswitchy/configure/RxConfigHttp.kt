package com.bcm.netswitchy.configure

import android.annotation.SuppressLint
import android.os.Looper
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.callback.StringCallback
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.facade.SyncHttpWrapper
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.JsonParseException
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.IOException
import java.lang.reflect.Type
import java.net.URI
import java.util.*


class RxConfigHttp : BaseHttp() {
    companion object {
        private const val TAG = "RxConfigHttp"
        private const val FRONT_DOMAIN = "fastly.bcm.social"
        const val FRONT_HOST = "cdn.kernel.org"
        const val DIRECT_HOST = "boot.bcm.social:6443"
        const val DIRECT_URL = "https://$DIRECT_HOST"
    }

    private var succeedType = SucceedType.DIRECT

    private enum class SucceedType {
        DIRECT,
        FRONT_DOMAIN,
        ALL_FAILED
    }

    init {
        val client = OkHttpClient.Builder()
                .build()
        setClient(client)
    }

    @Throws(HttpErrorException::class, IOException::class)
    fun <T:Config> syncGetConfig(url: String, typeOfT: Type):T {
        val frontDomain = succeedType == SucceedType.FRONT_DOMAIN
        return try {
            syncGetConfigImpl(url, typeOfT, frontDomain)
        } catch (e:HttpErrorException) {
            val result = syncGetConfigImpl<T>(url, typeOfT, !frontDomain)
            succeedType = typeByDomain(!frontDomain)
            result
        }
    }

    fun <T : Config> getConfig(url: String, typeOfT: Type): Observable<T> {
        return Observable.create<T> { emit ->
            ALog.i(TAG, "getConfig with $succeedType start")

            val frontDomain = succeedType == SucceedType.FRONT_DOMAIN
            getConfigImpl<T>(url, typeOfT, frontDomain)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .doOnError { e ->
                        if (e is HttpErrorException && e.code != 0) {
                            ALog.e(TAG, "getConfig with $succeedType failed, error:${e.code}", e)
                            emit.onError(e)
                            return@doOnError
                        }

                        ALog.e(TAG, "getConfig with $succeedType failed, and retry", e)
                        getConfigImpl<T>(url, typeOfT, !frontDomain)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .doOnError {
                                    succeedType = SucceedType.ALL_FAILED
                                    ALog.e(TAG, "getConfig all failed", it)
                                    emit.onError(it)
                                }
                                .subscribe {
                                    succeedType = typeByDomain(!frontDomain)
                                    ALog.i(TAG, "getConfig with $succeedType succeed")
                                    emit.onNext(it)
                                    emit.onComplete()
                                }
                    }
                    .subscribe {
                        ALog.i(TAG, "getConfig with $succeedType succeed")
                        emit.onNext(it)
                        emit.onComplete()
                    }
        }
    }

    private fun typeByDomain(frontDomain: Boolean): SucceedType {
        return if (frontDomain) {
            SucceedType.FRONT_DOMAIN
        } else {
            SucceedType.DIRECT
        }
    }

    @SuppressLint("CheckResult")
    private fun <T : Config> getConfigImpl(url: String, typeOfT: Type, frontDomain: Boolean = false): Observable<T> {
        val origUri = URI(url)
        val realUrl = if (frontDomain) {
            setFrontDomain(FRONT_DOMAIN)
            URI(origUri.scheme.toLowerCase(Locale.US), FRONT_HOST,
                    origUri.path, origUri.query, origUri.fragment)
        } else {
            setFrontDomain(null)
            URI(origUri.scheme.toLowerCase(Locale.US), DIRECT_HOST,
                    origUri.path, origUri.query, origUri.fragment)
        }

        val queryUrl = realUrl.toString()

        val scheduler = if (Looper.myLooper() == Looper.getMainLooper()) {
            AndroidSchedulers.mainThread()
        } else {
            Schedulers.io()
        }

        ALog.d(TAG, "getConfigImpl $queryUrl")
        return Observable.create<ConfigurePackage> { emit ->
            this.get()
                    .url(queryUrl)
                    .build()
                    .writeTimeOut(DEFAULT_MILLISECONDS)
                    .readTimeOut(DEFAULT_MILLISECONDS)
                    .connTimeOut(DEFAULT_MILLISECONDS)
                    .enqueue(GsonSerializeHelper<ConfigurePackage>(emit, ConfigurePackage::class.java))
        }.map {
            ALog.d("RxConfigHttp", "$queryUrl ${GsonUtils.toJson(it)}")
            try {
                val dataString = if (it.settings != null) {
                    GsonUtils.toJson(it.settings)

                } else if (it.lbs != null) {
                    GsonUtils.toJson(it.lbs)
                } else if (it.name != null && it.services != null && it.deviceArea != null) {
                    GsonUtils.toJson(it)
                } else {
                    throw HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "query url:$url parse error")
                }
                return@map Gson().fromJson<T>(dataString, typeOfT)
            } catch (e: Throwable) {
                ALog.e(TAG, "getConfig url: $url", e)
                throw HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "query url:$url parse error")
            }
        }.observeOn(scheduler)
    }

    @SuppressLint("CheckResult")
    @Throws(HttpErrorException::class, IOException::class)
    private fun <T : Config> syncGetConfigImpl(url: String, typeOfT: Type, frontDomain: Boolean = false): T {
        val origUri = URI(url)
        val realUrl = if (frontDomain) {
            setFrontDomain(FRONT_DOMAIN)
            URI(origUri.scheme.toLowerCase(Locale.US), FRONT_HOST,
                    origUri.path, origUri.query, origUri.fragment)
        } else {
            setFrontDomain(null)
            URI(origUri.scheme.toLowerCase(Locale.US), DIRECT_HOST,
                    origUri.path, origUri.query, origUri.fragment)
        }

        val queryUrl = realUrl.toString()
        ALog.d(TAG, "syncGetConfigImpl $queryUrl")

        val syncHttp = SyncHttpWrapper(this)
        try {
            val resp = syncHttp.get<ConfigurePackage>(queryUrl, null, ConfigurePackage::class.java)
            val dataString = if (resp.settings != null) {
                GsonUtils.toJson(resp.settings)
            } else if (resp.lbs != null) {
                GsonUtils.toJson(resp.lbs)
            } else if (resp.name != null && resp.services != null && resp.deviceArea != null) {
                GsonUtils.toJson(resp)
            } else {
                throw HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "query url:$url parse error")
            }
            return GsonUtils.fromJson(dataString, typeOfT)
        } catch (e: Throwable) {
            ALog.e(TAG, "syncGetConfigImpl url: $url", e)
            when (e) {
                is HttpErrorException -> throw e
                is JsonParseException -> throw HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "query url:$url parse error")
                else -> {
                    throw IOException(e)
                }
            }
        }
    }

    private class GsonSerializeHelper<T>(private val emitter: Emitter<T>, private val typeOfT: Type) : StringCallback() {
        override fun onError(call: Call?, e: Exception?, id: Long) {
            emitter.onError(e ?: Exception("unknown"))
        }

        override fun onResponse(response: String?, id: Long) {
            if (typeOfT == AmeEmpty::class.java) {
                emitter.onNext(AmeEmpty() as T)
            } else if (response.isNullOrEmpty()) {
                emitter.onError(HttpErrorException(204, "no content"))
                return
            } else {
                try {
                    emitter.onNext(GsonUtils.fromJson(response, typeOfT))
                } catch (e: Throwable) {
                    emitter.onError(e)
                    return
                }
            }
            emitter.onComplete()
        }
    }

    data class ConfigurePackage(val settings: LinkedHashMap<Any, Any>?,
                                val data: LinkedHashMap<Any, Any>?,
                                val lbs: LinkedHashMap<Any, Any>?,
                                val name: String?,
                                val deviceArea: Int?,
                                val services: Any?) : NotGuard

    open class Config : NotGuard
}