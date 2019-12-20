package com.bcm.messenger.chats.group.logic.secure

import com.bcm.messenger.common.core.corebean.GroupKeyMode
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

data class GroupKeysContent(
        @SerializedName("encrypt_version")
        val keyEncryptedVersion:Long = KEY_ENCRYPTED_VERSION,
        @SerializedName("keys_v0")
        var strongModeKeys:List<StrongKeyContent> = listOf(),
        @SerializedName("keys_v1")
        var normalModeKeys:NormalKeyContent = NormalKeyContent("")
): NotGuard {
    companion object {
        const val KEY_ENCRYPTED_VERSION = 0L
    }

    data class StrongKeyContent(
            @SerializedName("uid")
            val uid:String,
            @SerializedName("key")
            val key:String?,
            @SerializedName("device_id")
            val deviceId:Int):NotGuard {

    }

    data class NormalKeyContent(
            @SerializedName("key")
            val key:String):NotGuard {

    }

    data class GroupKeyContent(
            @SerializedName("gid")
            var gid:Long = 0,
            @SerializedName("encrypt_version")
            val keyEncryptedVersion:Long = KEY_ENCRYPTED_VERSION,
            @SerializedName("version")
            val version:Long = 0,
            @SerializedName("group_keys_mode")
            val keyMode:Int = GroupKeyMode.STRONG_MODE.m,
            @SerializedName("keys_v0")
            var strongModeKey:StrongKeyContent? = null,
            @SerializedName("keys_v1")
            var normalModeKey:NormalKeyContent? = null
    ):NotGuard
}