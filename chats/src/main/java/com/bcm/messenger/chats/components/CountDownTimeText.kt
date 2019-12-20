package com.bcm.messenger.chats.components

import android.content.Context
import android.os.CountDownTimer
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView


/**
 * bcm.social.01 2019/1/25.
 */
class CountDownTimeText: AppCompatTextView {
    private var countDownTimer:CountDownTimer? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    init {

    }

    fun start(fromSecond:Long, toSecond:Long) {
        stop()

        if (fromSecond < toSecond){
            return
        }

        val duration = (fromSecond - toSecond)*1000L
        val step = 1000L

        countDownTimer = object : CountDownTimer(duration, step) {
            override fun onTick(millisUntilFinished: Long) {
                val v =  (millisUntilFinished / 1000).toString()
                text = v
            }

            override fun onFinish() {
                text = "0"
            }
        }.start()
    }


    fun stop() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

}