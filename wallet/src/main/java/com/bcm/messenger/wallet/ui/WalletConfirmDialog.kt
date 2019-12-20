package com.bcm.messenger.wallet.ui

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import com.bcm.messenger.common.ARouterConstants
import com.orhanobut.logger.Logger
import com.bcm.messenger.wallet.R
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.wallet_confirm_dialog.*
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference


/**
 * Created by wjh on 2018/5/3
 */
object WalletConfirmDialog {
    private var sLastDialogRef: WeakReference<WalletConfirmInternalDialog>? = null

    internal class WalletConfirmInternalDialog(context: Context, theme: Int = R.style.CommonLoadingStyle) : Dialog(context, theme) {

        private var mEditListener: TextWatcher? = null

        init {
            this.requestWindowFeature(Window.FEATURE_NO_TITLE)
            this.setContentView(R.layout.wallet_confirm_dialog)
            this.setCanceledOnTouchOutside(false)
            this.setCancelable(true)

            window?.setBackgroundDrawableResource(android.R.color.transparent)    //设置Dialog背景透明效果
            val windowParams = window?.attributes
            windowParams?.dimAmount = ARouterConstants.CONSTANT_BACKGROUND_DIM
            window?.attributes = windowParams

        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (mEditListener != null) {
                confirm_edit.addTextChangedListener(mEditListener)
                confirm_password_one.addTextChangedListener(mEditListener)
                confirm_password_two.addTextChangedListener(mEditListener)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            if (mEditListener != null) {
                confirm_edit.removeTextChangedListener(mEditListener)
                confirm_password_one.removeTextChangedListener(mEditListener)
                confirm_password_two.removeTextChangedListener(mEditListener)
            }
        }

        fun listenEditChange(watcher: TextWatcher) {
            mEditListener = watcher
        }

        fun setBody(title: CharSequence?, notice: CharSequence?,
                    passwordOne: CharSequence?, passwordOneHint: CharSequence?, passwordTwo: CharSequence?, passwordTwoHint: CharSequence?,
                    edit: CharSequence?, editHint: CharSequence?, cancel: CharSequence?, confirm: CharSequence?) {
            if (title.isNullOrEmpty()) {
                confirm_title.visibility = View.GONE
            } else {
                confirm_title.visibility = View.VISIBLE
                confirm_title.text = title
            }

            if (notice.isNullOrEmpty()) {
                confirm_notice.visibility = View.GONE
            } else {
                confirm_notice.visibility = View.VISIBLE
                confirm_notice.text = notice
            }
            if (passwordOne.isNullOrEmpty() && passwordOneHint.isNullOrEmpty()) {
                confirm_password_one.visibility = View.GONE
            } else {
                confirm_password_one.visibility = View.VISIBLE
                confirm_password_one.setPassword(passwordOne)
                confirm_password_one.setHint(passwordOneHint)
                confirm_password_one.setSecretEnable(true)
                confirm_ok.isEnabled = !passwordOne.isNullOrEmpty()
            }
            if (passwordTwo.isNullOrEmpty() && passwordTwoHint.isNullOrEmpty()) {
                confirm_password_two.visibility = View.GONE
            } else {
                confirm_password_two.visibility = View.VISIBLE
                confirm_password_two.setPassword(passwordTwo)
                confirm_password_two.setHint(passwordTwoHint)
                confirm_password_one.setSecretEnable(false)
                confirm_password_two.setSecretEnable(false)
                confirm_ok.isEnabled = !passwordTwo.isNullOrEmpty()
            }

            if (edit.isNullOrEmpty() && editHint.isNullOrEmpty()) {
                confirm_edit_layout.visibility = View.GONE
            } else {
                confirm_edit_layout.visibility = View.VISIBLE
                confirm_edit.setText(edit)
                confirm_edit.hint = editHint

                confirm_ok.isEnabled = !edit.isNullOrEmpty()
            }
            confirm_clear.setOnClickListener {
                confirm_edit.setText("")
            }
            if (cancel.isNullOrEmpty()) {
                confirm_cancel.visibility = View.GONE
            } else {
                confirm_cancel.visibility = View.VISIBLE
                confirm_cancel.text = cancel
            }
            if (confirm.isNullOrEmpty()) {
                confirm_ok.visibility = View.GONE
            } else {
                confirm_ok.visibility = View.VISIBLE
                confirm_ok.text = confirm
            }
        }

        fun setConfirmEnable(enable: Boolean) {
            confirm_ok.isEnabled = enable
        }

        fun setCancelCallback(listener: View.OnClickListener?) {
            confirm_cancel.setOnClickListener(listener)
        }

        fun setConfirmCallback(listener: View.OnClickListener?) {
            confirm_ok.setOnClickListener(listener)
        }
    }

