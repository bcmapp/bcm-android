package com.bcm.messenger.adhoc.sdk

import com.bcm.imcore.IAdHocActionResult

class AdHocResult(private val result:(succeed:Boolean)->Unit):IAdHocActionResult.Stub() {
    override fun onSucceed(succeed: Boolean) {
        result(succeed)
    }
}