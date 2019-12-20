package com.bcm.messenger.chats.mediapreview

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import com.bcm.messenger.chats.R
import kotlinx.android.synthetic.main.chats_media_more_view.view.*


/**
 *
 * Created by Kin on 2018/10/31.
 */
class MediaMoreView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val TAG = "MediaMoreView"

    private var listener: MoreViewActionListener? = null

    private var mDefaultOptionVisible = true//默认控制组件是否可见

    private var bottomShowAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 0f)

    private var bottomHideAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 1f)

    interface MoreViewActionListener {
        fun clickDownload()
        fun clickMediaBrowser()
        fun moreOptionVisibilityChanged(isShow: Boolean)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chats_media_more_view, this)
        initView()
    }

    fun initView() {
        chats_media_download_flow_btn.setOnClickListener {
            if (chats_media_download_flow_btn_mask.visibility == View.VISIBLE) {
                return@setOnClickListener
            }
            listener?.clickDownload()
        }

        chats_media_pool_btn.setOnClickListener {
            listener?.clickMediaBrowser()
        }
    }

    fun setMoreViewListener(listener: MoreViewActionListener) {
        this.listener = listener
    }

    fun displaySpinning() {
        if (chats_more_progress.visibility != View.VISIBLE) {
            chats_more_progress.visibility = View.VISIBLE
            chats_more_progress.startAnim()
        }
//        mDefaultOptionVisible = false
//        hideDefaultOptionLayout()
    }


    fun displayDefault() {
        if (chats_more_progress.visibility == View.VISIBLE) {
            chats_more_progress.stopAnim()
            chats_more_progress.visibility = View.GONE
        }
//        mDefaultOptionVisible = true
//        showDefaultOptionLayout()
    }


    fun displayNull() {
        if (chats_more_progress.visibility == View.VISIBLE) {
            chats_more_progress.stopAnim()
            chats_more_progress.visibility = View.GONE
        }
        mDefaultOptionVisible = false
        hideDefaultOptionLayout()
    }

    fun switchOptionLayout() {
        if (mDefaultOptionVisible) {
            hideDefaultOptionLayout()
        } else {
            showDefaultOptionLayout()
        }
    }

    fun showDefaultOptionLayout(useAnim: Boolean = true) {
        if (chats_default_option_layout.visibility != View.VISIBLE) {
            chats_default_option_layout.visibility = View.VISIBLE
            listener?.moreOptionVisibilityChanged(true)
            if (useAnim) {
                chats_default_option_layout.clearAnimation()
                bottomShowAnim.duration = 300
                chats_default_option_layout.startAnimation(bottomShowAnim)
            }
            mDefaultOptionVisible = true
        }
    }

    fun hideDefaultOptionLayout(useAnim: Boolean = true) {
        if (chats_default_option_layout.visibility == View.VISIBLE) {
            listener?.moreOptionVisibilityChanged(false)
            if (useAnim) {
                chats_default_option_layout.clearAnimation()
                bottomHideAnim.duration = 300
                bottomHideAnim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        chats_default_option_layout.visibility = View.GONE
                    }
                    override fun onAnimationStart(animation: Animation?) {}
                })
                chats_default_option_layout.startAnimation(bottomHideAnim)
            } else {
                chats_default_option_layout.visibility = View.GONE
            }
            mDefaultOptionVisible = false
        }
    }

    fun disableDownload() {
        chats_media_download_flow_btn_mask.visibility = View.VISIBLE
    }

    fun enableDownload() {
        chats_media_download_flow_btn_mask.visibility = View.GONE
    }
}