package com.bcm.messenger.common.core

import com.bcm.messenger.common.utils.BCMPrivateKeyUtils

object AddressUtil {
    fun isValid(uid:String, identityKey:String): Boolean {
       return uid == BCMPrivateKeyUtils.provideUid(identityKey)
    }

    fun isValid(address:Address, identityKey:String): Boolean {
        return address.serialize() == BCMPrivateKeyUtils.provideUid(identityKey)
    }
}