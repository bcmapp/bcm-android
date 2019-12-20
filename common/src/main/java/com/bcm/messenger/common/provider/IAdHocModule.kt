package com.bcm.messenger.common.provider

import android.content.Context
import com.bcm.route.api.IRouteProvider


interface IAdHocModule: IAmeModule {

    fun isAdHocMode(): Boolean
    fun configHocMode()
    fun repairAdHocServer()
    fun repairAdHocScanner()

    fun startAdHocServer(start: Boolean)
    fun startScan(start: Boolean)
    fun startBroadcast(start: Boolean)

    fun gotoPrivateChat(context: Context, uid: String)
}