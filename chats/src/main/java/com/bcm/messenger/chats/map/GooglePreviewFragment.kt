package com.bcm.messenger.chats.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Google Map preview
 *
 * Created by zjl on 2018/6/23.
 */
class GooglePreviewFragment : BaseFragment(), OnMapReadyCallback {

    var mapFragment: SupportMapFragment? = null
    var latitude: Double = 0.0
    var longitude: Double = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_google_map_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        latitude = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
        longitude = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
        mapFragment = childFragmentManager.findFragmentById(R.id.google_map) as SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return
        with(googleMap) {
            val latLng = LatLng(latitude, longitude)
            moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            addMarker(MarkerOptions().position(latLng))
        }
    }
}
