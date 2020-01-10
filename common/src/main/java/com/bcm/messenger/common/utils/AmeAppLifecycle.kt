package com.bcm.messenger.common.utils

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeResultPopup
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference

/**
 * 
 * Created by bcm.social.01 on 2018/6/30.
 */
object AmeAppLifecycle : Application.ActivityLifecycleCallbacks {
    private const val TAG = "ActivityLifecycle"

    //activity
    private var weakRefCurrentStartActivity: WeakReference<Activity?>? = null
    private var weakRefCurrentFocusActivity: WeakReference<Activity?>? = null

    //
    private var popConfig: AmeCenterPopup.PopConfig? = null
    private var needShowLoading: Boolean = false

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * activity
     */
    fun current(): Activity? {
        return weakRefCurrentFocusActivity?.get() ?: return weakRefCurrentStartActivity?.get()
    }

    fun show(title: String, okListener: () -> Unit) {
        val config = AmeCenterPopup.PopConfig()
        config.title = title
        config.ok = okListener
        config.okTitle = AppContextHolder.APP_CONTEXT.getString(R.string.common_popup_ok)

        config.cancelable = false
        val activity = current()
        if (activity is AppCompatActivity && !activity.isFinishing) {
            AmePopup.center.show(activity, config)
        } else {
            popConfig = config
        }
    }

    fun show(title: String, okTitle:String?, cancelTitle:String, okListener: () -> Unit) {
        val config = AmeCenterPopup.PopConfig()
        config.title = title
        config.ok = okListener
        config.okTitle = okTitle
        config.cancelTitle = cancelTitle

        val activity = current()
        if (activity is AppCompatActivity && !activity.isFinishing) {
            AmePopup.center.show(activity, config)
        } else {
            popConfig = config
        }
    }

    /**
     * toast
     */
    fun succeed(result: String, dark: Boolean) {
        val activity = current()
        if (activity is AppCompatActivity) {
            AmePopup.result.succeed(activity, result, dark)
        }
    }

    /**
     * toast
     */
    fun succeed(result: String, dark: Boolean, dismissCallback: () -> Unit) {
        val activity = current()
        if (activity is AppCompatActivity) {
            AmePopup.result.succeed(activity, result, dark, dismissCallback)
        }
    }

    /**
     * toast
     */
    fun failure(result: String, dark: Boolean) {
        val activity = current()
        if (activity is AppCompatActivity) {
            AmePopup.result.failure(activity, result, dark)
        }
    }

    /**
     * toast
     */
    fun failure(result: String, dark: Boolean, dismissCallback: () -> Unit) {
        val activity = current()
        if (activity is AppCompatActivity) {
            AmePopup.result.failure(activity, result, dark, dismissCallback)
        }
    }

    /**
     * loading
     */
    fun showLoading() {
        val activity = current()
        if (activity is AppCompatActivity && !activity.isFinishing) {
            AmePopup.loading.show(activity)
        } else {
            needShowLoading = true
        }

    }

    /**
     * loading
     */
    fun hideLoading() {
        val activity = current()
        if (activity is AppCompatActivity && !activity.isFinishing) {
            AmePopup.loading.dismiss()
        } else {
            needShowLoading = false
        }
    }

    fun showProxyGuild(tryProxy:()->Unit) {
        try {
            AlertDialog.Builder(current()?:return)
                    .setTitle(R.string.common_service_unreachable)
                    .setMessage(R.string.common_service_unreachable_tip)
                    .setPositiveButton(R.string.common_service_unreachable_try_proxy) { _, _ ->
                        tryProxy()
                    }
                    .setNegativeButton(R.string.common_ignore, null)
                    .setCancelable(true)
                    .show() 
        } catch (e:Throwable) {
            ALog.e(TAG, "showProxyGuild", e)
        }
        
    }

    override fun onActivityPaused(activity: Activity?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityPaused")

        if (activity == weakRefCurrentFocusActivity?.get()) {
            weakRefCurrentFocusActivity = null
        }
    }

    override fun onActivityResumed(activity: Activity?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityResumed")
        weakRefCurrentFocusActivity = WeakReference(activity)
        val config = popConfig
        popConfig = null

        if (activity is FragmentActivity) {
            activity.supportFragmentManager.fragments.forEach {
                if (it is AmeResultPopup.PopupWindow) {
                    try {
                        activity.supportFragmentManager.beginTransaction().remove(it).commit()
                    } catch (tr: Throwable) {
                        tr.printStackTrace()
                    }
                }
            }
        }

        if (null != config && activity is AppCompatActivity) {
            AmePopup.center.show(activity, config)
        }

        if (needShowLoading) {
            needShowLoading = false
            if (activity is AppCompatActivity) {
                AmePopup.loading.show(activity)
            }
        }
    }

    override fun onActivityStarted(activity: Activity?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityStarted")
        weakRefCurrentStartActivity = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityDestroyed")

        if (weakRefCurrentStartActivity?.get() == activity) {
            weakRefCurrentStartActivity = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
        ALog.i(TAG, "${activity?.localClassName} onActivitySaveInstanceState")
    }

    override fun onActivityStopped(activity: Activity?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityStopped")
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        ALog.i(TAG, "${activity?.localClassName} onActivityCreated")
        weakRefCurrentStartActivity = WeakReference(activity)
    }
}