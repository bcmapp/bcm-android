package com.bcm.messenger.wallet.btc

import com.bcm.messenger.wallet.btc.jsonrpc.TcpEndpoint


/**
 * Mycelium的TCP接口变更回调
 * Created by wjh on 2019/3/20
 */
interface ElectrumxServersChangedListener {

    fun serverListChanged(newEndpoints: Collection<TcpEndpoint>)

}