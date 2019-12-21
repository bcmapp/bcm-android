package com.bcm.messenger.common.provider

import com.bcm.route.api.IRouteProvider

/**
 * 
 * Created by wjh on 2018/6/13
 */
interface IWalletProvider : IRouteProvider {

    /**
     * 
     */
    fun initWallet(privateKeyArray: ByteArray, password: String)

    /**
     * 
     */
    fun logoutWallet()

    /**
     * 
     */
    fun destroyWallet()

}