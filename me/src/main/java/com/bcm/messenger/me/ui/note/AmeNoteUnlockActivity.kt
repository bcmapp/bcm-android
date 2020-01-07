package com.bcm.messenger.me.ui.note

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.audio.GlobalRinger
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import kotlinx.android.synthetic.main.me_note_unlock_activity.*
import java.lang.ref.WeakReference

class AmeNoteUnlockActivity : SwipeBaseActivity() {
    private lateinit var unlockAnim: Animator
    private lateinit var globalRinger: GlobalRinger
    private val scrollViewLayoutChanged = View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (note_unlock_scroll_view.getChildAt(0).height > (bottom - top)) {
            note_unlock_scroll_view.post {
                note_unlock_scroll_view.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_note_unlock_activity)



        globalRinger = GlobalRinger(this)

        val unlockAnim = ObjectAnimator.ofFloat(me_note_lock_icon, "rotation", 0.0f, 360.0f)
        this.unlockAnim = unlockAnim
        unlockAnim.duration = 3000
        unlockAnim.repeatCount = ValueAnimator.INFINITE

        note_unlock_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        me_note_unlock_do.setOnClickListener {
            val pwd = me_note_unlock_password_edit.text.toString()
            if (pwd.isNotBlank()) {

                unlockAnim.start()
                val wself = WeakReference(this@AmeNoteUnlockActivity)

                val animStart = System.currentTimeMillis()

                showUnlockDoing()

                AmeNoteLogic.getInstance().unlock(pwd) {
                    if (it) {
                        wself.get()?.pwdSucceed(System.currentTimeMillis() - animStart)
                    } else {
                        wself.get()?.showError(getString(R.string.me_note_unlock_wrong_password))
                    }
                }

            } else {
                showError(getString(R.string.me_note_unlock_enter_password))
            }
        }

        note_unlock_scroll_view.addOnLayoutChangeListener(scrollViewLayoutChanged)

        setSwipeBackEnable(false)
    }

    private fun showUnlockDoing() {
        me_note_unlock_doing.visibility = View.VISIBLE
        me_note_unlock_do.text = ""
        me_note_unlock_do.isEnabled = false
    }

    private fun showUnlockDo() {
        me_note_unlock_doing.visibility = View.GONE
        me_note_unlock_do.text = getString(R.string.common_done)
        me_note_unlock_do.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        note_unlock_scroll_view.removeOnLayoutChangeListener(scrollViewLayoutChanged)
        unlockAnim.cancel()
        globalRinger.stop()
    }

    private fun showError(error: String) {
        if (isFinishing) {
            return
        }

        showUnlockDo()

        unlockAnim.end()
        me_note_unlock_password_edit?.error = error
    }

    private fun pwdSucceed(duration: Long) {
        if (isFinishing) {
            return
        }

        val wself = WeakReference(this)

        val left = 600 - duration
        if (left > 50) {
            AmeDispatcher.mainThread.dispatch({
                wself.get()?.pwdSucceed(600)
            }, left)
            return
        }


        unlockAnim.pause()
        globalRinger.start(R.raw.note_unlock_done)
        me_note_lock_background.setBackgroundResource(R.drawable.me_note_unlock_pwd_background)


        AmeDispatcher.mainThread.dispatch({
            wself.get()?.toNoteActivity()
        }, 300)
    }

    private fun toNoteActivity() {
        if (isFinishing) {
            return
        }

        val intent = Intent(this, AmeNoteActivity::class.java)
        startBcmActivity(intent)

        finish()
    }

}