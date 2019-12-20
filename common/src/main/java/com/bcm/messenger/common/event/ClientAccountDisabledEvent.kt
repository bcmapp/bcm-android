package com.bcm.messenger.common.event

/**
 * 当前客户端账号不可用事件
 * Created by bcm.social.01 on 2018/6/29.
 */
class ClientAccountDisabledEvent(val type: Int, val data: String? = null) {
    companion object {
        const val TYPE_EXPIRE = 1//token过期
        const val TYPE_EXCEPTION_LOGIN= 2//其他设备异常登录
        const val TYPE_ACCOUNT_GONE = 3//当前账号已销毁
    }
}