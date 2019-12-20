package com.bcm.messenger.wallet.btc

internal interface WapiClientLifecycle {
    fun setAppInForeground(isInForeground: Boolean)
    fun setNetworkConnected(isNetworkConnected: Boolean)
}