    private fun prepareHide(dialog: WalletConfirmInternalDialog) {
        try {
            dialog.dismiss()
        } catch (ex: Exception) {
            ALog.e("WalletConfirmDialog", "prepareHide fail", ex)
        }
    }

    private fun prepareShow(newDialog: WalletConfirmInternalDialog) {
        try {
            val lastDialog = sLastDialogRef?.get()
            if (lastDialog?.isShowing == true) {
                lastDialog.dismiss()
            }
            sLastDialogRef = WeakReference(newDialog)
            newDialog.show()

        } catch (ex: Exception) {
            ALog.e("WalletConfirmDialog", "prepareShow fail", ex)
        }
    }

    fun showForPassword(context: Context?, title: CharSequence?, notice: CharSequence? = null,
                        password: CharSequence? = null,
                        hint: CharSequence? = context?.getString(R.string.wallet_password_hint),
                        cancel: CharSequence? = context?.getString(R.string.wallet_confirm_cancel_button),
                        confirm: CharSequence? = context?.getString(R.string.wallet_confirm_ok_button),
                        cancelListener: (() -> Unit)? = null, confirmListener: ((password: String) -> Unit)?,
                        passwordChecker: ((password: String) -> Boolean)? = null) {

        if (context == null) {
            return
        }
        val dialog = WalletConfirmInternalDialog(context)
        dialog.setBody(title, notice, password, hint, null, null, null, null, cancel, confirm)
        dialog.listenEditChange(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty()) {
                    dialog.confirm_ok.isEnabled = false
                    dialog.confirm_password_one.showWarning(dialog.context.getString(R.string.wallet_password_empty_warning))
                } else {
                    dialog.confirm_ok.isEnabled = true
                    dialog.confirm_password_one.showWarning(null)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

        })
        dialog.setCancelCallback(View.OnClickListener {
            prepareHide(dialog)
            cancelListener?.invoke()
        })
        dialog.setConfirmCallback(View.OnClickListener {
            val password = dialog.confirm_password_one.getPassword()
            if (passwordChecker != null) {
                dialog.confirm_ok.isEnabled = false
                dialog.confirm_password_one.showLoading(true)
                val o = Observable.create(ObservableOnSubscribe<Boolean> {
                    try {
                        it.onNext(passwordChecker.invoke(password.toString()))
                    } catch (ex: Exception) {
                        Logger.e(ex, "WalletConfirmDialog check password error")
                        it.onNext(false)
                    } finally {
                        it.onComplete()
                    }
                }).subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnTerminate {
                            dialog.confirm_password_one.showLoading(false)
                            dialog.confirm_ok.isEnabled = true
                        }
                o.subscribe { result ->
                    if (result) {
                        prepareHide(dialog)
                        confirmListener?.invoke(password.toString())
                    } else {
                        dialog.confirm_password_one.showWarning(dialog.context.getString(R.string.wallet_password_error_description))
                    }
                }
            } else {
                prepareHide(dialog)
                confirmListener?.invoke(password.toString())
            }
        })
        prepareShow(dialog)
    }

