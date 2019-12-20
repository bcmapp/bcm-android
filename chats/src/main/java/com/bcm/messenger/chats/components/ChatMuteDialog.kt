package com.bcm.messenger.chats.components

import android.app.Dialog
import android.content.Context
import android.view.Window
import com.bcm.messenger.chats.R
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.chats_mute_dialog.*
import java.util.concurrent.TimeUnit

/**
 * Created by wjh on 2018/6/19
 */
object ChatMuteDialog {

    private var sLastDialog: ChatMuteDialog? = null

    internal class ChatMuteDialog(context: Context, theme: Int = R.style.CommonLoadingStyle) : Dialog(context, theme) {

        var cancelListener: (() -> Unit)? = null
        var confirmListener: ((time: Long) -> Unit)? = null

        init {
            this.requestWindowFeature(Window.FEATURE_NO_TITLE)
            this.setContentView(R.layout.chats_mute_dialog)
            this.setCanceledOnTouchOutside(false)

            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val windowParams = window?.attributes
            windowParams?.dimAmount = 0.3f
            window?.attributes = windowParams

            mute_cancel.setOnClickListener {
                cancelListener?.invoke()
                prepareHide(this)
            }

            hour_btn.setOnClickListener {
                confirmListener?.invoke(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
                prepareHide(this)
            }

            day_btn.setOnClickListener {
                confirmListener?.invoke(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
                prepareHide(this)
            }

            week_btn.setOnClickListener {
                confirmListener?.invoke(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7))
                prepareHide(this)
            }

            year_btn.setOnClickListener {
                confirmListener?.invoke(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365))
                prepareHide(this)
            }

        }
    }


    private fun prepareHide(dialog: ChatMuteDialog) {
        try {
            dialog.dismiss()
            if (sLastDialog === dialog) {
                sLastDialog = null
            }
        } catch (ex: Exception) {
            Logger.e(ex, "ChatMuteDialog prepareHide error")
        }
    }


    private fun prepareShow(newDialog: ChatMuteDialog) {
        try {
            if (sLastDialog?.isShowing == true) {
                sLastDialog?.dismiss()
            }
            sLastDialog = newDialog
            newDialog.show()

        } catch (ex: Exception) {
            Logger.e(ex, "ChatMuteDialog prepareShow error")
        }
    }


    fun show(context: Context?, cancelListener: (() -> Unit)?, confirmListener: ((time: Long) -> Unit)?) {
        if (context == null) {
            return
        }
        val dialog = ChatMuteDialog(context)
        dialog.cancelListener = cancelListener
        dialog.confirmListener = confirmListener
        prepareShow(dialog)
    }

}