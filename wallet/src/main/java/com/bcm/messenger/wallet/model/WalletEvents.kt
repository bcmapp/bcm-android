package com.bcm.messenger.wallet.model

import com.bcm.messenger.wallet.utils.BCMWalletManager

/**
 * Created by wjh on 2018/7/17
 */
data class WalletProgressEvent(val stage: BCMWalletManager.WalletStage, val progress: Int)

data class WalletNewEvent(val new: BCMWallet?, val message: String?)

data class WalletTransferEvent(val tx: String?, val wallet: BCMWallet, val message: String?)