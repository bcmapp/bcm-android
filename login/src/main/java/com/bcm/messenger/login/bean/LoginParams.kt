package com.bcm.messenger.login.bean

import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by bcm.social.01 on 2018/9/5.
 *
 */

/**
 *
{    //account login params
"signalingKey":"",   //websocket encrypt key(base64)
"pubKey":"",   //account identity key(base64)
"fetchesMessages":true,
"registrationId":8728,   //registration id
"name":null,   //nicknme (base64)
"deviceName":"",//(base64)
"voice":true,
"video":true
}
 */
data class AmeAccountParams(val signalingKey:String, val publicKey:String, val fetchesMessages:Boolean, val registrationId:Int, val name:String, val deviceName:String, val voice:Boolean, val video:Boolean): NotGuard


/**
{
"sign":"xxxx",  //req signature
"nonce": 756369242, //nonce code
"account"：
}
 */
data class AmeRegisterParams(val sign:String, val nonce:Long, val account: AmeAccountParams): NotGuard


/**
{
"sign":"xxxx",  //req signature
"account"：
}
 */
data class AmeLoginParams(val sign:String, val account: AmeAccountParams): NotGuard

data class AmeOnlineDeviceParams(val device: String): NotGuard