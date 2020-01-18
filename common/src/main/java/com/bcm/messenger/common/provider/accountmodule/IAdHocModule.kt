package com.bcm.messenger.common.provider.accountmodule

import android.content.Context
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.IAmeModule


interface IAdHocModule: IAmeModule {

    fun isAdHocMode(): Boolean
    fun configHocMode(accountContext: AccountContext)
    fun repairAdHocServer()
    fun repairAdHocScanner()

    fun startAdHocServer(start: Boolean)
    fun startScan(start: Boolean)
    fun startBroadcast(start: Boolean)

    fun gotoPrivateChat(context: Context, uid: String)
}