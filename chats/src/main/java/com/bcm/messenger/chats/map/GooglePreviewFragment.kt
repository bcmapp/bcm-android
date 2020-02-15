package com.bcm.messenger.chats.map

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.MapApiConstants
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.places.Places
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.chats_fragment_google_map_preview.*

/**
 * Google Map preview
 *
 * Created by zjl on 2018/6/23.
 */
class GooglePreviewFragment : Fragment(), OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener {
    var mapFragment: SupportMapFragment? = null
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    private var googleClient: GoogleApiClient? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_google_map_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        latitude = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
        longitude = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
        mapFragment = childFragmentManager.findFragmentById(R.id.google_map) as SupportMapFragment

        try {
            mapFragment?.getMapAsync(this)

            googleClient = GoogleApiClient.Builder(activity)
                    .addApi(Places.GEO_DATA_API)
                    .addApi(Places.PLACE_DETECTION_API)
                    .enableAutoManage(activity, this)
                    .build()

            mapFragment?.getMapAsync(this)
        } catch (e:Throwable) {
            ALog.e("GooglePreviewFragment", "onViewCreated", e)
            showStaticView()
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        if (null != googleMap) {
            with(googleMap) {
                val latLng = LatLng(latitude, longitude)
                moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                addMarker(MarkerOptions().position(latLng))
            }
        } else {
            showStaticView()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (googleClient?.isConnected == true) {
            googleClient?.disconnect()
        }
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        if (p0.errorCode != ConnectionResult.SUCCESS) {
            showStaticView()
        }
    }

    private fun showStaticView() {
        static_map_view.visibility = View.VISIBLE

        val display = context?.resources?.displayMetrics?:return
        val w = display.widthPixels - 20.dp2Px()
        val h = display.heightPixels - AppContextHolder.APP_CONTEXT.getStatusBarHeight()
        GlideApp.with(AppContextHolder.APP_CONTEXT).load(getMap(latitude, longitude, w, h))
                .into(static_map_view)
    }


    private fun getMap(lat: Double, lon: Double, width: Int, height: Int): String {
        var builder: StringBuilder? = null
        builder = StringBuilder(MapApiConstants.googleImgeUrl)
        builder.append("?center=").append(lat).append(",").append(lon)
        builder.append("&zoom=").append("15")
        builder.append("&size=").append(width).append("x").append(height)
        builder.append("&markers=").append(lat).append(",").append(lon)
        builder.append("&key=").append(MapApiConstants.googlePlaceKey)

        return builder.toString()
    }
}
