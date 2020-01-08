package com.bcm.messenger.me.ui.scan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.google.zxing.Result

/**
 * Created by wjh on 2019/7/2
 */
@Route(routePath = ARouterConstants.Activity.SCAN_NEW)
class NewScanActivity : AppCompatActivity() {

    class ScanResultEvent(val result: Result)
    class ScanResumeEvent

    private val TAG = "NewScanActivity"
    private var mScanResultHandleDelegate: Boolean = false
    private var mScanType: Int = ARouterConstants.PARAM.SCAN.TYPE_OTHER

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(TAG)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_scan)
        RxBus.subscribe<ScanResultEvent>(TAG) {scanResult ->
            handleScanResult(scanResult.result)
        }
        initFragment()
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA ->
                // don't launch camera app
                return true
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                return true
            }
            else -> {
            }
        }
        return super.onKeyDown(keyCode, event)
    }


    private fun initFragment() {
        mScanType = intent.getIntExtra(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_OTHER)
        mScanResultHandleDelegate = intent.getBooleanExtra(ARouterConstants.PARAM.SCAN.HANDLE_DELEGATE, false)
        val tran = supportFragmentManager.beginTransaction()
        when (mScanType) {
            ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT -> {
                tran.add(R.id.scan_container_root, ScanAccountContainerFragment())
            }
            ARouterConstants.PARAM.SCAN.TYPE_CONTACT -> {
                tran.add(R.id.scan_container_root, ScanWithCodeContainerFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARouterConstants.PARAM.SCAN.TAB, 1)
                    }
                })
            }
            ARouterConstants.PARAM.SCAN.TYPE_SCAN -> {
                tran.add(R.id.scan_container_root, ScanWithCodeContainerFragment())
            }
            else -> {
                tran.add(R.id.scan_container_root, ScanOtherContainerFragment())
            }
        }
        tran.commitAllowingStateLoss()
    }

    private fun handleScanResult(scanResult: Result) {
        try {
            if (mScanResultHandleDelegate && AMELogin.isLogin) {
                AmeModuleCenter.contact(AMELogin.majorContext)?.discernScanData(this, scanResult.text) { discern ->
                    if (discern) {
                        AmeDispatcher.mainThread.dispatch({
                            finish()
                        }, 500)
                    }else {
                        RxBus.post(ScanResumeEvent())
                    }
                }

            }else {
                val result = Intent()
                result.putExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT, scanResult.text)
                setResult(Activity.RESULT_OK, result)
                finish()
            }

        }catch (ex: Exception) {
            ALog.e(TAG, "scan result error", ex)
        }
    }
}