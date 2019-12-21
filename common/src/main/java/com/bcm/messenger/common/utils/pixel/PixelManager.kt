package com.bcm.messenger.common.utils.pixel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.ArrayMap
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference
import com.bcm.messenger.utility.AppContextHolder

/**
 * 
 * Created by wjh on 2019-08-30
 */
class PixelManager private constructor(target: Class<out Activity>?, bundle: Bundle?) {

    inner class ActivityManager {
        private val mActivities = ArrayMap<Class<out Activity>, WeakReference<Activity>>(1)

        fun add(activity: Activity) {
            findTargetAndRemove(activity.javaClass)
            mActivities.put(activity.javaClass, WeakReference(activity))
        }

        fun remove(activityClass: Class<out Activity>) {
            findTargetAndRemove(activityClass)
        }

        fun remove(activity: Activity) {
            findTargetAndRemove(activity.javaClass)
        }

        fun exist(activityClass: Class<out Activity>): Boolean {
            return mActivities[activityClass]?.get() != null
        }

        private fun findTargetAndRemove(activityClass: Class<out Activity>) {
            val ref = mActivities.remove(activityClass)
            if (ref != null) {
                ref.get()?.finish()
            }
        }
    }

    inner class ScreenStateBroadcast : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            ALog.i(TAG, "action: ${intent.action}")
            /**/
            if (Intent.ACTION_USER_PRESENT == intent.action) {
                doOnScreenOn()
            } else if (Intent.ACTION_SCREEN_OFF == intent.action) {
                doOnScreenOff()
            }
        }
    }

    class Builder {
        private val TAG = "PixelBuilder"

        private var mActivityClass: Class<out Activity>? = null
        private var mBundle: Bundle? = null

        fun target(target: Class<out Activity>): Builder {
            ALog.i(TAG, "target class: ${target.simpleName}")
            mActivityClass = target
            return this
        }

        fun args(bundle: Bundle): Builder {
            mBundle = bundle
            return this
        }

        fun build(): PixelManager {
            return PixelManager(mActivityClass, mBundle)
        }
    }

    companion object {
        private var mCurrentObject: WeakReference<PixelManager>? = null

        fun getCurrent(): PixelManager? {
            return mCurrentObject?.get()
        }
    }

    private val TAG = "PixelManager"
    private var mActivityClass: Class<out Activity>? = target
    private var mBundle: Bundle? = bundle
    private var mBroadcast: ScreenStateBroadcast? = null
    private var isStarting: Boolean = false
    private var activityManager: ActivityManager = ActivityManager()

    init {
        mCurrentObject = WeakReference(this)
    }

    fun start(context: Context) {
        doBroadcast(context)
    }

    fun quit(context: Context) {
        doExit(context)
    }

    fun addActivity(activity: Activity) {
        activityManager.add(activity)
        if (activity.javaClass == mActivityClass) {
            isStarting = false
        }
    }

    fun removeActivity(activity: Activity) {
        activityManager.remove(activity)
    }

    private fun doExit(context: Context) {
        if (mBroadcast != null) {
            ALog.i(TAG, "doExit")
            context.unregisterReceiver(mBroadcast)
            mBroadcast = null
        }
    }

    private fun doBroadcast(context: Context) {

        if (mBroadcast == null) {
            ALog.i(TAG, "doBroadcast")
            mBroadcast = ScreenStateBroadcast()

            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            context.registerReceiver(mBroadcast, filter)
        }

    }

    private fun doOnScreenOn() {

        mActivityClass?.let {
            ALog.i(TAG, "doOnScreenOn")
            activityManager.remove(it)
            isStarting = false
        }

    }

    private fun doOnScreenOff() {

        mActivityClass?.let {
            ALog.i(TAG, "doOnScreenOff")
            val exist = activityManager.exist(it)
            if (exist) {
                ALog.i(TAG, "doOnScreenOff exit targetClass")
                return
            }
            if (isStarting) {
                ALog.i(TAG, "doOnScreenOff isStarting")
                return
            }
            isStarting = true
            val mIntent = Intent(AppContextHolder.APP_CONTEXT, it)
            mBundle?.let { args ->
                mIntent.putExtras(args)
            }
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppContextHolder.APP_CONTEXT.startActivity(mIntent)
        }

    }
}