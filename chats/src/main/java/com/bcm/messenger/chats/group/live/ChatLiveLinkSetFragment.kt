package com.bcm.messenger.chats.group.live

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.util.analyseYoutubeUrl
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.showKeyboard
import com.bcm.messenger.common.video.VideoPlayer
import com.google.android.exoplayer2.Player
import kotlinx.android.synthetic.main.chats_fragment_live_link_set.*

class ChatLiveLinkSetFragment : Fragment() {
    private var nextImpl: LinkSetImpl? = null
    private var animator: ObjectAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_live_link_set, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
    }

    fun initView() {
        chats_live_next.setOnClickListener {
            it.hideKeyboard()
            val url = chats_link_tv_link.text.toString()
            when {
                url.isEmpty() -> {
                    context?.let { ctx ->
                        ToastUtil.show(ctx, ctx.getString(R.string.chats_live_settings_empty_url))
                    }
                }
                !url.startsWith("http") -> {
                    context?.let { ctx ->
                        ToastUtil.show(ctx, ctx.getString(R.string.chats_live_settings_not_url))
                    }
                }
                else -> {
                    val uri = Uri.parse(url)
                    if (uri.host == "www.youtube.com" || uri.host == "m.youtube.com" || uri.host == "youtu.be") {
                        startAnimator()
                        analyseYoutubeUrl(url) { success, realUrl, duration ->
                            if (success) {
                                nextImpl?.next(url, realUrl, duration)
                            } else {
                                animator?.cancel()
                                chats_link_tv_warning.visibility = View.VISIBLE
                                chats_live_next.isEnabled = false
                            }
                        }
                    } else {
                        analyseNormalUrl(url)
                    }
                }
            }
        }

        chats_link_tv_link.requestFocus()
        chats_link_tv_link.showKeyboard()

        chats_link_tv_link.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrBlank()) {
                    chats_link_tv_link.hideClearButton()
                    chats_live_next.setImageResource(R.drawable.chats_48_next_grey)
                    chats_live_next.isEnabled = false
                } else {
                    chats_link_tv_link.showClearButton()
                    chats_live_next.setImageResource(R.drawable.chats_48_next)
                    chats_live_next.isEnabled = true
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    fun setLinkSetImpl(nextImpl: LinkSetImpl) {
        this.nextImpl = nextImpl
    }

    private fun analyseNormalUrl(url: String) {
        val player = VideoPlayer(context)
        player.setStreamingVideoSource(url, false)
        player.setVideoStateChangeListener(object : VideoPlayer.VideoStateChangeListener() {
            override fun onChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    animator?.cancel()
                    if (player.duration > 0L) {
                        chats_link_tv_warning.visibility = View.GONE
                        nextImpl?.next(url, url, player.duration)
                    } else {
                        chats_link_tv_warning.visibility = View.VISIBLE
                        chats_live_next.isEnabled = false
                    }
                } else if (state == Player.STATE_IDLE) {
                    animator?.cancel()
                    chats_link_tv_warning.visibility = View.VISIBLE
                    chats_live_next.isEnabled = false
                }
                // Fix leaks
                player.stopVideo()
                player.cleanup()
            }
        })
        startAnimator()
    }

    private fun startAnimator() {
        chats_live_next.setImageResource(R.drawable.chats_48_loading_black)
        animator = ObjectAnimator.ofFloat(chats_live_next, "rotation", 0f, 360f).setDuration(500)
        animator?.repeatCount = -1
        animator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator?) {
                chats_live_next?.setImageResource(R.drawable.chats_48_next_grey)
                chats_live_next?.rotation = 0f
            }
        })
        animator?.start()
    }
}