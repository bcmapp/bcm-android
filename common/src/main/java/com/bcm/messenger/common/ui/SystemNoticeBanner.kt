package com.bcm.messenger.common.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 系统推送通知banner
 * Created by wjh on 2018/10/29
 */
class SystemNoticeBanner : ConstraintLayout {

    private lateinit var mCloseView: ImageView
    private lateinit var mTextView: TextView
    private lateinit var mImageView: ImageView

    private lateinit var mWebView: NestedScrollWebView

    private var initSucceed = false

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        try {
            LayoutInflater.from(context).inflate(R.layout.common_system_banner_layout, this)
        } catch (e: Throwable) {
            ALog.e("SystemNoticeBanner", e)
            visibility = View.GONE
            return
        }
        initSucceed = true
        initView()
    }

    private fun initView() {
        mCloseView = findViewById(R.id.banner_close_iv)
        mTextView = findViewById(R.id.banner_content_tv)
        mImageView = findViewById(R.id.banner_content_iv)
        mWebView = findViewById(R.id.banner_content_wv)

        this.setBackgroundColor(Color.parseColor("#F2F3F4"))
        this.visibility = View.GONE

        mCloseView.setOnClickListener {
            this.visibility = View.GONE
            TextSecurePreferences.setStringPreference(context, TextSecurePreferences.SYS_PUSH_MESSAGE + "_" + AMESelfData.uid + "_" + AmePushProcess.SystemNotifyData.TYPE_BANNER, "")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEvent(event: AmePushProcess.SystemNotifyData.BannerData) {
        ALog.d("SystemNoticeBanner", "onEvent: ${event.type}")
        if (!initSucceed) {
            return
        }

        try {
            when (event.type) {
                "text" -> {
                    mTextView.visibility = View.VISIBLE
                    mTextView.text = event.content
                    mImageView.visibility = View.GONE
                    mWebView.visibility = View.GONE
                }
                "image" -> {
                    mImageView.visibility = View.VISIBLE
                    GlideApp.with(context).load(event.content).diskCacheStrategy(DiskCacheStrategy.ALL).into(mImageView)
                    mTextView.visibility = View.GONE
                    mWebView.visibility = View.GONE
                }
            }
        }catch (ex: Exception) {
            ALog.e("SystemNoticeBanner", "onEvent error", ex)
        }
        setOnClickListener {
            try {
                val action = event.action
                if(action.isNotEmpty()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action)))
                }
            }catch (ex: Exception) {
                ALog.e("SystemNoticeDialog", "TextAlert action handle fail", ex)
            }
        }
        this.visibility = View.VISIBLE
    }
}