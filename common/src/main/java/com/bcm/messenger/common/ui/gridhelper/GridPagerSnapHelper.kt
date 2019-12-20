package com.bcm.messenger.common.ui.gridhelper

import android.graphics.PointF
import androidx.recyclerview.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Created by Kin on 2018/11/26
 */
class GridPagerSnapHelper : SnapHelper() {

    private val DEFAULT_ROW = 1
    private val DEFAULT_COLUMN = 1

    private var mRow = DEFAULT_ROW
    private var mColumn = DEFAULT_COLUMN

    private val MAX_SCROLL_ON_FLING_DURATION = 100 // ms

    fun setRow(row: Int): GridPagerSnapHelper {
        if (mRow <= 0) {
            throw IllegalArgumentException("row must be greater than zero")
        }
        this.mRow = row
        return this
    }

    fun setColumn(column: Int): GridPagerSnapHelper {
        if (mColumn <= 0) {
            throw IllegalArgumentException("column must be greater than zero")
        }
        this.mColumn = column
        return this
    }

    // Orientation helpers are lazily created per LayoutManager.
    private var mVerticalHelper: OrientationHelper? = null
    private var mHorizontalHelper: OrientationHelper? = null

    override fun calculateDistanceToFinalSnap(layoutManager: RecyclerView.LayoutManager, targetView: View): IntArray? {
        val out = IntArray(2)
        out[0] = if (layoutManager.canScrollHorizontally()) {
            distanceToCenter(layoutManager, targetView, getHorizontalHelper(layoutManager))
        } else {
            0
        }

        out[1] = if (layoutManager.canScrollVertically()) {
            distanceToCenter(layoutManager, targetView, getVerticalHelper(layoutManager))
        } else {
            0
        }
        return out
    }

    private fun distanceToCenter(layoutManager: RecyclerView.LayoutManager,
                                 targetView: View, helper: OrientationHelper): Int {
        val screenWidth = targetView.context.resources.displayMetrics.widthPixels

        val columnWidth = screenWidth / mColumn

        val position = layoutManager.getPosition(targetView)
        val pageIndex = pageIndex(position)

        val currentPageStart = pageIndex * countOfPage()

        val distance = (position - currentPageStart) / mRow * columnWidth

        val childStart = helper.getDecoratedStart(targetView)

        return childStart - distance
    }


    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        return when {
            layoutManager.canScrollVertically() -> findCenterView(layoutManager, getVerticalHelper(layoutManager))
            layoutManager.canScrollHorizontally() -> findCenterView(layoutManager, getHorizontalHelper(layoutManager))
            else -> null
        }
    }

    /**
     * get the page of position
     *
     * @param position
     * @return
     */
    private fun pageIndex(position: Int) = position / countOfPage()

    /**
     * the total count of per page
     *
     * @return
     */
    private fun countOfPage() = mRow * mColumn

    /**
     * Return the child view that is currently closest to the center of this parent.
     *
     * @param layoutManager The [RecyclerView.LayoutManager] associated with the attached
     * [RecyclerView].
     * @param helper        The relevant [android.support.v7.widget.OrientationHelper] for the attached [RecyclerView].
     * @return the child view that is currently closest to the center of this parent.
     */
    private fun findCenterView(layoutManager: RecyclerView.LayoutManager,
                               helper: OrientationHelper): View? {
        val childCount = layoutManager.childCount
        if (childCount == 0) {
            return null
        }

        var closestChild: View? = null
        val center = if (layoutManager.clipToPadding) {
            helper.getStartAfterPadding() + helper.getTotalSpace() / 2
        } else {
            helper.getEnd() / 2
        }
        var absClosest = Integer.MAX_VALUE

        for (i in 0 until childCount) {
            val child = layoutManager.getChildAt(i)
            if (child != null) {
                val childCenter = helper.getDecoratedStart(child) + helper.getDecoratedMeasurement(child) / 2
                val absDistance = abs(childCenter - center)

                /** if child center is closer than previous closest, set it as closest   */
                if (absDistance < absClosest) {
                    absClosest = absDistance
                    closestChild = child
                }
            }
        }

        return closestChild
    }

    override fun findTargetSnapPosition(layoutManager: RecyclerView.LayoutManager, velocityX: Int,
                                        velocityY: Int): Int {
        val itemCount = layoutManager.itemCount
        if (itemCount == 0) {
            return RecyclerView.NO_POSITION
        }

        val mStartMostChildView = when {
            layoutManager.canScrollVertically() -> findStartView(layoutManager, getVerticalHelper(layoutManager))
            layoutManager.canScrollHorizontally() -> findStartView(layoutManager, getHorizontalHelper(layoutManager))
            else -> null
        }

        mStartMostChildView ?: return RecyclerView.NO_POSITION

        val centerPosition = layoutManager.getPosition(mStartMostChildView)
        if (centerPosition == RecyclerView.NO_POSITION) {
            return RecyclerView.NO_POSITION
        }

        val forwardDirection = if (layoutManager.canScrollHorizontally()) {
            velocityX > 0
        } else {
            velocityY > 0
        }
        var reverseLayout = false
        if (layoutManager is RecyclerView.SmoothScroller.ScrollVectorProvider) {
            val vectorProvider = layoutManager as RecyclerView.SmoothScroller.ScrollVectorProvider
            val vectorForEnd = vectorProvider.computeScrollVectorForPosition(itemCount - 1)
            if (vectorForEnd != null) {
                reverseLayout = vectorForEnd.x < 0 || vectorForEnd.y < 0
            }
        }

        val pageIndex = pageIndex(centerPosition)

        val currentPageStart = pageIndex * countOfPage()

        return if (reverseLayout)
            if (forwardDirection) currentPageStart - countOfPage() else currentPageStart
        else
            if (forwardDirection) currentPageStart + countOfPage() else currentPageStart + countOfPage() - 1
    }

    private fun findStartView(layoutManager: RecyclerView.LayoutManager,
                              helper: OrientationHelper): View? {
        val childCount = layoutManager.childCount
        if (childCount == 0) {
            return null
        }

        var closestChild: View? = null
        var startest = Integer.MAX_VALUE

        for (i in 0 until childCount) {
            val child = layoutManager.getChildAt(i)
            if (child != null) {
                val childStart = helper.getDecoratedStart(child)

                /** if child is more to start than previous closest, set it as closest   */
                if (childStart < startest) {
                    startest = childStart
                    closestChild = child
                }
            }
        }

        return closestChild
    }

    override fun createSnapScroller(layoutManager: RecyclerView.LayoutManager): LinearSmoothScroller? {
        return if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) {
            null
        } else object : LinearSmoothScroller(mRecyclerView?.context) {
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                val snapDistances = calculateDistanceToFinalSnap(layoutManager, targetView)
                val dx = snapDistances!![0]
                val dy = snapDistances[1]
                val time = calculateTimeForDeceleration(max(abs(dx), abs(dy)))
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator)
                }
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
            }

            override fun calculateTimeForScrolling(dx: Int): Int {
                return min(MAX_SCROLL_ON_FLING_DURATION, super.calculateTimeForScrolling(dx))
            }

            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                return null
            }
        }
    }

    private fun getVerticalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        if (mVerticalHelper == null || mVerticalHelper?.mLayoutManager !== layoutManager) {
            mVerticalHelper = OrientationHelper.createVerticalHelper(layoutManager)
        }
        return mVerticalHelper!!
    }

    private fun getHorizontalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        if (mHorizontalHelper == null || mHorizontalHelper?.mLayoutManager !== layoutManager) {
            mHorizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager)
        }
        return mHorizontalHelper!!
    }
}