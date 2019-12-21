package com.bcm.messenger.common.provider

import android.app.Activity
import com.bcm.messenger.common.event.HomeTopEvent

/**
 * Created by bcm.social.01 on 2018/6/20.
 */
interface IAmeAppModule : IAmeModule {
    /**
     * true 
     */
    fun isDevBuild(): Boolean

    /**
     * true 
     */
    fun isBetaBuild(): Boolean

    /**
     * true （google play , official ）
     */
    fun isReleaseBuild(): Boolean

    /**
     * true google play 
     */
    fun isSupportGooglePlay(): Boolean

    fun isGooglePlayEdition(): Boolean

    /**
     * true lbs
     */
    fun lbsEnable(): Boolean

    /**
     * true , false 
     */
    fun testEnvEnable(): Boolean

    fun isEnabledHttps(): Boolean

    /**
     * true ，false 
     */
    fun useDevBlockChain(): Boolean

    /**
     * 
     */
    fun lastBuildTime(): Long

    /**
     * host
     */
    fun serverHost(): String

    /**
     * 
     */
    fun systemForward(activity: Activity, text: String)

    /**
     * 
     */
    fun gotoHome(event: HomeTopEvent)

}