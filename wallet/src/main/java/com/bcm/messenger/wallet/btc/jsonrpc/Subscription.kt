package com.bcm.messenger.wallet.btc.jsonrpc

import com.bcm.messenger.utility.proguard.NotGuard


class Subscription(val methodName: String, val params: RpcParams, val callback: Consumer<AbstractResponse>) : NotGuard