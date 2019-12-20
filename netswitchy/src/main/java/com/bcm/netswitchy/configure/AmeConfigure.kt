package com.bcm.netswitchy.configure

import android.annotation.SuppressLint
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.storage.SPEditor
import okhttp3.OkHttpClient
import java.io.IOException
import java.lang.reflect.Type

/**
 * Created by bcm.social.01 on 2018/8/8.
 */
object AmeConfigure {
    private const val TAG = "AmeConfigure"

    const val LBS_API = "/v1/lbs/"
    const val SETTING_API = "/v1/settings?"
    const val ALL_API = "/v1/all?"

    private val configSp = SPEditor("login_profile_preferences")
    private val configHttp = RxConfigHttp()

    private var devMode = false

    fun init(devMode: Boolean) {
        this.devMode = devMode
    }


    fun setClient(client: OkHttpClient) {
        configHttp.setClient(client)
    }

    /***
     * fetch lbs configure
     */
    @Throws(BaseHttp.HttpErrorException::class, IOException::class)
    fun queryLBS(url: String): LBSConfig {
        return configHttp.syncGetConfig(url, object : TypeToken<LBSConfig>() {}.type)
    }

    fun queryProxy(): Observable<String> {
        return querySetting<ProxyConfig>("proxy_uris", object : TypeToken<ProxyConfig>() {}.type)
                .map {
                    it.proxy_uris ?: ""
                }
    }

    fun queryOfficialEnable():Observable<Boolean> {
        return Observable.create<Boolean> { emit ->
            querySetting<OfficialEnable>("g_secure_v3", object : TypeToken<OfficialEnable>() {}.type)
                    .doOnError {
                        ALog.e(TAG, "queryOfficialEnable enable", it)
                        emit.onNext(false)
                        emit.onComplete()
                    }
                    .subscribe {
                        ALog.i(TAG, "queryOfficialEnable enable ${it.g_official_enable ?: "null"}")
                        emit.onNext(it.g_official_enable != 0)
                        emit.onComplete()
                    }
        }
    }

    @SuppressLint("CheckResult")
    fun checkAutoDeleteEnable(result: (enable: Boolean) -> Unit) {
        querySetting<AutoDeleteEnable>("auto_delete_enable", object : TypeToken<AutoDeleteEnable>() {}.type)
                .doOnError {
                    result(false)
                }
                .subscribe {
                    if (null != it) {
                        try {
                            configSp.set("auto_delete_enable", it.auto_delete_enable?.toInt() == 1)
                        } catch (e: Throwable) {
                            ALog.e(TAG, e)
                        }

                        result(configSp.get("auto_delete_enable", false))
                    }
                }
    }

    fun isOutgoingAutoDelete(): Boolean {
        return configSp.get("auto_delete_enable", false)
    }

    fun isContactTransformEnable(): Boolean {
        return configSp.get("contact_transform_enable", true)
    }

    @SuppressLint("CheckResult")
    fun checkContactTransformEnable() {
        querySetting<ContactTransformEnable>("contact_transform_enable", object : TypeToken<ContactTransformEnable>() {}.type)
                .doOnError {
                    ALog.e(TAG, it)
                }
                .subscribe {
                    if (null != it) {
                        try {
                            configSp.set("contact_transform_enable", it.contact_transform_enable?.toInt() == 1)
                            ALog.i(TAG, "checkContactTransformEnable contact_transform_enable ${it.contact_transform_enable}")
                        } catch (e: Throwable) {
                            ALog.e(TAG, e)
                        }
                    }
                }
    }

    /**
     * check v3 group enable
     */
    fun queryGroupSecureV3Enable(): Observable<Boolean> {
        return Observable.create<Boolean> { emit ->
            querySetting<GroupSecureV3Enable>("g_secure_v3", object : TypeToken<GroupSecureV3Enable>() {}.type)
                    .doOnError {
                        ALog.e(TAG, "Group Secure V3 enable", it)
                        emit.onNext(true)
                        emit.onComplete()
                    }
                    .subscribe {
                        ALog.i(TAG, "Group Secure V3 enable ${it.g_secure_v3 ?: "null"}")
                        emit.onNext(it.g_secure_v3 != 0)
                        emit.onComplete()
                    }
        }
    }

    /**
     * query upgrade configure
     */
    @SuppressLint("CheckResult")
    fun getUpgradeVersionInfo(result: (data: UpdateData?) -> Unit) {
        querySettingByTag<UpdateData>("upgrade", object : TypeToken<UpdateData>() {}.type)
                .doOnError {
                    result(null)
                }
                .subscribe {
                    result(it)
                }
    }

    private fun <T : RxConfigHttp.Config> querySetting(key: String, typeOfT: Type): Observable<T> {
        val appType = if (devMode) {
            2
        } else {
            1
        }

        val context = AppContextHolder.APP_CONTEXT
        val buildCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

        val url = "${RxConfigHttp.DIRECT_URL}${SETTING_API}apptype=${appType}&buildcode=${buildCode}&key=$key"
        return configHttp.getConfig(url, typeOfT)
    }

    private fun <T : RxConfigHttp.Config> querySettingByTag(tag: String, typeOfT: Type): Observable<T> {
        val appType = if (devMode) {
            2
        } else {
            1
        }

        val context = AppContextHolder.APP_CONTEXT
        val buildCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

        val url = "${RxConfigHttp.DIRECT_URL}${SETTING_API}apptype=${appType}&buildcode=${buildCode}&tag=$tag"
        return configHttp.getConfig(url, typeOfT)
    }


    data class AutoDeleteEnable(val auto_delete_enable: String?) : RxConfigHttp.Config()
    data class GroupSecureV3Enable(val g_secure_v3: Int?) : RxConfigHttp.Config()
    data class OfficialEnable(val g_official_enable: Int?) : RxConfigHttp.Config()
    data class ContactTransformEnable(val contact_transform_enable: String?) : RxConfigHttp.Config()
    data class UpdateData(val last_version: String,             // last release version，eg:1.1.8
                          val version_code: String,             // last release version code，eg:318
                          val download_url: String,             // apk download url
                          val force_update_min: String,         // force upgrade configure，range of min Version Code，-1 ignore
                          val force_update_max: String,         // force upgrade configure，range of max Version Code，-1 ignore
                          val update_info_zh: String,           // upgrade log
                          val update_info_en: String,           // upgrade log
                          val force_update_info_zh: String,     // force upgrade log
                          val force_update_info_en: String,     // force upgrade log
                          val google_package: String?           // Play Store package name
    ) : RxConfigHttp.Config() {
        fun checkDataAvailable(): Boolean {
            return !last_version.isNullOrBlank() &&
                    !version_code.isNullOrBlank() &&
                    !download_url.isNullOrBlank() &&
                    !force_update_max.isNullOrBlank() &&
                    !force_update_min.isNullOrBlank()
        }
    }

    data class LBSConfig(val name: String, val deviceArea: Int, val services: List<IMServer>) : RxConfigHttp.Config()
    data class IMServer(val name: String, val ip: String, val port: Int, val area: Int, val priority: Int) : RxConfigHttp.Config()
    data class ProxyConfig(val proxy_uris: String?) : RxConfigHttp.Config()
}