package com.bcm.messenger.common.metrics

import android.os.Build
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.common.utils.getVersionCode
import com.bcm.messenger.common.utils.getVersionName
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2019/8/19
 */
data class ReportData(
        val protoVersion: Int,
        val uid: String,
        val pubKey: String,
        val signature: String,

        val clientInfo: ClientInfo,

        val histogram: List<Histogram>,
        val counter: List<Counter>
) : NotGuard {
    fun toJson() = GsonUtils.toJson(this)
}

data class ClientInfo(val area: String, val accessNet: String) : NotGuard {
    val osType = "android"
    val osVersion = "Android ${Build.VERSION.RELEASE}"
    val phoneModel = Build.MODEL
    val bcmVersion = AppContextHolder.APP_CONTEXT.getVersionName()
    val bcmBuildCode = AppContextHolder.APP_CONTEXT.getVersionCode()
}

data class Histogram(
        val topic: String,
        val serverIp: String,
        val protoKey: String,
        val retCode: String,
        val timestamp: Long,

        val cfgType: String,
        val cfgVersion: Int,

        val timeData: TimeData
) : NotGuard

data class TimeData(
        var totalTime: Long,
        var totalCount: Int,
        val slices: MutableMap<String, Int>
) : NotGuard

data class Counter(
        val topic: String,
        val counterName: String,
        val timestamp: Long,
        var increment: Int
) : NotGuard

class MetricsConfigs {
    var protoVersion = 0
    var histogramConfig = linkedMapOf<String, TimeSlice>()
}

class TimeSlice(var name: String) : NotGuard {
    var timeSlice = listOf<Long>()
    var cfgVersion = 1

    fun getZone(time: Long): String {
        timeSlice.forEachIndexed { index, slice ->
            if (time < slice) {
                if (index == 0) return "0-$slice"
                return "${timeSlice[index - 1]}-$slice"
            }
        }
        return "${timeSlice[timeSlice.lastIndex]}-"
    }

    fun getZones(): List<String> {
        val zoneList = mutableListOf<String>()
        timeSlice.forEachIndexed { index, l ->
            if (index == 0) zoneList.add("0-$l")
            else zoneList.add("${timeSlice[index - 1]}-$l")
        }
        zoneList.add("${timeSlice[timeSlice.lastIndex]}-")
        return zoneList
    }
}

data class HistogramConfigReq(
        val uid: String,
        val pubKey: String,
        val signature: String
) : NotGuard {
    fun toJson() = GsonUtils.toJson(this)
}

class ConfigStorage {
    val configList = mutableListOf<Pair<String, String>>()
}

class SliceStorage {
    val sliceList = mutableListOf<TimeSlice>()
}