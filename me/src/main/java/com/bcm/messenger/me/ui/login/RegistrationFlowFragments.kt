package com.bcm.messenger.me.ui.login

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.HexUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_generate_key_fragment_2.*
import kotlinx.android.synthetic.main.me_key_load_anim_layout.*
import kotlinx.android.synthetic.main.me_layout_relogin.*
import kotlinx.android.synthetic.main.me_startup_fragment.*
import org.whispersystems.libsignal.ecc.ECKeyPair
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ReloginFragment : AbsRegistrationFragment() {

    private val TAG = "ReloginFragment"
    private var keyPair: ECKeyPair? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_layout_relogin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        val uid = arguments?.getString(RegistrationActivity.RE_LOGIN_ID) ?: ""
        if (!TextUtils.isEmpty(uid)) {
            fetchProfile(uid)
        }
    }

    fun initView() {
        relogin_avatar.setOnClickListener {
            relogin_layout.visibility = View.GONE
            gotoReloginInputPin()
        }

        create_account.setOnClickListener {
            AmePopup.tipLoading.show(this.activity)
            Observable.create(ObservableOnSubscribe<ECKeyPair> {
                try {
                    it.onNext(IdentityKeyUtil.generateIdentityKeys(context))
                } catch (ex: Exception) {
                    it.onError(ex)
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
                                        val f = GenerateKeyFragment2()
                                        val arg = Bundle()
                                        arg.putInt("action", 1)
                                        arg.putString("target", target)
                                        f.arguments = arg
                                        supportFragmentManager.beginTransaction()
                                                .replace(R.id.register_container, f, "generate_key_2")
                                                .addToBackStack("generate_key_2")
                                                .commitAllowingStateLoss()
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

        switch_account.setOnClickListener {
            activity?.apply {
                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_keybox)) {
                            if (AmeLoginLogic.getAccountList().isNotEmpty()) {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).navigation()
                            } else {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX_GUIDE).navigation()
                            }
                        })
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_str_scan_to_login)) {
                            try {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                                        .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT)
                                        .navigation(activity, RegistrationActivity.REQUEST_CODE_SCAN_QR_LOGIN)


                            } catch (ex: Exception) {
                                ALog.e(TAG, "start ScanActivity error", ex)
                            }
                        })
                        .withDoneTitle(getString(R.string.common_cancel))
                        .show(this)
            }
        }
    }

    private fun gotoReloginInputPin() {
        activity?.apply {
            val f = LoginVerifyPinFragment()
            val arg = Bundle()
            arg.putString(RegistrationActivity.RE_LOGIN_ID, arguments?.getString(RegistrationActivity.RE_LOGIN_ID)
                    ?: "")
            f.arguments = arg
            supportFragmentManager.beginTransaction()
                    .replace(R.id.register_container, f)
                    .addToBackStack("sms")
                    .commit()
        }
    }

    private fun fetchProfile(uid: String) {
        val account = AmeLoginLogic.accountHistory.getAccount(uid)

        val realUid: String? = account?.uid
        val name: String? = account?.name
        val avatar: String? = account?.avatar

        ALog.d(TAG, "fetchProfile uid: $uid, name: $name, avatar: $avatar")
        if (!realUid.isNullOrEmpty()) {

            val weakThis = WeakReference(this)
            Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                try {
                    val recipient = Recipient.from(getAccountContext(), realUid, false)
                    val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                        avatar
                    }else {
                        null
                    }
                    recipient.setProfile(recipient.profileKey, name, finalAvatar)
                    emitter.onNext(recipient)

                } finally {
                    emitter.onComplete()
                }

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ recipient ->
                        weakThis.get()?.relogin_avatar?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
                    }, {

                    })

        }
    }
}

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
        startup_keybox.setOnClickListener {
            if (AmeLoginLogic.getAccountList().isNotEmpty()) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).navigation()
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX_GUIDE).navigation()
            }
        }
    }

    private fun createAccount() {
        AmePopup.tipLoading.show(this.activity)
        Observable.create(ObservableOnSubscribe<ECKeyPair> {
            try {
                it.onNext(IdentityKeyUtil.generateIdentityKeys(context))
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
                                    val f = GenerateKeyFragment2()
                                    val arg = Bundle()
                                    arg.putInt("action", 1)
                                    arg.putString("target", target)
                                    f.arguments = arg
                                    supportFragmentManager.beginTransaction()
                                            .replace(R.id.register_container, f, "generate_key_2")
                                            .addToBackStack("generate_key_2")
                                            .commitAllowingStateLoss()
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
        startup_keybox?.visibility = View.VISIBLE
        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(startup_create, "scaleX", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_create, "scaleY", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_scan, "scaleX", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_scan, "scaleY", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_keybox, "scaleX", 0f, 1.1f, 0.9f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_keybox, "scaleY", 0f, 1.1f, 0.9f, 1f))
        }.start()
    }
}

