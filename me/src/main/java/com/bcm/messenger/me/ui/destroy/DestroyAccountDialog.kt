package com.bcm.messenger.me.ui.destroy

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_dialog_destroy_account.*

/**
 * Created by Kin on 2018/9/19
 */
class DestroyAccountDialog : DialogFragment() {
    private var callback: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.me_dialog_destroy_account, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.setLayout(325.dp2Px(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        destroy_dialog_checkbox.setOnClickListener {
            clickCheckbox()
        }
        destroy_dialog_notice.setOnClickListener {
            clickCheckbox()
        }
        destroy_dialog_cancel.setOnClickListener {
            dismiss()
        }
        destroy_dialog_confirm.setOnClickListener {
            dismiss()
            callback?.invoke()
        }
    }

    private fun clickCheckbox() {
        if (destroy_dialog_checkbox.isSelected) {
            destroy_dialog_checkbox.isSelected = false
            destroy_dialog_confirm.isEnabled = false
        } else {
            destroy_dialog_checkbox.isSelected = true
            destroy_dialog_confirm.isEnabled = true
        }
    }

    fun setCallback(callback: () -> Unit): DestroyAccountDialog {
        this.callback = callback
        return this
    }
}