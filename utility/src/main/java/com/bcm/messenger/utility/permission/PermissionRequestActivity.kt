package com.bcm.messenger.utility.permission

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bcm.messenger.utility.FullScreenTransparentActivity
import com.bcm.messenger.utility.R
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by wjh on 2019-09-05
 */
class PermissionRequestActivity : FullScreenTransparentActivity()  {

    companion object {
        private const val TAG = "PermissionRequestActivity"
        private const val REQUEST_CODE = 1

        const val PARAM_PERMISSION_REQUEST = "param_permission_request"

        var sRequestListener: PermissionListener? = null
    }

    /**
     * permission callback.
     */
    interface PermissionListener {
        fun onRequestResult(result: Array<Pair<String, Int>>)
    }

    private var mPermissions: Array<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(R.anim.utility_slide_silent, R.anim.utility_slide_silent)
        super.onCreate(savedInstanceState)
        val permissions = intent.getStringArrayExtra(PARAM_PERMISSION_REQUEST)
        mPermissions = permissions
        if (permissions == null) {
            ALog.i(TAG, "request no permission")
            finish()
            return
        }

        ALog.i(TAG, "requestPermissions")
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ALog.i(TAG, "onActivityResult")
        try {
            val perssmions = mPermissions ?: emptyArray()
            val result = Array(perssmions.size) { index ->
                Pair(perssmions[index], ContextCompat.checkSelfPermission(this, perssmions[index]))
            }
            callback(result)

        }catch (ex: Exception) {
            callback(emptyArray())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        ALog.i(TAG, "onRequestPermissionsResult")
        try {
            var check = false
            val result = Array<Pair<String, Int>>(permissions.size) { index ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    check = true
                }
                Pair(permissions[index], grantResults[index])
            }
            if (check) {
                if (!checkIfShowAlert(permissions)) {
                    callback(result)
                }
            }else {
                callback(result)
            }

        }catch (ex: Exception) {
            callback(emptyArray())
        }
    }

    override fun finish() {
        super.finish()
        ALog.i(TAG, "finish")
        sRequestListener = null
        overridePendingTransition(R.anim.utility_slide_silent, R.anim.utility_slide_silent)
    }

    private fun callback(pairArray: Array<Pair<String, Int>>) {
        sRequestListener?.onRequestResult(pairArray)
        finish()
    }

    private fun checkIfShowAlert(permissions: Array<out String>): Boolean {
//        var shouldShowRationale = false
//        for (p in permissions) {
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, p)) {
//                shouldShowRationale = true
//                break
//            }
//        }
//        if (shouldShowRationale) {
//            val languageSetting = PermissionUtil.languageSetting
//            if (null != languageSetting) {
//                AlertDialog.Builder(this).setMessage(languageSetting.permissionTitle())
//                        .setNegativeButton(languageSetting.permissionCancel()) { dialog, which ->
//                            dialog?.cancel()
//                            callback(emptyArray())
//                        }.setPositiveButton(languageSetting.permisstionGoSetting()) { dialog, which ->
//                            try {
//                                val localIntent = Intent()
//                                localIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
//                                localIntent.data = Uri.fromParts("package", packageName, null);
//                                startActivityForResult(localIntent, REQUEST_CODE)
//
//                            }catch (ex: Exception) {
//                                ALog.e(TAG, "start detail setting activity error", ex)
//                            }
//                        }.setCancelable(false).show()
//                return true
//            }
//        }
        return false
    }
}