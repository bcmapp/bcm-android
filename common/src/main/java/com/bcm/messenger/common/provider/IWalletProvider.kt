package com.bcm.messenger.common.provider

import com.bcm.route.api.IRouteProvider

/**
 * 钱包模块提供的接口
 * Created by wjh on 2018/6/13
 */
interface IWalletProvider : IRouteProvider {

    /**
     * 初始化钱包数据
     */
    fun initWallet(privateKeyArray: ByteArray, password: String)

    /**
     * 登出钱包
     */
    fun logoutWallet()

    /**
     * 销毁钱包
     */
    fun destroyWallet()

}