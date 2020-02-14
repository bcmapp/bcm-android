package com.bcm.messenger.me.ui.login

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.me.ui.base.AbsRegistrationFragment
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.HexUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.me_generate_key_fragment_2.*
import kotlinx.android.synthetic.main.me_key_load_anim_layout.*
import org.whispersystems.libsignal.ecc.ECKeyPair
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

class GenerateKeyFragment : AbsRegistrationFragment() {

    private var keyPair: ECKeyPair? = null
    private var genTime:Long = 0L
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

        key_link_next?.setOnClickListener {
            activity?.apply {
                val key = keyPair

                if (null != key && nonce >= 0) {
                    val f = SetPasswordFragment()
                    f.initParams(nonce, key)
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.register_container, f, "set_password_fragment")
                            .addToBackStack("set_password_fragment")
                            .commit()
                }
            }
        }

        account_key_title_bar.setListener(object :CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.onBackPressed()
            }

            override fun onClickRight() {
                UserModuleImp().gotoBackupTutorial()
            }
        })

        target = arguments?.getString("target")

        showCircleAnimation()

        val keyPair = this.keyPair
        if (keyPair != null && stopped) {
            key_load_anim_layout.visibility = View.VISIBLE
            val pubArray = keyPair.publicKey.serialize()
            anim_text_one?.setText(HexUtil.toString(pubArray))
            val privateArray = keyPair.privateKey.serialize()
            anim_text_two?.setText(HexUtil.toString(privateArray))

            key_link_next.visibility = View.VISIBLE
            key_link_next.scaleX = 1.0f
            key_link_next.scaleY = 1.0f
            account_key_title_bar?.visibility = View.VISIBLE

            anim_key_gen_date.text = getString(R.string.me_str_generation_key_date, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(genTime)))
        }
    }

    fun setKeyPair(keyPair: ECKeyPair) {
        this.keyPair = keyPair
        this.genTime = System.currentTimeMillis()
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
        val context = this.context
        if (context != null)
            hash_generated_running_text.setTextColor(context.getAttrColor(R.attr.common_text_secondary_color))
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

        if (startup_key == null) return
        anim_key_gen_date.text = getString(R.string.me_str_generation_key_date, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(genTime)))

        key_load_anim_layout.visibility = View.VISIBLE
        AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(key_load_anim_layout, "translationY", 1f*AppContextHolder.APP_CONTEXT.getScreenHeight(), 1000f).apply {
                duration = 400
            }).before(AnimatorSet().apply {
                play(ObjectAnimator.ofFloat(startup_key, "translationY", startup_key.bottom*-1f))
                        .with(ObjectAnimator.ofFloat(key_load_anim_layout, "translationY", 1000f, 0f))
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
                                val keyPair = this@GenerateKeyFragment.keyPair
                                if (null != keyPair) {
                                    val pubArray = keyPair.publicKey.serialize()
                                    anim_text_one?.setText(HexUtil.toString(pubArray))
                                    val privateArray = keyPair.privateKey.serialize()
                                    anim_text_two?.setText(HexUtil.toString(privateArray))
                                }
                                key_link_next.visibility = View.VISIBLE
                                account_key_title_bar?.visibility = View.VISIBLE
                            }
                        })
                    }
            )

        }.addToList(animatorSetList).start()
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

    fun AnimatorSet.addToList(list: ArrayList<AnimatorSet>): AnimatorSet {
        list.add(this)
        return this
    }

    override fun onDestroyView() {
        stopped = true
        animatorSetList.forEach {
            if (it.isRunning) it.cancel()
        }
        super.onDestroyView()
    }
}