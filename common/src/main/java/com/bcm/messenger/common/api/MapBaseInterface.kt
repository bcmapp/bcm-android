package com.bcm.messenger.common.api

import android.view.View
import com.bcm.messenger.common.core.LocationItem

/**
 * Created by zjl on 2018/6/23.
 */

interface MapBaseInterface {
    /**
     * 地址查找
     *
     */
    fun doSearch(key: String, city: String?)

    /**
     * 定位
     */
    fun locate(latitude: Double, longitude: Double)

    /**
     * 移动摄像头
     */
    fun moveCamera(item: LocationItem, fromSearch: Boolean)

    /**
     * 设置回调
     */
    fun setCallback(callback: MapActionCallback)

    /**
     * 自身定位触发
     */
    fun selfLocate()
}