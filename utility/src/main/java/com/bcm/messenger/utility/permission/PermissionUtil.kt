package com.bcm.messenger.utility.permission

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by bcm.social.01 on 2018/5/31.
 */
object PermissionUtil {

    private val TAG = "PermissionUtil"

    interface ILanguageSetting {
        fun permissionTitle(): String
        fun permissionGoSetting(): String
        fun permissionCancel(): String
    }

    var languageSetting: ILanguageSetting? = null

    fun checkCameraAndStorage(context: Context, result: (ok: Boolean) -> Unit) {
        PermissionBuilder(context)
                .permission(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .onGranted {
                    result(true)
                }
                .onDenied {
                    result(false)
                }
                .request()
    }

    fun checkCamera(context: Context, result: (ok: Boolean) -> Unit) {

        PermissionBuilder(context)
                .permission(Manifest.permission.CAMERA)
                .onGranted {
                    result(true)
                }
                .onDenied {
                    result(false)
                }
                .request()
    }

    fun checkCamera(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun checkAudio(context: Context, result: (ok: Boolean) -> Unit) {

        PermissionBuilder(context)
                .permission(Manifest.permission.RECORD_AUDIO)
                .onGranted {
                    result(true)
                }
                .onDenied {
                    result(false)
                }
                .request()
    }

    fun checkAudio(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun checkStorage(context: Context, result: (ok: Boolean) -> Unit) {
        PermissionBuilder(context)
                .permission(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .onGranted {
                    result(true)
                }
                .onDenied {
                    result(false)
                }
                .request()
    }

    fun checkCommonPermission(activity: Activity, title: String, tip: String, buttonTitle: String, callback: () -> Unit) {

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            try {
                AlertDialog.Builder(activity)
                        .setCancelable(false)
                        .setTitle(title)
                        .setMessage(tip)
                        .setPositiveButton(buttonTitle) { dialog, _ ->
                            dialog.dismiss()

                            PermissionBuilder(activity)
                                    .permission(Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    .onGranted {
                                        callback()
                                    }
                                    .onDenied {
                                        activity.finish()
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                        try {
                                            val activityManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                            activityManager.appTasks.forEach {
                                                it.finishAndRemoveTask()
                                            }
                                            System.exit(0)
                                        } catch (e: java.lang.Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    .request()

                        }.show()
            } catch (e:Throwable) {

            }
        } else {
            callback()
        }
    }

    fun checkLocationPermission(context: Context, result: (ok: Boolean) -> Unit) {

        PermissionBuilder(context)
                .permission(Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                .onGranted {
                    result(true)
                }
                .onDenied {
                    result(false)
                }
                .request()
    }

    private fun hasPermission(context: Context, permissions: Array<String>?): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyList()
        }
        if (permissions.isNullOrEmpty()) {
            return emptyList()
        }
        val needRequestList = mutableListOf<String>().apply {
            addAll(permissions)
        }
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED) {
                needRequestList.remove(p)
            }
        }
        return needRequestList
    }

    internal class PermissionBuilder(private val context: Context) : PermissionRequestActivity.PermissionListener {

        private var mPermissions: Array<String>? = null
        private var mOnGrantedCallback: (() -> Unit)? = null
        private var mOnDeniedCallback: (() -> Unit)? = null

        override fun onRequestResult(result: Array<Pair<String, Int>>) {
            var granted = result.isNotEmpty()
            for (pair in result) {
                if (pair.second != PackageManager.PERMISSION_GRANTED) {
                    granted = false
                }
            }
            if (!granted) {
                mOnDeniedCallback?.invoke()
            } else {
                mOnGrantedCallback?.invoke()
            }
        }

        fun permission(vararg permissions: String): PermissionBuilder {
            mPermissions = arrayOf(*permissions)
            return this
        }

        fun onGranted(callback: () -> Unit): PermissionBuilder {
            mOnGrantedCallback = callback
            return this
        }

        fun onDenied(callback: () -> Unit): PermissionBuilder {
            mOnDeniedCallback = callback
            return this
        }

        fun request() {
            val result = hasPermission(context, mPermissions)
            if (result.isEmpty()) {
                ALog.i(TAG, "request done, hasPermission, call onGranted")
                mOnGrantedCallback?.invoke()
            } else {
                val p = mPermissions
                val intent = Intent(context, PermissionRequestActivity::class.java).apply {
                    putExtra(PermissionRequestActivity.PARAM_PERMISSION_REQUEST, p)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PermissionRequestActivity.sRequestListener = this
                context.startActivity(intent)
            }
        }
    }
}