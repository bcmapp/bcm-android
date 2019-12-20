package com.bcm.messenger.common.mms

import android.net.Uri

/**
 * Created by Kin on 2019/12/2
 */
class HistoryUriParser(private val uri: Uri) {
    fun getPath(): String {
        val sb = StringBuilder("/")
        var start = false
        uri.pathSegments.forEach {
            if (it == "data") start = true
            if (start) {
                sb.append(it)
                sb.append("/")
            }
        }
        sb.delete(sb.length - 1, sb.length)
        return sb.toString()
    }
}