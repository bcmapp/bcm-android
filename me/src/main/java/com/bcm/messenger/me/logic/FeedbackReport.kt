package com.bcm.messenger.me.logic

import android.os.Environment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.SystemUtils
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.interceptor.ProgressInterceptor
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object FeedbackReport {
    private val TAG = "FeedbackReport"
    private const val reportUrl = "https://reporting.bcm.social:8070/userFeedback"
    private const val FEEDBACK_APPID: String = "ame-android"

    private class NYyData(val reportType: String, var feedback: String, val contactInfo: String, val uid: String, val networkState: String, val productVer: String, val guid: String, val osVer: String, val phoneType: String): NotGuard
    private class NYy(val appId: String, val sign: String, val data: NYyData): NotGuard

    fun feedback(reportType: String, content: String, reportFileList: List<String>, result: (succeed: Boolean) -> Unit): Boolean {
        val data = NYyData("UFB", "[$reportType] $content",
                AMELogin.majorUid,
                "0",
                "",
                AppUtil.getVersionName(AppContextHolder.APP_CONTEXT),
                "",
                SystemUtils.getSimpleSystemVersion(),
                SystemUtils.getSimpleSystemInfo()
        )
        val nyy = NYy(FEEDBACK_APPID, "", data)

        return uploadReport(nyy, reportFileList, result)
    }


    private fun uploadReport(nyy: NYy, reportFileList: List<String>, callback: (succeed: Boolean) -> Unit): Boolean {
        var ret = true
        try {
            if (reportFileList.isNotEmpty()) {
                val diskPath = Environment.getExternalStorageDirectory().absolutePath
                val folder = diskPath + File.separatorChar + ARouterConstants.SDCARD_ROOT_FOLDER
                val path = "$folder/report_log_${System.currentTimeMillis()}.zip"

                AppUtil.zipRealCompress(path, reportFileList)

                ret = postFile(reportUrl, nyy, path, callback)
            } else {
                ret = post(reportUrl, nyy, "", callback)
            }
        } catch (e: Exception) {
            ret = false
            callback(false)
        }

        return ret
    }


    private fun postFile(url: String, nyy: NYy, fileName: String, callback: (succeed: Boolean) -> Unit): Boolean {
        AmeFileUploader.uploadAttachmentToAws(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, AmeFileUploader.AttachmentType.GROUP_MESSAGE, File(fileName), object : AmeFileUploader.FileUploadCallback() {
            override fun onUploadSuccess(fileUrl: String?, id: String?) {
                post(url, nyy, fileUrl ?: "", callback)
                File(fileName).delete()
            }

            override fun onUploadFailed(filepath: String?, msg: String?) {
                callback(false)
                File(fileName).delete()
            }
        })
        return true
    }

    private fun post(url: String, nyy: NYy, feedbackFileUrl: String, callback: (succeed: Boolean) -> Unit): Boolean {
        try {
            if (feedbackFileUrl.isNotEmpty()) {
                nyy.data.feedback = "[$feedbackFileUrl]\n${nyy.data.feedback}"
            }

            val params = Gson().toJson(nyy)


            val sslFactory = IMServerSSL()
            val client = OkHttpClient.Builder()
                    .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                    .hostnameVerifier(BaseHttp.trustAllHostVerify())
                    .addInterceptor(NormalMetricsInterceptor())
                    .addInterceptor(ProgressInterceptor())
                    .readTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                    .connectTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                    .build()

            val http = BaseHttp()
            http.setClient(client)
            val response = http.postForm()
                    .addFormPart("nyy", null, RequestBody.create(null, params))
                    .url(url)
                    .build()
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .buildCall().execute()

            val statusCode = response.code()
            callback(statusCode == 204 || statusCode == 206)

            ALog.i(TAG, "post $statusCode")
        } catch (e: Exception) {
            ALog.e(TAG, "post", e)
            callback(false)
        }

        return true
    }

}