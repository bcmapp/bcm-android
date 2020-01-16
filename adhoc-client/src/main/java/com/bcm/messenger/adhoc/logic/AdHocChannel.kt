package com.bcm.messenger.adhoc.logic

import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.util.AdHocUtil
import com.bcm.messenger.common.utils.AppUtil

data class AdHocChannel(val cid:String, val channelName: String, val passwd: String) {
    companion object {
        const val OFFICIAL_CHANNEL = "im/official/channel/bongbongbong/didididi"
        const val OFFICIAL_PWD = "official_1234"
        val OFFICIAL = AdHocChannel(cid(OFFICIAL_CHANNEL, OFFICIAL_PWD), OFFICIAL_CHANNEL, OFFICIAL_PWD)

        fun cid(channelName: String, passwd: String): String {
            return AdHocUtil.toCid(channelName, passwd)
        }
    }

    fun viewName(): String {
        return if (cid == OFFICIAL.cid) {
            AppUtil.getString(R.string.adhoc_official_channel_name)
        } else {
            channelName
        }
    }
}