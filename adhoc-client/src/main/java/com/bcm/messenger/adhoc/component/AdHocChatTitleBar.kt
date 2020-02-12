package com.bcm.messenger.adhoc.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.common.utils.getStatusBarHeight
import kotlinx.android.synthetic.main.adhoc_titlebar_layout.view.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener

/**
 * adhoc title bar
 * Created by wjh on 2019/08/20.
 */
class AdHocChatTitleBar: androidx.constraintlayout.widget.ConstraintLayout, RecipientModifiedListener, AdHocChannelLogic.IAdHocChannelListener {

    interface OnChatTitleCallback {
        fun onLeft()
        fun onRight()
        fun onTitle()
    }

    private var mCallback: OnChatTitleCallback? = null
    private var mSession: AdHocSession? = null
    private var accountContext:AccountContext? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.adhoc_titlebar_layout, this)
        adhoc_status_fill.layoutParams = adhoc_status_fill.layoutParams.apply {
            height = context.getStatusBarHeight()
        }

        bar_back.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mCallback?.onLeft()
        }

        bar_right.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mCallback?.onRight()
        }
        bar_title.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mCallback?.onTitle()
        }
    }

    fun setOnChatTitleCallback(callback: OnChatTitleCallback?) {
        mCallback = callback
    }

    /**
     * current session
     */
    fun setSession(accountContext: AccountContext, session: AdHocSession) {
        this.accountContext = accountContext
        AdHocChannelLogic.get(accountContext).addListener(this)

        mSession = session
        var name = session.displayName(accountContext)
        if (session.isChat()) {
            session.getChatRecipient()?.addListener(this)
        }else if (session.isChannel()) {
            name = "$name (${AdHocChannelLogic.get(accountContext).getChannelUserCount(session.sessionId)})"
        }
        bar_title.text = name
        bar_right.setSession(accountContext, session)
    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        post {
            val session = mSession ?: return@post
            val accountContext = this.accountContext?:return@post
            if (sessionList.contains(session.sessionId)) {
                setSession(accountContext, session)
            }
        }
    }

    override fun onModified(recipient: Recipient) {
        if (mSession?.uid == recipient.address.serialize()) {
            bar_title.text = recipient.name
        }
    }
}