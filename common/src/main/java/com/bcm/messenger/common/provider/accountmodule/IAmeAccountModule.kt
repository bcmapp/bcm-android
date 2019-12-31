package com.bcm.messenger.common.provider.accountmodule

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.IAmeModule

interface IAmeAccountModule : IAmeModule {
    val context: AccountContext

    fun setContext(context: AccountContext)
}