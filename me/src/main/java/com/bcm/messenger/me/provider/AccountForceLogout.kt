package com.bcm.messenger.me.provider

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

object AccountForceLogout {
    private val TAG = "AccountForceLogout"
    @SuppressLint("CheckResult")
    fun handleForceLogout(accountContext: AccountContext, info: String?) {
        if (!accountContext.isLogin) {
            return
        }

        ALog.i(TAG, "handleForceLogout 1")

        val finish = {
            accountForceLogoutFinish(accountContext, info ?: "")
        }

        ALog.i(TAG, "handleForceLogout 2")
        Observable.create<Boolean> {
            ALog.i(TAG, "handleForceLogout 3")
            AmeModuleCenter.login().quit(accountContext, clearHistory = false, withLogOut = false)
            it.onNext(true)
            it.onComplete()
        }.subscribeOn(AmeDispatcher.singleScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    ALog.e(TAG, "handleForceLogout", it)
                }
                .subscribe({ finish() }, { finish() })

        AmePopup.center.dismiss()
    }


    private fun accountForceLogoutFinish(accountContext: AccountContext, info: String) {
        ALog.i(TAG, "accountForceLogoutFinish 1")
        if (!AMELogin.isLogin) {
            ALog.i(TAG, "accountForceLogoutFinish 2")
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_DESTROY)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK))
                    .putString(ARouterConstants.PARAM.PARAM_CLIENT_INFO, info)
                    .startBcmActivity(accountContext)
        } else {
            ALog.i(TAG, "accountForceLogoutFinish 3")
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_DESTROY)
                    .putString(ARouterConstants.PARAM.PARAM_CLIENT_INFO, info)
                    .startBcmActivity(accountContext)
        }
    }

    @SuppressLint("CheckResult")
    fun handleTokenExpire(accountContext: AccountContext, activity: Activity) {
        if (!accountContext.isLogin) {
            return
        }

        val finish = {
            accountForceLogoutFinish(accountContext, "")
        }

        Observable.create<Boolean> {
            ALog.i(TAG, "handleTokenExpire 1")
            if (AMELogin.isLogin) {
                AmeModuleCenter.login().quit(accountContext, clearHistory = false, withLogOut = false)
            } else {
                throw java.lang.Exception("not login")
            }

            it.onNext(true)
            it.onComplete()
        }.delaySubscription(3000, TimeUnit.MILLISECONDS, AmeDispatcher.singleScheduler)
                .subscribeOn(AmeDispatcher.singleScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    ALog.e(TAG, "handleTokenExpire", it)
                }
                .subscribe({ finish() }, { finish() })

    }

    @SuppressLint("CheckResult")
    fun handleAccountGone(accountContext: AccountContext, activity: Activity) {
        val finish = {
            MeConfirmDialog.showConfirm(activity, activity.getString(R.string.me_destroy_account_confirm_title),
                    activity.getString(R.string.me_destroy_account_warning_notice), activity.getString(R.string.me_destroy_account_confirm_button)) {
                if (!AMELogin.isLogin) {
                    if (AmeModuleCenter.login().accountSize() > 0) {
                        ALog.i(TAG, "routeToLogin")
                        BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_SWITCHER)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                .navigation()
                    } else {
                        ALog.i(TAG, "routeToRegister")
                        BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                .navigation()
                    }
                }
            }
        }

        Observable.create<Boolean> {
            AmeModuleCenter.login().quit(accountContext, false)
            AmeLoginLogic.accountHistory.deleteAccount(accountContext.uid)
            it.onNext(true)
            it.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.mainScheduler)
                .subscribe({ finish() }, { finish() })
    }
}