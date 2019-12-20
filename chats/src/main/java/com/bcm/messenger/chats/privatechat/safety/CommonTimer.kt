package com.bcm.messenger.chats.privatechat.safety

import java.util.*

/**
 * 
 * Created by zjl on 2018/8/30.
 */
object CommonTimer {
    private var timer: Timer? = null
    private val list = ArrayList<TimerWork>()
    private var isTimeRunning = false

    fun register(tw: TimerWork) {
        synchronized(list) {
            list.remove(tw)
            list.add(tw)
            if (list.size > 0 && !isTimeRunning) {  
                if (timer == null) {
                    timer = Timer()
                }
                timer?.scheduleAtFixedRate(object : TimerTask() {  
                    override fun run() {
                        synchronized(list) {
                            list.forEach { it.doWork() }
                        }
                    }
                }, 0, 500)
                isTimeRunning = true
            }
        }
    }

    fun unregister(tw: TimerWork) {
        synchronized(list) {
            if (list.contains(tw))
                list.remove(tw)
            if (list.size == 0 && timer != null && isTimeRunning) {
                timer?.cancel()
                timer = null
                isTimeRunning = false
            }
        }
    }

    interface TimerWork {
        fun doWork()
    }
}