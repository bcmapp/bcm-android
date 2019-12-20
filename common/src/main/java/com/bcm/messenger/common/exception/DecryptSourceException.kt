package com.bcm.messenger.common.exception

/**
 * Created by Kin on 2019/5/6
 */
class DecryptSourceException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}