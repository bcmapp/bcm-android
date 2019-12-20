package com.bcm.messenger.utility.foreground

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.IActivityCounter
import com.bcm.messenger.utility.ProcessUtil
import com.bcm.messenger.utility.listener.IWeakListeners
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog

object AppForeground: Application.ActivityLifecycleCallbacks {

    private val TAG = "AppForeground"

    private enum class STATE {
        UNKNOWN,
        FOREGROUND,
        BACKGROUND
    }

    interface IForegroundEvent {
        fun onForegroundChanged(isForeground: Boolean)
    }

    val listener:IWeakListeners<IForegroundEvent> = SafeWeakListeners()

    //record app time
    private var backgroundTime:Long = 0
    private var foregroundTime: Long = 0

    private var state = STATE.UNKNOWN

    //foreground activity count
    private var count: Int = 0

    private val mainProcess:Boolean = ProcessUtil.isMainProcess(AppContextHolder.APP_CONTEXT)
    private var delayer: Handler? = null
    private val serviceConn = object : ServiceConnection {
        var kvInstance: IActivityCounter? = null

        override fun onServiceDisconnected(p0: ComponentName?) {
            kvInstance = null
        }
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            kvInstance = IActivityCounter.Stub.asInterface(p1)
            if (count > 0){
                val n = count
                count = 0
                for (i in 1..n){
                    increase()
                }
            }
        }
    }

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        initCounter()
    }

    private fun initCounter() {
        delayer = Handler(Looper.getMainLooper())
        Log.i("AmeApplication", "initCounter")
        if (!mainProcess){
            bindCounterService()
        }
    }

    private fun unInitCounter() {
        if (!mainProcess){
            unbindCounterService()
        }
    }

    private fun bindCounterService() {
        val intent = Intent(AppContextHolder.APP_CONTEXT, ActivityCounterService::class.java)
        AppContextHolder.APP_CONTEXT.bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)
    }

    private fun unbindCounterService(){
        if (serviceConn.kvInstance != null){
            AppContextHolder.APP_CONTEXT.unbindService(serviceConn)
            serviceConn.kvInstance = null
        }
    }

    @Synchronized
    fun increase(): Int{
        ++count
        try {
            return when {
                mainProcess -> {
                    ALog.i(TAG, "activity count $count")
                    checkForegroundStateChange()
                    count
                }
                serviceConn.kvInstance?.pid() != Process.myPid() -> serviceConn.kvInstance?.increase()?:0
                else -> --count
            }
        } catch (e:Throwable) {
            ALog.e(TAG, "increase", e)
        }
        return count
    }

    @Synchronized
    fun decrease(): Int {
        --count
        try {
            return when {
                mainProcess -> {
                    checkForegroundStateChange()
                    count
                }
                serviceConn.kvInstance?.pid() != Process.myPid() -> return serviceConn.kvInstance?.decrease()
                        ?: 0
                else -> ++count
            }
        } catch (e:Throwable) {
            ALog.e(TAG, "decrease", e)
        }
        return count
    }

    fun count(): Int {
        return count
    }

    private fun checkForegroundStateChange(){
        val fgnd = if(count > 0) {
            STATE.FOREGROUND
        } else {
            STATE.BACKGROUND
        }

        if (state != fgnd) {
            state = fgnd

            when(state) {
                STATE.FOREGROUND -> {
                    foregroundTime = System.currentTimeMillis()
                }
                else -> {
                    backgroundTime = System.currentTimeMillis()
                }
            }

            (listener as SafeWeakListeners).forEach {
                it.onForegroundChanged(state == STATE.FOREGROUND)
            }
        }
    }

    fun foreground(): Boolean{
        return state == STATE.FOREGROUND
    }

    fun timeOfBackground(): Long {
        return backgroundTime
    }

    fun timeOfForeground(): Long {
        return foregroundTime
    }

    override fun onActivityResumed(activity: Activity?) {
    }

    override fun onActivityStarted(activity: Activity?) {
        increase()
    }

    override fun onActivityStopped(activity: Activity?) {
        decrease()
    }

    override fun onActivityPaused(activity: Activity?) {

    }

    override fun onActivityDestroyed(activity: Activity?) {

    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
    }
}