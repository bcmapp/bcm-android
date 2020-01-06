package com.bcm.messenger.contacts.net

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.callback.Callback
import com.bcm.messenger.utility.bcmhttp.callback.OriginCallback
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.signalservice.internal.util.Base64

/**
 * Created by bcm.social.01 on 2019/3/11.
 */
class BcmContactCore(private val mAccountContext: AccountContext) {

    companion object {
        private const val TAG = "BcmContactCore"

        private const val CONTACTS_ALGORITHM = "AES/CBC/PKCS7Padding"
        private const val SYNC_FRIEND_API = "/v2/contacts/parts"
        private const val UPLOAD_BCM_USER_API = "/v2/contacts/tokens"
        private const val QUERY_BCM_USER_API = "/v2/contacts/tokens/users"
        private const val REQUEST_ADD_FRIEND = "/v2/contacts/friends/request"
        private const val REQUEST_REPLAY_ADD_FRIEND = "/v2/contacts/friends/reply"
        private const val REQUEST_DELETE_FRIEND = "/v2/contacts/friends"

        private const val CONTACT_FILTER_API = "/v2/contacts/filters"

        const val CONTACT_SYNC_VERSION = 2

        const val CONTACT_PART_MAX = 20 //通讯录最大组数

        /**
         * 根据UID转化成通讯录part index
         */
        fun getPartKey(uid: String): String {
            return (BcmHash.hash(uid.toByteArray()) % CONTACT_PART_MAX).toString()
        }

    }

    data class ContactSyncRequest(val parts: Map<String, Long> = HashMap()) : NotGuard

    data class ContactSyncResponse(val parts: HashMap<String, String?>? = HashMap()) : NotGuard
    /**
     * @param parts key: UID bucket index， value: FriendListPart
     */
    data class ContactPatchRequest(val parts: HashMap<String, String> = HashMap()) : NotGuard

    /**
     * bucket data class
     */
    data class FriendListPart(val contacts_version: Int, val contacts_time: Long, val contacts_key: String, val contacts_body: String) : NotGuard

    data class ContactItem(val uid: String, var profileName: String?, var localName: String?, var relationship: Int = RecipientRepo.Relationship.FOLLOW.type, var nameKey: String?, var avatarKey: String?) : NotGuard {

        companion object {
            fun from(settings: RecipientSettings): ContactItem {
                return ContactItem(settings.uid, settings.profileName ?: "", settings.localName ?: "", settings.relationship, settings.privacyProfile.nameKey ?: "", settings.privacyProfile.avatarKey ?: "")
            }
            fun from(jsonString: String): ContactItem? {
                try {
                    return GsonUtils.fromJson(jsonString, object : TypeToken<ContactItem>(){}.type)
                }catch (ex: Exception) {

                }
                return null
            }
        }

        fun toSetting(): RecipientSettings {
            val settings = RecipientSettings(uid)
            val r = this.relationship
            settings.setTemporaryProfile(this.profileName, null) //兼容旧版本
            settings.setLocalProfile(this.localName, null)
            settings.relationship = RecipientRepo.Relationship.values()[r].type
            settings.setPrivacyKey(this.nameKey, this.avatarKey)
            return settings
        }

        override fun toString(): String {
            return GsonUtils.toJson(this)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ContactItem

            if (uid != other.uid) return false

            return true
        }

        override fun hashCode(): Int {
            return uid.hashCode()
        }


    }

    data class ContactSyncResult(val different: Map<String, List<ContactItem>>, val contactVersion: Int)
    data class ContactPatchResult(val uploadMap: Map<String, List<ContactItem>>)

    data class FriendRequestBody(val handleBackground: Boolean, val nameKey: String, val avatarKey: String, val requestMemo: String, val version: Int) : NotGuard

    data class ContactFilterResponse(val version: String, val uploadAll: Boolean) : NotGuard

    data class ContactParts(val version: Int, val list: List<ContactItem>)
    data class ContactFilterPatchRequest(val position: Int, val value: Boolean) : NotGuard {

        override fun toString(): String {
            return GsonUtils.toJson(this)
        }

    }

