package com.bcm.messenger.common.database.repositories

/**
 * Created by Kin on 2019/9/16
 */

const val TOTAL_MASK = 0xFFFFFFFF
// Base Types
const val BASE_TYPE_MASK: Long = 0x1F

const val INCOMING_CALL_TYPE: Long = 1
const val OUTGOING_CALL_TYPE: Long = 2
const val MISSED_CALL_TYPE: Long = 3
const val JOINED_TYPE: Long = 4
const val MISSED_INCOMING_CALL_TYPE: Long = 5
const val MISSED_OUTGOING_CALL_TYPE: Long = 6

const val BASE_INBOX_TYPE: Long = 20
const val BASE_OUTBOX_TYPE: Long = 21
const val BASE_SENDING_TYPE: Long = 22
const val BASE_SENT_TYPE: Long = 23
const val BASE_SENT_FAILED_TYPE: Long = 24
const val BASE_PENDING_SECURE_SMS_FALLBACK: Long = 25
const val BASE_PENDING_INSECURE_SMS_FALLBACK: Long = 26
const val BASE_DRAFT_TYPE: Long = 27
const val BASE_FAIL_CANNOT_DECRYPT_TYPE: Long = 28
const val BASE_AT_ME_TYPE: Long = 29
const val BASE_JOIN_REQUEST: Long = 30

val OUTGOING_MESSAGE_TYPES = longArrayOf(BASE_OUTBOX_TYPE, BASE_SENT_TYPE, BASE_SENDING_TYPE, BASE_SENT_FAILED_TYPE, BASE_PENDING_SECURE_SMS_FALLBACK, BASE_PENDING_INSECURE_SMS_FALLBACK, OUTGOING_CALL_TYPE, MISSED_OUTGOING_CALL_TYPE, BASE_FAIL_CANNOT_DECRYPT_TYPE)

// Message attributes
const val MESSAGE_ATTRIBUTE_MASK: Long = 0xE0
const val MESSAGE_FORCE_SMS_BIT: Long = 0x40


//TODO:  type  location
//FIXME:  1 
const val KEY_LOCATION_BIT: Long = 0x8400
// Key Exchange Information
const val KEY_EXCHANGE_MASK: Long = 0xFF00
const val KEY_EXCHANGE_BIT: Long = 0x8000
const val KEY_EXCHANGE_IDENTITY_VERIFIED_BIT: Long = 0x4000
const val KEY_EXCHANGE_IDENTITY_DEFAULT_BIT: Long = 0x2000
const val KEY_EXCHANGE_CORRUPTED_BIT: Long = 0x1000
const val KEY_EXCHANGE_INVALID_VERSION_BIT: Long = 0x800
const val KEY_EXCHANGE_BUNDLE_BIT: Long = 0x400
const val KEY_EXCHANGE_IDENTITY_UPDATE_BIT: Long = 0x200
const val KEY_EXCHANGE_CONTENT_FORMAT: Long = 0x100

// Secure Message Information
const val SECURE_MESSAGE_BIT: Long = 0x800000
const val END_SESSION_BIT: Long = 0x400000
const val PUSH_MESSAGE_BIT: Long = 0x200000

// Group Message Information
const val GROUP_UPDATE_BIT: Long = 0x10000
const val GROUP_QUIT_BIT: Long = 0x20000
const val EXPIRATION_TIMER_UPDATE_BIT: Long = 0x40000

// Encrypted Storage Information
const val ENCRYPTION_MASK: Long = -0x1000000
const val ENCRYPTION_SYMMETRIC_BIT: Long = -0x80000000
const val ENCRYPTION_ASYMMETRIC_BIT: Long = 0x40000000
const val ENCRYPTION_REMOTE_BIT: Long = 0x20000000
const val ENCRYPTION_REMOTE_FAILED_BIT: Long = 0x10000000
const val ENCRYPTION_REMOTE_NO_SESSION_BIT: Long = 0x08000000
const val ENCRYPTION_REMOTE_DUPLICATE_BIT: Long = 0x04000000
const val ENCRYPTION_REMOTE_LEGACY_BIT: Long = 0x02000000

fun isJoinRequestType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_JOIN_REQUEST
}

fun isAtMeType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_AT_ME_TYPE
}

fun isDraftMessageType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_DRAFT_TYPE
}

fun isFailedMessageType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_SENT_FAILED_TYPE
}

fun isFailedCannotDecryptType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_FAIL_CANNOT_DECRYPT_TYPE
}

fun isOutgoingMessageType(type: Long): Boolean {
    for (outgoingType in OUTGOING_MESSAGE_TYPES) {
        if (type and BASE_TYPE_MASK == outgoingType) {
            return true
        }
    }

    return false
}

fun getOutgoingEncryptedMessageType(): Long {
    return BASE_SENDING_TYPE or SECURE_MESSAGE_BIT or PUSH_MESSAGE_BIT
}

fun getOutgoingSmsMessageType(): Long {
    return BASE_SENDING_TYPE
}

