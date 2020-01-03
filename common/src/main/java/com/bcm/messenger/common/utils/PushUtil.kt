package com.bcm.messenger.common.utils

import android.annotation.SuppressLint
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.common.gcm.FcmUtil
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IUmengModule
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.io.IOException

/**
 * bcm.social.01 2018/6/28.
 */
object PushUtil {
    private const val REGISTER_GCM_PATH = "/v1/accounts/gcm/"

    class SystemMessageList : NotGuard {
        var msgs = mutableListOf<AmePushProcess.SystemNotifyData>()
    }

    private val TAG = "PushUtil"
    private val SYSTEM_TAG = "system_broadcast"

    private const val SYSTEM_MESSAGE_GET = "/v1/system/msgs"
    private const val SYSTEM_MESSAGE_DELETE_MAXID = "/v1/system/msgs/%s"

    fun registerPush(accountContext: AccountContext): Boolean {
        if (!AMELogin.isLogin){
            return false
        }
        val status = PlayServicesUtil.getPlayServicesStatus(AppContextHolder.APP_CONTEXT)
        var gcmToken = ""
        if (status == PlayServicesUtil.PlayServicesStatus.SUCCESS){
            gcmToken = FcmUtil.getToken().orNull() ?: ""
        }
        return registerPush(accountContext, gcmToken)
    }

    fun registerPush(accountContext: AccountContext, gcmToken: String): Boolean {
        ALog.i(TAG, "registerPush")
        if (!AMELogin.isLogin){
            return false
        }
        val umengToken = AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.getPushToken(AppContextHolder.APP_CONTEXT) ?: ""
        ALog.d(TAG, "register push umeng token:$umengToken, gcmToken:$gcmToken")
        ALog.i(TAG, "register push umeng token size:${umengToken.length}, gcmToken size:${gcmToken.length}")

        try {
            registerPush2Server(accountContext, gcmToken, umengToken)
        } catch (e: Exception) {
            ALog.e(TAG, e)
            return false
        }

        if (umengToken.isNotEmpty()) {
            initUmengSystemTag()
        } else if (gcmToken.isNotEmpty()) {
            initFCMSystemTag()
        }

        return true
    }

    @Throws(IOException::class)
    private fun registerPush2Server(accountContext: AccountContext, gcmRegistrationId: String, umengRegistrationId: String) {
        val registration = GcmRegistrationId(gcmRegistrationId, umengRegistrationId, true)
        RxIMHttp.getHttp(accountContext).put<AmeEmpty>(BcmHttpApiHelper.getApi(REGISTER_GCM_PATH), GsonUtils.toJson(registration), AmeEmpty::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {
                    ALog.e(TAG, "registerPush2Server", it)
                }
                .subscribe()
    }

    private fun unregisterPush2Server(accountContext: AccountContext) {
        RxIMHttp.getHttp(accountContext).delete<AmeEmpty>(BcmHttpApiHelper.getApi(REGISTER_GCM_PATH),null,"",AmeEmpty::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {
                    ALog.e(TAG, "unregisterPush2Server", it)
                }
                .subscribe()
    }

    fun unregisterPush(accountContext: AccountContext): Boolean {
        ALog.i(TAG, "unregister push")
        try {
            unregisterPush2Server(accountContext)
        } catch (e:Exception) {
            ALog.e(TAG,e)
            return false
        }
        unregisterUmengSystemTag()
        unregisterFcmSystemTag()

        return true
    }

    private fun initUmengSystemTag() {
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.registerPushTag(AppContextHolder.APP_CONTEXT, SYSTEM_TAG)
    }

    private fun initFCMSystemTag() {
        FirebaseMessaging.getInstance().subscribeToTopic(SYSTEM_TAG)
    }

    private fun unregisterUmengSystemTag() {
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.unregisterPushTag(AppContextHolder.APP_CONTEXT, SYSTEM_TAG)

    }

    private fun unregisterFcmSystemTag() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(SYSTEM_TAG)
    }

    /**
     * App
     */
    private fun getSystemMessages(accountContext: AccountContext): Observable<ServerResult<SystemMessageList>> {
        return RxIMHttp.getHttp(accountContext).get<ServerResult<SystemMessageList>>(BcmHttpApiHelper.getApi(SYSTEM_MESSAGE_GET),
                null, object : TypeToken<ServerResult<SystemMessageList>>() {}.type)
    }

    /**
     * ，
     */
    fun confirmSystemMessages(accountContext: AccountContext, maxMid: Long): Observable<AmeEmpty> {
        return RxIMHttp.getHttp(accountContext).delete<AmeEmpty>(BcmHttpApiHelper.getApi(String.format(SYSTEM_MESSAGE_DELETE_MAXID, maxMid)),
                null, null, object : TypeToken<ServerResult<Void>>() {}.type)
    }

    var maxSystemMsgId = -1L
    @SuppressLint("CheckResult")
    fun loadSystemMessages(accountContext: AccountContext) {
        getSystemMessages(accountContext)
                .subscribeOn(Schedulers.io())
                .map { result->
                    if (result.isSuccess && result.data != null &&  result.data.msgs.size > 0) {
                        val dataMap = HashMap<String, AmePushProcess.SystemNotifyData>()
                        result.data.msgs.forEach {
                            if (it.id > maxSystemMsgId) {  //id，，id
                                maxSystemMsgId = it.id
                                if (!dataMap.containsKey(it.type))
                                    dataMap[it.type] = it
                                else {
                                    val exitData = dataMap.get(it.type)
                                    if (exitData != null) {
                                        if (exitData.activity_id == it.id) {
                                            if (exitData.id <= it.id) {
                                                dataMap[it.type] = it
                                            }
                                        } else {
                                            dataMap[it.type] = it
                                        }
                                    }
                                }
                            }

                        }
                        dataMap.values.map { AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.SYSTEM_NOTIFY, null, null, null, null, it)) }
                    } else {
                        throw Exception("")
                    }
                }
                .observeOn(Schedulers.io())
                .subscribe({
                    it.forEach { data ->
                        AmePushProcess.processPush(accountContext, data)
                    }
                }, {
                    ALog.e(TAG, it.localizedMessage)
                })
    }

    private data class GcmRegistrationId(val gcmRegistrationId: String, val umengRegistrationId: String, val webSocketChannel: Boolean):NotGuard
}