    data class AlgoEntity(val id: Int, val seed: Long, val func: Int, val tweek: Long) : NotGuard {

        companion object {
            fun ofZero(): AlgoEntity {
                return AlgoEntity(0, 0xFBA4C795L, 5, 0x03L)
            }
        }

    }

    /**
     * 同步通讯录
     * @param uploadContactMap 当前通讯录，key: 分组ID， value: 对应的联系人信息
     */
    fun syncFriendList(uploadContactMap: Map<String, List<ContactItem>>): Observable<ContactSyncResult> {
        val req = genContactPatchRequest(uploadContactMap)
        //if (!AppUtil.isReleaseBuild()) {
            //ALog.i(TAG, "syncFriendList request body: ${GsonUtils.toJson(uploadContactMap)}")
        //}
        val reqJson = GsonUtils.toJson(genContactSyncRequest(req))
        if (!AppUtil.isReleaseBuild()) {
            ALog.i(TAG, "genContactSyncRequest: " + GsonUtils.toJson(reqJson))
        }
        return RxIMHttp.get(mAccountContext).post<ContactSyncResponse>(BcmHttpApiHelper.getApi(SYNC_FRIEND_API), reqJson, ContactSyncResponse::class.java)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map {
                    var contactVersion = CONTACT_SYNC_VERSION
                    val result = if (it.parts.isNullOrEmpty()) {
                        mapOf<String, List<ContactItem>>()
                    }else {
                        val targetUploadMap = mutableMapOf<String, List<ContactItem>>()
                        for ((k, v) in it.parts) {
                            val p = parsePartToRecipientList(v)
                            if (p != null) {
                                contactVersion = p.version
                            }
                            targetUploadMap[k] = p?.list ?: listOf()
                        }
                        targetUploadMap
                    }
                    ContactSyncResult(result, contactVersion)
                }

    }

    fun uploadFriendList(uploadMap: Map<String, List<ContactItem>>, checkFull: Boolean): Observable<ContactPatchResult> {
        val targetUploadMap = mutableMapOf<String, List<ContactItem>>()
        if (checkFull) {
            for (i in 0 until CONTACT_PART_MAX) {
                val k = i.toString()
                val v = uploadMap[k]
                if (v == null) {
                    targetUploadMap[k] = listOf()
                }else {
                    targetUploadMap[k] = v
                }
            }
        }else {
            targetUploadMap.putAll(uploadMap)
        }
        if (!isReleaseBuild()) {
            ALog.i(TAG, "uploadFriendList uploadMap: ${GsonUtils.toJson(targetUploadMap)}")
        }
        val req = genContactPatchRequest(targetUploadMap)
        val reqJson = GsonUtils.toJson(req)
        if (!AppUtil.isReleaseBuild()) {
            ALog.i(TAG, "uploadFriendList: $reqJson")
        }
        return RxIMHttp.get(mAccountContext).put<AmeEmpty>(BcmHttpApiHelper.getApi(SYNC_FRIEND_API), null, reqJson, object : TypeToken<AmeEmpty>() {}.type)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map {
                    ContactPatchResult(uploadMap)
                }
    }


    private fun genContactSyncRequest(req: ContactPatchRequest): ContactSyncRequest {
        val hashList = mutableMapOf<String, Long>()
        for ((k, v) in req.parts) {
            hashList[k] = BcmHash.hash(v.toByteArray())
        }
        for (i in 0 until CONTACT_PART_MAX) {
            if (hashList[i.toString()] == null) {
                hashList[i.toString()] = 0
            }
        }
        return ContactSyncRequest(hashList)
    }

