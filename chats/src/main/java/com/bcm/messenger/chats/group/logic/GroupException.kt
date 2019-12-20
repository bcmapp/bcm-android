package com.bcm.messenger.chats.group.logic

import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil

/**
 * Created by bcm.social.01 on 2019/1/14.
 */
class GroupException(val err: String) : Exception(err) {

    companion object {
        fun error(it: Throwable, default: String): String {
            if (it is GroupException) {
                return it.err
            }
            return default
        }

        fun code(it:Throwable):Int {
            return ServerCodeUtil.getNetStatusCode(it)
        }
    }

    override val message: String?
        get() = if (err.isEmpty()) {
            super.message
        } else {
            err
        }
}