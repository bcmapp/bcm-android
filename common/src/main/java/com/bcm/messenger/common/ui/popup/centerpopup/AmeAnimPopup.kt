package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.DialogFragment
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.annotation.StyleRes
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.setTranslucentStatus
import kotlinx.android.synthetic.main.common_anim_popup_base_layout.*
import java.lang.ref.WeakReference

class AmeAnimPopup: Application.ActivityLifecycleCallbacks {
    companion object {
        private val inst = AmeAnimPopup()
        fun instance(): AmeAnimPopup {
            return inst
        }
    }



    class Builder {
        private var viewCreator: ViewCreator? = null
        private var cancelable: Boolean = false
        private var dismiss = {}
        @StyleRes
        private var style:Int = R.style.CommonBottomAnimPopupWindow

        fun withViewCreator(viewCreator: ViewCreator): Builder {
            this.viewCreator = viewCreator
            return this
        }

        fun withCancelable(cancelable:Boolean): Builder {
            this.cancelable = cancelable
            return this
        }

        fun withDismiss(dismiss:()->Unit): Builder {
            this.dismiss = dismiss
            return this
        }

        fun withStyle(@StyleRes style:Int): Builder {
            this.style = style
            return this
        }

        fun show(activity: Activity?) {
            val viewCreator = this.viewCreator
            if (viewCreator != null) {
                instance().show(activity, viewCreator, cancelable, style, dismiss)
            }
        }
    }

    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null

    fun newBuilder(): Builder {
        return Builder()
    }

    private fun show(activity: Activity?, viewCreator: ViewCreator, cancelable: Boolean, @StyleRes style:Int, dismissCallback:()->Unit) {
        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.viewCreator = viewCreator
            popup.isCancelable = cancelable
            popup.dismissCallback = dismissCallback
            popup.style = style
            popup.animPopup = this
            this.popup = popup

            try {
                AmeDispatcher.mainThread.dispatch({
                    activity.hideKeyboard()
                    popup.show(activity.fragmentManager, activity.javaClass.simpleName)

                }, 200)//bug

            } catch (ex: Throwable) {
                ALog.e("AmeCenterPopup", "show error", ex)
            }
        }
    }

    /**
     * 
     */
    fun dismiss() {
        dismissInner(popup)
    }

    private fun dismissInner(window: PopupWindow?) {
        try {
            if (popup == window) {
                attachActivity?.get()?.application?.unregisterActivityLifecycleCallbacks(this)
                attachActivity = null

                if (window?.isVisible == true) {
                    window.dismiss()
                }

                window?.dismissCallback?.invoke()
                popup = null
            }
        } catch (ex: Exception) {
            ALog.e("AmeCenterPopup", "dismissInner error", ex)
        }
    }

    interface ViewCreator {
        fun onCreateView(parent: ViewGroup): View?
        fun onDetachView()
    }

    class PopupWindow : DialogFragment() {
        var animPopup: AmeAnimPopup? = null
        var viewCreator: ViewCreator? = null
        var dismissCallback:()->Unit = {}

        @StyleRes
        var style:Int = R.style.CommonBottomPopupWindow

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_anim_popup_base_layout, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewCreator?.onCreateView(anim_popup_root)
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            dialog.window.setTranslucentStatus()
            dialog.window.statusBarColor = Color.WHITE
            dialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        override fun onDestroy() {
            super.onDestroy()
            animPopup?.dismissInner(this)
            viewCreator?.onDetachView()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = Dialog(activity, style)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.window.setBackgroundDrawableResource(android.R.color.transparent)    //Dialog
            dialog.window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window.setWindowAnimations(style)

            val windowParams = dialog.window.attributes
            windowParams.dimAmount = 0.0f
            windowParams.gravity = Gravity.CENTER
            windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
            dialog.window.attributes = windowParams

            dialog.window.decorView.setPadding(0, 0, 0, 0)
            return dialog
        }

        override fun dismiss() {
            animPopup = null
            viewCreator = null
            super.dismiss()
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