package com.bcm.messenger.common.imagepicker.ui.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.R
import com.bcm.messenger.common.imagepicker.*
import com.bcm.messenger.common.imagepicker.bean.SelectedModel
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil

/**
 * Created by Kin on 2019/4/18
 */
class PickPhotoDelegateActivity : AppCompatActivity(), BcmPickPhotoCropHelper.OnImageCropCompleteListener {
    private val TAG = "PickPhotoDelegateActivity"

    private val config = BcmPickHelper.currentPickConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.background = AppUtil.getDrawable(resources, R.color.common_color_transparent)

        BcmPickPhotoCropHelper.addOnImageCropCompleteListener(this)

        if (!config.isPicking) {
            config.isPicking = true
            if (config.capturePhoto) {
                ALog.i(TAG, "Start capture photo")
                PermissionUtil.checkCamera(this) { res ->
                    if (res) {
                        BcmTakePhotoHelper.takePicture(AMELogin.majorContext,this)
                    } else {
                        finish()
                    }
                }
            } else {
                ALog.i(TAG, "Start pick photo")
                PermissionUtil.checkStorage(this) { res ->
                    if (res) {
                        startActivityForResult(Intent(this, PickPhotoActivity::class.java), REQ_INNER_PICK)
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    private fun clearAndFinish() {
        BcmPickPhotoCropHelper.removeOnImageCropCompleteListener(this)
        BcmPickHelper.clear()

        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_INNER_PICK -> {
                if (resultCode == Activity.RESULT_OK) {
                    ALog.i(TAG, "Pick photo return OK")
                    if (config.cropPhoto) {
                        ALog.i(TAG, "Pick photo OK, start crop photo")
                        // 
                        val pathList = data?.getSerializableExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST) as? ArrayList<SelectedModel>
                        if (pathList?.isNotEmpty() == true) {
                            val intent = Intent()
                            intent.setClass(this, ImageCropActivity::class.java)
                            intent.putExtra(BcmPickPhotoCropHelper.KEY_PIC_PATH, pathList[0].path)
                            startActivityForResult(intent, REQ_INNER_CROP)
                        }
                    } else {
                        ALog.i(TAG, "Pick photo OK, return selected list")
                        val intent = Intent(data)
                        setResult(Activity.RESULT_OK, intent)

                        clearAndFinish()
                    }
                } else {
                    ALog.i(TAG, "Pick photo return canceled")
                    setResult(Activity.RESULT_CANCELED)

                    clearAndFinish()
                }
            }
            REQ_INNER_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK) {
                    ALog.i(TAG, "Capture photo return OK")
                    if (config.cropPhoto) {
                        ALog.i(TAG, "Capture photo OK, start crop photo")
                        val intent = Intent()
                        intent.setClass(this, ImageCropActivity::class.java)
                        intent.putExtra(BcmPickPhotoCropHelper.KEY_PIC_PATH, BcmTakePhotoHelper.currentPhotoPath)
                        startActivityForResult(intent, REQ_INNER_CROP)
                    } else {
                        ALog.i(TAG, "Capture photo OK, return photo path")
                        val intent = Intent()
                        intent.putExtra(BcmPickPhotoConstants.EXTRA_CAPTURE_PATH, BcmTakePhotoHelper.currentPhotoPath)
                        setResult(Activity.RESULT_OK, intent)

                        clearAndFinish()
                    }
                } else {
                    ALog.i(TAG, "Capture photo return OK")
                    setResult(Activity.RESULT_CANCELED)

                    clearAndFinish()
                }
            }
            else -> {
                setResult(Activity.RESULT_CANCELED)

                clearAndFinish()
            }
        }
    }

    override fun onImageCropComplete(bmp: Bitmap?, ratio: Float) {
        ALog.i(TAG, "Crop photo complete, return result")
        if (bmp != null) {
            config.callbacks.forEach {
                it.cropResult(bmp)
            }
        }

        clearAndFinish()
    }
}