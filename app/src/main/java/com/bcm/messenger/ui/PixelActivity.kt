package com.bcm.messenger.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.pixel.PixelManager


/**
 * Activity with one pixel
 * Created by wjh on 2019-08-30
 */
class PixelActivity : AppCompatActivity() {

    private val TAG = "PixelActivity"
    private var isFirst = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ALog.i(TAG, "onCreate")
        PixelManager.getCurrent()?.addActivity(this)
        val mWindow = this.window
        mWindow.setGravity(Gravity.START or Gravity.TOP)
        val mLayoutParams = mWindow.attributes
        mLayoutParams.width = 1
        mLayoutParams.height = 1

        mLayoutParams.x = 0
        mLayoutParams.y = 0

        mWindow.attributes = mLayoutParams
        isFirst = true
    }

    override fun finish() {
        super.finish()
        ALog.i(TAG, "finish")
        PixelManager.getCurrent()?.removeActivity(this)
    }

    override fun onResume() {
        super.onResume()
        ALog.i(TAG, "onResume")
        if (!isFirst) {
            finish()
        }
        isFirst = false
    }

}