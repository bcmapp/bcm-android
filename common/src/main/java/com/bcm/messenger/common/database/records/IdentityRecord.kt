package com.bcm.messenger.common.database.records

import androidx.room.Ignore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.model.IdentityDbModel
import com.bcm.messenger.common.database.repositories.IdentityRepo
import com.bcm.messenger.utility.Base64
import org.whispersystems.libsignal.IdentityKey

/**
 * Created by Kin on 2019/9/26
 */
class IdentityRecord : IdentityDbModel() {
    fun getAddress(accountContext: AccountContext): Address {
        return Address.from(accountContext, uid)
    }

    @Ignore lateinit var identityKey: IdentityKey
    override var key: String
        get() = super.key
        set(value) {
            identityKey = IdentityKey(Base64.decode(value), 0)
            super.key = value
        }

    fun isNonBlockingApproval() = nonBlockingApproval == 1

    fun isFirstUse() = firstUse == 1

    fun getVerifyStatus(): IdentityRepo.VerifiedStatus {
        return IdentityRepo.VerifiedStatus.values()[verified]
    }
}