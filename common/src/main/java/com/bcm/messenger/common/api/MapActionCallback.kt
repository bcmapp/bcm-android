package com.bcm.messenger.common.api

import com.bcm.messenger.common.core.LocationItem
/**
 * Created by zjl on 2018/6/26.
 */
interface MapActionCallback {

    fun onCameraMove() //地图摄像头移动回调通知
    fun onLocationSelect(item: LocationItem) //地图地址选择回调

    fun onNearByResult(list: List<LocationItem>) //附近地址回调
    fun onSearchResult(isAdd: Boolean, list: List<LocationItem>) //搜索结果回调

}