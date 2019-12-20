package com.bcm.messenger.logic

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.ui.LaunchActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 *
 * Created by wjh on 2019-12-05
 */
object DataTransferCensor : Application.ActivityLifecycleCallbacks {

    private const val TAG = "DataTransferCensor"

    private const val PREF_KEY = "pref_device_feature"

    private var mWarningFlag: Boolean = true

    init {

    }

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPaused(activity: Activity?) {
    }

    override fun onActivityResumed(activity: Activity?) {
        ALog.d(TAG, "onActivityResumed")
    }

    override fun onActivityStarted(activity: Activity?) {
        ALog.d(TAG, "onActivityStarted")
        prepareCheck()
        check(activity as? FragmentActivity)
    }

    override fun onActivityDestroyed(activity: Activity?) {
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity?) {
        ALog.d(TAG, "onActivityStopped")
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        ALog.d(TAG, "onActivityCreated")
    }

    private fun check(activity: FragmentActivity?) {

        if (RomUtil.isEmui()) {
            //ALog.i(TAG, "check, getSystemInfo: ${SystemUtils.getSystemInfo()}")
            val newFeature = hasDifferentFeature(AppContextHolder.APP_CONTEXT)
            ALog.i(TAG, "check newFeature: $newFeature")
            if (!newFeature.isNullOrEmpty()) {
                showWarning(activity) { isOk ->

                    if (isOk) {
                        mWarningFlag = false
                        SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, PREF_KEY, newFeature)

                        AmeAppLifecycle.showLoading()
                        Observable.create<Boolean> {
                            AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)?.clearAll()
                            it.onNext(true)
                            it.onComplete()
                        }.subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    AmeAppLifecycle.hideLoading()
                                    AppContextHolder.APP_CONTEXT.startActivity(Intent(AppContextHolder.APP_CONTEXT, LaunchActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                                    })

                                }, {
                                    ALog.e(TAG, "check error", it)
                                    AmeAppLifecycle.hideLoading()
                                    AmeDispatcher.mainThread.dispatch({
                                        AppUtil.exitApp()
                                    }, 300)

                                })
                    }else {
                        AppUtil.exitApp()
                    }

                }

            }
        }else {
            ALog.i(TAG, "no need check, not huawei")
        }
    }

    private fun getDeviceFeature(): String {
        return "${Build.BRAND}_${Build.MANUFACTURER}_${Build.MODEL}_${Build.BOARD}"
    }

    private fun showWarning(activity: FragmentActivity?, callback: (ok: Boolean) -> Unit) {
        mWarningFlag = true
        AmePopup.center.newBuilder()
                .withTitle("data had been transferred，please delete data")
                .withContent("app will be available after data deleted")
                .withOkTitle(AppContextHolder.APP_CONTEXT.getString(R.string.common_popup_ok))
                .withCancelTitle(AppContextHolder.APP_CONTEXT.getString(R.string.common_cancel))
                .withContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                .withCancelable(false)
                .withTopMost(true)
                .withOkListener {
                    callback.invoke(true)
                }.withCancelListener {
                    callback.invoke(false)
                }.show(activity)

    }

    private fun prepareCheck() {
        if (!hasDifferentFeature(AppContextHolder.APP_CONTEXT).isNullOrEmpty()) {
            AmePopup.center.dismiss()
        }
    }

    private fun hasDifferentFeature(context: Context): String? {
        val feature = getDeviceFeature()
        val lastFeature = SuperPreferences.getStringPreference(context, PREF_KEY)
        if (lastFeature.isNullOrEmpty()) { //if null, just store current feature
            SuperPreferences.setStringPreference(context, PREF_KEY, feature)
            mWarningFlag = false
            return null
        }
        if (feature != lastFeature && mWarningFlag && RomUtil.isEmui()) {
            return feature
        }
        return null
    }
}