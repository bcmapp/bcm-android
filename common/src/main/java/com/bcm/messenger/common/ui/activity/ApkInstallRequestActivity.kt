package com.bcm.messenger.common.ui.activity

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.FullScreenTransparentActivity

/**
 * apk
 * Created by wjh on 2019-09-05
 */
class ApkInstallRequestActivity : FullScreenTransparentActivity()  {

    companion object {
        private const val TAG = "ApkInstallRequestActivity"
        private const val REQUEST_CODE = 1
    }

    private var mApkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(R.anim.utility_slide_silent, R.anim.utility_slide_silent)
        super.onCreate(savedInstanceState)
        mApkPath = intent.getStringExtra(ARouterConstants.PARAM.PARAM_APK)
        if (mApkPath == null) {
            finish()
            return
        }
        val isUpgrade = intent.getBooleanExtra(ARouterConstants.PARAM.PARAM_UPGRADE, false) //，，
        if (isUpgrade) {
            if (AppUtil.checkInstallPermission(this@ApkInstallRequestActivity)) {
                BcmFileUtils.installApk(this@ApkInstallRequestActivity, mApkPath
                        ?: return)
                finish()

            } else {
                AppUtil.requestInstallPermission(this@ApkInstallRequestActivity, REQUEST_CODE)
            }
        }else {
            AlertDialog.Builder(this).setMessage(R.string.common_apk_install_notice_description)
                    .setNegativeButton(R.string.common_cancel, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            dialog?.cancel()
                            finish()
                        }

                    }).setPositiveButton(R.string.common_open, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            if (AppUtil.checkInstallPermission(this@ApkInstallRequestActivity)) {
                                BcmFileUtils.installApk(this@ApkInstallRequestActivity, mApkPath
                                        ?: return)
                                finish()

                            } else {
                                AppUtil.requestInstallPermission(this@ApkInstallRequestActivity, REQUEST_CODE)
                            }
                        }

                    }).setCancelable(false).show()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (AppUtil.checkInstallPermission(this)) {
                BcmFileUtils.installApk(this, mApkPath ?: return)
            }
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.utility_slide_silent, R.anim.utility_slide_silent)
    }
}