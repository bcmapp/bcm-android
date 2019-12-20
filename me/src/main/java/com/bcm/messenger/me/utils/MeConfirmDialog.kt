package com.bcm.messenger.me.utils

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.me.R
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.me_confirm_dialog.*
import java.lang.ref.WeakReference


/**
 * Created by wjh on 2018/5/3
 */
object MeConfirmDialog {
    private var sLastDialogRef: WeakReference<MeConfirmInternalDialog>? = null

    internal class MeConfirmInternalDialog(context: Context, theme: Int = R.style.CommonLoadingStyle) : Dialog(context, theme) {

        var cancelListener: (() -> Unit)? = null
        var confirmListener: ((content: CharSequence) -> Unit)? = null

        var multiConfirmListener: ((choose: Int) -> Unit)? = null

        private var mEditListener: TextWatcher? = null

        init {
            this.requestWindowFeature(Window.FEATURE_NO_TITLE)
            this.setContentView(R.layout.me_confirm_dialog)
            this.setCanceledOnTouchOutside(false)

            val windowParams = window?.attributes
            windowParams?.dimAmount = ARouterConstants.CONSTANT_BACKGROUND_DIM
            window?.attributes = windowParams

            confirm_cancel.setOnClickListener {
                cancelListener?.invoke()
                prepareHide(this)
            }

            confirm_ok.setOnClickListener {
                when {
                    confirm_password.visibility == View.VISIBLE -> confirmListener?.invoke(confirm_password.getPassword())
                    confirm_edit.visibility == View.VISIBLE -> confirmListener?.invoke(confirm_edit.text.toString())
                    else -> confirmListener?.invoke("")
                }
                prepareHide(this)
            }

            confirm_second_button.setOnClickListener {
                multiConfirmListener?.invoke(2)
                prepareHide(this)
            }

            confirm_main_button.setOnClickListener {
                multiConfirmListener?.invoke(0)
                prepareHide(this)
            }

            confirm_second_button.setOnClickListener {
                multiConfirmListener?.invoke(1)
                prepareHide(this)
            }

        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            confirm_password.addTextChangedListener(mEditListener)
            confirm_password2.addTextChangedListener(mEditListener)
            confirm_edit.addTextChangedListener(mEditListener)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            confirm_password.removeTextChangedListener(mEditListener)
            confirm_password2.removeTextChangedListener(mEditListener)
            confirm_edit.removeTextChangedListener(mEditListener)
        }

        fun setBodyForBig(title: CharSequence?, notice: CharSequence?,
                    editText: CharSequence?, editHint: CharSequence?,
                    password: CharSequence?, passwordHint: CharSequence?,
                    confirmBigMainText: CharSequence?, confirmBigSecondText: CharSequence?) {

            setBody(title, notice, editText, editHint, password, passwordHint, null, null,
                    confirmBigMainText, confirmBigSecondText, null, null)
        }

        fun setBodyForNormal(title: CharSequence?, notice: CharSequence?,
                    editText: CharSequence?, editHint: CharSequence?,
                    password: CharSequence?, passwordHint: CharSequence?,
                    cancelText: CharSequence?, confirmText: CharSequence?) {

            setBody(title, notice, editText, editHint, password, passwordHint, null, null,
                    null, null, cancelText, confirmText)
        }

        fun setBody(title: CharSequence?, notice: CharSequence?,
                    editText: CharSequence?, editHint: CharSequence?,
                    password: CharSequence?, passwordHint: CharSequence?, passwordConfirm: CharSequence?, passwordConfirmHint: CharSequence?,
                    confirmBigMainText: CharSequence?, confirmBigSecondText: CharSequence?,
                    cancelText: CharSequence?, confirmText: CharSequence?) {

            if (title.isNullOrEmpty()) {
                confirm_title.visibility = View.GONE
            } else {
                confirm_title.visibility = View.VISIBLE
                confirm_title.text = title
            }

            if (notice.isNullOrEmpty()) {
                confirm_tip.visibility = View.GONE
            } else {
                confirm_tip.visibility = View.VISIBLE
                confirm_tip.text = notice
            }

            if (editText.isNullOrEmpty() && editHint.isNullOrEmpty()) {
                confirm_edit.visibility = View.GONE
            } else {
                confirm_edit.visibility = View.VISIBLE
                confirm_edit.hint = editHint
                confirm_edit.setText(editText)

                confirm_ok.isEnabled = !editText.isNullOrEmpty()
            }

            if (password.isNullOrEmpty() && passwordHint.isNullOrEmpty()) {
                confirm_password.visibility = View.GONE
            } else {
                confirm_password.visibility = View.VISIBLE
                confirm_password.setHint(passwordHint)
                confirm_password.setPassword(password)
                confirm_ok.isEnabled = !password.isNullOrEmpty()
            }
            if (passwordConfirm.isNullOrEmpty() && passwordConfirmHint.isNullOrEmpty()) {
                confirm_password2.visibility = View.GONE
            } else {
                confirm_password2.visibility = View.VISIBLE
                confirm_password2.setHint(passwordConfirmHint)
                confirm_password2.setPassword(passwordConfirm)
            }

            if (confirmBigMainText.isNullOrEmpty()) {
                confirm_main_button.visibility = View.GONE
            } else {
                confirm_main_button.visibility = View.VISIBLE
                confirm_main_button.text = confirmBigMainText
            }

            if (confirmBigSecondText.isNullOrEmpty()) {
                confirm_second_button.visibility = View.GONE
            } else {
                confirm_second_button.visibility = View.VISIBLE
                confirm_second_button.text = confirmBigSecondText
            }

            if (cancelText.isNullOrEmpty()) {
                confirm_cancel.visibility = View.GONE
            } else {
                confirm_cancel.visibility = View.VISIBLE
                confirm_cancel.text = cancelText
            }

            if (confirmText.isNullOrEmpty()) {
                confirm_ok.visibility = View.GONE
            } else {
                confirm_ok.visibility = View.VISIBLE
                confirm_ok.text = confirmText
            }
        }

