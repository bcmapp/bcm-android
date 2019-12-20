package com.bcm.messenger.chats.group.live

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import kotlinx.android.synthetic.main.chats_live_input_layout.view.*

class ChatLiveInputLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    interface ChatLiveInputLayoutListener {
        fun onSendMessage(msg: String)
    }

    private var listener: ChatLiveInputLayoutListener? = null

    init {
        inflate(context, R.layout.chats_live_input_layout, this)
        initView()
    }

    private fun initView() {
        flow_input.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && !textView.text.isNullOrBlank()) {
                listener?.onSendMessage(textView.text.toString())
            }
            return@setOnEditorActionListener false
        }
    }
}