package com.bcm.messenger.chats.bean

import android.view.View
import androidx.annotation.DrawableRes

data class BottomPanelItem(val name: String, @DrawableRes val imgId: Int, val listener: BottomPanelClickListener)

interface BottomPanelClickListener {
    fun onClick(name: String, view: View)
}