    private fun genContactPatchRequest(localFriendMap: Map<String, List<ContactItem>>?): ContactPatchRequest {
        val req = ContactPatchRequest()
        if (localFriendMap == null) {
            return req
        }

        val keyPair = BCMPrivateKeyUtils.generateKeyPair()
        val otherPrivateKeyArray = keyPair.privateKey.serialize()

        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(mAccountContext)
        val myPublicKeyArray = (myKeyPair.publicKey.publicKey as DjbECPublicKey).publicKey

        val dhPassword = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(myPublicKeyArray, otherPrivateKeyArray)

        val aesKeyPair = createAESKeyPair(dhPassword)

        val pubKey = Base64.encodeBytes((keyPair.publicKey as DjbECPublicKey).publicKey)
        for ((key, uploadSet) in localFriendMap) {
            val contactBody = GsonUtils.toJson(uploadSet.filter {
                it.relationship != RecipientRepo.Relationship.STRANGER.type
            })
            if (!isReleaseBuild()) {
                ALog.d(TAG, "genContactPatchRequest key: $key, contactBody: $contactBody")
            }
            val part = FriendListPart(CONTACT_SYNC_VERSION,
                    AmeTimeUtil.serverTimeMillis(),
                    pubKey,
                    Base64.encodeBytes(EncryptUtils.encryptAES(contactBody.toByteArray(), aesKeyPair.first, CONTACTS_ALGORITHM, aesKeyPair.second)))
            req.parts[key] = GsonUtils.toJson(part)
        }
        return req
    }

    private fun parsePartToRecipientList(partString: String?): ContactParts? {
        if (partString.isNullOrEmpty()) {
            return null
        }

        val part = GsonUtils.fromJson(partString, FriendListPart::class.java)
        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(mAccountContext)
        val dhPassword = Curve25519.getInstance(Curve25519.BEST)
                .calculateAgreement(Base64.decode(part.contacts_key), myKeyPair.privateKey.serialize())

        val aesKeyPair = createAESKeyPair(dhPassword)
        val contactBodyString = String(EncryptUtils.decryptAES(Base64.decode(part.contacts_body), aesKeyPair.first, CONTACTS_ALGORITHM, aesKeyPair.second))

        if (!isReleaseBuild()) {
            ALog.d(TAG, "parsePartToRecipientList contactBodyString: $contactBodyString")
        }

        val list = GsonUtils.fromJson<List<ContactItem>>(contactBodyString, object : TypeToken<List<ContactItem>>() {}.type)

        return ContactParts(part.contacts_version, list)
    }

    private fun createAESKeyPair(dhPassword: ByteArray): Pair<ByteArray, ByteArray> {
        val sha512Data = EncryptUtils.encryptSHA512(dhPassword)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(sha512Data, 0, aesKey256, 0, 32)
        System.arraycopy(sha512Data, 48, iv, 0, 16)

        return Pair(aesKey256, iv)
    }

    fun sendAddFriendReq(targetUid: String, payload: String, callback: Callback<Response>) {
        val timestamp = AmeTimeUtil.serverTimeMillis()
        val signatureString = "${mAccountContext.uid}$targetUid$timestamp$payload$REQUEST_ADD_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(mAccountContext, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("target", targetUid)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("payload", payload)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        val content = jsonObj.toString()

        IMHttp.get(mAccountContext).putString()
                .url(BcmHttpApiHelper.getApi(REQUEST_ADD_FRIEND))
                .content(content)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(callback)
    }

    fun sendAddFriendReply(approved: Boolean, proposer: String, payload: String, addFriendSignature: String, callback: Callback<Response>) {
        val timestamp = AmeTimeUtil.serverTimeMillis()
        val signatureString = "${mAccountContext.uid}$approved$proposer$timestamp$payload$addFriendSignature$REQUEST_REPLAY_ADD_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(mAccountContext, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("approved", approved)
        jsonObj.put("proposer", proposer)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("payload", payload)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        jsonObj.put("requestSignature", addFriendSignature)
        val content = jsonObj.toString()

        IMHttp.get(mAccountContext).putString()
                .url(BcmHttpApiHelper.getApi(REQUEST_REPLAY_ADD_FRIEND))
                .content(content)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(callback)
    }

    fun sendDeleteFriendReq(targetUid: String, callback: Callback<Response>) {
        val timestamp = AmeTimeUtil.serverTimeMillis()
        val signatureString = "${mAccountContext.uid}$targetUid$timestamp$REQUEST_DELETE_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(mAccountContext, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("target", targetUid)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        val content = jsonObj.toString()

        IMHttp.get(mAccountContext).deleteString()
                .url(BcmHttpApiHelper.getApi(REQUEST_DELETE_FRIEND))
                .content(content)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(callback)
    }

