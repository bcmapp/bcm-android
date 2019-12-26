package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.adhoc_activity_media_preview.*

/**
 * adhoc preview
 * Created by Kin on 2018/10/31
 */
class AdHocPreviewActivity : AppCompatActivity() {

    interface OnPreviewListener {
        fun onInit(index: Long, mid: String, type: Long)
        fun onAttachmentComplete(complete: Boolean)
        fun onDismiss()
        fun onControllerVisible(isVisible: Boolean)
    }

    companion object {
        private const val TAG = "AdHocPreviewActivity"

        const val DATA_TYPE = "__data_type"          // Data type, use type enum above
        const val SESSION_ID = "__thread_id"          // Private chat thread id or group chat gid
        const val INDEX_ID = "__index_id"            // Message database index id

        fun isContentTypeSupported(contentType: String?): Boolean {
            return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)
        }
    }

    private var enterTransitionId = 0L
    private var mFragment: AdHocPreviewFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ALog.i(TAG, "onCreate")
        setContentView(R.layout.adhoc_activity_media_preview)
        initView()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        ALog.i(TAG, "onNewIntent")
        setIntent(intent)
        initResources()
    }

    override fun onBackPressed() {
        dismissActivity()
    }

    private fun initView() {
        window.decorView.setBackgroundColor(Color.BLACK)
        postponeEnterTransition()
        ALog.i(TAG, "initView")
        initResources()
    }


    private fun <T : Fragment> initFragment(@IdRes target: Int,
                                            fragment: T,
                                            extras: Bundle?): T {
        if (extras != null) {
            fragment.arguments = extras
        }
        supportFragmentManager.beginTransaction()
                .replace(target, fragment)
                .commitAllowingStateLoss()
        return fragment
    }

    private fun initResources() {
        mFragment = initFragment(R.id.preview_container, AdHocPreviewFragment(), null)
        mFragment?.setOnPreviewListener(object : OnPreviewListener {

            override fun onInit(index: Long, mid: String, type: Long) {
                if (enterTransitionId == 0L) {
                    enterTransitionId = index
                    ViewCompat.setTransitionName(preview_container, "${ShareElements.Activity.MEDIA_PREIVEW}$enterTransitionId")
                    startPostponedEnterTransition()
                }
                if (type == AmeGroupMessage.IMAGE) {
                    preview_more_view.showDefaultOptionLayout()
                    preview_more_view?.displayDefault()
                } else {
                    preview_more_view?.displayNull()
                }
                preview_more_view?.enableDownload()
            }

            override fun onAttachmentComplete(complete: Boolean) {
                ALog.i(TAG, "onAttachmentComplete preview: ${preview_more_view == null}")
                preview_more_view?.visibility = if (complete) View.VISIBLE else View.GONE
            }

            override fun onDismiss() {
                dismissActivity()
            }

            override fun onControllerVisible(isVisible: Boolean) {
                ALog.i(TAG, "onControllerVisible isVisible: $isVisible, ${preview_more_view == null}")
                if (isVisible) {
                    preview_more_view?.hideDefaultOptionLayout()
                } else {
                    preview_more_view?.showDefaultOptionLayout()
                }
            }

        })

        preview_more_view.setMoreViewListener(mFragment ?: return)
//        enterTransitionId = intent.getLongExtra(INDEX_ID, 0L)
//        startPostponedEnterTransition()
//        ViewCompat.setTransitionName(preview_container, "${ShareElements.Activity.MEDIA_PREIVEW}$enterTransitionId")

    }


    fun dismissActivity() {
        try {
            ViewCompat.setTransitionName(preview_container, null)
            setEnterSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                    names?.clear()
                    sharedElements?.clear()
                    names?.add("${ShareElements.Activity.MEDIA_PREIVEW}${enterTransitionId}")
                    ViewCompat.setTransitionName(preview_container, "${ShareElements.Activity.MEDIA_PREIVEW}${enterTransitionId}")
                    sharedElements?.put("${ShareElements.Activity.MEDIA_PREIVEW}${enterTransitionId}", preview_container)
                }
            })
            setResult(Activity.RESULT_OK, Intent().apply { putExtra(ShareElements.PARAM.MEDIA_INDEX, enterTransitionId) })
        } catch (ex: Exception) {
            ALog.e(TAG, "dismissActivity error", ex)
        } finally {
            finishAfterTransition()
        }
    }
}