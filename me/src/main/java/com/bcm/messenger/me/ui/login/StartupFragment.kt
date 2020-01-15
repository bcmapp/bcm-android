package com.bcm.messenger.me.ui.login

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_startup_fragment.*
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECKeyPair

class StartupFragment : AbsRegistrationFragment() {

    private val TAG = "StartupFragment"
    private var keyPair: ECKeyPair? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_startup_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()

        if (arguments?.getBoolean(RegistrationActivity.CREATE_ACCOUNT_ID, false) == true) {
            createAccount()
        } else {
            showLogoAnimation()
        }

        SuperPreferences.setTablessIntroductionFlag(AppContextHolder.APP_CONTEXT)
    }

    private fun initView() {
        startup_create.setOnClickListener {
            createAccount()
        }

        startup_scan.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                    .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT)
                    .navigation(activity, RegistrationActivity.REQUEST_CODE_SCAN_QR_LOGIN)
        }

        startup_import.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                    .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT)
                    .navigation(activity, RegistrationActivity.REQUEST_CODE_SCAN_QR_IMPORT)
        }

        startup_keybox.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.APP_DEV_SETTING).navigation(context)
        }
    }

    private fun createAccount() {
        AmePopup.tipLoading.show(this.activity)
        Observable.create(ObservableOnSubscribe<ECKeyPair> {
            try {
                it.onNext(Curve.generateKeyPair())
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    keyPair = it
                    if (keyPair != null) {
                        AmeLoginLogic.queryChallengeTarget(it) { target ->
                            AmePopup.tipLoading.dismiss()
                            if (target != null) {
                                activity?.apply {
                                    val f = GenerateKeyFragment()
                                    val arg = Bundle()
                                    arg.putInt("action", 1)
                                    arg.putString("target", target)
                                    f.arguments = arg
                                    val transaction = supportFragmentManager.beginTransaction()
                                            .replace(R.id.register_container, f, "generate_key_2")
                                    if (arguments?.getBoolean(RegistrationActivity.CREATE_ACCOUNT_ID, false) != true) {
                                        transaction.addToBackStack("generate_key_2")
                                    }
                                    transaction.commitAllowingStateLoss()
                                    f.setKeyPair(it)
                                }
                            }
                        }
                    } else {
                        AmePopup.tipLoading.dismiss()
                    }
                }, {
                    AmePopup.tipLoading.dismiss()
                    ALog.e(TAG, it.toString())
                })
    }

    private fun showLogoAnimation() {
        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(startup_icon, "scaleX", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_icon, "scaleY", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_icon, "translationY", 300f, 0f))
                    .with(ObjectAnimator.ofFloat(startup_icon, "alpha", 0f, 1f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    showBlinkAnimation()
                }
            })
        }.start()
    }

    private fun showBlinkAnimation() {
        Handler().postDelayed({
            (startup_icon?.drawable as? AnimationDrawable)?.start()
            showButtonAnimation()
        }, 500)
    }

    private fun showButtonAnimation() {
        startup_create?.visibility = View.VISIBLE
        startup_scan?.visibility = View.VISIBLE

        if (!AppUtil.isReleaseBuild()) {
            startup_keybox?.visibility = View.VISIBLE
        }

        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(startup_create, "scaleX", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_create, "scaleY", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_scan, "scaleX", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_scan, "scaleY", 0f, 1.1f, 0.9f, 1f))
        }.start()
    }
}