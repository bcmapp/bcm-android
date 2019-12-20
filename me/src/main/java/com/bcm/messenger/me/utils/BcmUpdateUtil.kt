package com.bcm.messenger.me.utils

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.bcmhttp.TPHttp
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.ui.activity.ApkInstallRequestActivity
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.bcmhttp.callback.OriginCallback
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import okhttp3.Call
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

/**
 * App upgrade util
 *
 * Created by Kin on 2018/7/28
 */
object BcmUpdateUtil {
    private const val TAG = "BcmUpdateUtil"
    private const val playUrl = "https://play.google.com/store/apps/details?id=com.bcm.messenger"

    // 通知相关
    private var downloadDialog: ForceUpdateDownloadDialog? = null

    // 是否有更新
    private var hasUpdate = false
    // 是否强制更新
    private var forceUpdate = false

    // 当前版本号
    private var currentVersion = AppUtil.getVersionName(AppContextHolder.APP_CONTEXT)
    // 当前内部版本号
    private var currentVersionCode = AppUtil.getVersionCode(AppContextHolder.APP_CONTEXT)
    // 升级提示
    private var updateInfo = ""
    // Google Play包名
    private var packageName = AppUtil.getCurrentPkgName(AppContextHolder.APP_CONTEXT)

    private var updateData: AmeConfigure.UpdateData? = null

    private var enabledGoogle = false

    private var isDownloading = false

    private fun getDownloadPath(data: AmeConfigure.UpdateData): String {
        return AmeFileUploader.APK_DIRECTORY + "/bcm-${data.last_version}.apk"
    }

