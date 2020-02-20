package com.bcm.messenger.chats.privatechat

import android.view.View
import android.view.ViewStub
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.titlebar.ChatTitleBar
import com.bcm.messenger.chats.components.titlebar.ChatTitleDropItem
import com.bcm.messenger.chats.components.titlebar.ChatTitleDropItemView
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.ViewUtils
import com.bcm.messenger.utility.storage.SPEditor

class AmeConversationGuide {
    companion object {
        private const val CONVERSATION_GUIDE_DISABLE = "conversation_guide_disable"
    }

    private val config = SPEditor(SuperPreferences.LOGIN_PROFILE_PREFERENCES)
    fun checkShow(stubView: ViewStub, menuList: List<ChatTitleDropItem>, recipient: Recipient) {
        if (!config.get(CONVERSATION_GUIDE_DISABLE, false)) {
            val rootView = stubView.inflate()

            rootView.visibility = View.VISIBLE

            rootView.findViewById<ChatTitleBar>(R.id.chat_guide_title_bar).setPrivateChat(recipient)

            val menu = rootView.findViewById<LinearLayout>(R.id.chat_guide_drop_menu)
            for (i in menuList) {
                val v = ChatTitleDropItemView(stubView.context)
                v.setViewItem(i) {}
                menu.addView(v)
            }

            val guide2 = rootView.findViewById<View>(R.id.chat_guide_2)
            val guide3 = rootView.findViewById<View>(R.id.chat_guide_3)
            val guide4 = rootView.findViewById<View>(R.id.chat_guide_4)

            val next = rootView.findViewById<Button>(R.id.chat_guide_next)
            val tip = rootView.findViewById<TextView>(R.id.chats_guide_tip)

            val showGuideFinish: View.OnClickListener = View.OnClickListener {
                ViewUtils.fadeOut(rootView, 250)
                rootView.visibility = View.GONE

                config.set(CONVERSATION_GUIDE_DISABLE, true)
            }

            val showGuide4: View.OnClickListener = View.OnClickListener {
                guide3.visibility = View.GONE
                guide4.visibility = View.VISIBLE
                tip.text = next.context.getString(R.string.chats_conversation_guide_shreddler_tip)
                next.setBackgroundResource(R.drawable.chats_blue_round_selector)
                next.text = next.context.getString(R.string.chats_got_it)
                next.setOnClickListener(showGuideFinish)
            }

            next.setOnClickListener {
                rootView.findViewById<View>(R.id.chat_guide_tab).visibility = View.GONE
                tip.text = next.context.getString(R.string.chats_conversation_guide_tiktalk_tip)
                guide2.visibility = View.GONE
                guide3.visibility = View.VISIBLE
                next.setOnClickListener(showGuide4)
            }
        }
    }
}