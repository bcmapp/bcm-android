package com.bcm.messenger.common.expiration

import com.bcm.messenger.utility.logger.ALog

class IgnoreExpiringScheduler:IExpiringScheduler {
    override fun getUid(): String {
        ALog.w("IgnoreExpiringScheduler", "getUid")
        return ""
    }

    override fun scheduleDeletion(id: Long, mms: Boolean, expiresInMillis: Long) {
        ALog.w("IgnoreExpiringScheduler", "scheduleDeletion")
    }

    override fun scheduleDeletion(id: Long, mms: Boolean, startedAtTimestamp: Long, expiresInMillis: Long) {
        ALog.w("IgnoreExpiringScheduler", "scheduleDeletion")
    }

    override fun checkSchedule() {
        ALog.w("IgnoreExpiringScheduler", "checkSchedule")
    }
}