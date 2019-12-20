package com.bcm.messenger.common.ui.popup.centerpopup

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_result_popup_layout_new.*
import java.lang.ref.WeakReference

/**
 * 自定义的吐司类
 * Created by bcm.social.01 on 2018/5/31.
 */
class AmeResultPopup : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "AmeResultPopup"
        private const val DURATION_POPUP = 2000L //popup展示时长
    }

    private var popup: PopupWindow? = null
    private var attachActivity: WeakReference<Activity>? = null

    class PopConfig {
        var result: String? = null
        var succeed: Boolean? = true
        var dark: Boolean = false
        var dismiss: () -> Unit = {}
    }

    /**
     * 展示成功的吐司
     */
    fun succeed(activity: FragmentActivity?, result: String?) {
        succeed(activity, result, true) {}
    }

    /**
     * 展示失败的吐司
     */
    fun failure(activity: FragmentActivity?, result: String?) {
        failure(activity, result, true) {}
    }

    /**
     * 展示成功的吐司
     */
    fun succeed(activity: FragmentActivity?, result: String?, dismiss: () -> Unit) {
        succeed(activity, result, true, dismiss)
    }

    /**
     * 展示失败的吐司
     */
    fun failure(activity: FragmentActivity?, result: String?, dismiss: () -> Unit) {
        failure(activity, result, true, dismiss)
    }

    /**
     * 展示成功的吐司
     */
    fun succeed(activity: FragmentActivity?, result: String?, dark: Boolean) {
        succeed(activity, result, dark) {}
    }

    /**
     * 展示失败的吐司
     */
    fun failure(activity: FragmentActivity?, result: String?, dark: Boolean) {
        failure(activity, result, dark) {}
    }

    /**
     * 展示无图标的吐司提示
     */
    fun notice(activity: FragmentActivity?, result: String?, dark: Boolean = true) {
        notice(activity, result, dark) {}
    }

    /**
     * 展示无图标的吐司提示
     */
    fun notice(activity: FragmentActivity?, result: String?, dark: Boolean = true, dismiss: () -> Unit) {
        val config = PopConfig()
        config.succeed = null
        config.dark = dark
        config.result = result
        config.dismiss = dismiss
        show(activity, config)
    }

    /**
     * 展示成功的吐司
     */
    fun succeed(activity: FragmentActivity?, result: String?, dark: Boolean, dismiss: () -> Unit) {
        val config = PopConfig()
        config.succeed = true
        config.dark = dark
        config.result = result
        config.dismiss = dismiss
        show(activity, config)
    }

    /**
     * 展示失败的吐司
     */
    fun failure(activity: FragmentActivity?, result: String?, dark: Boolean, dismiss: () -> Unit) {
        if (null == result) {
            return
        }

        val config = PopConfig()
        config.succeed = false
        config.dark = dark
        config.result = result
        config.dismiss = dismiss
        show(activity, config)
    }

    private fun show(activity: FragmentActivity?, config: PopConfig) {

        /**
         * 展示Toast之前先检查服务状态码缓存，是否存在需要特殊展示的错误
         */
        fun checkServerCodeState(config: PopConfig): PopConfig {

            if (config.succeed == false) {
                if (ServerCodeUtil.pullLastErrorCode() == ServerCodeUtil.CODE_LOW_VERSION) {
                    config.result = AppContextHolder.APP_CONTEXT.getString(R.string.common_too_low_version_notice)
                }else {
                    ALog.d(TAG, "pullLastErrorCode is low version code, but PopConfig succeed is true")
                }
            }
            return config
        }

        dismiss()
        if (activity != null && !activity.isFinishing) {
            activity.application.registerActivityLifecycleCallbacks(this)
            attachActivity = WeakReference(activity)

            val popup = PopupWindow()
            popup.resultPopup = this
            this.popup = popup
            popup.config = checkServerCodeState(config)

            try {
                popup.show(activity.supportFragmentManager, activity.javaClass.simpleName)
            } catch (ex: Exception) {
                ALog.e("AmeResultPopup", "show error", ex)
                config.dismiss.invoke()
            }
        }else {
            config.dismiss.invoke()
        }
    }

    /**
     * 隐藏
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
        var resultPopup: AmeResultPopup? = null
        var config: PopConfig? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.common_result_popup_layout_new, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            updateUI()
        }

        override fun onDestroy() {
            super.onDestroy()
            resultPopup?.dismissInner(this)
        }

        override fun onStart() {
            super.onStart()
            val dialog = dialog
            if (dialog != null) {
                val window = getDialog()?.window
                val windowParams = window!!.attributes
                windowParams.dimAmount = 0.0f
                windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                window.attributes = windowParams
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.setWindowAnimations(R.style.CommonAlphaWindowAnimation)
            }
        }

        /**
         * 根据配置更新UI
         */
        private fun updateUI() {
            common_result_root.postDelayed({
                resultPopup?.dismiss()
            },  DURATION_POPUP)

            val config = this.config
            if (null != config) {
                if (config.dark) {
                    common_result_dark_view.visibility = View.VISIBLE
                    common_result_light_view.visibility = View.GONE
                    updateDark(config)
                } else {
                    common_result_dark_view.visibility = View.GONE
                    common_result_light_view.visibility = View.VISIBLE
                    updateLight(config)
                }
            }
        }

        private fun updateDark(config: PopConfig) {
            common_result_root.setBackgroundResource(R.color.common_color_transparent)
            when {
                config.succeed == true -> {
                    common_result_dark_text.text = "✔︎ ${config.result ?: ""}".trim()
                }
                config.succeed == false -> {
                    common_result_dark_text.text = "✘ ${config.result ?: ""}".trim()
                }
            }
        }

        private fun updateLight(config: PopConfig) {
            when {
                config.succeed == true -> {
                    common_result_light_text.text =  "✔︎ ${config.result ?: ""}".trim()
                }
                config.succeed == false -> {
                    common_result_light_text.text = "✘ ${config.result ?: ""}".trim()
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