package com.bcm.messenger.common

import android.content.Context
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.HexUtil
import java.util.*

class p {
    companion object {
        init {
            System.loadLibrary("native-utils")
        }
    }

    fun f(d: ByteArray):String {
        return HexUtil.toString(EncryptUtils.computeSHA256(d)).toUpperCase(Locale.ENGLISH)
    }

    fun e(q:String, b:String) {
        SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, q, b)
    }

    fun j(a:String) {
        ToastUtil.show(AppContextHolder.APP_CONTEXT, a)
    }

    external fun a(context:Context): String
    external fun b(context:Context): String
}