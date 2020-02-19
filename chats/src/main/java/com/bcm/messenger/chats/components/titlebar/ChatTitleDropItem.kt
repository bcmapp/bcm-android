package com.bcm.messenger.chats.components.titlebar

import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes

data class ChatTitleDropItem(val icon: Int, @AttrRes val tint: Int, val title: String, val action: ((view: View) -> Unit)? = null)