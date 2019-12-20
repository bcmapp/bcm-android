package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.*
import com.orhanobut.logger.Logger
import org.webrtc.SurfaceViewRenderer

/**
 * Created by wjh on 2018/04/16
 */
class ChatRenderLayout : ViewGroup {

    companion object {
        private val TAG = ChatRenderLayout::class.java.simpleName
    }

    private var mRadius: Float = 0f
    private var mSurfaceViewRender: SurfaceViewRenderer? = null

    var isHidden: Boolean = true
        set(value) {
            Log.d(TAG, "isHidden: $value")
            if (field == value) {
                return
            }
            if (value) {
                showRender(mSurfaceViewRender, false)
            } else {
                showRender(mSurfaceViewRender, true)
            }
            field = value
        }

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeAllViews()
    }

    override fun setVisibility(visibility: Int) {

    }

    override fun setOutlineProvider(provider: ViewOutlineProvider?) {

    }

    override fun setClipToOutline(clipToOutline: Boolean) {

    }

    fun getSurface(): SurfaceViewRenderer? {
        return mSurfaceViewRender
    }

    /**
     */
    fun setSurface(surfaceRender: SurfaceViewRenderer?) {
        if (mSurfaceViewRender == surfaceRender) {
            Log.d(TAG, "surface view is same")
            return
        }
        mSurfaceViewRender = surfaceRender
        showRender(mSurfaceViewRender, !isHidden)
    }

    private fun showRender(surfaceRender: SurfaceViewRenderer?, show: Boolean) {
        try {
            if (surfaceRender == null) {
                Log.d(TAG, "showRender render is null")
                return
            }
            if (show) {
                val parent = surfaceRender.parent
                if (parent is ViewGroup) {
                    Log.d(TAG, "surface render parent remove")
                    parent.removeView(surfaceRender)
                }
                addView(surfaceRender, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            } else {
                removeView(surfaceRender)

            }
        } catch (ex: Exception) {
            Logger.e(ex, "ChatRenderLayout setSurface error")
        }
    }

    fun setRadius(radius: Float) {
        if (mRadius != radius) {
            mRadius = radius
            super.setOutlineProvider(RoundOutlineProvider(radius))
            super.setClipToOutline(true)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {

        (0 until childCount)
                .map { getChildAt(it) }
                .filter { it.visibility != View.GONE }
                .forEach {
                    val width = right - left
                    val height = bottom - top
                    it.layout(0, 0, width, height)
                }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    class RoundOutlineProvider(private val radius: Float) : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            val leftMargin = 0
            val topMargin = 0
            val selfRect = Rect(leftMargin, topMargin, rect.right - rect.left - leftMargin, rect.bottom - rect.top - topMargin)
            outline.setRoundRect(selfRect, radius)
        }
    }
}
