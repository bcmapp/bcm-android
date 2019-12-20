package com.bcm.messenger.utility.dispatcher

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import com.bcm.messenger.utility.logger.ALog
import java.util.concurrent.*

/**
 * Created by bcm.social.01 on 2018/8/4.
 */
object AmeDispatcher {
    private val handlerExecutorThread: HandlerThread = HandlerThread("HandlerThreadFactory")

    //work thread runnable dispatcher
    val io: IDispatcher
    //main thread runnable dispatcher
    val mainThread: IDispatcher = MainThreadDispatchImpl()

    val ioScheduler:Scheduler
    val mainScheduler = AndroidSchedulers.mainThread()!!

    val singleScheduler: Scheduler

    init {
        val minCount = (Runtime.getRuntime().availableProcessors() * 1.5f).toInt()
        val maxCount = Runtime.getRuntime().availableProcessors() * 3
        ioScheduler = Schedulers.from(ThreadPoolExecutor(minCount, maxCount,
                60L, TimeUnit.SECONDS,
                LinkedBlockingQueue()))

        io = IODispatchImpl(-1, -1)

        handlerExecutorThread.start()
        singleScheduler = AndroidSchedulers.from(handlerExecutorThread.looper)
    }

    fun newDispatcher(maxThreadCount: Int): IODispatchImpl {
        return IODispatchImpl(0, maxThreadCount)
    }

    interface IDispatcher {
        fun dispatch(runnable: () -> Unit)
        fun dispatch(runnable: () -> Unit, delayMillis: Long): Disposable
        fun repeat(runnable: () -> Unit, delayMillis:Long): Disposable
    }


    class IODispatchImpl(minThreadCount: Int, maxThreadCount: Int) : IDispatcher {
        private var schedulers = ioScheduler

        init {
            if (minThreadCount >= 0 && maxThreadCount >= 0) {
                schedulers = Schedulers.from(ThreadPoolExecutor(minThreadCount, maxThreadCount,
                        60L, TimeUnit.SECONDS,
                        LinkedBlockingQueue()))
            }
        }

        override fun dispatch(runnable: () -> Unit) {
            dispatch(runnable, 0)
        }

        override fun dispatch(runnable: () -> Unit, delayMillis: Long): Disposable {
            return Observable.create<Any> {
                runnable.invoke()
                it.onComplete()
            }.delaySubscription(delayMillis, TimeUnit.MILLISECONDS, ioScheduler)
                    .subscribeOn(ioScheduler)
                    .observeOn(ioScheduler)
                    .subscribe({

                    }, {
                        ALog.e("IODispatchImpl", "io dispatch", it)
                    })
        }

        override fun repeat(runnable: () -> Unit, delayMillis: Long): Disposable {
            return Observable.timer(delayMillis, TimeUnit.MILLISECONDS)
                    .repeat()
                    .subscribeOn(ioScheduler)
                    .observeOn(ioScheduler)
                    .subscribe({
                        runnable()
                    }, {
                        ALog.e("IODispatchImpl", "repeat", it)
                    })
        }
    }

    class MainThreadDispatchImpl : IDispatcher {
        private val handler: Handler = Handler(Looper.getMainLooper())
        override fun dispatch(runnable: () -> Unit) {
            dispatch(runnable, 0)
        }

        override fun dispatch(runnable: () -> Unit, delayMillis: Long): Disposable {
            val disposable = MainIODisposable(handler)

            val runProxy = Runnable {
                disposable.finish()
                runnable()
            }

            disposable.init(runProxy)

            handler.postDelayed(runProxy, delayMillis)

            return disposable
        }

        override fun repeat(runnable: () -> Unit, delayMillis: Long): Disposable {
            return Observable.timer(delayMillis, TimeUnit.MILLISECONDS)
                    .repeat()
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.d("MainThreadDispatchImpl", "repeat")
                        runnable()
                    }, {
                        ALog.e("IODispatchImpl", "main repeat", it)
                    })
        }
    }


    class MainIODisposable(private val executeHandler: Handler) : Disposable {
        private var runnable: Runnable? = null

        fun init(runnable: Runnable) {
            this.runnable = runnable
        }

        override fun isDisposed(): Boolean {
            return runnable == null
        }

        override fun dispose() {
            executeHandler.post {
                if (runnable != null) {
                    executeHandler.removeCallbacks(runnable)
                    runnable = null
                }
            }
        }

        fun finish() {
            runnable = null
        }
    }
}