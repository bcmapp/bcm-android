package com.bcm.messenger.common.provider

import com.bcm.route.api.IRouteProvider

interface IAmeModule:IRouteProvider {
    fun initModule()
    fun uninitModule()
}