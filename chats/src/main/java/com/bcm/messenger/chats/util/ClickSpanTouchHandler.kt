package com.bcm.messenger.chats.util

import android.annotation.SuppressLint
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.bcm.messenger.utility.logger.ALog
import android.view.ViewConfiguration
import android.view.ViewParent


/**
 */
class ClickSpanTouchHandler() : View.OnTouchListener {

    companion object {

        private val TAG = "ClickSpanTouchHandler"

        @SuppressLint("StaticFieldLeak")
        private var sLocal: ClickSpanTouchHandler? = null

        fun getInstance(context: Context): ClickSpanTouchHandler {
            if (sLocal == null) {
                sLocal = ClickSpanTouchHandler()
            }
            return sLocal!!
        }
    }

    private var mLongClickCallback: LongClickCallback? = null

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v is TextView) {
            v.movementMethod = null
            val text = v.text
            val spannable = Spannable.Factory.getInstance().newSpannable(text)
            val action = event?.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= v.totalPaddingLeft
                y -= v.totalPaddingTop
                x += v.scrollX
                y += v.scrollY
                val layout = v.layout
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())

                val clickSpanArray = spannable.getSpans(off, off, ClickableSpan::class.java)
                if (clickSpanArray.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(spannable,
                                spannable.getSpanStart(clickSpanArray[0]),
                                spannable.getSpanEnd(clickSpanArray[0]))
                        if (mLongClickCallback == null) {
                            ALog.i(TAG, "create LongClickCallback")
                            mLongClickCallback = LongClickCallback(v)
                            v.postDelayed(mLongClickCallback, ViewConfiguration.getLongPressTimeout().toLong())
                        }

                    } else {
                        if (mLongClickCallback != null) {
                            v.removeCallbacks(mLongClickCallback)
                            clickSpanArray[0].onClick(v)
                            ALog.i(TAG, "onTouch let ClickSpan onclick")
                            mLongClickCallback = null
                        }
                    }
                    return true

                }else {
                    Selection.removeSelection(spannable)
                }

            }else if (action == MotionEvent.ACTION_CANCEL) {
                v.removeCallbacks(mLongClickCallback)
                mLongClickCallback = null
            }
        }
        return false
    }

    private inner class LongClickCallback internal constructor(private val view: View) : Runnable {

        override fun run() {
            ALog.i(TAG, "LongClickCallback onRun")
            var consumed = view.performLongClick()
            var parent: ViewParent
            while (!consumed) {
                parent = view.parent
                if (parent is View) {
                    consumed = parent.performLongClick()
                }else {
                    break
                }
            }
            if (consumed) {
                ALog.i(TAG, "performLongClick")
            }
            mLongClickCallback = null
        }
    }


}