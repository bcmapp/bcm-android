package com.bcm.messenger.chats.group

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bcm.messenger.chats.R

class ChatGroupNoticeDialog : DialogFragment() {

    lateinit var content: String
    lateinit var confirmListener: ConfirmListener

    interface ConfirmListener {
        fun onConfirm()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.setCanceledOnTouchOutside(false)

        val windowParams = dialog.window.attributes
        windowParams.dimAmount = 0.0f
        windowParams.gravity = Gravity.BOTTOM
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.gravity = Gravity.CENTER
        dialog.window.attributes = windowParams
        dialog.window.decorView.setPadding(0, 0, 0, 0)

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.chats_group_notice_dialog, container, false)
        view.findViewById<View>(R.id.group_notice_dialog_confirm_button).setOnClickListener {
            dismiss()
            confirmListener.onConfirm()
        }
        view.findViewById<TextView>(R.id.notice_dialog_content).text = content
        return view
    }

    fun create(content: String, confirmListener: ConfirmListener) {
        this.content = content
        this.confirmListener = confirmListener
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}