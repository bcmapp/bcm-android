package com.bcm.messenger.chats.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.utility.AppContextHolder
import kotlinx.android.synthetic.main.chats_dialog_send_contact.*

/**
 * Confirm dialog for sending contact cards
 *
 * Created by Kin on 2018/8/15
 */
class ChatSendContactDialog : DialogFragment() {

    private var mChatRecipient: Recipient? = null

    private var mSendList: List<Recipient>? = null
    private var mCallback: ((sendList: List<Recipient>, comment: CharSequence?) -> Unit)? = null

    private var mAdapter = object : LinearBaseAdapter<Recipient>() {

        private val mInflater: LayoutInflater = LayoutInflater.from(AppContextHolder.APP_CONTEXT)

        override fun onBindContentHolder(holder: ViewHolder<Recipient>, trueData: Recipient?) {
            if (holder is ContactViewHolder) {
                holder.bind()
            }
        }

        override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<Recipient> {
            return ContactViewHolder(mInflater.inflate(R.layout.chats_send_contact_item, parent, false))
        }

        inner class ContactViewHolder(itemView: View) : ViewHolder<Recipient>(itemView) {
            private val logoView = itemView.findViewById<ImageView>(R.id.send_contact_logo)
            private val nameView = itemView.findViewById<TextView>(R.id.send_contact_name)

            fun bind() {
                nameView.text = data?.name ?: ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ForwardDialogStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_dialog_send_contact, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setLayout(AppContextHolder.APP_CONTEXT.getScreenWidth() - 60.dp2Px(), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(R.color.common_color_transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        mChatRecipient?.let {
            send_contact_portrait.setPhoto(it)
        }
        send_contact_to_name.text = mChatRecipient?.name
        send_contact_cancel.setOnClickListener {
            dismiss()
        }
        send_contact_ok.setOnClickListener {
            dismiss()
            mCallback?.invoke(mSendList ?: listOf(), send_contact_comment_text.text?.toString())
            mCallback = null
        }

        if (mSendList?.size ?: 0 > 3) {
            val lp = send_contact_content_rv.layoutParams
            lp.height = 150.dp2Px()
            send_contact_content_rv.layoutParams = lp
        }

        send_contact_content_rv.adapter = mAdapter
        send_contact_content_rv.layoutManager = LinearLayoutManager(context)

        mAdapter.setDataList(mSendList)

    }


    fun setChatRecipient(recipient: Recipient): ChatSendContactDialog {
        mChatRecipient = recipient
        return this
    }


    fun setSendRecipient(recipientList: List<Recipient>): ChatSendContactDialog {
        mSendList = recipientList
        return this
    }

    fun getSendRecipient(): List<Recipient> {
        return mSendList ?: listOf()
    }


    fun setSendCallback(callback: (sendList: List<Recipient>, comment: CharSequence?) -> Unit): ChatSendContactDialog {
        mCallback = callback
        return this
    }
}