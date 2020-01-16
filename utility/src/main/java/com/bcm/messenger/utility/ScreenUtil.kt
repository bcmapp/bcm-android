package com.bcm.messenger.utility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ScreenUtil {
    private val listenerSet = Collections.newSetFromMap(ConcurrentHashMap<IScreenStateListener, Boolean>())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_USER_PRESENT == intent.action) {
                Log.i("ScreenUtil", "screen unlock")
                listenerSet.forEach {
                    it.onScreenStateChanged(true)
                }
            } else if (Intent.ACTION_SCREEN_OFF == intent.action) {
                Log.i("ScreenUtil", "screen lock")
                listenerSet.forEach {
                    it.onScreenStateChanged(false)
                }
            }
        }
    }

    fun init(context:Context) {
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        context.registerReceiver(receiver, filter)
    }

    fun unInit(context: Context) {
        context.unregisterReceiver(receiver)
    }

    fun addListener(listener: IScreenStateListener) {
        listenerSet.add(listener)
    }

    fun removeListener(listener: IScreenStateListener) {
        listenerSet.remove(listener)
    }

    interface IScreenStateListener {
        fun onScreenStateChanged(on:Boolean)
    }
}