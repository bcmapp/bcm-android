package com.bcm.messenger.chats.privatechat.webrtc

/**
 * Created by Kin on 2019/10/10
 */
class EmptyTurnException : RuntimeException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}