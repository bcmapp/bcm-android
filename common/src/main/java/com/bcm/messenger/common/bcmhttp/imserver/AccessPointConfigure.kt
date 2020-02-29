package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptor
import com.bcm.messenger.utility.storage.SPEditor

object AccessPointConfigure {
    private const val ACCESS_POINT_ENABLE = "access_point_enable"
    private const val ACCESS_POINT_SELECTED = "access_point_selected"
    private const val ACCESS_POINT_LIST = "access_point_list"

    private val pointEditor = SPEditor("server_config")
    private var pointList = mutableSetOf<String>()

    var isEnable:Boolean = false
        set(value) {
            field = value
            pointEditor.set(ACCESS_POINT_ENABLE, value)
        }

    var current:String = ""
        set(value) {
            field = value
            pointEditor.set(ACCESS_POINT_SELECTED, value)
        }

    val list:List<String> get() {
        return pointList.toList()
    }

    init {
        isEnable = pointEditor.get(ACCESS_POINT_ENABLE, false)
        current = pointEditor.get(ACCESS_POINT_SELECTED, "")
        pointList = pointEditor.get(ACCESS_POINT_LIST, mutableSetOf())
    }

    fun addPoint(point:String) {
        if (pointList.contains(point)) {
            return
        }
        pointList.add(point)

        pointEditor.set(ACCESS_POINT_LIST, pointList)
    }

    fun remotePoint(point: String) {
        if (!pointList.contains(point)) {
            return
        }
        pointList.remove(point)

        pointEditor.set(ACCESS_POINT_LIST, pointList)
    }
}