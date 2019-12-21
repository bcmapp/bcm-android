package com.bcm.messenger.common.event

/**
 * 
 * Created by bcm.social.01 on 2018/6/29.
 */
class ClientAccountDisabledEvent(val type: Int, val data: String? = null) {
    companion object {
        const val TYPE_EXPIRE = 1//token
        const val TYPE_EXCEPTION_LOGIN= 2//
        const val TYPE_ACCOUNT_GONE = 3//
    }
}