    private fun openApkFile() {
        updateData?.let {
            val filePath = getDownloadPath(it)
            if (BcmFileUtils.isExist(filePath)) {
                AppContextHolder.APP_CONTEXT.startActivity(Intent(AppContextHolder.APP_CONTEXT, ApkInstallRequestActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_APK, filePath)
                    putExtra(ARouterConstants.PARAM.PARAM_UPGRADE, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    private fun downloadApkFile(url: String) {
        if (isDownloading) return
        isDownloading = true

        updateData?.let {
            AmePushProcess.notifyDownloadApkNotification(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_notification_downloading, 0), false, "")
            AmeFileUploader.downloadFile(AppContextHolder.APP_CONTEXT, url, object : FileDownCallback(AmeFileUploader.AME_PATH, "bcm-${it.last_version}.apk") {
                override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                    // 下载失败，强更继续显示强更对话框
                    isDownloading = false
                    if (forceUpdate) {
                        downloadDialog?.setFailed()
                        downloadDialog = null
                        showForceUpdateDialog()
                    }
                    AmePushProcess.notifyDownloadApkNotification(getString(R.string.me_update_notification_failed), false, "")
                    e?.printStackTrace()
                }

                override fun onResponse(response: File?, id: Long) {
                    isDownloading = false
                    if (response != null) {
                        // 下载成功，移动文件到外部存储
                        val apkFile = File(AmeFileUploader.APK_DIRECTORY, "bcm-${it.last_version}.apk")
                        moveFile(response.path, apkFile.path)

                        AmePushProcess.notifyDownloadApkNotification(getString(R.string.me_update_notification_success), true, getDownloadPath(it))
                        downloadDialog?.setSuccess()
                        openApkFile()
                    } else {
                        if (forceUpdate) {
                            downloadDialog?.setFailed()
                            downloadDialog = null
                            showForceUpdateDialog()
                        }
                        AmePushProcess.notifyDownloadApkNotification(getString(R.string.me_update_notification_failed), false, "")
                    }
                }

                override fun inProgress(progress: Int, total: Long, id: Long) {
                    ALog.d(TAG, "downloadApkFile progress: $progress")
                    AmePushProcess.notifyDownloadApkNotification(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_notification_downloading, progress), false, "")
                    downloadDialog?.updateProgress(progress)
                }
            })
        }
    }

    fun showUpdateDialog() {
        updateData?.let {
            val data = it
            AmePopup.center.newBuilder()
                    .withTitle(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_dialog_title, it.last_version))
                    .withContent(updateInfo)
                    .withOkTitle(getString(R.string.me_update_dialog_ok))
                    .withCancelTitle(getString(R.string.common_cancel))
                    .withContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                    .withOkListener {
//                        if (isGooglePlayEdition() || enabledGoogle) {
                            goToPlayStore()
//                        } else {
//                            openOrDownloadApk(data)
//                        }
                    }.show(AmeAppLifecycle.current() as? FragmentActivity)
        }
    }

    fun showForceUpdateDialog() {
        if (downloadDialog != null) return
        updateData?.let {
            val data = it
            AmePopup.center.newBuilder()
                    .withTitle(AppUtil.getString(R.string.me_force_upgrade_title))
                    .withContent(updateInfo)
                    .withOkTitle(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_dialog_ok))
                    .withCancelTitle(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_dialog_cancel))
                    .withContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                    .withCancelable(false)
                    .withTopMost(true)
                    .withOkListener {
//                        if (isGooglePlayEdition() || enabledGoogle) {
                            goToPlayStore()
//                        } else {
//                            downloadApkFile(data.download_url)
//                            val activity = AmeAppLifecycle.current() as? FragmentActivity
//                            if (null != activity){
//                                showForceDownloadingDialog(activity)
//                            }
//                            AmePopup.center.dismiss()
//                        }
                    }.withCancelListener {
                        exitApp()
                    }.withDismissListener {
                        if (downloadDialog == null) {
                            AmeDispatcher.mainThread.dispatch({
                                showForceUpdateDialog()
                            }, 1000)
                        }
                    }.show(AmeAppLifecycle.current() as? FragmentActivity)
        }
    }

    private fun showForceDownloadingDialog(activity: FragmentActivity) {
        updateData?.let {
            downloadDialog = ForceUpdateDownloadDialog.show(
                    activity,
                    activity.getString(R.string.me_update_dialog_title, it.last_version),
                    activity.getString(R.string.me_update_dialog_downloading),
                    object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            openApkFile()
                        }
                    })
        }
    }

    fun checkUpdate(result: (hasUpdate: Boolean, forceUpdate: Boolean, version: String) -> Unit) {
        // 已经查询过版本
        updateData?.let {
            val versionName = "${it.last_version}-${it.version_code}"
            result(hasUpdate, forceUpdate, versionName)
            return
        }

        checkGoogleConnection {
            enabledGoogle = it
            AmeConfigure.getUpgradeVersionInfo { data ->
                ALog.d(TAG, "checkupdata data: ${data?.download_url}, lastVersion: ${data?.last_version}")
                hasUpdate = false
                forceUpdate = false
                var versionName = ""
                try {
                    if (data != null && data.checkDataAvailable()) {
                        updateData = data
                        ALog.i(TAG, "last version ${data.last_version}")
                        if (isNewVersion()) {
                            val forceUpdateMinVersion = data.force_update_min.toInt()
                            val forceUpdateMaxVersion = data.force_update_max.toInt()
                            // 强制升级版本号都不等于-1且当前版本号处于强制升级版本号区间中则为强制升级，否则是普通升级
                            forceUpdate = forceUpdateMinVersion != -1 && forceUpdateMaxVersion != -1 &&
                                    forceUpdateMinVersion <= currentVersionCode && currentVersionCode <= forceUpdateMaxVersion
                            // 根据语言选择更新日志
                            updateInfo = if (Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
                                if (forceUpdate) data.force_update_info_zh else data.update_info_zh
                            } else {
                                if (forceUpdate) data.force_update_info_en else data.update_info_en
                            }
                            updateInfo = updateInfo.replace("\\n", "\n")
                            // 获取Google Play上架的包名
                            if (!data.google_package.isNullOrBlank()) {
                                this.packageName = data.google_package
                            }
                            hasUpdate = true
                            versionName = "${data.last_version}-${data.version_code}"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                result(hasUpdate, forceUpdate, versionName)
            }
        }
    }

    private fun isNewVersion(): Boolean {
        ALog.i(TAG, "${updateData?.last_version}-${updateData?.version_code}")
        updateData?.let {
            try {
                if (!it.last_version.isNullOrEmpty()) {
                    val currentVersionParts = currentVersion.split(".")
                    val newVersionParts = it.last_version.split(".")
                    for (i in 0 until newVersionParts.size) {
                        val newV = newVersionParts[i].toInt()
                        var oldV = -1
                        if (currentVersionParts.size > i) {
                            oldV = currentVersionParts[i].toInt()
                        }

                        if (newV > oldV) {
                            return true
                        }
                        else if(newV < oldV) {
                            return false
                        }
                    }

                    if (it.version_code.isNotEmpty()) {
                        return it.version_code.toInt() > currentVersionCode
                    }
                } else {
                    return false
                }
            }
            catch (e:Throwable) {
                ALog.e(TAG, e)
            }
        }
        return false
    }

    private fun checkGoogleConnection(result: (succeed:Boolean) -> Unit) {
        TPHttp.get()
                .url(playUrl)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .enqueue(object : OriginCallback() {
                    override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                        e?.printStackTrace()
                        AmeDispatcher.mainThread.dispatch {
                            result(false)
                        }
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        val resStr = response?.body()?.string()
                        val doc = Jsoup.parse(resStr)
                        val devTextList = doc.select("a[href=/store/apps/developer?id=BCM+Social]")
                        val classes = doc.getElementsByClass("T32cc UAO9ie")
                        AmeDispatcher.mainThread.dispatch {
                            result(devTextList.isNotEmpty() || classes.isNotEmpty())
                        }
                    }
                })
    }

    private fun goToPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("market://details?id=$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.`package` = "com.android.vending"
            AppContextHolder.APP_CONTEXT.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppContextHolder.APP_CONTEXT.startActivity(intent, null)
            }catch (ex: Exception) {
                ALog.e(TAG, "goToPlayStore error", ex)
            }
        }
    }

    private fun openOrDownloadApk(data: AmeConfigure.UpdateData) {
        val apkFile = File(getDownloadPath(data))
        if (apkFile.exists()) {
            openApkFile()
        } else {
            downloadApkFile(data.download_url)
        }
    }

    private fun moveFile(sourcePath: String, destPath: String) {
        try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath)
            if (!destFile.exists()) {
                destFile.parentFile.mkdirs()
                destFile.createNewFile()
            }
            BcmFileUtils.copy(FileInputStream(sourceFile), FileOutputStream(destFile))
            sourceFile.delete()
        } catch (tr: Throwable) {
            tr.printStackTrace()
        }
    }

    private fun exitApp() {
        AppUtil.exitApp()
    }
}
