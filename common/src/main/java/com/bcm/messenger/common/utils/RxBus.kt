package com.bcm.messenger.common.utils

import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import com.bcm.messenger.utility.logger.ALog
import java.lang.Exception
import java.util.*

/**
 * 
 * Created by wjh on 2018/12/14
 */
object RxBus {

    private const val TAG = "RxBus"

    private val mSubject: Subject<RxBusEvent<Any>> = PublishSubject.create<RxBusEvent<Any>>().toSerialized()

    private val mSubscriptionMap = HashMap<String, Disposable>()

    /**
     * ，
     */
    fun <T : Any> subscribe(tag: String, callback: (event: T) -> Unit) {
        ALog.d(TAG, "subscribe tag: $tag")
        val disposable = createSubscription(AndroidSchedulers.mainThread(), Consumer {
            ALog.d(TAG, "doSubscribe event: $tag")
            try {
                if (it.tag == null || it.tag == tag) {
                    val value = it.data as? T
                    callback.invoke(value ?: return@Consumer)
                }

            } catch (ex: Exception) {
//                ALog.e(TAG, "doSubscribe next", ex)
            }
        }, Consumer {
            ALog.e(TAG, "doSubscribe error", it)
        })

        updateDisposable(tag, disposable)
    }

    /**
     * ，
     */
    fun <T : Any> subscribeAsync(tag: String, callback: (event: T) -> Unit) {
        ALog.d(TAG, "subscribeAsync tag: $tag")
        val disposable = createSubscription(Schedulers.io(), Consumer {
            ALog.d(TAG, "doSubscribe event: $tag")
            try {
                if (it.tag == null || it.tag == tag) {
                    val value = it.data as? T
                    callback.invoke(value ?: return@Consumer)
                }

            } catch (ex: Exception) {
//                ALog.e(TAG, "doSubscribe next", ex)
            }
        }, Consumer {
            ALog.e(TAG, "doSubscribe error", it)
        })

        updateDisposable(tag, disposable)
    }

    @Synchronized
    private fun updateDisposable(tag: String, disposable: Disposable) {
        val lastDisposable = mSubscriptionMap[tag]
        if (lastDisposable != null) {
            ALog.d(TAG, "doSubscribe dispose tag: $tag")
            lastDisposable.dispose()
        }
        mSubscriptionMap[tag] = disposable

    }

    private fun createSubscription(observeOn: Scheduler, success: Consumer<RxBusEvent<Any>>, error: Consumer<Throwable>): Disposable {
        return mSubject.observeOn(observeOn).subscribe(success, error)
    }

    /**
     * （tag）
     */
    fun post(tag: String, event: Any) {
        ALog.d(TAG, "post tag: $tag")
        mSubject.onNext(RxBusEvent(tag, event))
    }

    /**
     * （）
     */
    fun post(event: Any) {
        ALog.d(TAG, "post tag: null")
        mSubject.onNext(RxBusEvent(null, event))
    }

    /**
     * 
     */
    fun unSubscribe(tag: String) {
        val disposable = mSubscriptionMap[tag]
        if (disposable?.isDisposed != true) {
            disposable?.dispose()
        }
        mSubscriptionMap.remove(tag)
    }

    class RxBusEvent<T>(val tag: String?, val data: T)
}