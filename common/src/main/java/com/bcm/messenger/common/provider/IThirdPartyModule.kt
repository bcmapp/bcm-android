package com.bcm.messenger.common.provider

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.fragment.app.Fragment
import com.bcm.route.api.IRouteProvider

/**
 * 基本的第三方库工具接口
 * Created by wjh on 2019-09-16
 */
interface IUmengModule : IRouteProvider {

    fun onAppInit(context: Application)

    fun onActivityCreate(activity: Activity)

    fun onActivityResume(activity: Activity)

    fun onActivityPause(activity: Activity)

    fun getPushToken(context: Context): String?

    fun registerPushTag(context: Context, tag: String)

    fun unregisterPushTag(context: Context, tag: String)

    fun onAccountLogin(context: Context, uid: String)

    fun onAccountLogout(context: Context, uid: String)
}

interface IAMapModule : IRouteProvider {

    fun getWorkFragment(): Fragment

    fun getPreviewFragment(): Fragment

    fun isSupport(context: Context): Boolean

    fun isSupport(context: Context, latitude: Double, longitude: Double): Boolean

    fun toGDLatLng(context: Context, latitude: Double, longitude: Double): Pair<Double, Double>

    fun fromGDLatLng(context: Context, latitude: Double, longitude: Double): Pair<Double, Double>
}

interface IAFModule : IRouteProvider {

    fun onAppInit(context: Application)

}