package com.bcm.messenger.common.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.utility.logger.ALog


/**
 * 
 * Created by wjh on 2019/7/4
 */
class StickyLinearDecoration(val mCallback: StickyHeaderCallback) : RecyclerView.ItemDecoration() {

    class StickyHeaderData(val isFirstView: Boolean, val isLastView: Boolean,
                           val header: View, val height: Int, val dividerHeight: Int) {
    }

    interface StickyHeaderCallback {
        fun getHeaderData(pos: Int): StickyHeaderData?
    }

    private val TAG = "StickyLinearDecoration"
    private var mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        val childCount = parent.childCount ?: 0
        //ALog.d(TAG, "childCount: $childCount")
        for (i in 0 until childCount) {
            val itemView = parent.getChildAt(i)
            if (itemView == null) {
                ALog.d(TAG, "onDrawOver index: $i, itemView is null")
                continue
            }
            val itemIndex = parent.getChildAdapterPosition(itemView)
            //ALog.d(TAG, "onDrawOver index: $i, itemIndex: $itemIndex")

            val headerData = mCallback.getHeaderData(itemIndex)
            if (headerData == null) {//headerData，
                ALog.d(TAG, "onDrawOver index: $i, itemIndex: $itemIndex, headerData is null")
                continue
            }
            val left = parent.paddingStart
            val right = parent.width - parent.paddingEnd

            // ItemView ，i == 0;
            if (i == 0) {
                // ItemView View ，View
                // StickyHeader。

                //  ItemView  View
                var top = parent.paddingTop
                if (headerData.isLastView) {
                    val suggestTop = itemView.bottom - headerData.height
                    //  ItemView  Header ， Header 
                    // parent ， Header.top ，
                    // parent 
                    if (suggestTop < top) {
                        top = suggestTop
                    }
                } else {

                }
                val bottom = top + headerData.height
                drawHeader(c, headerData, left, top, right, bottom)

            } else {

                //ItemView
                if (headerData.isFirstView) {
                    val top = itemView.top - headerData.height
                    val bottom = itemView.top
                    drawHeader(c, headerData, left, top, right, bottom)
                }
            }
        }
    }


    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        val position = parent.getChildAdapterPosition(view)
        val headerData = mCallback.getHeaderData(position)
        if (headerData != null) {
            //Header，
            if (headerData.isFirstView) {
                outRect.top = headerData.height
            } else {
                outRect.top = headerData.dividerHeight
            }
        }else {
            outRect.top = 0
        }
    }

    /**
     * header
     */
    private fun drawHeader(canvas: Canvas?, headerData: StickyHeaderData, left: Int, top: Int, right: Int, bottom: Int) {
        canvas?.apply {
            //ALog.d(TAG, "left: $left, right: $right, top: $top, bottom: $bottom")
            val view = headerData.header
            view.isDrawingCacheEnabled = true
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, right - left, bottom - top)
            view.buildDrawingCache()
            val bitmap = view.drawingCache
            drawBitmap(bitmap, left.toFloat(), top.toFloat(), mPaint)
        }
    }


}