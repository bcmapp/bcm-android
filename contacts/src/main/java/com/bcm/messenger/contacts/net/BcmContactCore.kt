package com.bcm.messenger.contacts.net

import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
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
class BcmContactCore {

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

    }

    private data class GetFriendListReq(val parts: Map<String, Long> = HashMap()) : NotGuard
    private data class GetFriendListRes(val parts: HashMap<String, String?>? = HashMap()) : NotGuard
    /**
     * @param parts key: UID bucket index， value: FriendListPart
     */
    private data class UploadFriendListReq(val parts: HashMap<String, String> = HashMap()) : NotGuard

    /**
     * bucket data class
     */
    private data class FriendListPart(val contacts_version: Int, val contacts_time: Long, val contacts_key: String, val contacts_body: String) : NotGuard

    private data class ContactItem(val uid: String, val profileName: String, val localName: String, val relationship: Int, val nameKey: String, val avatarKey: String) : NotGuard {

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

        fun toSetting(contactVersion: Int): RecipientSettings {
            val settings = RecipientSettings(uid)
            var r = this.relationship
            if (r == 0) {
                r = RecipientRepo.Relationship.FOLLOW.type
            }
            settings.setTemporaryProfile(this.profileName, null)
            settings.setLocalProfile(this.localName, null)
            settings.relationship = RecipientRepo.Relationship.values()[r].type
            settings.setPrivacyKey(this.nameKey, this.avatarKey)
            settings.contactVersion = contactVersion
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

    data class UploadFriendResult(val uploadMap: Map<String, Set<RecipientSettings>>, val hashResult: Map<String, Long>)

    data class FriendRequestBody(val handleBackground: Boolean, val nameKey: String, val avatarKey: String, val requestMemo: String, val version: Int) : NotGuard

    data class ContactFilterResponse(val version: String, val uploadAll: Boolean) : NotGuard

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

    fun syncFriendList(currentContactMap: Map<String, Set<RecipientSettings>>, uploadContactMap: Map<String, Long>, handlingMap: Map<String, RecipientSettings>): Observable<UploadFriendResult> {
        ALog.i(TAG, "syncFriendList begin localMap: ${currentContactMap.size}, handlingMap: ${handlingMap.size}")
        val req = GetFriendListReq(uploadContactMap)
        val reqJson = GsonUtils.toJson(req)
        if (!AppUtil.isReleaseBuild()) {
            ALog.i(TAG, "syncFriendList request body: $reqJson")
        }
        return RxIMHttp.post<GetFriendListRes>(BcmHttpApiHelper.getApi(SYNC_FRIEND_API), reqJson, GetFriendListRes::class.java)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap {response ->
                    ALog.i(TAG, "syncFriendList response size: ${response.parts?.size}")

                    var uploadMap: Map<String, Set<RecipientSettings>>? = null

                    if (!response.parts.isNullOrEmpty()) {
                        uploadMap = mergePart(response.parts, currentContactMap, handlingMap)

                    }else {
                        if (handlingMap.isNotEmpty()) {
                            uploadMap = currentContactMap
                            if (!isReleaseBuild()) {
                                ALog.d(TAG, "syncFriendList uploadNew: ${GsonUtils.toJson(currentContactMap)}")
                            }
                        }
                    }

                    ALog.i(TAG, "syncFriendList uploadMap size: ${uploadMap?.size}")
                    if (uploadMap.isNullOrEmpty()) {
                        Observable.just(response)
                                .map {
                                    UploadFriendResult(uploadMap ?: mapOf(), mapOf())
                                }
                    } else {
                        uploadFriendList(uploadMap)
                    }

                }
    }

    fun uploadFriendList(uploadMap: Map<String, Set<RecipientSettings>>): Observable<UploadFriendResult> {
        ALog.i(TAG, "uploadFriendList")
        val req = genUploadFriendListRequest(uploadMap)
        val reqJson = GsonUtils.toJson(req)
        if (!AppUtil.isReleaseBuild()) {
            ALog.i(TAG, "uploadFriendList: $reqJson")
        }
        return RxIMHttp.put<AmeEmpty>(BcmHttpApiHelper.getApi(SYNC_FRIEND_API), null, reqJson, object : TypeToken<AmeEmpty>() {}.type)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map {
                    UploadFriendResult(uploadMap, genUploadFriendHashMap(req))
                }
    }

    private fun mergePart(remoteHashMap: Map<String, String?>, localMap: Map<String, Set<RecipientSettings>>, handlingMap: Map<String, RecipientSettings>):
            Map<String, Set<RecipientSettings>> {
        ALog.i(TAG, "mergePart remote size: ${remoteHashMap.size}, local size: ${localMap.size}, handling size: ${handlingMap.size}")
        val targetUploadMap = mutableMapOf<String, MutableSet<RecipientSettings>>()
        for ((k, v) in remoteHashMap) {
            ALog.i(TAG, "mergePart k: $k")
            val uploadSet = mutableSetOf<RecipientSettings>()
            val localSet = localMap[k]
            val remoteSet = parsePartToRecipientList(v)
            if (remoteSet != null) {
                uploadSet.addAll(remoteSet)
                localSet?.forEach {
                    if (remoteSet.contains(it)) {
                        ALog.logForSecret(TAG, "mergePart remote exist, uid: ${it.uid}, name: ${it.profileName}")
                    }else {
                        if (handlingMap[it.uid] == null) {
                            ALog.logForSecret(TAG, "mergePart handling not exist: ${it.uid}, set stranger")
                            it.relationship = RecipientRepo.Relationship.STRANGER.type
                        }
                        uploadSet.add(it)
                    }
                }
            }else {
                localSet?.forEach {
                    if (handlingMap[it.uid] == null) {
                        ALog.logForSecret(TAG, "mergePart remote is null, handling not exist: ${it.uid}, set stranger")
                        it.relationship = RecipientRepo.Relationship.STRANGER.type
                    }
                    uploadSet.add(it)
                }
            }
            targetUploadMap[k] = uploadSet
        }

        for ((uid, l) in handlingMap) {
            val key = l.contactPartKey ?: ""
            var uploadSet = targetUploadMap[key]
            if (uploadSet == null) {
                uploadSet = mutableSetOf()
                targetUploadMap[key] = uploadSet
            }
            uploadSet.remove(l)
            uploadSet.add(l)
            ALog.d(TAG, "mergePart uploadSet add handlingSettings: $l")
        }

        return targetUploadMap
    }

    private fun genUploadFriendHashMap(req: UploadFriendListReq): Map<String, Long> {
        val hashList = mutableMapOf<String, Long>()
        for ((k, v) in req.parts) {
            hashList[k] = BcmHash.hash(v.toByteArray())
        }
        if (!AppUtil.isReleaseBuild()) {
            ALog.i(TAG, "genUploadFriendHashMap: " + GsonUtils.toJson(hashList))
        }
        return hashList
    }

    private fun genUploadFriendListRequest(localFriendMap: Map<String, Set<RecipientSettings>>?): UploadFriendListReq {
        val req = UploadFriendListReq()
        if (localFriendMap == null) {
            return req
        }

        val keyPair = BCMPrivateKeyUtils.generateKeyPair()
        val otherPrivateKeyArray = keyPair.privateKey.serialize()

        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(AppContextHolder.APP_CONTEXT)
        val myPublicKeyArray = (myKeyPair.publicKey.publicKey as DjbECPublicKey).publicKey

        val dhPassword = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(myPublicKeyArray, otherPrivateKeyArray)

        val aesKeyPair = createAESKeyPair(dhPassword)

        val pubKey = Base64.encodeBytes((keyPair.publicKey as DjbECPublicKey).publicKey)
        for ((key, uploadSet) in localFriendMap) {
            val contactBody = GsonUtils.toJson(uploadSet.mapNotNull {
                if (it.relationship == RecipientRepo.Relationship.STRANGER.type) {
                    null
                }else {
                    ContactItem.from(it)
                }
            })
            if (!isReleaseBuild()) {
                ALog.d(TAG, "genUploadFriendListRequest contactBody: $contactBody")
            }
            val part = FriendListPart(RecipientSettings.CONTACT_SYNC_VERSION,
                    AmeTimeUtil.serverTimeMillis(),
                    pubKey,
                    Base64.encodeBytes(EncryptUtils.encryptAES(contactBody.toByteArray(), aesKeyPair.first, CONTACTS_ALGORITHM, aesKeyPair.second)))
            req.parts[key] = GsonUtils.toJson(part)
        }
        return req
    }

    private fun parsePartToRecipientList(partString: String?): Set<RecipientSettings>? {
        if (partString.isNullOrEmpty()) {
            return null
        }

        val part = GsonUtils.fromJson(partString, FriendListPart::class.java)
        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(AppContextHolder.APP_CONTEXT)
        val dhPassword = Curve25519.getInstance(Curve25519.BEST)
                .calculateAgreement(Base64.decode(part.contacts_key), myKeyPair.privateKey.serialize())

        val aesKeyPair = createAESKeyPair(dhPassword)
        val contactBodyString = String(EncryptUtils.decryptAES(Base64.decode(part.contacts_body), aesKeyPair.first, CONTACTS_ALGORITHM, aesKeyPair.second))

        if (!isReleaseBuild()) {
            ALog.d(TAG, "parsePartToRecipientList contactBodyString: $contactBodyString")
        }
        val contactItemList = GsonUtils.fromJson<ArrayList<ContactItem>>(contactBodyString, object : TypeToken<ArrayList<ContactItem>>() {}.type)

        return contactItemList.map { it.toSetting(part.contacts_version) }.toSet()
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
        val signatureString = "${AMESelfData.uid}$targetUid$timestamp$payload$REQUEST_ADD_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("target", targetUid)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("payload", payload)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        val content = jsonObj.toString()

        IMHttp.putString()
                .url(BcmHttpApiHelper.getApi(REQUEST_ADD_FRIEND))
                .content(content)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(callback)
    }

    fun sendAddFriendReply(approved: Boolean, proposer: String, payload: String, addFriendSignature: String, callback: Callback<Response>) {
        val timestamp = AmeTimeUtil.serverTimeMillis()
        val signatureString = "${AMESelfData.uid}$approved$proposer$timestamp$payload$addFriendSignature$REQUEST_REPLAY_ADD_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("approved", approved)
        jsonObj.put("proposer", proposer)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("payload", payload)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        jsonObj.put("requestSignature", addFriendSignature)
        val content = jsonObj.toString()

        IMHttp.putString()
                .url(BcmHttpApiHelper.getApi(REQUEST_REPLAY_ADD_FRIEND))
                .content(content)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
                .enqueue(callback)
    }

    fun sendDeleteFriendReq(targetUid: String, callback: Callback<Response>) {
        val timestamp = AmeTimeUtil.serverTimeMillis()
        val signatureString = "${AMESelfData.uid}$targetUid$timestamp$REQUEST_DELETE_FRIEND"
        val signature = BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, signatureString.toByteArray())
        val jsonObj = JSONObject()
        jsonObj.put("target", targetUid)
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("signature", Base64.encodeBytes(signature))
        val content = jsonObj.toString()

        IMHttp.deleteString()
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
        IMHttp.putString()
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
        IMHttp.patchString()
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
        IMHttp.deleteString()
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