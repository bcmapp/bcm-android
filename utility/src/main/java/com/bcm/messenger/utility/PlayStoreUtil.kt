package com.bcm.messenger.utility

import android.content.Context
import android.content.Intent
import android.net.Uri


/**
 * Google Play Store connection check
 *
 * Created by Kin on 2018/7/16
 */
object PlayStoreUtil {

    fun isGoogleStoreInstalled(context: Context): Boolean{
        val packageManager = context.packageManager
        val info = packageManager.getInstalledPackages(0)
        for (j in info.indices) {
            if (info[j].packageName.equals("com.android.vending", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * jump to Google Play App detail page
     * @param context Activity/Fragment/Application的Context
     */
    fun goToPlayStore(context: Context) {
        val appPackageName = context.packageName
        try {
            val uri = Uri.parse("market://details?id=$appPackageName")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.`package` = "com.android.vending"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")), null)
        }

    }
}