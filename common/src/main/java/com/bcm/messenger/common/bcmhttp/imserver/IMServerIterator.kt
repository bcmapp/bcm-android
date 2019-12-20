package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.ServerNode

class IMServerIterator(list: List<ServerNode>, private val default: IMServerUrl):IServerIterator {
    private var index = -1
    private val serverList: List<IMServerUrl> = list.map { IMServerUrl(it.scheme, it.ip, it.port, it.area) }

    override fun next(): IMServerUrl {
        return if (serverList.isEmpty()) {
            default
        } else {
            if (index < 0) {
                index = 0
            } else if (index >= serverList.size) {
                ++index
                return random()
            }

            val node = serverList[index % serverList.size]
            ++index
            node
        }
    }

    private fun random(): IMServerUrl {
        return if (serverList.isEmpty()) {
            default
        } else {
            serverList.random()
        }
    }

    override fun isValid(): Boolean {
        return index <= serverList.size && serverList.isNotEmpty()
    }
}