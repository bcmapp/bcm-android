package com.bcm.messenger.common.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Window
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.R
import com.bcm.messenger.common.api.BcmJSInterface
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import java.lang.ref.WeakReference

/**
 * dialog
 * Created by wjh on 2018/10/29
 */
object SystemNoticeDialog {

    private val TAG = SystemNoticeDialog::class.java.simpleName

    private var sLastDialogRef: WeakReference<Dialog>? = null

    internal class SystemNoticeDialog(context: Context, theme: Int = R.style.CommonLoadingStyle) : Dialog(context, theme) {

        private var webView: NestedScrollWebView

        init {

            this.requestWindowFeature(Window.FEATURE_NO_TITLE)
            this.setContentView(R.layout.common_system_dialog_layout)
            this.setCanceledOnTouchOutside(false)

            findViewById<ImageView>(R.id.dialog_close_iv).setOnClickListener {
                dismiss()
            }

            webView = findViewById(R.id.dialog_content_wv)
            webView.addJavascriptInterface(BcmJSInterface().apply {
                setListener(object : BcmJSInterface.JSActionListener {
                    override fun onRoute(api: String, json: String): Boolean {
                        when (api) {
                            "browse" -> {
                                val p = BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, json)
                                if (context is Activity) {
                                    p.navigation(context)
                                } else {
                                    p.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    p.navigation(context)
                                }
                            }
                            "close" -> {
                                dismiss()
                            }
                        }
                        return true
                    }
                })
            }, "bcm")
        }

        fun show(url: String) {
            this.webView.loadUrl(url)
            show()
        }
    }

    fun show(context: Context, alert: AmePushProcess.SystemNotifyData.WebAlertData) {

        try {
            val newDialog = SystemNoticeDialog(context)
            val lastDialog = sLastDialogRef?.get()
            if (lastDialog?.isShowing == true) {
                lastDialog.dismiss()
            }
            sLastDialogRef = WeakReference(newDialog)
            newDialog.show(alert.url)

        } catch (ex: Exception) {
            ALog.e("SystemNoticeDialog", "show error", ex)
        }
    }

    fun show(context: Context, alert: AmePushProcess.SystemNotifyData.TextAlertData) {
        try {
            val config = AmeCenterPopup.PopConfig()
            config.title = alert.title
            config.content = alert.content
            if (alert.buttons == null || alert.buttons.isEmpty()) {
                config.okTitle = context.getString(R.string.common_popup_ok)
            } else {  //
                config.cancelTitle = context.getString(R.string.common_cancel)
                config.okTitle = context.getString(R.string.common_confirm_detail)
                config.ok = {
                    try {
                        val action = alert.buttons[0].action
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action))
                        if (context is Activity) {
                            context.startActivity(intent)
                        } else {
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        ALog.e(TAG, e.toString())
                    }
                }
            }

            if (context is AppCompatActivity) {
                AmePopup.center.show(context, config)
            }
        } catch (ex: Exception) {
            ALog.e("SystemNoticeDialog", "show error", ex)
        }
    }
}