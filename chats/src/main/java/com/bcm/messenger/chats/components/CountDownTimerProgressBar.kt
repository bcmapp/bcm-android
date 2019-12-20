package com.bcm.messenger.chats.components

import android.content.Context
import android.os.Build
import android.os.CountDownTimer
import android.util.AttributeSet
import android.widget.ProgressBar

/**
 * Created by bcm.social.01 on 2019/1/25.
 */
class CountDownTimerProgressBar:ProgressBar {
    private var countDownTimer: CountDownTimer? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)


    fun start(fromSecond:Long, toSecond:Long) {
        stop()

        this.max = 1000
        if (fromSecond < toSecond){
            setProgressInner(0)
            return
        }

        val toMills = toSecond * 1000
        val totalMills = fromSecond*1000
        val duration = (fromSecond - toSecond)*1000L
        val step = 1000 / 16L


        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, step) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = ((toMills + duration - millisUntilFinished)*max/totalMills).toInt()
                setProgressInner(progress)
            }

            override fun onFinish() {
                setProgressInner(max)
            }
        }.start()
    }

    fun stop() {
        countDownTimer?.cancel()
        countDownTimer = null
        setProgressInner(0)
    }

    private fun setProgressInner(progress:Int){
        if (Build.VERSION.SDK_INT > 23){
            setProgress(progress, false)
        } else {
            setProgress(progress)
        }
    }


}