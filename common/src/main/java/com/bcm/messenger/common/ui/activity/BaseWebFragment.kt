package com.bcm.messenger.common.ui.activity

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.api.BcmJSInterface
import com.bcm.messenger.common.ui.NestedScrollWebView
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog

/**
 * web fragment
 * Created by wjh on 2019-09-21
 */
open class BaseWebFragment : Fragment() {

    interface OnWebActionListener {
        fun onPageLoad(finished: Boolean, url: String?, favicon: Bitmap?)
    }

    private val TAG = "BaseWebFragment"
    private var mWeb: NestedScrollWebView? = null
    private var mListener: OnWebActionListener? = null
    private var mJSInterface = BcmJSInterface()

    override fun onDestroyView() {
        super.onDestroyView()
        mWeb?.settings?.builtInZoomControls = true
        mWeb?.visibility = View.GONE
        var delay = ViewConfiguration.getZoomControlsTimeout()
        if (delay < 0) {
            delay = 0
        }
        AmeDispatcher.mainThread.dispatch({
            try {
                mWeb?.destroy()
            } catch (ex: Exception) {
            }

        }, delay)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        try {
            mWeb = NestedScrollWebView(activity ?: container?.context ?: AppContextHolder.APP_CONTEXT).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

        } catch (ex: Resources.NotFoundException) {
            mWeb = NestedScrollWebView(AppContextHolder.APP_CONTEXT).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "onCreateView error", ex)
        }
        return mWeb
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity?.let {
            init(mWeb ?: return)
            load(it.intent.getStringExtra(ARouterConstants.PARAM.WEB_URL))
        }
    }

    override fun onResume() {
        super.onResume()
        mWeb?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mWeb?.onPause()
    }

    protected fun getWebView(): NestedScrollWebView? {
        return mWeb
    }

    protected open fun init(web: NestedScrollWebView) {
        web.webViewClient = object : WebViewClient() {

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                ALog.w(TAG, "onReceivedError url: ${view.url}, error: $description")
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                ALog.w(TAG, "onReceivedError url: ${view.url}, error: ${error.description}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                mListener?.onPageLoad(true, url, null)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                mListener?.onPageLoad(false, url, favicon)
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                ALog.d(TAG, "：" + request.url)
                try {
                    val host = request.url.host
                    val scheme = request.url.scheme
                    ALog.d(TAG, "：${request.url}, host: $host, scheme: $scheme, part: ${request.url.path}")
                    if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                        return false
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        } catch (ex: Exception) {
                            ALog.e(TAG, "startActivity fail uri: ${request.url}", ex)
                        }
                        return true
                    }
                } catch (ex: Exception) {
                    ALog.e("", ex)
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                ALog.d(TAG, "：$url")
                if (TextUtils.isEmpty(url)) {
                    return true
                }
                val uri = Uri.parse(url)
                try {
                    val host = uri.host
                    val scheme = uri.scheme
                    ALog.d(TAG, "：$url, host: $host, scheme: $scheme, part: ${uri.path}")
                    if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
                        return false
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (ex: Exception) {
                            ALog.e(TAG, "startActivity fail, uri: $uri", ex)
                        }
                        return true
                    }

                } catch (ex: Exception) {
                    ALog.e("", ex)
                }
                return super.shouldOverrideUrlLoading(view, url)
            }
        }
        web.addJavascriptInterface(mJSInterface, "bcm")
    }

    fun setListener(listener: OnWebActionListener?) {
        mListener = listener
    }

    open fun load(url: String?) {
        if (!url.isNullOrEmpty()) {
            mWeb?.loadUrl(url)
        }
    }

    open fun reload() {
        mWeb?.reload()
    }

    open fun checkGoBack(): Boolean {
        return if (mWeb?.canGoBack() == true) {
            mWeb?.goBack()
            true
        } else {
            false
        }
    }
}