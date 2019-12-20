package com.bcm.messenger.wallet.btc

import com.bcm.messenger.wallet.btc.net.ServerEndpoints

/**
 * Mycelium的HTTP接口变更回调
 * Created by wjh on 2019/3/24
 */
interface NormalServersChangedListener {

    fun serverListChanged(newEndpoints: ServerEndpoints)

}