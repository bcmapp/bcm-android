package com.bcm.messenger.common.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.utility.logger.ALog


/**
 * 自定义线形悬浮粘性头部
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
            if (headerData == null) {//当headerData是空的时候，不绘制
                ALog.d(TAG, "onDrawOver index: $i, itemIndex: $itemIndex, headerData is null")
                continue
            }
            val left = parent.paddingStart
            val right = parent.width - parent.paddingEnd

            //屏幕上第一个可见的 ItemView 时，i == 0;
            if (i == 0) {
                //当 ItemView 是屏幕上第一个可见的View 时，不管它是不是组内第一个View
                //它都需要绘制它对应的 StickyHeader。

                // 还要判断当前的 ItemView 是不是它组内的最后一个 View
                var top = parent.paddingTop
                if (headerData.isLastView) {
                    val suggestTop = itemView.bottom - headerData.height
                    // 当 ItemView 与 Header 底部平齐的时候，判断 Header 的顶部是否小于
                    // parent 顶部内容开始的位置，如果小于则对 Header.top 进行位置更新，
                    //否则将继续保持吸附在 parent 的顶部
                    if (suggestTop < top) {
                        top = suggestTop
                    }
                } else {

                }
                val bottom = top + headerData.height
                drawHeader(c, headerData, left, top, right, bottom)

            } else {

                //只有组内的第一个ItemView之上才绘制
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
            //如果是组内的第一个则将间距撑开为一个Header的高度，或者就是普通的分割线高度
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
     * 画header逻辑
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