package com.bcm.messenger.common.api

import com.bcm.messenger.common.core.LocationItem
/**
 * Created by zjl on 2018/6/26.
 */
interface MapActionCallback {

    fun onCameraMove() //
    fun onLocationSelect(item: LocationItem) //

    fun onNearByResult(list: List<LocationItem>) //
    fun onSearchResult(isAdd: Boolean, list: List<LocationItem>) //

}