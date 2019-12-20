package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import kotlinx.android.synthetic.main.common_download_popup_layout.*
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference

/**
 * 自定义的downloading窗口
 * Created by zjl on 2018/8/27.
 */
class AmeDownloadPopup : Application.ActivityLifecycleCallbacks {
    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null
    private val TAG = "AmeDownloadPopup"
    private var isShow = false

    class PopConfig

    fun show(activity: FragmentActivity?, config: PopConfig) {
        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.loadingPopup = this
            popup.config = config
            this.popup = popup

            try {
                popup.show(activity.supportFragmentManager, activity.javaClass.simpleName)
                isShow = true
            } catch (ex: Exception) {
                ALog.e(TAG, "show error", ex)
            }
        }

    }

    fun show(activity: FragmentActivity?) {
        val config = PopConfig()
        show(activity, config)
    }

    fun isShow(): Boolean {
        return isShow
    }


    /**
     * 隐藏
     */
    fun dismiss() {
        dismissInner(popup)
        isShow = false
    }

    fun dismissInner(window: PopupWindow?) {
        try {
            if (popup == window && window != null) {
                attachActivity?.get()?.application?.unregisterActivityLifecycleCallbacks(this)
                attachActivity = null

                try {
                    if (!window.isDetached) {
                        window.dismiss()
                    }
                } catch (e: Throwable) {
                    ALog.e(TAG, e)
                }
                popup = null
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "dismissInner error", ex)
        }
    }

    class PopupWindow : DialogFragment() {
        var loadingPopup: AmeDownloadPopup? = null
        var config: PopConfig? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_download_popup_layout, container, false)
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            pd_close.setOnClickListener {
                dismiss()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            loadingPopup?.dismissInner(this)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            activity?.let {
                val dialog = Dialog(it, R.style.CommonLoadingStyle)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCanceledOnTouchOutside(false)

                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)    //设置Dialog背景透明效果
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