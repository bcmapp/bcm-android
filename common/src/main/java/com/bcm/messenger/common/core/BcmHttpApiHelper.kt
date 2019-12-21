package com.bcm.messenger.common.core

import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode


/**
 * ling created in 2018/5/29
 **/
object BcmHttpApiHelper {

    private val TAG = "BcmHttpApiHelper"


    fun getApi(urlPath: String): String {
        return "${ServerNode.SCHEME}://bcm.social$urlPath"
    }


    fun getDownloadApi(urlPath: String): String {
        return "${BuildConfig.DOWNLOAD_URL}$urlPath"
    }
}