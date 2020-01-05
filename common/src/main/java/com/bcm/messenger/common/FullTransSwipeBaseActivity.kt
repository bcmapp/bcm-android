package com.bcm.messenger.common

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.TypedArray
import android.os.Build
import android.os.Bundle

/**
 * Created by bcm.social.01 on 2018/11/8.
 */
open class FullTransSwipeBaseActivity : SwipeBaseActivity() {
    private val TAG = "FullTransSwipeBaseActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTranslucentOrFloating(this)) { 
            fixOrientation(this)
        }
        super.onCreate(savedInstanceState)

    }

    override fun setRequestedOrientation(requestedOrientation: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTranslucentOrFloating(this)) {
            return
        }
        super.setRequestedOrientation(requestedOrientation)
    }

    private fun isTranslucentOrFloating(activity: Activity): Boolean {
        var isTranslucentOrFloating = false
        try {
            val styleableRes = Class.forName("com.android.internal.R\$styleable").getField("Window").get(null) as IntArray
            val ta = activity.obtainStyledAttributes(styleableRes)
            val m = ActivityInfo::class.java.getMethod("isTranslucentOrFloating", TypedArray::class.java)
            m.isAccessible = true
            isTranslucentOrFloating = m.invoke(null, ta) as Boolean
            m.isAccessible = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return isTranslucentOrFloating
    }

    private fun fixOrientation(activity: Activity): Boolean {
        try {
            val field = Activity::class.java.getDeclaredField("mActivityInfo")
            field.setAccessible(true)
            val o = field.get(activity) as ActivityInfo
            o.screenOrientation = -1
            field.setAccessible(false)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}