fun isForcedSms(type: Long): Boolean {
    return type and MESSAGE_FORCE_SMS_BIT != 0L
}

fun isPendingMessageType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_OUTBOX_TYPE || type and BASE_TYPE_MASK == BASE_SENDING_TYPE
}

fun isPendingSmsFallbackType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_PENDING_INSECURE_SMS_FALLBACK || type and BASE_TYPE_MASK == BASE_PENDING_SECURE_SMS_FALLBACK
}

fun isPendingSecureSmsFallbackType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_PENDING_SECURE_SMS_FALLBACK
}

fun isPendingInsecureSmsFallbackType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_PENDING_INSECURE_SMS_FALLBACK
}

fun isInboxType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == BASE_INBOX_TYPE
}

fun isJoinedType(type: Long): Boolean {
    return type and BASE_TYPE_MASK == JOINED_TYPE
}

fun isSecureType(type: Long): Boolean {
    return type and SECURE_MESSAGE_BIT != 0L
}

fun isPushType(type: Long): Boolean {
    return type and PUSH_MESSAGE_BIT != 0L
}

fun isEndSessionType(type: Long): Boolean {
    return type and END_SESSION_BIT != 0L
}

fun isKeyExchangeType(type: Long): Boolean {
    return if (type and KEY_LOCATION_BIT != 0L) false else type and KEY_EXCHANGE_BIT != 0L
}

fun isLocationType(type: Long): Boolean {
    return type and KEY_LOCATION_BIT != 0L
}

fun isIdentityVerifiedType(type: Long): Boolean {
    return type and KEY_EXCHANGE_IDENTITY_VERIFIED_BIT != 0L
}

fun isIdentityDefaultType(type: Long): Boolean {
    return type and KEY_EXCHANGE_IDENTITY_DEFAULT_BIT != 0L
}

fun isCorruptedKeyExchangeType(type: Long): Boolean {
    return type and KEY_EXCHANGE_CORRUPTED_BIT != 0L
}

fun isInvalidVersionKeyExchangeType(type: Long): Boolean {
    return type and KEY_EXCHANGE_INVALID_VERSION_BIT != 0L
}

fun isBundleKeyExchangeType(type: Long): Boolean {
    return if (type and KEY_LOCATION_BIT != 0L) false else type and KEY_EXCHANGE_BUNDLE_BIT != 0L
}

fun isContentBundleKeyExchangeType(type: Long): Boolean {
    return type and KEY_EXCHANGE_CONTENT_FORMAT != 0L
}

fun isIdentityUpdateType(type: Long): Boolean {
    return type and KEY_EXCHANGE_IDENTITY_UPDATE_BIT != 0L
}

fun isCallLogType(type: Long): Boolean {
    return type == INCOMING_CALL_TYPE || type == OUTGOING_CALL_TYPE || type == MISSED_INCOMING_CALL_TYPE || type == MISSED_OUTGOING_CALL_TYPE
}

fun isExpirationTimerUpdateType(type: Long): Boolean {
    return type and EXPIRATION_TIMER_UPDATE_BIT != 0L
}

fun isIncomingCallType(type: Long): Boolean {
    return type == INCOMING_CALL_TYPE
}

fun isOutgoingCallType(type: Long): Boolean {
    return type == OUTGOING_CALL_TYPE
}

fun isMissedCallType(type: Long): Boolean {
    return type == MISSED_CALL_TYPE
}

fun isOutgoingMissedCallType(type: Long): Boolean {
    return type == MISSED_OUTGOING_CALL_TYPE
}

fun isIncomingMissedCallType(type: Long): Boolean {
    return type == MISSED_INCOMING_CALL_TYPE
}

fun isGroupUpdateType(type: Long): Boolean {
    return type and GROUP_UPDATE_BIT != 0L
}

fun isGroupQuitType(type: Long): Boolean {
    return type and GROUP_QUIT_BIT != 0L
}

fun isSymmetricEncryptionType(type: Long): Boolean {
    return type and ENCRYPTION_SYMMETRIC_BIT != 0L
}

fun isAsymmetricEncryptionType(type: Long): Boolean {
    return type and ENCRYPTION_ASYMMETRIC_BIT != 0L
}

fun isFailedDecryptType(type: Long): Boolean {
    return type and ENCRYPTION_REMOTE_FAILED_BIT != 0L
}

fun isDuplicateMessageType(type: Long): Boolean {
    return type and ENCRYPTION_REMOTE_DUPLICATE_BIT != 0L
}

fun isDecryptInProgressType(type: Long): Boolean {
    return type and ENCRYPTION_ASYMMETRIC_BIT != 0L
}

fun isNoRemoteSessionType(type: Long): Boolean {
    return type and ENCRYPTION_REMOTE_NO_SESSION_BIT != 0L
}

fun isLegacyType(type: Long): Boolean {
    return type and ENCRYPTION_REMOTE_LEGACY_BIT != 0L || type and ENCRYPTION_REMOTE_BIT != 0L
}