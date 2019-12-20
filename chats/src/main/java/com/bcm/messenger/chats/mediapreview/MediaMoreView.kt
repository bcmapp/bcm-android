package com.bcm.messenger.chats.mediapreview

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import com.google.zxing.Result
import kotlinx.android.synthetic.main.chats_media_more_view.view.*


/**
 *
 * Created by Kin on 2018/10/31.
 */
class MediaMoreView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val TAG = "MediaMoreView"

    private var listener: MoreViewActionListener? = null

    private var mDefaultOptionVisible = true
    private var mScanResult: Array<out Result>? = null

    private var topShowAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, -1f, Animation.RELATIVE_TO_SELF, 0f)

    private var bottomShowAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 0f)

    private var topHideAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, -1f)

    private var bottomHideAnim = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 1f)

    interface MoreViewActionListener {
        fun clickDownload()
        fun clickForward()
        fun clickDelete()
        fun clickMediaBrowser()
        fun clickScanQRCode()
        fun clickClose()
        fun moreOptionVisibilityChanged(isShow: Boolean)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chats_media_more_view, this)
        initView()
    }

    fun initView() {
        chats_media_more_btn.setOnClickListener {
            if (chats_more_option_layout.visibility == View.VISIBLE) {
                hideMoreOptionLayout()
                if (mDefaultOptionVisible) {
                    showDefaultOptionLayout()
                }
            } else {
                showMoreOptionLayout()
                if (mDefaultOptionVisible) {
                    hideDefaultOptionLayout()
                }
            }
        }

        chats_media_forward_btn.setOnClickListener {
            listener?.clickForward()
        }

        chats_media_download_flow_btn.setOnClickListener {
            listener?.clickDownload()
        }

        chats_media_download_btn.setOnClickListener {
            listener?.clickDownload()
        }

        chats_media_delete_btn.setOnClickListener {
            listener?.clickDelete()
        }
        chats_media_browser_btn.setOnClickListener {
            listener?.clickMediaBrowser()
        }

        chats_media_scan_btn.setOnClickListener {
            doQrDiscernAction(context, mScanResult?.get(0) ?: return@setOnClickListener)
            listener?.clickScanQRCode()
        }

        close_btn.setOnClickListener {
            listener?.clickClose()
        }
    }

    fun setMoreViewListener(listener: MoreViewActionListener) {
        this.listener = listener
    }

    fun hideCloseButton() {
        close_btn.visibility = View.GONE
    }


    fun displayMoreOption() {
        showBarLayout()
        showMoreOptionLayout()
        if (mDefaultOptionVisible) {
            hideDefaultOptionLayout()
        }
    }


    fun hideMoreOption() {
        showBarLayout()
        hideMoreOptionLayout()
        if (mDefaultOptionVisible) {
            showDefaultOptionLayout()
        }
    }


    fun displaySpinning() {
        if (chats_more_progress.visibility != View.VISIBLE) {
            chats_more_progress.visibility = View.VISIBLE
            chats_more_progress.spin()
        }
        mDefaultOptionVisible = false
        showBarLayout()
        hideDefaultOptionLayout()
        hideMoreOptionLayout()
    }


    fun displayDefault() {
        if (chats_more_progress.visibility == View.VISIBLE) {
            chats_more_progress.stopSpinning()
            chats_more_progress.visibility = View.GONE
        }
        mDefaultOptionVisible = true
        showBarLayout()
        showDefaultOptionLayout()
        hideMoreOptionLayout()
    }


    fun displayNull() {
        if (chats_more_progress.visibility == View.VISIBLE) {
            chats_more_progress.stopSpinning()
            chats_more_progress.visibility = View.GONE
        }
        mDefaultOptionVisible = false
        showBarLayout()
        hideDefaultOptionLayout()
        hideMoreOptionLayout()
    }

    private fun showBarLayout(useAnim: Boolean = true) {
        if (chats_more_bar_layout.visibility != View.VISIBLE) {
            chats_more_bar_layout.visibility = View.VISIBLE

            if (useAnim) {
                chats_more_bar_layout.clearAnimation()
                topShowAnim.duration = 300
                chats_more_bar_layout.startAnimation(topShowAnim)
            }
        }
    }

    private fun hideBarLayout(useAnim: Boolean = true) {
        if (chats_more_bar_layout.visibility == View.VISIBLE) {
            if (useAnim) {
                chats_more_bar_layout.clearAnimation()
                topHideAnim.duration = 300
                topHideAnim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        chats_more_bar_layout.visibility = View.GONE
                    }
                    override fun onAnimationStart(animation: Animation?) {}
                })
                chats_more_bar_layout.startAnimation(topHideAnim)
            } else {
                chats_more_bar_layout.visibility = View.GONE
            }
        }
    }

    private fun showMoreOptionLayout(useAnim: Boolean = true) {
        if (chats_more_option_layout.visibility != View.VISIBLE) {
            listener?.moreOptionVisibilityChanged(true)
            chats_more_option_layout.visibility = View.VISIBLE
            if (useAnim) {
                chats_more_option_layout.clearAnimation()
                bottomShowAnim.duration = 300
                chats_more_option_layout.startAnimation(bottomShowAnim)
            }
        }
    }

    private fun hideMoreOptionLayout(useAnim: Boolean = true) {
        if (chats_more_option_layout.visibility == View.VISIBLE) {
            if (useAnim) {
                chats_more_option_layout.clearAnimation()
                bottomHideAnim.duration = 300
                bottomHideAnim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        chats_more_option_layout.visibility = View.GONE
                        listener?.moreOptionVisibilityChanged(false)
                    }
                    override fun onAnimationStart(animation: Animation?) {}
                })
                chats_more_option_layout.startAnimation(bottomHideAnim)
            } else {
                chats_more_option_layout.visibility = View.GONE
                listener?.moreOptionVisibilityChanged(false)
            }
        }
    }


    private fun showDefaultOptionLayout(useAnim: Boolean = true) {
        if (chats_default_option_layout.visibility != View.VISIBLE) {
            chats_default_option_layout.visibility = View.VISIBLE
            if (useAnim) {
                chats_default_option_layout.clearAnimation()
                bottomShowAnim.duration = 300
                chats_default_option_layout.startAnimation(bottomShowAnim)
            }
        }
    }


    private fun hideDefaultOptionLayout(useAnim: Boolean = true) {
        if (chats_default_option_layout.visibility == View.VISIBLE) {
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
        }
    }


    private fun doQrDiscernAction(context: Context, result: Result) {
        ALog.d(TAG, "doQrDiscernAction result: ${result.text}")
        val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
        provider.discernScanData(context, result.text)
    }
}