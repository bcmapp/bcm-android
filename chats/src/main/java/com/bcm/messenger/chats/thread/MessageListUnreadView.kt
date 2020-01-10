package com.bcm.messenger.chats.thread

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.ViewUtils
import kotlinx.android.synthetic.main.chats_message_list_unread_view.view.*

/**
 * Created by Kin on 2020/1/10
 */
class MessageListUnreadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val unreadAccountList = mutableListOf<AccountContext>()

    init {
        LayoutInflater.from(context).inflate(R.layout.chats_message_list_unread_view, this, true)
    }

    fun updateUnreadAccounts(list: List<AccountContext>) {
        unreadAccountList.clear()
        unreadAccountList.addAll(list)

        updateUI()
    }

    private fun updateUI() {
        when (unreadAccountList.size) {
            0 -> ViewUtils.fadeOut(this, 250)
            1 -> {
                ViewUtils.fadeIn(this, 250)

                val accountContext = unreadAccountList[0]
                unread_avatar_1.setPhoto(Recipient.from(accountContext, accountContext.uid, true))
                unread_avatar_1.visibility = View.VISIBLE
                unread_avatar_2.visibility = View.GONE
            }
            2 -> {
                ViewUtils.fadeIn(this, 250)

                val accountContext1 = unreadAccountList[0]
                unread_avatar_1.setPhoto(Recipient.from(accountContext1, accountContext1.uid, true))
                unread_avatar_1.visibility = View.VISIBLE

                val accountContext2 = unreadAccountList[0]
                unread_avatar_2.setPhoto(Recipient.from(accountContext2, accountContext2.uid, true))
                unread_avatar_2.visibility = View.VISIBLE
            }
        }
    }
}