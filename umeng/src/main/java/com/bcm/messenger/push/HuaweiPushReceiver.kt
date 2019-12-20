package com.bcm.messenger.push

import android.content.Context
import com.huawei.android.pushagent.PushReceiver
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by bcm.social.01 on 2018/7/10.
 */
class HuaweiPushReceiver : PushReceiver() {
    companion object {
        const val TAG = "HuaweiPushReceiver"
    }

    override fun onToken(p0: Context?, p1: String?) {
        ALog.i(TAG, p1)
    }

    override fun onPushMsg(p0: Context?, p1: ByteArray?, p2: String?) {
        ALog.i(TAG, "msg $p2")
    }

}