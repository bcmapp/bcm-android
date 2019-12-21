package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import kotlinx.android.synthetic.main.common_popup_progress.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.dp2Px
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2018/10/9
 */
class AmeProgressPopup : Application.ActivityLifecycleCallbacks {
    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null

    class PopConfig {
        var tipText: String = ""
        var dismiss: () -> Unit = {}
    }

    fun show(activity: FragmentActivity?, tipText: String): AmeProgressPopup? {
        val config = PopConfig().apply {
            this.tipText = tipText
        }
        show(activity, config)
        return this
    }

    fun updateProgress(progress: Int) {
        popup?.updateProgress(progress)
    }

    private fun show(activity: FragmentActivity?, config: PopConfig) {
        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.resultPopup = this
            this.popup = popup
            popup.config = config

            try {
                popup.show(activity.supportFragmentManager, activity.javaClass.simpleName)
            } catch (ex: Exception) {
                ALog.e("AmeResultPopup", "show error", ex)
            }
        }
    }

    /**
     * 
     */
    fun dismiss() {
        dismissInner(popup)
    }

    fun dismissInner(window: PopupWindow?) {
        try {
            if (popup == window) {
                attachActivity?.get()?.application?.unregisterActivityLifecycleCallbacks(this)
                attachActivity = null

                if (window?.isVisible == true) {
                    window.dismiss()
                }
                popup?.resultPopup = null
                popup?.config?.dismiss?.invoke()
                popup = null
            }
        } catch (ex: Exception) {
            ALog.e("AmeResultPopup", "dismissInner error", ex)
        }
    }

    class PopupWindow : DialogFragment() {
        var resultPopup: AmeProgressPopup? = null
        var config: PopConfig? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_popup_progress, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            updateUI()
        }

        override fun onDestroy() {
            super.onDestroy()
            resultPopup?.dismissInner(this)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            activity?.let {
                val dialog = Dialog(it, R.style.CommonLoadingStyle)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCanceledOnTouchOutside(false)

                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)    //Dialog
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                    val windowParams = window.attributes
                    windowParams.gravity = Gravity.CENTER
                    windowParams.dimAmount = 0.0f
                    windowParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    windowParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    window.attributes = windowParams

                    window.decorView.setPadding(0, 0, 0, 0)
                }

                return dialog
            }
            return super.onCreateDialog(savedInstanceState)
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            dialog?.window?.setLayout(200.dp2Px(), 110.dp2Px())
        }

        private fun updateUI() {
            popup_progress.max = 100
            popup_progress.progress = 0
            popup_progress_text.text = config?.tipText
            popup_progress_hidden.setOnClickListener {
                resultPopup?.dismissInner(this)
            }
        }

        fun updateProgress(progress: Int) {
            popup_progress.progress = progress
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
}