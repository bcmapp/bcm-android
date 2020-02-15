package com.bcm.messenger.chats.map

import android.app.Activity
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.map.bean.GoogleLocation
import com.bcm.messenger.common.api.MapActionCallback
import com.bcm.messenger.common.api.MapBaseInterface
import com.bcm.messenger.common.bcmhttp.TPHttp
import com.bcm.messenger.common.core.LocationItem
import com.bcm.messenger.common.core.MapApiConstants
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.callback.JsonDeserializeCallback
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.logger.ALog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.data.DataBufferUtils
import com.google.android.gms.location.places.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RuntimeRemoteException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Tasks
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import java.text.DecimalFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.sqrt


/**
 * google map fragment
 * Created by zjl on 2018/6/25.
 */
class GoogleMapFragment : Fragment(), OnMapReadyCallback, MapBaseInterface, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private val TAG = "GoogleMapFragment"
    private var mapFragment: SupportMapFragment? = null
    private var googleMap: GoogleMap? = null

    private var googleClient: GoogleApiClient? = null
    private var isFirstLoc = true
    private var selfLatLag: LatLng? = null
    private var locationManager: LocationManager? = null
    private var mGeoDataClient: GeoDataClient? = null
    private var mCallback: MapActionCallback? = null

    private var mCameraFormAction: Boolean = false
    private var mSearchItem: LocationItem? = null

    private var mNearByDisposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_google_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        mapFragment = childFragmentManager.findFragmentById(R.id.google_map) as SupportMapFragment

        try {
            mapFragment?.getMapAsync(this)
            googleClient = GoogleApiClient.Builder(activity)
                    .addApi(Places.GEO_DATA_API)
                    .addApi(Places.PLACE_DETECTION_API)
                    .enableAutoManage(activity, this)
                    .build()
            locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            mGeoDataClient = Places.getGeoDataClient(context as Activity, null)
        } catch (e:Throwable) {
            ALog.e("GoogleMapFragment", "onViewCreated", e)
        }
    }

    override fun onDestroy() {
        ALog.d(TAG, "onDestroy")
        mNearByDisposable?.dispose()
        if (googleClient?.isConnected == true) {
            googleClient?.disconnect()
        }
        val activity = activity
        activity?.let {
            googleClient?.stopAutoManage(it)
        }
        googleMap?.clear()
        mCallback = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 10f, this)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 10f, this)
        } catch (e: SecurityException) {
            ALog.e(TAG, e)
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager?.removeUpdates(this)
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return
        this.googleMap = googleMap
        with(googleMap.uiSettings) {
            isZoomGesturesEnabled = true
            isMyLocationButtonEnabled = true
            isScrollGesturesEnabled = true
        }
        try {
            googleMap.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            ALog.e(TAG, e)
        }

        googleMap.setOnCameraChangeListener(object : GoogleMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) {
                position ?: return
                if (!mCameraFormAction) {
                    mSearchItem = null
                    locate(position.target.latitude, position.target.longitude)
                }else {
                    mCameraFormAction = false
                    if (mSearchItem != null) {
                        locate(position.target.latitude, position.target.longitude)
                    }
                }
            }
        })
        googleClient?.connect()
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        ALog.e(TAG, "onConnectionFailed code: ${p0.errorCode}, ${p0.errorMessage}")
    }

    override fun doSearch(key: String, city: String?) {
        Observable.create(ObservableOnSubscribe<ArrayList<AutocompletePrediction>> {
            it.onNext(getAutocomplete(key) ?: throw Exception("search result is null"))
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mCallback?.onSearchResult(false, listOf())
                    for (item in it) {
                        val placeResult = mGeoDataClient?.getPlaceById(item.placeId)
                        placeResult?.addOnCompleteListener(mUpdatePlaceDetailsCallback)
                    }
                }, {
                    ALog.e(TAG, "doSearch error", it)
                })
    }


    private val mUpdatePlaceDetailsCallback = OnCompleteListener<PlaceBufferResponse> { task ->
        try {
            val place = task.result?.get(0)
            if (place != null) {
                mCallback?.onSearchResult(true, listOf(LocationItem(false, place.latLng.latitude, place.latLng.longitude, place.name.toString(), place.address.toString())))
                ALog.i(TAG, "Place details received: " + place.name)
            }
            task.result?.release()
        } catch (e: RuntimeRemoteException) {
            ALog.e(TAG, e)
        }
    }


    override fun locate(latitude: Double, longitude: Double) {
        ALog.d(TAG, "locate result item: $latitude, $longitude")
        getGoogleNearByPlaces(latitude, longitude, 1000) {list ->

            ALog.d(TAG, "locate callback ${list.size}")
            if (list.isNotEmpty()) {
                list.first().markFlag = true
                mCallback?.onLocationSelect(list.first())
            }
            mCallback?.onNearByResult(list)

        }
    }

    override fun moveCamera(item: LocationItem, fromSearch: Boolean) {
        mCameraFormAction = true
        if (fromSearch) {
            mSearchItem = item
        }else {
            mSearchItem = null
        }
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(item.latitude, item.longitude), 16f))
        googleMap?.animateCamera(CameraUpdateFactory.zoomTo(16f))
        mCallback?.onCameraMove()
    }

    override fun setCallback(callback: MapActionCallback) {
        this.mCallback = callback
    }

    override fun selfLocate() {
        if (selfLatLag != null) {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLng(selfLatLag))
            googleMap?.animateCamera(CameraUpdateFactory.zoomTo(16f))
        }
    }


    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            ALog.d(TAG, "onLocationChanged latitude: ${location.latitude}, longitude:${location.longitude}")
            if (isFirstLoc) {
                selfLatLag = LatLng(location.latitude, location.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLng(selfLatLag))
                googleMap?.animateCamera(CameraUpdateFactory.zoomTo(16f))
                locate(location.latitude, location.longitude)
                isFirstLoc = false
            } else {
                selfLatLag = LatLng(location.latitude, location.longitude)
            }
        }
    }

    override fun onProviderDisabled(provider: String?) {

    }

    override fun onProviderEnabled(provider: String?) {

    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

    }


    private fun getDefaultLocation(latitude: Double, longitude: Double): LocationItem {
        val address = "${DecimalFormat(".######").format(latitude)}, ${DecimalFormat(".######").format(latitude)}"
        return LocationItem(false, latitude, longitude, AppUtil.getString(R.string.chats_map_camera_mark_location_title), address)
    }

    private fun getGoogleNearByPlaces(latitude: Double, longitude: Double, radius: Int, callback: (nearby: List<LocationItem>) -> Unit) {

        mNearByDisposable?.dispose()
        mNearByDisposable = Observable.create<List<LocationItem>> {emitter ->

            try {
                val urlBuilder = StringBuilder(MapApiConstants.googlePlaceUrl)
                urlBuilder.append("?location=").append(latitude.toString()).append(",").append(longitude.toString())
                urlBuilder.append("&radius=").append(radius.toString())
                urlBuilder.append("&key=").append(MapApiConstants.googlePlaceKey)
                urlBuilder.append("&language=").append(getSelectedLocale(AppContextHolder.APP_CONTEXT).language)
                TPHttp.get().url(urlBuilder.toString()).addHeader("content-type", "application/json")
                        .build()
                        .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                        .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                        .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                        .enqueue(object : JsonDeserializeCallback<GoogleLocation>() {
                            override fun onError(call: Call?, e: Exception?, id: Long) {
                                ALog.e(TAG, "getGoogleNearByPlaces error", e)
                                emitter.onNext(listOf())
                                emitter.onComplete()
                            }

                            override fun onResponse(response: GoogleLocation?, id: Long) {
                                val list = mutableListOf<LocationItem>()
                                val choose = if (mSearchItem != null) mSearchItem else {
                                    getDefaultLocation(latitude, longitude)
                                }
                                var exist = false
                                if (response != null) {
                                    var item: LocationItem
                                    response.results.forEach {
                                        item = LocationItem(false, it.geometry.location.lat, it.geometry.location.lng, it.name, it.vicinity)
                                        ALog.d(TAG, "locate result item: ${item.latitude}, ${item.longitude}, ${item.title}, ${item.address}")
                                        list.add(item)
                                        if (item.latitude == choose?.latitude && item.longitude == choose?.longitude) {
                                            exist = true
                                        }
                                    }
                                    list.sortWith(Comparator { o1, o2 ->
                                        var a = o1.latitude - latitude
                                        var b = o1.longitude - longitude
                                        val t1 = sqrt(a * a + b * b)
                                        a = o2.latitude - latitude
                                        b = o2.longitude - longitude
                                        val t2 = sqrt(a * a + b * b)
                                        when {
                                            t1 > t2 -> 1
                                            t1 < t2 -> -1
                                            else -> 0
                                        }
                                    })
                                    if (!exist && choose != null) {
                                        list.add(0, choose)
                                    }
                                }
                                emitter.onNext(list)
                                emitter.onComplete()
                            }

                        })

            }catch (ex: Exception) {
                emitter.onError(ex)
            }
        }
                .delaySubscription(500, TimeUnit.MILLISECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "getGoogleNearByPlaces fail", it)
                    callback.invoke(listOf(getDefaultLocation(latitude, longitude)))
                })



    }

    private fun getAutocomplete(constraint: CharSequence): ArrayList<AutocompletePrediction>? {
        ALog.d(TAG, "Starting autocomplete query for: $constraint")

        // Submit the query to the autocomplete API and retrieve a PendingResult that will
        // contain the results when the query completes.
        val results = mGeoDataClient?.getAutocompletePredictions(constraint.toString(), null, null)
                ?: return null
        // This method should have been called off the main UI thread. Block and wait for at most
        // 60s for a result from the API.
        try {
            Tasks.await<AutocompletePredictionBufferResponse>(results, 2, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: TimeoutException) {
            e.printStackTrace()
        }

        return try {
            val autocompletePredictions = results.result

            ALog.d(TAG, "Query completed. Received " + autocompletePredictions?.count + " predictions.")

            // Freeze the results immutable representation that can be stored safely.
            DataBufferUtils.freezeAndClose<AutocompletePrediction, AutocompletePrediction>(autocompletePredictions)
        } catch (e: Exception) {
            // If the query did not complete successfully return null
            ALog.e(TAG, "Error getting autocomplete prediction API call", e)
            null
        }

    }
}