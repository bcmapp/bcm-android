package com.bcm.messenger.common.core

/**
 * 定位显示项
 * Created by zjl on 2018/6/25.
 */
data class LocationItem(var markFlag: Boolean, val latitude: Double, val longitude: Double, var title: String = "", var address: String = "") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationItem

        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (title != other.title) return false
        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + address.hashCode()
        return result
    }
}