    fun showForPasswordChange(context: Context?, title: CharSequence?, notice: CharSequence? = null,
                              hintOne: CharSequence? = context?.getString(R.string.wallet_password_hint),
                              hintTwo: CharSequence? = context?.getString(R.string.wallet_password_confirm_hint),
                              cancel: CharSequence? = context?.getString(R.string.wallet_confirm_cancel_button),
                              confirm: CharSequence? = context?.getString(R.string.wallet_confirm_ok_button),
                              cancelListener: (() -> Unit)? = null, confirmListener: ((password: String) -> Unit)?,
                              passwordChecker: ((password: String) -> Boolean)? = null) {

        if (context == null) {
            return
        }
        val dialog = WalletConfirmInternalDialog(context)
        dialog.setBody(title, notice, null, hintOne, null, hintTwo, null, null, cancel, confirm)
        dialog.listenEditChange(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {

                if (dialog.confirm_password_one.getPassword().isEmpty()) {
                    dialog.confirm_ok.isEnabled = false
                    dialog.confirm_password_one.showWarning(dialog.context.getString(R.string.wallet_password_empty_warning))
                } else if (dialog.confirm_password_one.getPassword() != dialog.confirm_password_two.getPassword()) {
                    dialog.confirm_ok.isEnabled = false
                    dialog.confirm_password_one.showWarning(dialog.context.getString(R.string.wallet_password_not_match_warning))
                } else {
                    dialog.confirm_ok.isEnabled = true
                    dialog.confirm_password_one.showWarning(null)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

        })
        dialog.setCancelCallback(View.OnClickListener {
            prepareHide(dialog)
            cancelListener?.invoke()
        })
        dialog.setConfirmCallback(View.OnClickListener {
            val password = dialog.confirm_password_two.getPassword()
            if (passwordChecker != null) {
                dialog.confirm_ok.isEnabled = false
                dialog.confirm_password_two.showLoading(true)
                val o = Observable.create(ObservableOnSubscribe<Boolean> {
                    try {
                        it.onNext(passwordChecker.invoke(password.toString()))
                    } catch (ex: Exception) {
                        Logger.e(ex, "WalletConfirmDialog check password error")
                        it.onNext(false)
                    } finally {
                        it.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnTerminate {
                            dialog.confirm_password_two.showLoading(false)
                            dialog.confirm_ok.isEnabled = true
                        }
                o.subscribe { result ->
                    if (result) {
                        prepareHide(dialog)
                        confirmListener?.invoke(password.toString())
                    } else {
                        dialog.confirm_password_one.showWarning(dialog.context.getString(R.string.wallet_password_error_description))
                    }
                }
            } else {
                prepareHide(dialog)
                confirmListener?.invoke(password.toString())
            }
        })
        prepareShow(dialog)
    }

    fun showForEdit(context: Context?, title: CharSequence?, notice: CharSequence? = null,
                    previous: CharSequence?,
                    hint: CharSequence? = context?.getString(R.string.wallet_password_hint),
                    cancel: CharSequence? = context?.getString(R.string.wallet_confirm_cancel_button),
                    confirm: CharSequence? = context?.getString(R.string.wallet_confirm_ok_button),
                    cancelListener: (() -> Unit)? = null, confirmListener: ((newText: String) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = WalletConfirmInternalDialog(context)
        dialog.setBody(title, notice, null, null, null, null, previous, hint, cancel, confirm)
        dialog.listenEditChange(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty()) {
                    dialog.confirm_ok.isEnabled = false
                    dialog.confirm_warning.visibility = View.VISIBLE
                    dialog.confirm_warning.text = dialog.context.getString(R.string.wallet_edit_null_warning)
                    dialog.confirm_clear.visibility = View.GONE
                } else {
                    dialog.confirm_ok.isEnabled = true
                    dialog.confirm_warning.visibility = View.GONE
                    dialog.confirm_clear.visibility = View.VISIBLE
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

        })
        dialog.setCancelCallback(View.OnClickListener {
            prepareHide(dialog)
            cancelListener?.invoke()
        })
        dialog.setConfirmCallback(View.OnClickListener {
            prepareHide(dialog)
            confirmListener?.invoke(dialog.confirm_edit.text.toString())
        })
        prepareShow(dialog)
    }

    fun showForNotice(context: Context?, title: CharSequence?, notice: CharSequence? = null,
                      cancel: CharSequence? = context?.getString(R.string.wallet_confirm_cancel_button),
                      confirm: CharSequence? = context?.getString(R.string.wallet_confirm_ok_button),
                      cancelListener: (() -> Unit)? = null, confirmListener: ((newText: String) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = WalletConfirmInternalDialog(context)
        dialog.setBody(title, notice, null, null, null, null, null, null, cancel, confirm)
        dialog.setCancelCallback(View.OnClickListener {
            prepareHide(dialog)
            cancelListener?.invoke()
        })
        dialog.setConfirmCallback(View.OnClickListener {
            prepareHide(dialog)
            confirmListener?.invoke("")
        })
        prepareShow(dialog)
    }

}
