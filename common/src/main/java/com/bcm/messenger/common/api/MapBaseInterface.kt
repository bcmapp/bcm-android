package com.bcm.messenger.common.api

import android.view.View
import com.bcm.messenger.common.core.LocationItem

/**
 * Created by zjl on 2018/6/23.
 */

interface MapBaseInterface {
    /**
     * 
     *
     */
    fun doSearch(key: String, city: String?)

    /**
     * 
     */
    fun locate(latitude: Double, longitude: Double)

    /**
     * 
     */
    fun moveCamera(item: LocationItem, fromSearch: Boolean)

    /**
     * 
     */
    fun setCallback(callback: MapActionCallback)

    /**
     * 
     */
    fun selfLocate()
}