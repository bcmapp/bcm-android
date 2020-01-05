package com.bcm.messenger.common.bcmhttp.conncheck

import android.annotation.SuppressLint
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.bcmhttp.callback.StringCallback
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.support.IConnectionChecker
import io.reactivex.Observable
import io.reactivex.Scheduler
import okhttp3.Call
import java.lang.Long.max
import java.util.concurrent.TimeUnit

class IMServerConnectionChecker:IConnectionChecker {
    companion object {
        private const val TEST_BODY = "toot"
    }

    @SuppressLint("CheckResult")
    override fun check(delay:Long,scheduler: Scheduler): Observable<Boolean> {
        val url = BcmHttpApiHelper.getApi("/echo/$TEST_BODY")

        ALog.d("IMServerConnectionChecker", url)
        return Observable.create<Boolean> { emit ->
            IMHttp.get(AMELogin.majorContext).get()
                    .url(url)
                    .build()
                    .writeTimeOut(1000)
                    .readTimeOut(3000)
                    .connTimeOut(10000)
                    .enqueue(object : StringCallback() {
                        override fun onError(call: Call?, e: Exception?, id: Long) {
                            emit.onError(e?:Exception(""))
                        }

                        override fun onResponse(response: String?, id: Long) {
                            emit.onNext(response == TEST_BODY)
                            emit.onComplete()
                        }
                    })
        }.delaySubscription(delay, TimeUnit.MILLISECONDS)
                .subscribeOn(scheduler)
    }


}