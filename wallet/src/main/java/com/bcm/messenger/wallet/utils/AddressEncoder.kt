package com.bcm.messenger.wallet.utils


/**
 * 扫码地址处理器
 * Created by wjh on2018/05/31
 */
class AddressEncoder(val input: String) {

    var amount: String? = null
        private set
    var type: Byte = 0
        private set

    constructor(input: String, amount: String) : this(input) {
        this.amount = amount
    }

    /**
     * 解析出实际地址
     */
    fun parseAddress(): String {
        return when {
            input.startsWith(BtcWalletUtils.getQRScheme()) -> input.substring(BtcWalletUtils.getQRScheme().length)
            input.startsWith(EthWalletUtils.getQRScheme()) -> input.substring(EthWalletUtils.getQRScheme().length)
            else -> input
        }
    }
}
