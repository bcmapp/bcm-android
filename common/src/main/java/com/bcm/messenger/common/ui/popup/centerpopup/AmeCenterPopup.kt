package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_center_popup_base_layout.*
import java.lang.ref.WeakReference

/**
 *
 * Created by bcm.social.01 on 2018/5/31.
 */
class AmeCenterPopup : Application.ActivityLifecycleCallbacks {
    companion object {
        private val inst = AmeCenterPopup()
        fun instance(): AmeCenterPopup {
            return inst
        }
    }

    class Builder {
        private val config = PopConfig()

        fun withTitle(title: String): Builder {
            config.title = title
            return this
        }

        fun withContent(content: String): Builder {
            config.content = content
            return this
        }

        fun withCancelTitle(title: String): Builder {
            config.cancelTitle = title
            return this
        }

        fun withOkTitle(title: String): Builder {
            config.okTitle = title
            return this
        }

        fun withWarningTitle(title: String): Builder {
            config.warningTitle = title
            return this
        }

        fun withOkListener(listener: () -> Unit): Builder {
            config.ok = listener
            return this
        }

        fun withCancelListener(listener: () -> Unit): Builder {
            config.cancel = listener
            return this
        }

        fun withWarningListener(listener: () -> Unit): Builder {
            config.warning = listener
            return this
        }

        fun withContentAlignment(contentAlignment: Int): Builder {
            config.contentAlignment = contentAlignment
            return this
        }

        fun withCancelable(cancelable: Boolean): Builder {
            config.cancelable = cancelable
            return this
        }

        fun withTopMost(topMost: Boolean): Builder {
            config.topMost = topMost
            return this
        }

        fun withCustomView(customViewCreator: CustomViewCreator): Builder {
            config.viewCreator = customViewCreator
            return this
        }

        fun withDismissListener(listener: () -> Unit): Builder {
            config.dismissListener = listener
            return this
        }

        fun show(activity: FragmentActivity?) {
            instance().show(activity, config)
        }
    }


    private var popup: ICenterPop? = null
    private var attachActivity: WeakReference<Activity>? = null

    fun newBuilder(): Builder {
        return Builder()
    }

