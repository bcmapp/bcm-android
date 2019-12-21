/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.ui

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Build.VERSION_CODES

import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.LinearLayoutCompat

import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.*

import java.util.HashSet

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
open class KeyboardAwareLinearLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayoutCompat(context, attrs, defStyle) {

    private val rect = Rect()
    private val hiddenListeners = HashSet<OnKeyboardHiddenListener>()
    private val shownListeners = HashSet<OnKeyboardShownListener>()
    private val minKeyboardSize: Int
    private val minCustomKeyboardSize: Int
    private val defaultCustomKeyboardSize: Int
    private val minCustomKeyboardTopMarginPortrait: Int
    private val minCustomKeyboardTopMarginLandscape: Int
    private val statusBarHeight: Int
    private var mKeyboardPH: Int = 0
    private var mKeyboardLH: Int = 0
    private var viewInset: Int = 0

    var isKeyboardOpen = false
        private set
    private var rotation = -1
    private var isFullscreen = false

    private val availableHeight: Int
        get() {
            val availableHeight = this.rootView.height - viewInset - if (!isFullscreen) statusBarHeight else 0
            val availableWidth = this.rootView.width - if (!isFullscreen) statusBarHeight else 0

            return if (isLandscape && availableHeight > availableWidth) {
                availableWidth
            } else availableHeight

        }

    val keyboardHeight: Int
        get() = if (isLandscape) keyboardLandscapeHeight else keyboardPortraitHeight

    val isLandscape: Boolean
        get() {
            val rotation = deviceRotation
            return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        }

    private val deviceRotation: Int
        get() {
            val manager = context.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
            return manager.defaultDisplay.rotation
        }

    private var keyboardLandscapeHeight: Int
        get() {
            if (mKeyboardLH > 0) {
                return mKeyboardLH
            }
            val keyboardHeight = SuperPreferences.getLandscapeKeyboardHeight(context, defaultCustomKeyboardSize)
            mKeyboardLH = AppUtil.clamp(keyboardHeight, minCustomKeyboardSize, rootView.height - minCustomKeyboardTopMarginLandscape)
            return mKeyboardLH
        }
        set(height) {
            mKeyboardLH = height
            SuperPreferences.setLandscapeKeyboardHeight(context, height)
        }

    private var keyboardPortraitHeight: Int
        get() {
            if (mKeyboardPH > 0) {
                return mKeyboardPH
            }
            val keyboardHeight = SuperPreferences.getPortraitKeyboardHeight(context, defaultCustomKeyboardSize)
            mKeyboardPH = AppUtil.clamp(keyboardHeight, minCustomKeyboardSize, rootView.height - minCustomKeyboardTopMarginPortrait)
            return mKeyboardPH
        }
        set(height) {
            mKeyboardPH = height
            SuperPreferences.setPortraitKeyboardHeight(context, height)
        }

    init {
        minKeyboardSize = 50.dp2Px()
        minCustomKeyboardSize = 110.dp2Px()
        defaultCustomKeyboardSize = 220.dp2Px()
        minCustomKeyboardTopMarginPortrait = 170.dp2Px()
        minCustomKeyboardTopMarginLandscape = 170.dp2Px()
        statusBarHeight = context.getStatusBarHeight()
        viewInset = getViewInset()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateRotation()
        updateKeyboardState()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun updateRotation() {
        val oldRotation = rotation
        rotation = deviceRotation
        if (oldRotation != rotation) {
            ALog.i(TAG, "rotation changed")
            onKeyboardClose()
        }
    }

    private fun updateKeyboardState() {
        if (viewInset == 0 && Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            viewInset = getViewInset()
        }

        getWindowVisibleDisplayFrame(rect)

        val availableHeight = availableHeight
        val keyboardHeight = availableHeight - (rect.bottom - rect.top)

        if (keyboardHeight > minKeyboardSize) {
            if (this.keyboardHeight != keyboardHeight) {
                if (isLandscape) {
                    keyboardLandscapeHeight = keyboardHeight
                } else {
                    keyboardPortraitHeight = keyboardHeight
                }
                //
                if (isKeyboardOpen) {
                    onKeyboardOpen(this.keyboardHeight)
                }
            }
            if (!isKeyboardOpen) {
                onKeyboardOpen(keyboardHeight)
            }
        } else if (isKeyboardOpen) {
            onKeyboardClose()
        }
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    private fun getViewInset(): Int {
        try {
            val attachInfoField = View::class.java.getDeclaredField("mAttachInfo")
            attachInfoField.isAccessible = true
            val attachInfo = attachInfoField.get(this)
            if (attachInfo != null) {
                val stableInsetsField = attachInfo.javaClass.getDeclaredField("mStableInsets")
                stableInsetsField.isAccessible = true
                val insets = stableInsetsField.get(attachInfo) as Rect
                return insets.bottom
            }
        } catch (nsfe: NoSuchFieldException) {
            ALog.e(TAG, "field reflection error when measuring view inset", nsfe)
        } catch (iae: IllegalAccessException) {
            ALog.e(TAG, "access reflection error when measuring view inset", iae)
        }

        return 0
    }

    protected fun onKeyboardOpen(keyboardHeight: Int) {
        ALog.i(TAG, "onKeyboardOpen($keyboardHeight)")
        isKeyboardOpen = true

        notifyShownListeners()
    }

    protected fun onKeyboardClose() {
        ALog.i(TAG, "onKeyboardClose()")
        isKeyboardOpen = false
        notifyHiddenListeners()
    }

    fun postOnKeyboardClose(runnable: Runnable) {
        if (isKeyboardOpen) {
            addOnKeyboardHiddenListener(object : OnKeyboardHiddenListener {
                override fun onKeyboardHidden() {
                    removeOnKeyboardHiddenListener(this)
                    runnable.run()
                }
            })
        } else {
            runnable.run()
        }
    }

    fun postOnKeyboardOpen(runnable: Runnable) {
        if (!isKeyboardOpen) {
            addOnKeyboardShownListener(object : OnKeyboardShownListener {
                override fun onKeyboardShown() {
                    removeOnKeyboardShownListener(this)
                    runnable.run()
                }
            })
        } else {
            runnable.run()
        }
    }

    fun addOnKeyboardHiddenListener(listener: OnKeyboardHiddenListener) {
        hiddenListeners.add(listener)
    }

    fun removeOnKeyboardHiddenListener(listener: OnKeyboardHiddenListener) {
        hiddenListeners.remove(listener)
    }

    fun addOnKeyboardShownListener(listener: OnKeyboardShownListener) {
        shownListeners.add(listener)
    }

    fun removeOnKeyboardShownListener(listener: OnKeyboardShownListener) {
        shownListeners.remove(listener)
    }

    fun setFullscreen(isFullscreen: Boolean) {
        this.isFullscreen = isFullscreen
    }

    private fun notifyHiddenListeners() {
        val listeners = HashSet(hiddenListeners)
        for (listener in listeners) {
            listener.onKeyboardHidden()
        }
    }

    private fun notifyShownListeners() {
        val listeners = HashSet(shownListeners)
        for (listener in listeners) {
            listener.onKeyboardShown()
        }
    }

    interface OnKeyboardHiddenListener {
        fun onKeyboardHidden()
    }

    interface OnKeyboardShownListener {
        fun onKeyboardShown()
    }

    companion object {
        private val TAG = "KeyboardAwareLinearLayout"
    }
}
