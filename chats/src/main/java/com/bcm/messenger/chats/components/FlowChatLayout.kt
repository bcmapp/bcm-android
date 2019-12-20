package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.utility.LimitQueue

class FlowChatLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        RelativeLayout(context, attrs, defStyle), RecipientModifiedListener {
    private var flowChatLayout1: ConstraintLayout
    private var flowChatLayout2: ConstraintLayout
    private var recipientImg1: IndividualAvatarView
    private var recipientImg2: IndividualAvatarView
    private var flowText1: TextView
    private var flowText2: TextView
    private var recipient1: Recipient? = null
    private var recipient2: Recipient? = null
    private var text1ShowFlag = false
    private var text2ShowFlag = false
    private var canShowNextMessage = true
    private var limitQueue = LimitQueue<AmeGroupMessageDetail>(10)

    init {
        View.inflate(context, R.layout.chats_flow_layout, this)
        flowChatLayout1 = findViewById(R.id.chats_flow_layout1)
        flowChatLayout2 = findViewById(R.id.chats_flow_layout2)
        recipientImg1 = findViewById(R.id.chats_flow_recipient1)
        recipientImg2 = findViewById(R.id.chats_flow_recipient2)
        flowText1 = findViewById(R.id.chats_flow_text1)
        flowText2 = findViewById(R.id.chats_flow_text2)
    }

    fun addMessage(message: AmeGroupMessageDetail) {
        if (canShowNextMessage) {
            canShowNextMessage = false
            if (text1ShowFlag) {
                flowChatLayout1.removeCallbacks(exitRunnable)
            } else if (text2ShowFlag) {
                flowChatLayout2.removeCallbacks(exitRunnable)
            }
            addMessageView(message)
        } else {
            limitQueue.offer(message)
        }
    }

    private fun addMessageView(message: AmeGroupMessageDetail) {
        if (!text1ShowFlag) {
            recipient1 = Recipient.from(context, Address.fromSerialized(message.senderId
                    ?: return), true)
            recipient1?.addListener(this)
            recipientImg1.setPhoto(recipient1)
            flowText1.text = message.message.content.getDescribe(message.gid)
            enter(flowChatLayout1)
            if (text2ShowFlag) {
                exit(flowChatLayout2)
            }
        } else if (!text2ShowFlag) {
            recipient2 = Recipient.from(context, Address.fromSerialized(message.senderId
                    ?: return), true)
            recipient2?.addListener(this)
            recipientImg2.setPhoto(recipient2)
            flowText2.text = message.message.content.getDescribe(message.gid)
            enter(flowChatLayout2)
            if (text1ShowFlag) {
                exit(flowChatLayout1)
            }
        }
    }

    private fun enter(layout: ConstraintLayout) {
        layout.let {
            val enterScaleX = ObjectAnimator.ofFloat(it, "scaleX", 0.9f, 1.0f)
            val enterScaleY = ObjectAnimator.ofFloat(it, "scaleY", 0.5f, 1.0f)
            val enterAlpha = ObjectAnimator.ofFloat(it, "alpha", 0.0f, 1.0f)
            val enterAnimator = AnimatorSet()

            enterAnimator.playTogether(enterScaleX, enterScaleY, enterAlpha)
            enterAnimator.duration = 500
            enterAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    super.onAnimationStart(animation)
                    it.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    postDelayed({
                        if (layout == flowChatLayout1) {
                            text1ShowFlag = true
                        } else if (layout == flowChatLayout2) {
                            text2ShowFlag = true
                        }
                        if (limitQueue.size() == 0) {
                            canShowNextMessage = true
                            layout.postDelayed(exitRunnable, 2500)
                        } else {
                            addMessageView(limitQueue.poll())
                        }
                    }, 500)

                }
            })
            enterAnimator.start()
        }
    }

    private fun exit(layout: ConstraintLayout) {
        layout.let {
            val exitScaleX = ObjectAnimator.ofFloat(it, "scaleX", 1.0f, 1.1f)
            val exitScaleY = ObjectAnimator.ofFloat(it, "scaleY", 1.0f, 1.5f)
            val exitAlpha = ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.0f)
            val exitAnimator = AnimatorSet()
            exitAnimator.playTogether(exitScaleX, exitScaleY, exitAlpha)
            exitAnimator.duration = 500
            exitAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    it.visibility = View.GONE
                    if (layout == flowChatLayout1) {
                        text1ShowFlag = false
                    } else if (layout == flowChatLayout2) {
                        text2ShowFlag = false
                    }
                }
            })
            exitAnimator.start()
        }
    }

    private val exitRunnable = Runnable {
        if (text1ShowFlag) {
            exit(flowChatLayout1)
        } else if (text2ShowFlag) {
            exit(flowChatLayout2)
        }
    }

    fun clear() {
        text1ShowFlag = false
        text2ShowFlag = false
        flowChatLayout1.visibility = View.GONE
        flowChatLayout2.visibility = View.GONE
        limitQueue.clear()
    }

    override fun onModified(recipient: Recipient) {
        if (recipient.address == recipient1?.address) {
            recipientImg1.setPhoto(recipient)
        } else if (recipient.address == recipient2?.address) {
            recipientImg2.setPhoto(recipient)
        }
    }

}
