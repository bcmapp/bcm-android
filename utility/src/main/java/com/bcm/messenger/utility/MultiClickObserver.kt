package com.bcm.messenger.utility

import android.view.View

open class MultiClickObserver(count: Int = 2, l: MultiClickListener?) : View.OnClickListener {
    companion object {
        const val CLICK_DURATION = 350
    }

    private var clickCount: Int = 2
    private var lastClickTimeStamps: Long = 0
    private var totalClickCount = 0
    private var listener: MultiClickListener? = null

    init {
        clickCount = count
        listener = l
    }

    override fun onClick(v: View?) {
        val current = System.currentTimeMillis()
        if (current - lastClickTimeStamps < CLICK_DURATION) {
            if (totalClickCount == clickCount - 1) {
                totalClickCount = 0
                lastClickTimeStamps = 0

                listener?.onMultiClick(v, clickCount)
            } else {
                totalClickCount++
            }

        } else {
            totalClickCount = 1
        }

        lastClickTimeStamps = current
    }

    interface MultiClickListener {
        fun onMultiClick(view: View?, count: Int)
    }
}