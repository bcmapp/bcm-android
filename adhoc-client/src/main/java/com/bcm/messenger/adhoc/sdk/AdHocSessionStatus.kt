package com.bcm.messenger.adhoc.sdk

enum class AdHocSessionStatus(val v:Int) {
    NOT_EXIST(0),//need add session
    CONNECTING(1),
    READY(2),
    TIMEOUT(3),
    CLOSED(4)
}