fun AnimatorSet.addToList(list: ArrayList<AnimatorSet>): AnimatorSet {
    list.add(this)
    return this
}

class GenerateKeyFragment2 : AbsRegistrationFragment() {

    private var action = -1
    private var keyPair: ECKeyPair? = null
    private var nonce: Long = 0
    private val CHALLENGE_KEY_LENGTH = 32
    private var target: String? = null
    private var stopped = false

    private val animatorSetList = arrayListOf<AnimatorSet>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_generate_key_fragment_2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startup_stop.setOnClickListener {
            startup_stop.isEnabled = false
            hideButtonAnimation()
        }

        target = arguments?.getString("target")

        showCircleAnimation()
    }

    fun setKeyPair(keyPair: ECKeyPair) {
        this.keyPair = keyPair
    }

    private fun showCircleAnimation() {
        if (stopped) {
            return
        }
        AnimatorSet().apply {
            duration = 50
            play(ObjectAnimator.ofFloat(startup_circle_number, "scaleX", 5f))
                    .with(ObjectAnimator.ofFloat(startup_circle_number, "scaleY", 5f))
        }.addToList(animatorSetList).start()
        AnimatorSet().apply {
            duration = 1000
            play(ObjectAnimator.ofFloat(startup_circle_out, "scaleX", 1f, 50f, 50f))
                    .with(ObjectAnimator.ofFloat(startup_circle_out, "scaleY", 1f, 50f, 50f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    startup_circle_out?.visibility = View.GONE
                    showCircleAnimation2()
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showCircleAnimation2() {
        if (stopped) {
            return
        }
        startup_circle_in?.visibility = View.VISIBLE
        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(startup_circle_number, "scaleX", 5f, 0.95f, 1.05f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_circle_number, "scaleY", 5f, 0.95f, 1.05f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "scaleX", 5f, 0.95f, 1.05f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "scaleY", 5f, 0.95f, 1.05f, 1f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "translationY", 50f, 0f, 0f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    showHashCalculation()
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showHashCalculation() {
        if (stopped) {
            return
        }
        hash_generated_text.visibility = View.VISIBLE
        hash_generated_target_text.visibility = View.VISIBLE
        hash_generated_running_text.visibility = View.VISIBLE
        hash_result_text.visibility = View.VISIBLE
        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(hash_generated_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_text, "translationY", 50f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_generated_text?.postDelayed({
                        showTarget()
                    }, 1000)
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showTarget() {
        if (stopped) {
            return
        }
        hash_generated_text.text = context?.resources?.getText(R.string.me_login_generate_target)
        AnimatorSet().apply {
            duration = 200
            play(ObjectAnimator.ofFloat(hash_generated_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_text, "translationY", 50f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_generated_target_text?.postDelayed({
                        showTargetText()
                    }, 500)
                }
            })
        }.addToList(animatorSetList).start()
        runNumberAnimation()
    }

    private fun showTargetText() {
        if (stopped) {
            return
        }
        AmeDispatcher.mainThread.dispatch {
            if (target != null) {
                val starSize = CHALLENGE_KEY_LENGTH - target!!.length
                val spannable = StringBuilder(target ?: "")
                for (i in 0 until starSize) {
                    spannable.append("*")
                }
                hash_generated_target_text.text = spannable
                AnimatorSet().apply {
                    duration = 200
                    play(ObjectAnimator.ofFloat(hash_generated_target_text, "alpha", 0f, 1f))
                            .with(ObjectAnimator.ofFloat(hash_generated_target_text, "translationY", 50f, 0f))
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            hash_generated_target_text?.postDelayed({
                                showResult()
                            }, 500)
                        }
                    })
                }.addToList(animatorSetList).start()
            }
        }
        runNumberAnimation()
    }

    private fun showResult() {
        if (stopped) {
            return
        }
        hash_result_text.text = context?.resources?.getText(R.string.me_login_generate_result)
        AnimatorSet().apply {
            duration = 200
            play(ObjectAnimator.ofFloat(hash_result_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_result_text, "translationY", 50f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_result_text?.postDelayed({
                        showResultText()
                    }, 500)
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showResultText() {
        if (stopped) {
            return
        }
        AnimatorSet().apply {
            duration = 500
            play(ObjectAnimator.ofFloat(hash_generated_running_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_running_text, "translationY", 50f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {

                    keyPair?.let {
                        AmeLoginLogic.calcChallengeRegister(it) { target, hash, clientNotice, isCalculate ->

                            nonce = clientNotice
                            hash_generated_running_text?.text = hash.substring(0, CHALLENGE_KEY_LENGTH)

                            AnimatorSet().apply {
                                duration = 100
                                play(ObjectAnimator.ofFloat(hash_generated_running_text, "alpha", 0f, 1f))
                                        .with(ObjectAnimator.ofFloat(hash_generated_running_text, "alpha", 0f, 1f))
                                        .with(ObjectAnimator.ofFloat(hash_generated_running_text, "translationY", 25f, 0f))
                            }.addToList(animatorSetList).start()

                            if (!isCalculate) {
                                hash_generated_success_img?.postDelayed({
                                    showResultCalculated()
                                }, 1000)
                            }
                        }
                    }

                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showResultCalculated() {
        if (stopped) {
            return
        }
        if (context != null)
            hash_generated_running_text.setTextColor(getColorCompat(R.color.common_color_white))
        AnimatorSet().apply {
            duration = 200
            play(ObjectAnimator.ofFloat(hash_generated_running_text, "alpha", 0f, 1f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_generated_running_text?.postDelayed({
                        showTickImg()
                    }, 800)
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showTickImg() {
        if (stopped) {
            return
        }
        hash_generated_success_img.scaleX = 0f
        hash_generated_success_img.scaleY = 0f
        hash_generated_success_img.visibility = View.VISIBLE
        AnimatorSet().apply {
            duration = 100
            play(ObjectAnimator.ofFloat(hash_generated_success_img, "scaleX", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_success_img, "scaleY", 0f, 1f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_generated_text.postDelayed({ showAccountKeyGenerate() }, 900)
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showAccountKeyGenerate() {
        if (stopped) {
            return
        }
        hash_generated_running_text.visibility = View.GONE
        hash_result_text.visibility = View.GONE
        hash_generated_success_img.visibility = View.GONE
        hash_generated_target_text.visibility = View.GONE
        hash_generated_text.text = context?.resources?.getText(R.string.me_login_generate_account_key)
        AnimatorSet().apply {
            duration = 1000
            play(ObjectAnimator.ofFloat(hash_generated_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_text, "alpha", 0f, 1f))
                    .with(ObjectAnimator.ofFloat(hash_generated_text, "translationY", 50f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    hash_generated_text?.postDelayed({
                        hideButtonAnimation()
                    }, 2000)
                }
            })
        }.addToList(animatorSetList).start()
        stopNumberAnimation()
    }

    private fun runNumberAnimation() {
        if (stopped) {
            return
        }
        val d = startup_circle_number?.drawable
        if (d is AnimationDrawable) {
            d.start()
        }
    }

    private fun stopNumberAnimation() {
        val d = startup_circle_number?.drawable
        if (d is AnimationDrawable) {
            d.stop()
        }
    }

    private fun hideButtonAnimation() {
        if (stopped) {
            return
        }
        AnimatorSet().apply {
            duration = 300
            play(ObjectAnimator.ofFloat(startup_stop, "alpha", 1f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    startup_stop?.visibility = View.GONE
                    hideTextAnimation()
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun hideTextAnimation() {
        if (stopped) {
            return
        }
        AnimatorSet().apply {
            duration = 300
            play(ObjectAnimator.ofFloat(hash_generated_text, "alpha", 1f, 0f))
                    .with(ObjectAnimator.ofFloat(hash_generated_text, "translationY", 0f, 50f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    startup_tip_text?.visibility = View.GONE
                    showKeyAnimation()
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showKeyAnimation() {
        if (stopped) {
            return
        }
        startup_key?.visibility = View.VISIBLE
        AnimatorSet().apply {
            duration = 1000
            play(ObjectAnimator.ofFloat(startup_circle_number, "scaleX", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_circle_number, "scaleY", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_circle_number, "alpha", 1f, 0f))
                    .with(ObjectAnimator.ofFloat(startup_circle_number, "translationY", 0f, -100f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "scaleX", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "scaleY", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "alpha", 1f, 0f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "translationY", 0f, -100f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "translationY", 0f, 50f))
                    .with(ObjectAnimator.ofFloat(startup_circle_in, "translationY", 0f, -100f))
                    .with(ObjectAnimator.ofFloat(startup_key, "translationY", 0f, -500f))
                    .with(ObjectAnimator.ofFloat(startup_key, "scaleX", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_key, "scaleY", 1f, 0.15f))
                    .with(ObjectAnimator.ofFloat(startup_key, "translationY", 0f, -500f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    showKeyLoadAnimLayout()
                }
            })
        }.addToList(animatorSetList).start()
    }

    private fun showKeyLoadAnimLayout() {
        if (stopped) {
            return
        }
        val height = AppContextHolder.APP_CONTEXT.getScreenHeight() - AppContextHolder.APP_CONTEXT.getStatusBarHeight()
        if (startup_key == null) return
        anim_key_gen_date.text = getString(R.string.me_str_generation_key_date, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis())))
        AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(key_load_anim_layout, "translationY", 0f, -1000f).apply {
                duration = 400
            }).before(AnimatorSet().apply {
                play(ObjectAnimator.ofFloat(startup_key, "translationY", -500f, 0f - startup_key.bottom))
                        .with(ObjectAnimator.ofFloat(key_load_anim_layout, "translationY", -1000f, 0f - height))
                duration = 1500
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        startLetterFallAnim()
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        startShowNextAnim()
                    }
                })
            })
        }.addToList(animatorSetList).start()
    }

    private fun startShowNextAnim() {
        if (stopped) {
            return
        }
        key_link_next?.setOnClickListener {
            activity?.apply {
                val key = keyPair

                if (null != key && nonce >= 0) {
                    val f = SetPasswordFragment()
                    f.initParams(nonce, key)
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.register_container, f, "set_password_fragment")
//                            .addToBackStack("generate_key_fragment")
                            .commit()
                }
            }
        }

        AnimatorSet().apply {
            duration = 1000
            playSequentially(
                    AnimatorSet().apply {
                        play(ObjectAnimator.ofFloat(account_key_anim_tips, "translationY", 40f, 0f).setDuration(300))
                                .with(ObjectAnimator.ofFloat(account_key_anim_tips, "alpha", 0f, 1f))
                                .before(ObjectAnimator.ofFloat(key_link_next, "scaleX", 0f, 1.0f, 0.8f, 0.9f))
                                .before(ObjectAnimator.ofFloat(key_link_next, "scaleY", 0f, 1.0f, 0.8f, 0.9f))
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                val keyPair = this@GenerateKeyFragment2.keyPair
                                if (null != keyPair) {
                                    val pubArray = keyPair.publicKey.serialize()
                                    anim_text_one?.setText(HexUtil.toString(pubArray))
                                    val privateArray = keyPair.privateKey.serialize()
                                    anim_text_two?.setText(HexUtil.toString(privateArray))
                                }
                                account_info_btn?.visibility = View.VISIBLE
                                startup_back_btn?.visibility = View.VISIBLE
                            }
                        })
                    }
            )

        }.addToList(animatorSetList).start()
        account_info_btn?.setOnClickListener {
            UserModuleImp().gotoBackupTutorial()
        }
        startup_back_btn?.setOnClickListener {
            activity?.onBackPressed()
        }
    }

    @SuppressLint("CheckResult")
    private fun startLetterFallAnim() {
        if (stopped) {
            return
        }
        var ketStr = getString(R.string.me_default_private_key).substring(0, 40) + getString(R.string.me_default_private_key).substring(0, 40)

        val keyPair = this.keyPair
        if (null != keyPair) {
            val pub = HexUtil.toString(keyPair.publicKey.serialize())
            val private = HexUtil.toString(keyPair.privateKey.serialize())

            ketStr = pub.substring(0, min(pub.length, 40))
            ketStr += private.substring(0, min(private.length, 40))
        }

        val location = IntArray(2)
        val strLen = ketStr.length.toLong()
        var animEndX: Float
        var animEndY: Float
        Observable.interval(30, TimeUnit.MILLISECONDS)
                .take(strLen)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    anim_letter_tv?.text = ""
                }
                .subscribe {
                    val index = it.toInt()
                    anim_letter_tv?.text = ketStr[index].toString()
                    val width = AppUtil.getScreenWidth(AppContextHolder.APP_CONTEXT) / 2
                    anim_text_one?.getLocationOnScreen(location)
                    animEndY = (location[1] - 150f)
                    animEndX = if (index < (strLen / 2)) {
                        (location[0] + (anim_text_one?.width ?: 0)).toFloat()
                    } else {
                        (location[0] + (anim_text_two?.width ?: 0)).toFloat()
                    }
                    animEndX -= width
                    val y = ObjectAnimator.ofFloat(anim_letter_tv, "translationY", 0f, animEndY)
                    val x = if (index < (strLen / 2)) {
                        ObjectAnimator.ofFloat(anim_letter_tv, "translationX", 0f, animEndX)
                    } else {
                        ObjectAnimator.ofFloat(anim_letter_tv, "translationX", 0f, animEndX)
                    }

                    y.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            if (index < (strLen / 2)) {
                                anim_text_one?.setText(ketStr.subSequence(0, it.toInt()))
                            } else {
                                anim_text_two?.setText(ketStr.subSequence((strLen / 2).toInt(), it.toInt()))
                            }
                        }
                    })
                    AnimatorSet().apply {
                        duration = 25
                        playTogether(x, y, ObjectAnimator.ofFloat(anim_letter_tv, "alpha", 0f, 1f))
                    }.addToList(animatorSetList).start()
                }
    }

    override fun onDestroyView() {
        stopped = true
        animatorSetList.forEach {
            if (it.isRunning) it.cancel()
        }
        super.onDestroyView()
    }
}