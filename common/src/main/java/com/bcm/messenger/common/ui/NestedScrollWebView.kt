package com.bcm.messenger.common.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.core.view.MotionEventCompat
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import kotlin.math.max

/**
 * 浏览器组件
 * Created by zjl on 2018/7/3.
 */
class NestedScrollWebView : WebView, NestedScrollingChild {

    private val TAG = "NestedScrollWebView"

    private var mLastMotionY: Int = 0

    private val mScrollOffset = IntArray(2)
    private val mScrollConsumed = IntArray(2)

    private var mNestedYOffset: Int = 0

    private lateinit var mChildHelper: NestedScrollingChildHelper

    private lateinit var progressView: WebProgressView

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun init() {

        //初始化进度条
        progressView = WebProgressView(context)
        progressView.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4.dp2Px())
        progressView.setColor(getColor(R.color.common_blue_3))
        progressView.setProgress(10)
        //把进度条加到Webview中
        addView(progressView)

        mChildHelper = NestedScrollingChildHelper(this)
        isNestedScrollingEnabled = true

        val webSettings = settings

        webSettings.javaScriptEnabled = true

        //设置自适应屏幕，两者合用
        webSettings.useWideViewPort = true  //将图片调整到适合webview的大小
        webSettings.loadWithOverviewMode = true // 缩放至屏幕的大小

        webSettings.displayZoomControls = false //隐藏原生的缩放控件

        webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN //支持内容重新布局
        webSettings.supportMultipleWindows()  //多窗口
        // webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);  //关闭webview中缓存

        webSettings.setNeedInitialFocus(true) //当webview调用requestFocus时为webview设置节点
        webSettings.javaScriptCanOpenWindowsAutomatically = true //支持通过JS打开新窗口
        webSettings.loadsImagesAutomatically = true  //支持自动加载图片

        webSettings.defaultTextEncodingName = "UTF-8"

        webSettings.allowFileAccess = false  //设置可以访问文件
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = false

        // 设置可以支持缩放
        webSettings.setSupportZoom(true)
        // // 设置出现缩放工具
        webSettings.builtInZoomControls = true

        // 自适应屏幕
        webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        //缓存相关
        webSettings.setAppCacheEnabled(true)
        webSettings.domStorageEnabled = true

        //开启定位
        webSettings.setGeolocationEnabled(true)

        WebView.setWebContentsDebuggingEnabled(false)

        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                ALog.e(TAG, "onReceivedError: $failingUrl")
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                ALog.e(TAG, "onReceivedError: ${request.url}")
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                ALog.d(TAG, "网页重写地址：" + request.url)
                try {
                    val host = request.url.host
                    val scheme = request.url.scheme
                    if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                        return AppUtil.checkInvalidAddressV4(host)
                    }
                } catch (ex: Exception) {
                    ALog.e("网页重加载失败", ex)
                }

                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                ALog.d(TAG, "网页重写地址：$url")
                if (TextUtils.isEmpty(url)) {
                    return true
                }

                val uri = Uri.parse(url)
                try {
                    val host = uri.host
                    val scheme = uri.scheme
                    if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                        if (AppUtil.checkInvalidAddressV4(host)) {
                            return false
                        } else {
                            return false
                        }
                    }
                } catch (ex: Exception) {
                    ALog.e("网页重加载失败", ex)
                }
                return super.shouldOverrideUrlLoading(view, url)
            }
        }

        webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    //加载完毕进度条消失
                    progressView.visibility = View.GONE
                } else {
                    //更新进度
                    progressView.visibility = View.VISIBLE
                    progressView.setProgress(newProgress)
                }
                super.onProgressChanged(view, newProgress)
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return super.getDefaultVideoPoster() ?: return BitmapFactory.decodeResource(AppContextHolder.APP_CONTEXT.resources, R.drawable.common_web_shortcut)
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }catch (ex: Exception) {
                ALog.e(TAG, "web download fail", ex)
            }
        }
    }

    /**
     * 处理拦截的内部链接
     * @param context
     * @param uri
     * @return
     */
    fun handleOverrideUrl(context: Context, uri: Uri): Boolean {
        try {
            val host = uri.host
            val scheme = uri.scheme
            if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                return false
            }else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } catch (ex: Exception) {
            Logger.e("网页重加载失败", ex)
        }

        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var result = false

        val trackedEvent = MotionEvent.obtain(event)

        val action = MotionEventCompat.getActionMasked(event)

        if (action == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0
        }

        val y = event.y.toInt()

        event.offsetLocation(0f, mNestedYOffset.toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mLastMotionY = y
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                result = super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = mLastMotionY - y

                if (dispatchNestedPreScroll(0, deltaY, mScrollConsumed, mScrollOffset)) {
                    deltaY -= mScrollConsumed[1]
                    trackedEvent.offsetLocation(0f, mScrollOffset[1].toFloat())
                    mNestedYOffset += mScrollOffset[1]
                }

                val oldY = scrollY
                mLastMotionY = y - mScrollOffset[1]
                val newScrollY = max(0, oldY + deltaY)
                deltaY -= newScrollY - oldY
                if (dispatchNestedScroll(0, newScrollY - deltaY, 0, deltaY, mScrollOffset)) {
                    mLastMotionY -= mScrollOffset[1]
                    trackedEvent.offsetLocation(0f, mScrollOffset[1].toFloat())
                    mNestedYOffset += mScrollOffset[1]
                }
                if (mScrollConsumed[1] == 0 && mScrollOffset[1] == 0) {
                    trackedEvent.recycle()
                    result = super.onTouchEvent(trackedEvent)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopNestedScroll()
                result = super.onTouchEvent(event)
            }
        }
        return result
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        mChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return mChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

}