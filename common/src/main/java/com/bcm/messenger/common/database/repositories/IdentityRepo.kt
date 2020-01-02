package com.bcm.messenger.common.database.repositories

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.records.IdentityRecord
import com.bcm.messenger.common.utils.IdentityUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import org.greenrobot.eventbus.EventBus
import org.whispersystems.libsignal.IdentityKey

/**
 * Created by Kin on 2019/9/26
 */
class IdentityRepo(accountContext: AccountContext) {
    enum class VerifiedStatus(val type: Int) {
        DEFAULT(0),
        VERIFIED(1),
        UNVERIFIED(2)
    }

    private val identityDao = UserDatabase.getDatabase(accountContext).getIdentityDao()

    fun insertIdentities(identities: List<IdentityRecord>) {
        identityDao.insertIdentities(identities)
    }

    fun getIdentityForNonBlockingApproval(uid: String): IdentityRecord? {
        val record = identityDao.queryIdentity(uid)
        if (record != null) {
            if (!record.isNonBlockingApproval()) {
                IdentityUtil.saveIdentity(AppContextHolder.APP_CONTEXT, uid, record.identityKey, true)
                record.nonBlockingApproval = 1
            }
            return record
        }
        return null
    }

    fun getIdentityRecord(uid: String) = identityDao.queryIdentity(uid)

    fun saveIdentity(uid: String, identityKey: IdentityKey, verifyStatus: VerifiedStatus,
                     isFirstUse: Boolean, timestamp: Long, isNonBlockingApproval: Boolean) {
        val keyString = Base64.encodeBytes(identityKey.serialize())

        val model = IdentityRecord()
        model.uid = uid
        model.key = keyString
        model.timestamp = timestamp
        model.verified = verifyStatus.type
        model.nonBlockingApproval = if (isNonBlockingApproval) 1 else 0
        model.firstUse = if (isFirstUse) 1 else 0

        identityDao.insertIdentity(model)

        EventBus.getDefault().post(model)
    }

    fun setApproval(uid: String, isNonBlockingApproval: Boolean) {
        val record = identityDao.queryIdentity(uid)
        if (record != null) {
            record.nonBlockingApproval = if (isNonBlockingApproval) 1 else 0
            identityDao.updateIdentity(record)
        }
    }

    fun setVerified(uid: String, identityKey: IdentityKey, verifyStatus: VerifiedStatus) {
        val record = identityDao.queryIdentity(uid, Base64.encodeBytes(identityKey.serialize()))
        if (record != null) {
            record.verified = verifyStatus.type
            val int = identityDao.updateIdentity(record)
            if (int > 0) {
                EventBus.getDefault().post(record)
            }
        }
    }
}