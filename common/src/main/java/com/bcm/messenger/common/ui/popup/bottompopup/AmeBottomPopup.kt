package com.bcm.messenger.common.ui.popup.bottompopup

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_bottom_popup_base_layout.*
import java.lang.ref.WeakReference

/**
 *
 * Created by bcm.social.01 on 2018/5/31.
 */
class AmeBottomPopup : Application.ActivityLifecycleCallbacks {
    companion object {
        private val inst = AmeBottomPopup()
        fun instance(): AmeBottomPopup {
            return inst
        }
    }

    class PopupItem {
        companion object {
            val SEP = PopupItem("", 0) {}
            val CLR_BLUE = Color.parseColor("#379BFF")
            val CLR_RED = Color.parseColor("#FF3737")
            const val CLR_DEFAULT = 0
        }

        val text: String
        @ColorInt val textColor: Int
        val action: (v: View) -> Unit

        constructor(text: String) {
            this.textColor = CLR_DEFAULT
            this.text = text
            this.action = {}
        }


        constructor(text: String, action: (v: View) -> Unit) {
            this.textColor = CLR_DEFAULT
            this.text = text
            this.action = action
        }

        constructor(text: String, @ColorInt textColor: Int, action: (v: View) -> Unit) {
            this.text = text
            this.textColor = textColor
            this.action = action
        }
    }

    class Builder {
        private val config = PopConfig("", "", 0, {}, true, ArrayList(), {}, null)

        fun withTitle(title: String): Builder {
            config.title = title
            return this
        }

        fun withDoneTitle(title: String): Builder {
            config.doneTitle = title
            return this
        }

        fun withDoneTextColor(@ColorInt color: Int): Builder {
            config.doneTextColor = color
            return this
        }

        fun withDoneAction(action: (v: View) -> Unit): Builder {
            config.doneAction = action
            return this
        }

        fun withPopItem(popupItem: PopupItem): Builder {
            config.popList.add(popupItem)
            return this
        }

        fun withCancelable(cancelable: Boolean): Builder {
            config.cancelable = cancelable
            return this
        }

        fun withDismissListener(listener: () -> Unit): Builder {
            config.dismissListener = listener
            return this
        }

        fun withCustomView(customViewCreator: CustomViewCreator): Builder {
            config.viewCreator = customViewCreator
            return this
        }

        fun show(activity: FragmentActivity?) {
            activity?:return
            if (config.doneTitle.isNotEmpty()) {
                withPopItem(PopupItem.SEP)
            }
            instance().show(activity, config)
        }
    }

    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null

    fun newBuilder(): Builder {
        return Builder()
    }

    private fun show(activity: FragmentActivity, config: PopConfig) {
        dismiss()
        if (!activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.config = config
            popup.centerPopup = this
            this.popup = popup

            AmeDispatcher.mainThread.dispatch({
                activity.hideKeyboard()
                try {
                    popup.show(activity.supportFragmentManager, activity.javaClass.simpleName)
                } catch (e: Throwable) {
                    ALog.e("AmeBottomPopup", "show", e)
                }
            }, 200)//bug
        }
    }

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

                window?.config?.dismissListener?.invoke()
                popup = null
            }
        } catch (ex: Throwable) {
            ALog.e("AmeCenterPopup", "dismissInner error", ex)
        }
    }

    interface CustomViewCreator {
        fun onCreateView(parent: ViewGroup): View?
        fun onDetachView()
    }

    class PopupWindow : DialogFragment() {
        var config: PopConfig? = null
        var centerPopup: AmeBottomPopup? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_bottom_popup_base_layout, container, false)
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
            centerPopup?.dismissInner(this)
            config?.viewCreator?.onDetachView()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            activity?.let {
                val dialog = Dialog(it, R.style.CommonBottomPopupWindow)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)    //Dialog
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setWindowAnimations(R.style.CommonBottomPopupWindow)

                    val windowParams = window.attributes
                    windowParams.dimAmount = 0.0f
                    windowParams.gravity = Gravity.BOTTOM
                    windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                    windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
                    window.attributes = windowParams

                    window.decorView.setPadding(0, 0, 0, 0)
                }
                return dialog
            }
            return super.onCreateDialog(savedInstanceState)
        }

        private fun updateUI() {
            val config = this.config ?: return
            isCancelable = config.cancelable

            val customView = config.viewCreator?.onCreateView(common_popup_custom_view)
            if (null != customView) {
                common_popup_custom_view.visibility = View.VISIBLE
            } else {
                common_popup_custom_view.visibility = View.GONE
            }

            if (config.title.isNotEmpty()) {
                popup_title.text = config.title
                popup_title.visibility = View.VISIBLE
                popup_title_line.visibility = View.VISIBLE
            } else {
                popup_title.visibility = View.GONE
                popup_title_line.visibility = View.GONE
            }

            val inflater = LayoutInflater.from(activity)
            if (config.popList.isNotEmpty()) {
                for (item in config.popList) {
                    fillCellItem(inflater, popup_items_layout, item)
                }
                popup_items_layout.visibility = View.VISIBLE
            } else {
                popup_items_layout.visibility = View.GONE
            }


            if (config.doneTitle.isNotEmpty()) {
                done_action.text = config.doneTitle
                if (config.doneTextColor > 0) {
                    done_action.setTextColor(config.doneTextColor)
                }
                done_action.setOnClickListener {
                    dismiss()
                    config.doneAction(it)
                }
                done_action.visibility = View.VISIBLE
            } else {
                done_action.visibility = View.GONE
            }
        }

        private fun fillCellItem(inflater: LayoutInflater, parent: LinearLayout, popupItem: PopupItem): View {
            if (popupItem != PopupItem.SEP) {
                val view = inflater.inflate(R.layout.common_bottom_popup_item_layout, parent, false)
                val cellView = view as BottomPopupCellView
                val color = if (popupItem.textColor != PopupItem.CLR_DEFAULT) {
                    popupItem.textColor
                } else {
                    inflater.context.getAttrColor(R.attr.common_text_main_color)
                }
                cellView.setText(popupItem.text, color)
                cellView.setOnClickListener {
                    dismiss()
                    popupItem.action(it)
                }

                cellView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                parent.addView(cellView)
                return cellView
            } else {
                val sepView = View(inflater.context)
                sepView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        5.dp2Px())
                sepView.setBackgroundColor(inflater.context.getAttrColor(R.attr.common_item_line_color))
                parent.addView(sepView)
                return sepView
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


    data class PopConfig(var title: String,
                         var doneTitle: String,
                         var doneTextColor: Int,
                         var doneAction: (v: View) -> Unit,
                         var cancelable: Boolean,
                         var popList: MutableList<PopupItem>,
                         var dismissListener: () -> Unit,
                         var viewCreator: CustomViewCreator?)
}