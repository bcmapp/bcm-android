package com.bcm.messenger.common.utils.view

import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getDrawable

fun TextView.setLeftDrawable(@DrawableRes drawableRes: Int, @AttrRes tint: Int = 0) {
    val drawable = getDrawable(drawableRes)
    if (tint != 0) {
        drawable.setTint(context.getAttrColor(tint))
    }
    drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
    setCompoundDrawables(drawable, null, null, null)
}

fun TextView.setTopDrawable(@DrawableRes drawableRes: Int, @AttrRes tint: Int = 0) {
    val drawable = getDrawable(drawableRes)
    if (tint != 0) {
        drawable.setTint(context.getAttrColor(tint))
    }
    drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
    setCompoundDrawables(null, drawable, null, null)
}

fun TextView.setRightDrawable(@DrawableRes drawableRes: Int, @AttrRes tint: Int = 0) {
    val drawable = getDrawable(drawableRes)
    if (tint != 0) {
        drawable.setTint(context.getAttrColor(tint))
    }
    drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
    setCompoundDrawables(null, null, drawable, null)
}

fun TextView.setBottomDrawable(@DrawableRes drawableRes: Int, @AttrRes tint: Int = 0) {
    val drawable = getDrawable(drawableRes)
    if (tint != 0) {
        drawable.setTint(context.getAttrColor(tint))
    }

    drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
    setCompoundDrawables(null, null, null, drawable)
}

fun TextView.resetDrawable() {
    setCompoundDrawables(null, null, null, null)
}