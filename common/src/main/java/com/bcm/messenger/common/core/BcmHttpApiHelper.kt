package com.bcm.messenger.common.core

import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode


/**
 * 服务器域名配置在这里
 * ling created in 2018/5/29
 **/
object BcmHttpApiHelper {

    private val TAG = "BcmHttpApiHelper"

    /**
     * 获取当前ame地址
     */
    fun getApi(urlPath: String): String {
        return "${ServerNode.SCHEME}://bcm.social$urlPath"
    }

    /**
     * 获取当前多媒体地址
     */
    fun getDownloadApi(urlPath: String): String {
        return "${BuildConfig.DOWNLOAD_URL}$urlPath"
    }
}