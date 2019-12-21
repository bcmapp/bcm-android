package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.CommonLoadingView
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference

/**
 * loading
 * Created by bcm.social.01 on 2018/5/31.
 */
class AmeTipLoadingPopup : Application.ActivityLifecycleCallbacks {

    companion object {
        const val DELAY_DEFAULT = 1000L
    }

    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null

    fun show(activity: FragmentActivity?) {
        show(activity, true, "")
    }

    fun show(activity: FragmentActivity?, tip: String) {
        show(activity, true, tip)
    }

    fun show(activity: FragmentActivity?, canBackPress: Boolean, tip: String = "") {
        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.loadingPopup = this
            popup.loadingTip = tip

            this.popup = popup
            this.popup?.isCancelable = canBackPress

            try {
                popup.show(activity.supportFragmentManager, activity.javaClass.simpleName)
            } catch (ex: Exception) {
                ALog.e("AmeLoadingPopup", "show error", ex)
            }
        }
    }

    fun updateTip(tip: String) {
        if (popup?.isVisible == true) {
            popup?.update(tip)
        }
    }

    fun updateSubTip(subTip:String) {
        if (popup?.isVisible == true) {
            popup?.updateSubTip(subTip)
        }
    }

    /**
     * 
     */
    fun dismiss(delay: Long, callback: (() -> Unit)? = null) {
        if (delay > 0) {
            AmeDispatcher.mainThread.dispatch({
                dismissInner(popup)
                callback?.invoke()
            }, delay)
        } else {
            dismissInner(popup)
            callback?.invoke()
        }
    }

    /**
     * 
     */
    fun dismiss() {
        dismiss(0)
    }

    fun dismissInner(window: PopupWindow?) {
        try {
            if (popup == window && window != null) {
                attachActivity?.get()?.application?.unregisterActivityLifecycleCallbacks(this)
                attachActivity = null

                popup?.loadingAnim?.stopAnim()
                try {
                    if (!window.isDetached) {
                        window.dismissAllowingStateLoss()
                    }
                } catch (e: Throwable) {
                    ALog.e("AmeLoadingPopup", e)
                }
                popup = null
            }
        } catch (ex: Exception) {
            ALog.e("AmeLoadingPopup", "dismissInner error", ex)
        }
    }

    class PopupWindow : DialogFragment() {
        var loadingPopup: AmeTipLoadingPopup? = null
        var loadingAnim: CommonLoadingView? = null
            private set
        var loadingTip = ""
        var subTip = ""

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = inflater.inflate(R.layout.common_tip_loading_popup_layout, container, false)
            loadingAnim = view.findViewById(R.id.pb_load)
            loadingAnim?.startAnim()

            if (loadingTip.isNotEmpty()) {
                val tipView = view.findViewById<TextView>(R.id.loading_tip)
                tipView.text = loadingTip
                tipView.visibility = View.VISIBLE
            }
            return view
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        override fun onDestroy() {
            super.onDestroy()
            loadingAnim?.stopAnim()
            loadingPopup?.dismissInner(this)
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
                    windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                    windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
                    window.attributes = windowParams

                    window.decorView.setPadding(0, 0, 0, 0)
                }

                return dialog
            }
            return super.onCreateDialog(savedInstanceState)
        }

        fun update(tip: String) {
            this.loadingTip = tip
            val tipView = view?.findViewById<TextView>(R.id.loading_tip)
            val tipLayout = view?.findViewById<View>(R.id.tip_layout)
            if (tipView != null && tipLayout != null) {
                if (loadingTip.isNotEmpty()) {
                    tipView.text = loadingTip
                    tipView.visibility = View.VISIBLE
                    tipLayout.visibility = View.VISIBLE
                } else {
                    tipView.visibility = View.GONE
                }

                if (loadingTip.isEmpty() && subTip.isEmpty()) {
                    tipLayout.visibility = View.GONE
                }
            }
        }

        fun updateSubTip(subTip: String) {
            this.subTip = subTip
            val tipView = view?.findViewById<TextView>(R.id.loading_sub_tip)
            val tipLayout = view?.findViewById<View>(R.id.tip_layout)

            if (tipView != null && tipLayout != null) {
                if (subTip.isNotEmpty()) {
                    tipView.text = subTip
                    tipView.visibility = View.VISIBLE
                    tipLayout.visibility = View.VISIBLE
                } else {
                    tipView.visibility = View.GONE
                }

                if (loadingTip.isEmpty() && subTip.isEmpty()) {
                    tipLayout.visibility = View.GONE
                }
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
}