    fun show(activity: FragmentActivity?, config: PopConfig) {
        //dismiss
        if (popup != null && popup?.config?.topMost == true) {
            ALog.w("AmeCenterPopup", "show fail, lat popup is topMost, can not dismiss")
            return
        }

        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = if (config.viewCreator != null
                    || config.contentAlignment != View.TEXT_ALIGNMENT_CENTER
                    || config.topMost) {
                PopupWindow()
            } else {
                AlertWindow(config)
            }

            popup.config = config
            this.popup = popup

            try {
                popup.show(activity, activity.javaClass.simpleName)
            } catch (ex: Exception) {
                ALog.e("AmeCenterPopup", "show error", ex)
                config.dismissListener.invoke()
                this.popup = null
            }

        } else {
            ALog.w("AmeCenterPopup", "show fail, activity is no ready")
            config.dismissListener.invoke()
        }
    }

    /**
     *
     */
    fun dismiss() {
        dismissInner(popup)
    }

    fun dismissInner(window: ICenterPop?) {
        try {
            if (popup == window) {
                attachActivity?.get()?.application?.unregisterActivityLifecycleCallbacks(this)
                attachActivity = null

                if (window?.showing == true) {
                    window.destroy()
                }
                window?.config?.dismissListener?.invoke()
                popup = null
            }
        } catch (ex: Exception) {
            ALog.e("AmeCenterPopup", "dismissInner error", ex)
            popup = null
        }
    }

    interface CustomViewCreator {
        fun onCreateView(parent: ViewGroup): View?
        fun onDetachView()
    }


    interface ICenterPop {
        var config: PopConfig
        fun show(fragmentActivity: FragmentActivity, tag: String)
        fun destroy()
        val showing: Boolean
    }

    class AlertWindow(override var config: PopConfig) : ICenterPop {
        private var alertDialog: AlertDialog? = null

        override val showing: Boolean
            get() = alertDialog?.isShowing == true

        override fun show(fragmentActivity: FragmentActivity, tag: String) {
            val builder = AlertDialog.Builder(fragmentActivity)
            if (!config.title.isNullOrEmpty()) {
                builder.setTitle(config.title)
            }

            if (!config.content.isNullOrEmpty()) {
                builder.setMessage(config.content)
            }

            if (!config.cancelable) {
                builder.setCancelable(config.cancelable)
            }

            if (!config.cancelTitle.isNullOrEmpty()) {
                builder.setNegativeButton(config.cancelTitle) { _, _ -> config.cancel.invoke() }
            }

            if (!config.okTitle.isNullOrEmpty()) {
                builder.setPositiveButton(config.okTitle) { _, _ -> config.ok.invoke() }
            }

            if (!config.warningTitle.isNullOrEmpty()) {
                builder.setNeutralButton(config.warningTitle) { _, _ -> config.warning.invoke() }
            }

            builder.setOnDismissListener {
                AmePopup.center.dismissInner(this)
                config.dismissListener()
            }

            alertDialog = builder.show()
        }

        override fun destroy() {
            try {
                alertDialog?.dismiss()
                alertDialog = null
            } catch (e: Throwable) {
            }
        }
    }

    class PopupWindow : DialogFragment(), ICenterPop {
        private lateinit var configData: PopConfig
        override var config: PopConfig
            get() = configData
            set(value) {
                configData = value
            }

        override fun show(fragmentActivity: FragmentActivity, tag: String) {
            super.show(fragmentActivity.supportFragmentManager, tag)
        }

        override fun destroy() {
            dismiss()
        }

        override val showing: Boolean
            get() = isVisible

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_center_popup_base_layout, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            updateUI()
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        override fun onDestroy() {
            super.onDestroy()
            AmePopup.center.dismissInner(this)
            config.viewCreator?.onDetachView()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            activity?.let {
                val dialog = Dialog(it, R.style.CommonCenterPopupWindow)
                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)    //Dialog
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setWindowAnimations(R.style.CommonDropWindowAnimation)

                    val windowParams = window.attributes
                    windowParams.dimAmount = 0.0f
                    windowParams.gravity = Gravity.CENTER
                    windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                    windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
                    window.attributes = windowParams
                    window.decorView.setPadding(0, 0, 0, 0)
                }

                dialog.setCancelable(config.cancelable)
                dialog.setCanceledOnTouchOutside(config.cancelable)
                return dialog
            }

            return super.onCreateDialog(savedInstanceState)
        }

        private fun updateUI() {
            isCancelable = config.cancelable
            val config = this.config
            if (config.title?.length ?: 0 > 0) {
                common_popup_title.visibility = View.VISIBLE
                common_popup_title.text = config.title
            } else {
                common_popup_title.visibility = View.GONE
            }

            if (config.content?.length ?: 0 > 0) {
                common_popup_content.visibility = View.VISIBLE
                common_popup_content.text = config.content
                common_popup_content.textAlignment = config.contentAlignment
            } else {
                common_popup_content.visibility = View.GONE
            }

            if (!TextUtils.isEmpty(config.cancelTitle)) {
                common_popup_cancel.visibility = View.VISIBLE
                common_popup_cancel.text = config.cancelTitle
                common_popup_cancel.setOnClickListener {
                    dismiss()
                    config.cancel.invoke()
                }
            } else {
                common_popup_cancel.visibility = View.GONE
            }

            if (!TextUtils.isEmpty(config.okTitle)) {
                common_popup_ok.visibility = View.VISIBLE
                common_popup_ok.text = config.okTitle
                common_popup_ok.setOnClickListener {
                    dismiss()
                    config.ok.invoke()
                }
            } else {
                common_popup_ok.visibility = View.GONE
            }

            if (!TextUtils.isEmpty(config.warningTitle)) {
                common_popup_warning.visibility = View.VISIBLE
                common_popup_warning.text = config.warningTitle
                common_popup_warning.setOnClickListener {
                    dismiss()
                    config.warning.invoke()
                }
            } else {
                common_popup_warning.visibility = View.GONE
            }

            val customView = config.viewCreator?.onCreateView(common_popup_custom_view)
            if (null != customView) {
                common_popup_custom_view.visibility = View.VISIBLE
            } else {
                common_popup_custom_view.visibility = View.GONE
            }

        }
    }


    override fun onActivityPaused(activity: Activity?) {
    }

    override fun onActivityResumed(activity: Activity?) {
    }

    override fun onActivityStarted(activity: Activity?) {
    }

    override fun onActivityDestroyed(activity: Activity?) {
        if (activity == attachActivity?.get()) {
            dismiss()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity?) {
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
    }

    class PopConfig {
        var title: String? = null
        var content: String? = null
        var ok: () -> Unit = { instance().dismiss() }
        var cancel: () -> Unit = { instance().dismiss() }
        var warning: () -> Unit = { instance().dismiss() }
        var okTitle: String? = null
        var cancelTitle: String? = null
        var warningTitle: String? = null
        var viewCreator: CustomViewCreator? = null
        var cancelable: Boolean = true
        var topMost: Boolean = false //,
        var dismissListener: () -> Unit = {}
        var contentAlignment: Int = View.TEXT_ALIGNMENT_CENTER
    }
}