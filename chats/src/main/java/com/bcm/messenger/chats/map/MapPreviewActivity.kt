package com.bcm.messenger.chats.map

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import com.bcm.route.annotation.Route
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAMapModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.setStatusBarLightMode
import kotlinx.android.synthetic.main.chats_activity_map_preview.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Created by zjl on 2018/6/22.
 */
@Route(routePath = ARouterConstants.Activity.MAP_PREVIEW)
class MapPreviewActivity : SwipeBaseActivity() {

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var title: String? = null
    private var addresss: String? = null
    private var mapType: Int = 0
    private var mapFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_map_preview)

        latitude = intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
        mapType = intent.getIntExtra(ARouterConstants.PARAM.MAP.MAPE_TYPE, 0)
        title = intent.getStringExtra(ARouterConstants.PARAM.MAP.TITLE)
        addresss = intent.getStringExtra(ARouterConstants.PARAM.MAP.ADDRESS)
        initView()

        window?.setStatusBarLightMode()
    }

    fun initView() {
        val provider = AmeProvider.get<IAMapModule>(ARouterConstants.Provider.PROVIDER_AMAP)
        mapFragment = if (provider?.isSupport(this, latitude, longitude) == true) {
            initFragment(R.id.map_preview_fragment, provider.getPreviewFragment(), null)
        } else {
            initFragment(R.id.map_preview_fragment, GooglePreviewFragment(), null)
        }
        ViewCompat.setTransitionName(findViewById(R.id.map_preview_fragment), ShareElements.Activity.PREVIEW_MAP)

        map_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finishAfterTransition()
            }
        })
        location_title.text = title
        location_address.text = addresss
    }

}