package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import kotlinx.android.synthetic.main.chats_conversation_item_pin.view.*

/**
 * Chat message pin to top
 */
class ChatPinDisplayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyle) {

    companion object {
        val TAG = ChatPinDisplayView::class.java.simpleName
    }

    init {
        View.inflate(context, R.layout.chats_conversation_item_pin, this)
    }

    fun setPin(messageRecord: AmeGroupMessageDetail) {
        val data = messageRecord.message?.content as? AmeGroupMessage.PinContent
        data?.mid.let {
            if (it != -1L) {
                pin_text.text = "Pin a message."
            } else {
                pin_text.text = "Unpin a message."
            }
        }
    }
}