    fun uploadContactFilters(contentBase64: String, algo: Int, callback: (response: ContactFilterResponse?) -> Unit) {
        val bodyObj = JSONObject()
        bodyObj.put("content", contentBase64)
        bodyObj.put("algo", algo)
        val bodyObjString = bodyObj.toString()
        if (!isReleaseBuild()) {
            ALog.d(TAG, "uploadContactFilters request body: $bodyObjString")
        }
        IMHttp.get(mAccountContext).putString()
                .url(BcmHttpApiHelper.getApi(CONTACT_FILTER_API))
                .content(bodyObjString)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(object : OriginCallback() {

                    override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                        ALog.e(TAG, "uploadContactFilters error", e)
                        call?.cancel()
                        callback.invoke(null)
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        if (response == null) {
                            callback.invoke(null)
                        }else {
                            try {
                                val code = response.code()
                                if (code == 200) {
                                    val responseObj = JSONObject(response.body()?.string())
                                    val result = ContactFilterResponse(responseObj.optString("version", ""), false)
                                    callback.invoke(result)

                                } else {
                                    throw Exception("response error: $code")
                                }
                            }catch (ex: Exception) {
                                ALog.e(TAG, "uploadContactFilters error", ex)
                                callback.invoke(null)
                            }
                        }
                    }

                    override fun validateResponse(response: Response?, id: Long): Boolean {
                        return response?.isSuccessful == true || response?.code() == 409
                    }

                })
    }

    fun patchContactFilters(version: String, patchList: List<ContactFilterPatchRequest>, callback: (response: ContactFilterResponse?) -> Unit) {
        val bodyObj = JSONObject()
        bodyObj.put("version", version)
        val patchArray = JSONArray()
        var patchJson: JSONObject
        for (r in patchList) {
            patchJson = JSONObject()
            patchJson.put("position", r.position)
            patchJson.put("value", r.value)
            patchArray.put(patchJson)
        }
        bodyObj.put("patches", patchArray)
        val bodyObjString = bodyObj.toString()
        ALog.d(TAG, "patchContactFilters request body: $bodyObjString")
        IMHttp.get(mAccountContext).patchString()
                .url(BcmHttpApiHelper.getApi(CONTACT_FILTER_API))
                .content(bodyObjString)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(object : OriginCallback() {

                    override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                        ALog.e(TAG, "patchContactFilters error", e)
                        call?.cancel()
                        callback.invoke(null)
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        if (response == null) {
                            callback.invoke(null)
                        }else {
                            try {
                                val code = response.code()
                                if (code == 200) {
                                    val responseObj = JSONObject(response.body()?.string())
                                    callback.invoke(ContactFilterResponse(responseObj.optString("version", ""), false))
                                }
                                else if (code == 409) {
                                    callback.invoke(ContactFilterResponse(version, true))
                                }
                                else {
                                    throw Exception("response error: $code")
                                }
                            }catch (ex: Exception) {
                                ALog.e(TAG, "patchContactFilters error", ex)
                                callback.invoke(null)
                            }
                        }
                    }

                    override fun validateResponse(response: Response?, id: Long): Boolean {
                        return response?.isSuccessful == true || response?.code() == 409
                    }

                })
    }

    fun deleteContactFilters(callback: (response: Boolean) -> Unit) {
        IMHttp.get(mAccountContext).deleteString()
                .url(BcmHttpApiHelper.getApi(CONTACT_FILTER_API))
                .build()
                .enqueue(object : OriginCallback() {

                    override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                        ALog.e(TAG, "deleteContactFilters error", e)
                        call?.cancel()
                        callback.invoke(false)
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        if (response == null) {
                            callback.invoke(false)
                        }else {
                            try {
                                val code = response.code()
                                if (code == 200) {
                                    callback.invoke(true)

                                } else {
                                    throw Exception("response error: $code")
                                }
                            }catch (ex: Exception) {
                                ALog.e(TAG, "deleteContactFilters error", ex)
                                callback.invoke(false)
                            }
                        }
                    }

                })
    }
}