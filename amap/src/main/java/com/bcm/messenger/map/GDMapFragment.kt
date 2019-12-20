package com.bcm.messenger.map

import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.LocationSource
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.*
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.bcm.messenger.common.api.MapBaseInterface
import com.bcm.messenger.common.api.MapActionCallback
import com.bcm.messenger.common.core.LocationItem
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.utility.logger.ALog
import com.example.amap.R
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * GDMap fragment
 * Created by zjl on 2018/6/23.
 */
class GDMapFragment : Fragment(), AMapLocationListener, AMap.OnMarkerClickListener, LocationSource, MapBaseInterface {

    private val TAG = "GDMapFragment"

    private lateinit var map: MapView
    private var aMap: AMap? = null
    private var myLocationStyle: MyLocationStyle? = null
    private var mLocationClient: AMapLocationClient? = null
    private var mLocationOption: AMapLocationClientOption? = null
    private var mListener: LocationSource.OnLocationChangedListener? = null

    private var query: PoiSearch.Query? = null
    private var mSelfLatLag: LocationItem? = null
    private var firstLocation = true

    private var mCameraFromAction = false
    private var mSearchItem: LocationItem? = null

    private var mCallback: MapActionCallback? = null
    private var defaultCity: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.amap_fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        map = view.findViewById(R.id.map)
        map.onCreate(savedInstanceState)
        aMap = map.map
        setUpMap()
        initLoc()
    }

    private fun setUpMap() {
        myLocationStyle = MyLocationStyle()
        myLocationStyle?.strokeColor(Color.argb(0, 0, 0, 0))
        myLocationStyle?.radiusFillColor(Color.argb(0, 0, 0, 0))
        myLocationStyle?.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.common_self_location))
        aMap?.setMapLanguage(getSelectedLocale(context).language)
        aMap?.setMyLocationStyle(myLocationStyle)
        aMap?.isMyLocationEnabled = true
        aMap?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p0: CameraPosition?) {

            }

            override fun onCameraChangeFinish(position: CameraPosition?) {
                position ?: return
                if (!mCameraFromAction) {
                    mSearchItem = null
                    locate(getMapCenterPoint())
                } else {
                    mCameraFromAction = false
                    if (mSearchItem != null) {
                        locate(getMapCenterPoint())
                    }
                }
            }

        })
        val uriSettings = aMap?.uiSettings
        uriSettings?.isZoomControlsEnabled = false
    }


    private fun initLoc() {
        mLocationClient = AMapLocationClient(context!!.applicationContext)
        mLocationClient?.setLocationListener(this)
        mLocationOption = AMapLocationClientOption()
        mLocationOption?.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        mLocationOption?.isNeedAddress = true
        mLocationOption?.isOnceLocation = false
        mLocationOption?.isWifiActiveScan = false
        mLocationOption?.isMockEnable = false
        mLocationOption?.interval = (3000)
        mLocationClient?.setLocationOption(mLocationOption)
        mLocationClient?.startLocation()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        map.onDestroy()
        mLocationClient?.onDestroy()
        myLocationStyle = null
        mCallback = null
        super.onDestroy()
    }

    private fun locate(lanLng: LatLng) {
        locate(lanLng.latitude, lanLng.longitude)
    }

    private fun doSearchQuery(city: String, latitude: Double, longtitude: Double) {
        query = PoiSearch.Query("", "", city) // first: query param，second: query type，third: area（blank: china all area）
        query?.pageSize = 20
        query?.pageNum = 1

        val poiSearch = PoiSearch(context, query)
        poiSearch.setOnPoiSearchListener(onNearByListener)
        poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(latitude, longtitude), 1000, true)
        poiSearch.searchPOIAsyn()
    }

    private fun doSearchQuery(keyWord: String, city: String?) {
        query = if (city != null)
            PoiSearch.Query(keyWord, "", city)
        else
            PoiSearch.Query(keyWord, "", defaultCity)
        query?.pageSize = 20
        query?.pageNum = 1

        val poiSearch = PoiSearch(context ?: return, query)
        poiSearch.setOnPoiSearchListener(onSearchListener)
        poiSearch.searchPOIAsyn()
    }

    private val onSearchListener = object : PoiSearch.OnPoiSearchListener {
        override fun onPoiSearched(result: PoiResult?, rCode: Int) {
            if (rCode == 1000) {
                if (result?.query == query) {
                    val poiList = mutableListOf<LocationItem>()
                    var item: LocationItem
                    result?.pois?.forEach {
                        item = LocationItem(false, it.latLonPoint.latitude, it.latLonPoint.longitude, it.title, it.snippet)
                        poiList.add(item)
                    }
                    mCallback?.onSearchResult(false, poiList)
                }
            }
        }

        override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {

        }
    }

    private val onNearByListener = object : PoiSearch.OnPoiSearchListener {
        override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {
        }

        override fun onPoiSearched(result: PoiResult?, rCode: Int) {
            if (rCode == 1000) {
                if (result?.query == query) {
                    val poiList: MutableList<LocationItem> = arrayListOf()
                    mSearchItem?.let {
                        poiList.add(it)
                    }
                    var item: LocationItem
                    result?.pois?.forEach {
                        item = LocationItem(false, it.latLonPoint.latitude, it.latLonPoint.longitude, it.title, it.snippet)
                        if (item != mSearchItem) {
                            poiList.add(item)
                        }
                    }
                    if (poiList.isNotEmpty()) {
                        poiList[0].markFlag = true
                        mCallback?.onLocationSelect(poiList[0])
                    }
                    mCallback?.onNearByResult(poiList)
                }
            }
        }
    }


    override fun locate(latitude: Double, longitude: Double) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            doSearchQuery("", latitude, longitude)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {
                    ALog.e(TAG, "locate latitude: $latitude, longitude: $longitude error", it)
                })
    }

    override fun onLocationChanged(amapLocation: AMapLocation?) {
        if (amapLocation != null && amapLocation.errorCode == 0) {
            defaultCity = amapLocation.city
            mSelfLatLag = LocationItem(false, amapLocation.latitude, amapLocation.longitude)
            if (firstLocation) {
                firstLocation = false
                aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(amapLocation.latitude, amapLocation.longitude), 100f))
                locate(mSelfLatLag?.latitude ?: 0.0, mSelfLatLag?.longitude ?: 0.0)
            }
        }
    }


    override fun onMarkerClick(marker: Marker?): Boolean {
        if (marker != null) {
            locate(marker.position.latitude, marker.position.longitude)
        }
        return true
    }

    override fun activate(listener: LocationSource.OnLocationChangedListener?) {
        mListener = listener
    }

    override fun deactivate() {
        mListener = null
    }

    fun getMapCenterPoint(): LatLng {
        val left = map.left
        val top = map.top
        val right = map.right
        val bottom = map.bottom

        val x = (map.x + (right - left) / 2).toInt()
        val y = (map.y + (bottom - top) / 2).toInt()
        val projection = aMap?.projection
        return projection?.fromScreenLocation(Point(x, y)) ?: LatLng(0.0, 0.0)

    }

    override fun moveCamera(item: LocationItem, fromSearch: Boolean) {
        mCameraFromAction = true
        if (fromSearch) {
            mSearchItem = item
        }else {
            mSearchItem = null
        }
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(item.latitude, item.longitude), 100f))
        mCallback?.onCameraMove()
    }

    override fun doSearch(text: String, city: String?) {
        doSearchQuery(text, city)
    }

    override fun setCallback(callback: MapActionCallback) {
        this.mCallback = callback
    }

    override fun selfLocate() {
        mSelfLatLag?.let {
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 100f))
        }
    }
}