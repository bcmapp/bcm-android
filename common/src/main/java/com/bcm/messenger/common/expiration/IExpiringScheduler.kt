package com.bcm.messenger.common.expiration

interface IExpiringScheduler {
    fun getUid(): String
    fun scheduleDeletion(id: Long, mms: Boolean, expiresInMillis: Long)
    fun scheduleDeletion(id: Long, mms: Boolean, startedAtTimestamp: Long, expiresInMillis: Long)
    fun checkSchedule()
}