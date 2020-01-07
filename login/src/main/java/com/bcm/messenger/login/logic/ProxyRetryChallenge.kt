package com.bcm.messenger.login.logic

import android.annotation.SuppressLint
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.login.R
import com.bcm.messenger.login.bean.ChallengeResult
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

object ProxyRetryChallenge: IProxyStateChanged {
    private const val ACCOUNT_CHALLENGE = "/v1/accounts/challenge/%s"
    private var observableEmitter    = EmitterProxy(null)
    private var stashUid = ""

    fun request(uid: String): Observable<ChallengeResult> {
        stashUid = uid

        return Observable.create {
            observableEmitter.emiter = it

            val accountContext = AccountContext(uid, "", "")
            RxIMHttp.get(accountContext).get<ChallengeResult>(BcmHttpApiHelper.getApi(String.format(Locale.US, ACCOUNT_CHALLENGE, uid)),
                    null, object : TypeToken<ChallengeResult>() {}.type)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe ({ challenge ->
                        RxIMHttp.remove(accountContext)
                        observableEmitter.onComplete(challenge)
                    }, { e ->
                        RxIMHttp.remove(accountContext)
                        AmePopup.tipLoading.updateTip(AppUtil.getString(R.string.login_loading_connecting_server))
                        tryProxy(e)}
                    )
        }
    }

    private fun tryProxy(exception: Throwable) {
        if (!ProxyManager.isReady()) {
            observableEmitter.onError(exception)
            return
        }

        ProxyManager.setListener(this)
        ProxyManager.checkConnectionState {
            if (!it) {
                when {
                    ProxyManager.isReady() -> ProxyManager.startProxy()
                    else -> {
                        ProxyManager.refresh()
                        observableEmitter.onError(exception)
                    }
                }
            } else {
                observableEmitter.onError(exception)
                ALog.i("LoginVerifyPinFragment", "network is working, ignore start proxy")
            }
        }
    }

    override fun onProxyConnecting(proxyName: String, isOfficial: Boolean) {
        val tip = if (isOfficial) {
            AppContextHolder.APP_CONTEXT.resources.getString(R.string.login_try_user_proxy_using_xxx, proxyName)
        } else {
            AppContextHolder.APP_CONTEXT.resources.getString(R.string.login_try_official_proxy_using_xxx)
        }

        AmePopup.tipLoading.updateSubTip(tip)
    }

    @SuppressLint("CheckResult")
    override fun onProxyConnectFinished() {
        if (ProxyManager.isProxyRunning()) {
            AmePopup.tipLoading.updateTip("")
            AmePopup.tipLoading.updateTip("")
            val accountContext = AccountContext(stashUid, "", "")
            RxIMHttp.get(accountContext).get<ChallengeResult>(BcmHttpApiHelper.getApi(String.format(Locale.US, ACCOUNT_CHALLENGE, stashUid)),
                    null, object : TypeToken<ChallengeResult>() {}.type)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe ({ challenge ->
                        RxIMHttp.remove(accountContext)
                        observableEmitter.onComplete(challenge)
                    },{
                        RxIMHttp.remove(accountContext)
                        observableEmitter.onError(it)
                    })
        } else {
            observableEmitter.onError(Exception(""))
        }
    }

    private class EmitterProxy(var emiter:ObservableEmitter<ChallengeResult>?) {
        fun onError(e:Throwable) {
            emiter?.onError(e)
            emiter = null
        }

        fun onComplete(result: ChallengeResult) {
            emiter?.onNext(result)
            emiter?.onComplete()
            emiter = null
        }
    }
}