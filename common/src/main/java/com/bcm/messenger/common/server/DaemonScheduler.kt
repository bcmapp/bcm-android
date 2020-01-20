package com.bcm.messenger.common.server

import com.bcm.messenger.utility.listener.WeakListeners
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DaemonScheduler {
    private val DAEMON_TIMER_MILLI = 20_000L
    private val singleScheduler = Schedulers.from(Executors.newSingleThreadExecutor())
    private var daemonTimer: Disposable? = null
    val scheduler get() = singleScheduler
    val tickListener = WeakListeners<IDaemonTicker>()

    fun startTicker() {
        if (daemonTimer?.isDisposed != false) {
            daemonTimer = Observable.timer(DAEMON_TIMER_MILLI, TimeUnit.MILLISECONDS, singleScheduler)
                    .repeat()
                    .subscribeOn(singleScheduler)
                    .observeOn(singleScheduler)
                    .subscribe({
                        tickListener.forEach {
                            it.onDaemonTick()
                        }
                    }, {
                        ALog.i("DaemonScheduler", "DaemonScheduler error")
                    })
        }
    }

    fun stopTicker() {
        if (daemonTimer != null) {
            val daemon = daemonTimer
            daemonTimer = null

            if (daemon != null && !daemon.isDisposed) {
                daemon.dispose()
            }
        }
    }

    interface IDaemonTicker {
        fun onDaemonTick()
    }
}