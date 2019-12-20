package com.bcm.messenger.map

import android.content.Context
import androidx.fragment.app.Fragment
import com.amap.api.maps2d.CoordinateConverter
import com.amap.api.maps2d.model.LatLng
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.MapApiConstants
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.provider.IAMapModule
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.route.annotation.Route
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GDMap Module imp
 * Created by wjh on 2019-09-29
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_AMAP)
class AMapModuleImp : IAMapModule {

    private val TAG = "AMapProviderImp"
    //pi GCJ_02_To_WGS_84
    private val PI = 3.14159265358979324

    override fun getWorkFragment(): Fragment {
        return GDMapFragment()
    }

    override fun getPreviewFragment(): Fragment {
        return GDPreviewFragment()
    }

    override fun isSupport(context: Context): Boolean {
        val location = AppUtil.getBestLocation(context, AppUtil.getLowCriteria())
        if (location != null) {
            return isSupport(context, location.latitude, location.longitude)
        }else {
            ALog.d(TAG, "isSupport false, getBestLocation fail")
        }
        return !MapApiConstants.isGoogleMapSupport(context)
    }

    override fun isSupport(context: Context, latitude: Double, longitude: Double): Boolean {
        val gdPair = toGDLatLng(context, latitude, longitude)
        ALog.d(TAG, "isSupport location: ${gdPair.first}, ${gdPair.second}")
        return CoordinateConverter.isAMapDataAvailable(gdPair.first, gdPair.second) || !MapApiConstants.isGoogleMapSupport(context)
    }

    override fun toGDLatLng(context: Context, latitude: Double, longitude: Double): Pair<Double, Double> {
        val latLng = LatLng(latitude, longitude)
        val converter = CoordinateConverter()
        converter.from(CoordinateConverter.CoordType.GPS)
        converter.coord(latLng)
        val result = converter.convert()
        return Pair(result.latitude, result.longitude)
    }

    override fun fromGDLatLng(context: Context, latitude: Double, longitude: Double): Pair<Double, Double> {
        val a = 6378245.0 //Krasovsky ellipsoid
        val ee = 0.00669342162296594323//Krasovsky ellipsoid
        var dLat = this.transformLat(longitude - 105.0, latitude - 35.0)
        var dLon = this.transformLon(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * this.PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * this.PI)
        dLon = dLon * 180.0 / (a / sqrtMagic * cos(radLat) * this.PI)

        return Pair(latitude - dLat, longitude - dLon)
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * this.PI) + 20.0 * sin(2.0 * x * this.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * this.PI) + 40.0 * sin(x / 3.0 * this.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * this.PI) + 300.0 * sin(x / 30.0 * this.PI)) * 2.0 / 3.0
        return ret
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * this.PI) + 20.0 * sin(2.0 * x * this.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * this.PI) + 40.0 * sin(y / 3.0 * this.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * this.PI) + 320 * sin(y * this.PI / 30.0)) * 2.0 / 3.0
        return ret
    }
}