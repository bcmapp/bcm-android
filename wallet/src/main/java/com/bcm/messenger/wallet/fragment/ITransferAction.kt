package com.bcm.messenger.wallet.fragment

/**
 * 转账接口
 * Created by wjh on 2019/7/6
 */
interface ITransferAction {
    /**
     * 设置转账地址
     */
    fun setTransferAddress(content: String)

    fun getScanRequestCode(): Int
}