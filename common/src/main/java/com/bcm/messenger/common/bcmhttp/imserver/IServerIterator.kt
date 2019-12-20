package com.bcm.messenger.common.bcmhttp.imserver

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl

interface IServerIterator {
    fun next(): IMServerUrl
    fun isValid(): Boolean
}