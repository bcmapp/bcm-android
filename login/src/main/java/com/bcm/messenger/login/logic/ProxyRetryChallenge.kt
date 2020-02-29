package com.bcm.messenger.login.logic

import android.annotation.SuppressLint
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.login.R
import com.bcm.messenger.login.bean.ChallengeResult
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.utility.bcmhttp.exception.ConnectionException
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

object ProxyRetryChallenge : IProxyStateChanged {
    private const val ACCOUNT_CHALLENGE = "/v1/accounts/challenge/%s"
    private var stashUid = ""

    fun request(uid: String): Observable<ChallengeResult> {
        stashUid = uid

        val accountContext = AccountContext(uid, "", "")
        return RxIMHttp.get(accountContext).get<ChallengeResult>(BcmHttpApiHelper.getApi(String.format(Locale.US, ACCOUNT_CHALLENGE, uid)),
                null, object : TypeToken<ChallengeResult>() {}.type)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .map {
                    RxIMHttp.remove(accountContext)
                    IMHttp.remove(accountContext)
                    it
                }
    }
}