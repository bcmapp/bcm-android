package com.bcm.demo

import android.app.Application
import com.bcm.route.api.BcmRouter

/**
 * Created by "Kin" on 2019/7/25
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        BcmRouter.openDebug()
        BcmRouter.init(this)
    }
}