package com.bcm.messenger.chats.components.titlebar

import android.view.View

data class ChatTitleDropItem(val icon:Int, val title:String, val actionTitle:String? = null, val action:((view: View)->Unit)? = null)