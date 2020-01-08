package com.bcm.messenger.chats.map

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.MapActionCallback
import com.bcm.messenger.common.api.MapBaseInterface
import com.bcm.messenger.common.core.LocationItem
import com.bcm.messenger.common.core.MapApiConstants
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAMapModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.CustomDataSearcher
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.chats_activity_map.*

/**
 * Select location
 *
 * Created by zjl on 2018/5/14.
 */
@Route(routePath = ARouterConstants.Activity.MAP)
class MapActivity : SwipeBaseActivity(), MapActionCallback {

    private val TAG = "MapActivity"

    private var choosePoiItem: LocationItem? = null

    private lateinit var nearByAdapter: LocationAdapter
    private lateinit var searchAdapter: LocationAdapter

    private var mapFragment: Fragment? = null
    private var mapApi: MapBaseInterface? = null

    private lateinit var me: Recipient
    private lateinit var meId: String
    private var mapType = 0

    private var mModule: IAMapModule? = null

    override fun onDestroy() {
        super.onDestroy()
        map_search_inner_bar?.recycle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_map)
        initView()
        checkGPS()
        try {
            me = Recipient.major()
        } catch (ex: Exception) {
            finish()
            return
        }
        meId = me.address.toString()

        window?.setStatusBarLightMode()
    }

    override fun onBackPressed() {
        if (map_search_layout?.visibility == View.VISIBLE) {
            map_search_layout?.visibility = View.GONE
            hideKeyboard()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCameraMove() {

    }

    override fun onLocationSelect(item: LocationItem) {
        choosePoiItem = item
    }

    override fun onNearByResult(list: List<LocationItem>) {
        nearByAdapter.resetDataList(list)
        map_nearby_rv?.post {
            map_nearby_rv?.smoothScrollToPosition(0)
        }
    }

    override fun onSearchResult(isAdd: Boolean, list: List<LocationItem>) {
        if (isAdd) {
            searchAdapter.addDataList(list)
        } else {
            searchAdapter.resetDataList(list)
        }
        if (searchAdapter.itemCount != 0) {
            map_search_result_shade?.visibility = View.GONE
            map_search_result_rv.visibility = View.VISIBLE
        } else {
            map_search_result_shade?.visibility = View.VISIBLE
            map_search_result_rv.visibility = View.GONE
        }
    }

    private fun initView() {
        val provider = AmeProvider.get<IAMapModule>(ARouterConstants.Provider.PROVIDER_AMAP)
        mModule = provider
        if (provider?.isSupport(this) == true) {
            mapType = MapApiConstants.GDMAP
            mapFragment = initFragment(R.id.map_container, provider.getWorkFragment(), null)
        } else {
            mapType = MapApiConstants.GOOGLEMAP
            mapFragment = initFragment(R.id.map_container, GoogleMapFragment(), null)
        }
        ALog.d(TAG, "initView mapType: $mapType")
        ViewCompat.setTransitionName(findViewById(R.id.map_container), ShareElements.Activity.CHOOSE_LOCATION)

        if (mapFragment is MapBaseInterface) {
            mapApi = mapFragment as MapBaseInterface
            mapApi?.setCallback(this)
        }

        map_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                sendLocation()
            }
        })

        map_search_outer_bar.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            map_search_layout?.visibility = View.VISIBLE
            map_search_inner_bar?.setSearchText("")
            map_search_inner_bar?.postDelayed({
                map_search_inner_bar?.requestSearchFocus()
                map_search_inner_bar?.showKeyboard()
            }, 100)

        }

        map_search_result_rv.layoutManager = LinearLayoutManager(this)
        searchAdapter = LocationAdapter(this, object : LocationAdapter.ItemMarkerListener {
            override fun onClick(view: View, item: LocationItem) {
                map_search_layout?.visibility = View.GONE
                mapApi?.moveCamera(item, true)
                hideKeyboard()
            }
        })
        map_search_result_rv.adapter = searchAdapter

        map_search_inner_bar?.enableIMESearch(true)
        map_search_inner_bar?.setOnSearchActionListener(object : CustomDataSearcher.OnSearchActionListener<LocationItem>() {
            override fun onSearchNull(results: List<LocationItem>) {

            }

            override fun onSearchResult(filter: String, results: List<LocationItem>) {

            }

            override fun onMatch(data: LocationItem, compare: String): Boolean {
                return true
            }

            override fun onSearchClick(searchText: String) {
                mapApi?.doSearch(searchText, null)
            }

        })

        map_self_location.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mapApi?.selfLocate()
            map_self_location?.setImageResource(R.drawable.chats_map_locate_icon)
        }

        nearByAdapter = LocationAdapter(this, object : LocationAdapter.ItemMarkerListener {
            override fun onClick(view: View, item: LocationItem) {
                map_self_location?.setImageResource(R.drawable.chats_map_locate_blue_icon)
                choosePoiItem = item
                mapApi?.moveCamera(item, false)
            }
        })
        map_nearby_rv?.layoutManager = LinearLayoutManager(this)
        map_nearby_rv?.adapter = nearByAdapter
    }

    private fun sendLocation() {
        val item = this.choosePoiItem
        if (item != null) {
            val intent = Intent()
            if (mapType == MapApiConstants.GDMAP) {
                val pair = mModule?.fromGDLatLng(this, item.latitude, item.longitude)
                intent.putExtra(ARouterConstants.PARAM.MAP.LATITUDE, pair?.first ?: item.latitude)
                intent.putExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, pair?.second
                        ?: item.longitude)
            } else {
                intent.putExtra(ARouterConstants.PARAM.MAP.LATITUDE, item.latitude)
                intent.putExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, item.longitude)
            }
            intent.putExtra(ARouterConstants.PARAM.MAP.TITLE, item.title)
            intent.putExtra(ARouterConstants.PARAM.MAP.ADDRESS, item.address)
            intent.putExtra(ARouterConstants.PARAM.MAP.MAPE_TYPE, mapType)

            ALog.d(TAG, "sendLocation mapType: $mapType, address: ${item.address}")
            setResult(Activity.RESULT_OK, intent)
            finish()

        } else {
            ToastUtil.show(this, resources.getString(R.string.chats_choose_location))
        }
    }

    private fun checkGPS() {
        if (!((AppUtil.isMobileNetwork(this) || AppUtil.isWiFiNetwork(this)) && AppUtil.isGpsEnabled(this))) {
            AmePopup.center.newBuilder()
                    .withContent(AppUtil.getString(this, R.string.chats_check_gps_network))
                    .withOkTitle(AppUtil.getString(this, R.string.chats_item_confirm))
                    .withOkListener {

                    }
                    .show(this)
        }

    }
}