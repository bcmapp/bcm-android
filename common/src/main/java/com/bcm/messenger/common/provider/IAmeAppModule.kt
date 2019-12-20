package com.bcm.messenger.common.provider

import android.app.Activity
import com.bcm.messenger.common.event.HomeTopEvent

/**
 * Created by bcm.social.01 on 2018/6/20.
 */
interface IAmeAppModule : IAmeModule {
    /**
     * true 本地开发版本
     */
    fun isDevBuild(): Boolean

    /**
     * true 内测版本
     */
    fun isBetaBuild(): Boolean

    /**
     * true 发布到市场（google play , official 等）
     */
    fun isReleaseBuild(): Boolean

    /**
     * true 用户安装了google play 应用
     */
    fun isSupportGooglePlay(): Boolean

    fun isGooglePlayEdition(): Boolean

    /**
     * true 启用lbs策略
     */
    fun lbsEnable(): Boolean

    /**
     * true 启用了测试环境, false 启用的正式环境
     */
    fun testEnvEnable(): Boolean

    fun isEnabledHttps(): Boolean

    /**
     * true 用测试链，false 用公网链
     */
    fun useDevBlockChain(): Boolean

    /**
     * 返回构建时间
     */
    fun lastBuildTime(): Long

    /**
     * 返回当前的host配置
     */
    fun serverHost(): String

    /**
     * 系统分享
     */
    fun systemForward(activity: Activity, text: String)

    /**
     * 跳转主页
     */
    fun gotoHome(event: HomeTopEvent)

}