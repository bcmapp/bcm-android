package com.bcm.messenger.chats.components.titlebar

import android.view.View
import androidx.annotation.ColorRes

data class ChatTitleDropItem(val icon: Int, @ColorRes val tint: Int, val title: String, val action: ((view: View) -> Unit)? = null)