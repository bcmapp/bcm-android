package com.bcm.messenger.common.provider.accountmodule

/**
 * 
 * Created by wjh on 2018/6/13
 */
interface IWalletModule : IAmeAccountModule {

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