package com.bcm.messenger.chats.group.core

import com.bcm.messenger.chats.group.logic.secure.GroupKeysContent
import com.bcm.messenger.common.core.corebean.GroupKeyMode
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

class CreateGroupRequest: NotGuard {
    @SerializedName("name")
    var name:String? = null
    @SerializedName("owner_nickname")
    var ownerName:String? = null
    @SerializedName("icon")
    var icon:String? = null
    @SerializedName("broadcast")
    var broadcast:Int = 0
    @SerializedName("members")
    var members:List<String>? = null
    @SerializedName("owner_group_info_secret")
    var ownerSecret:String? = null
    @SerializedName("member_group_info_secrets")
    var memberSecrets:List<String>? = null
    @SerializedName("intro")
    var intro:String? = null
    @SerializedName("owner_profile_keys")
    var profileKeys:String? = null
    @SerializedName("share_qr_code_setting")
    var shareSetting:String? = null
    @SerializedName("share_sig")
    var shareSettingSign:String? = null
    @SerializedName("share_and_owner_confirm_sig")
    var shareConfirmSign:String? = null
    @SerializedName("owner_confirm")
    var needConfirm:Int = 0
    @SerializedName("encrypted_group_info_secret")
    var encryptedSecret:String? = null
    @SerializedName("encrypted_ephemeral_key")
    var encryptedEphemeralKey:String? = null
    @SerializedName("owner_proof")
    var ownerProof:String? = null
    @SerializedName("member_proofs")
    var memberProofs:List<String>? = null
    @SerializedName("group_keys")
    var groupKeys:GroupKeysContent? = null
    @SerializedName("group_keys_mode")
    var groupKeyMode = GroupKeyMode.STRONG_MODE.m
}