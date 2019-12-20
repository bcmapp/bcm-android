package org.whispersystems.signalservice.api.profiles

/**
 *
 * Created by wjh on 2018/7/4
 */
interface ISignalProfile {

    fun getName(): String?   //返回昵称
    fun getAvatar(): String?   //返回头像avatar
    fun getIdentityKey(): String?
    fun getProfileKey(): String?
    fun getProfileKeyArray(): ByteArray?
    fun getProfileBackupTime(): Long
    fun getPhone(): String?  //返回绑定的手机号
    fun isAllowStrangerMessages(): Boolean

    fun getEncryptPhone(): String? //返回加密过的手机号
    fun getEncryptPubkey(): String?//返回加密的公钥

    fun getEncryptName(): String?//返回加密的昵称
    fun getEncryptAvatarLD(): String?//返回加密的低清头像地址
    fun getEncryptAvatarHD(): String?//返回加密的高清头像地址
    fun getSupportFeatures():String? //返回用户支持的功能
}