        fun setLogOutStyle() {
            confirm_cancel.setTextColor(getColor(R.color.common_color_ff3737))
            confirm_cancel.setBackgroundResource(R.drawable.common_red_stroke_big_corner_bg)
            confirm_ok.setTextColor(getColor(R.color.common_color_379BFF))
            confirm_ok.setBackgroundResource(R.drawable.common_primary_thin_round_selector)
        }


    }

    private fun prepareHide(dialog: MeConfirmInternalDialog) {
        try {
            dialog.dismiss()
        } catch (ex: Exception) {
            Logger.e(ex, "MeConfirmDialog prepareHide error")
        }
    }

    private fun prepareShow(newDialog: MeConfirmInternalDialog) {
        try {
            val lastDialog = sLastDialogRef?.get()
            if (lastDialog?.isShowing == true) {
                lastDialog.dismiss()
            }
            sLastDialogRef = WeakReference(newDialog)
            newDialog.show()

        } catch (ex: Exception) {
            Logger.e(ex, "MeConfirmDialog prepareShow error")
        }
    }

    fun showForClearHistory(context: Context?, confirmListener: (() -> Unit)?, cancelListener: ((content: CharSequence) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = MeConfirmInternalDialog(context)
        dialog.setBodyForNormal(context.getString(R.string.me_confirm_clear_history_title),
                context.getString(R.string.me_confirm_clear_history_notice),
                null, null, null, null,
                context.getString(R.string.me_confirm_clear_history_ok_text),
                context.getString(R.string.common_cancel)

        )
        dialog.setLogOutStyle()
        dialog.cancelListener = confirmListener
        dialog.confirmListener = cancelListener
        prepareShow(dialog)
    }

    fun showBackupComplete(context: Context?, cancelListener: (() -> Unit)?, confirmListener: ((content: CharSequence) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = MeConfirmInternalDialog(context)
        dialog.setBodyForNormal(context.getString(R.string.me_mark_as_backed_up),
                context.getString(R.string.me_mark_as_backed_up_detail),
                null, null, null, null,
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_confirm_ok_text)
        )
        dialog.cancelListener = cancelListener
        dialog.confirmListener = confirmListener
        prepareShow(dialog)
    }

    fun showCopyBackup(context: Context?, cancelListener: (() -> Unit)?, confirmListener: ((content: CharSequence) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = MeConfirmInternalDialog(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            dialog.setBodyForNormal(context.getString(R.string.me_qr_code_copied),
                    context.getString(R.string.me_backup_cannot_copy_qr_code),
                    null, null, null, null,
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_popup_ok)
            )
        } else {
            dialog.setBodyForNormal(context.getString(R.string.me_qr_code_copied),
                    context.getString(R.string.me_store_safely),
                    null, null, null, null,
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_popup_ok)
            )
        }
        dialog.cancelListener = cancelListener
        dialog.confirmListener = confirmListener
        prepareShow(dialog)
    }

    fun showConfirm(context: Context?, title: CharSequence?, notice: CharSequence?,
                           confirm: CharSequence? = context?.getString(R.string.common_confirm_ok_text),
                           confirmListener: ((content: CharSequence) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = MeConfirmInternalDialog(context)
        dialog.setCancelable(false)
        dialog.setBodyForBig(title, notice, null, null, null, null,
                null, confirm)
        dialog.multiConfirmListener = {
            confirmListener?.invoke("")
        }
        prepareShow(dialog)
    }

}
