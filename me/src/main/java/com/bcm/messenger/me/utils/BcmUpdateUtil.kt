package com.bcm.messenger.me.utils

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import java.util.*

/**
 * App upgrade util
 *
 * Created by Kin on 2018/7/28
 */
object BcmUpdateUtil {
    private const val TAG = "BcmUpdateUtil"
    private var hasUpdate = false
    private var forceUpdate = false
    private var currentVersion = AppContextHolder.APP_CONTEXT.getVersionName()
    private var currentVersionCode = AppContextHolder.APP_CONTEXT.getVersionCode()
    private var updateInfo = ""
    private var packageName = BuildConfig.BCM_APPLICATION_ID
    private var updateData: AmeConfigure.UpdateData? = null

    fun showUpdateDialog() {
        updateData?.let {
            AmePopup.center.newBuilder()
                    .withTitle(AppContextHolder.APP_CONTEXT.getString(R.string.me_update_dialog_title, it.last_version))
                    .withContent(updateInfo)
                    .withOkTitle(getString(R.string.me_update_dialog_ok))
                    .withCancelTitle(getString(R.string.common_cancel))
                    .withContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                    .withOkListener {
                        goToPlayStore()
                    }.show(AmeAppLifecycle.current() as? FragmentActivity)
        }
    }

    fun showForceUpdateDialog() {
        updateData?.let {
            ForceUpdateDialog.show(
                    AmeAppLifecycle.current() as? FragmentActivity,
                    getString(R.string.me_force_upgrade_title),
                    updateInfo,
                    View.OnClickListener {
                goToPlayStore()
            })
        }
    }

    fun checkUpdate(result: (hasUpdate: Boolean, forceUpdate: Boolean, version: String) -> Unit) {

        updateData?.let {
            val versionName = "${it.last_version}-${it.version_code}"
            result(hasUpdate, forceUpdate, versionName)
            return
        }

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

                        forceUpdate = forceUpdateMinVersion != -1 && forceUpdateMaxVersion != -1 &&
                                forceUpdateMinVersion <= currentVersionCode && currentVersionCode <= forceUpdateMaxVersion

                        updateInfo = if (Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
                            if (forceUpdate) data.force_update_info_zh else data.update_info_zh
                        } else {
                            if (forceUpdate) data.force_update_info_en else data.update_info_en
                        }
                        updateInfo = updateInfo.replace("\\n", "\n")

                        if (!data.google_package.isNullOrBlank()) {
                            this.packageName = data.google_package ?: packageName
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

    private fun isNewVersion(): Boolean {
        ALog.i(TAG, "${updateData?.last_version}-${updateData?.version_code}")
        updateData?.let {
            try {
                if (!it.last_version.isNullOrEmpty()) {
                    val currentVersionParts = currentVersion.split(".")
                    val newVersionParts = it.last_version.split(".")
                    for (i in newVersionParts.indices) {
                        val newV = newVersionParts[i].toInt()
                        var oldV = -1
                        if (currentVersionParts.size > i) {
                            oldV = currentVersionParts[i].toInt()
                        }

                        if (newV > oldV) {
                            return true
                        } else if (newV < oldV) {
                            return false
                        }
                    }

                    if (it.version_code.isNotEmpty()) {
                        return it.version_code.toInt() > currentVersionCode
                    }
                } else {
                    return false
                }
            } catch (e: Throwable) {
                ALog.e(TAG, e)
            }
        }
        return false
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
            } catch (ex: Exception) {
                ALog.e(TAG, "goToPlayStore error", ex)
            }
        }
    }
}
