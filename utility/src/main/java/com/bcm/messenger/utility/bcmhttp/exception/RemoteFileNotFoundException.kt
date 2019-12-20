package com.bcm.messenger.utility.bcmhttp.exception

import java.io.IOException

/**
 * Created by Kin on 2019/12/16
 */
class RemoteFileNotFoundException : IOException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}