package com.bcm.messenger.adhoc.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.ui.channel.AdHocPreviewActivity
import com.bcm.messenger.chats.BuildConfig
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.activity.ApkInstallRequestActivity
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

/**
 *
 * Created by wjh on 2018/11/20
 */
open class AdHocPreviewClickListener(private val accountContext: AccountContext) : ChatComponentListener {

    companion object {
        private val TAG = "AdHocPreviewClickListener"

        private fun doForAdHoc(accountContext: AccountContext, v: View, messageRecord: AdHocMessageDetail) {

            if (AdHocPreviewActivity.isContentTypeSupported(messageRecord.getContentType())) {
                val intent = Intent(v.context, AdHocPreviewActivity::class.java)
                intent.putExtra(AdHocPreviewActivity.SESSION_ID, messageRecord.sessionId)
                intent.putExtra(AdHocPreviewActivity.INDEX_ID, messageRecord.indexId)
                intent.putExtra(AdHocPreviewActivity.DATA_TYPE, messageRecord.getContentType())
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as Activity, v, "${ShareElements.Activity.MEDIA_PREIVEW}${messageRecord.indexId}").toBundle()
                gotoActivity(accountContext, v.context, intent, bundle)
            } else {
                val uri = messageRecord.toAttachmentUri() ?: return
                doForOtherUri(accountContext, v.context, uri, messageRecord.getContentType())
            }
        }

        private fun doForOtherUri(accountContext: AccountContext, context: Context, uri: Uri, mimeType: String?) {

            fun handle(uri: Uri, path: String?) {
                ALog.i(TAG, "doForOtherUri: $uri, path: $path")
                AmeAppLifecycle.hideLoading()

                if (path != null && AppUtil.isApkFile(context, path)) {
                    context.startActivity(Intent(context, ApkInstallRequestActivity::class.java).apply {
                        putExtra(ARouterConstants.PARAM.PARAM_APK, path)
                    })
                } else {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val type = if (mimeType.isNullOrEmpty()) {
                        BcmFileUtils.getMimeType(context, path)
                    } else {
                        mimeType
                    }
                    intent.setDataAndType(uri, type)
                    gotoActivity(accountContext, context, intent, null)
                }
            }

            AmeAppLifecycle.showLoading()
            Observable.create<Pair<Uri, String?>> {
                val filePath = BcmFileUtils.getFileAbsolutePath(context, uri)
                        ?: throw Exception("get file path fail")
                val targetUri = FileProvider.getUriForFile(context, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", File(filePath))
                it.onNext(Pair(targetUri, filePath))
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        handle(it.first, it.second)
                    }, {
                        ALog.e(TAG, "doForOtherUri error", it)
                        handle(uri, null)
                    })
        }

        private fun gotoActivity(accountContext: AccountContext, context: Context, intent: Intent, bundle: Bundle?) {
            try {
                if (context is Activity) {
                    context.startBcmActivity(AMELogin.majorContext, intent, bundle)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startBcmActivity(accountContext, intent, bundle)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "gotoActivity error", ex)
                ToastUtil.show(context, context.getString(R.string.chats_there_is_no_app_available_to_handle_this_link_on_your_device))
            }
        }
    }


    override fun onClick(v: View, data: Any) {
        try {
            if (data is AdHocMessageDetail) {
                doForAdHoc(accountContext, v, data)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "onClick error", ex)
        }
    }
}