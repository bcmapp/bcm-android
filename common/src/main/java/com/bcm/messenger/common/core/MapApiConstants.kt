package com.bcm.messenger.common.core

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapsInitializer

/**
 * 地图Api
 * Created by zjl on 2018/6/22.
 */
object MapApiConstants {

    const val gdMapKey = "2238091900999d6b53e702898b2e1a04"
    const val gdImgUrl = "http://restapi.amap.com/v3/staticmap"
    const val googleMapKey = "AIzaSyCHdwjfuo49ONz-oxoUC9mfs5kPAWNJNVc"
    const val googleImgeUrl = "https://maps.googleapis.com/maps/api/staticmap"
    const val googlePlaceKey = "AIzaSyAjsHBpVbbeZ04o51-btMz7FCmN6_N77iE"
    const val googlePlaceUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    const val googlePlaceTextUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    const val GDMAP = 2
    const val GOOGLEMAP = 1

    fun isGoogleMapSupport(context: Context): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val result = MapsInitializer.initialize(context)
        return status == ConnectionResult.SUCCESS && result == ConnectionResult.SUCCESS
    }
}