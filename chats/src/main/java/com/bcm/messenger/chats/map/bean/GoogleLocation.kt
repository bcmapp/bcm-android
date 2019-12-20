package com.bcm.messenger.chats.map.bean

import com.bcm.messenger.utility.proguard.NotGuard


/**
 * Google Location structures
 * Created by zjl on 2018/6/26.
 */
data class GoogleLocation(val results: ArrayList<GoogleLocationItem>): NotGuard

data class GoogleLocationItem(val name: String, val geometry: Geometry, val vicinity: String): NotGuard

data class Geometry(val location: Location): NotGuard

data class Location(val lat: Double, val lng: Double): NotGuard