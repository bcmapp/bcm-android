package com.bcm.messenger.login.logic

import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.utility.bcmhttp.facade.SyncHttpWrapper
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.profiles.ProfilePrivacy
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.login.bean.AmeLoginParams
import com.bcm.messenger.login.bean.AmeRegisterParams
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyState
import org.whispersystems.signalservice.internal.push.PreKeyStatus
import java.util.*

/**
 * Created by bcm.social.01 on 2018/9/3.
 */
object AmeLoginCore {
    private const val INTERFACE_LOGIN = "/v1/accounts/signin"
    private const val INTERFACE_REGISTER = "/v1/accounts/signup"
    private const val INTERFACE_UNREGISTER = "/v1/accounts/%s/%s"
    private const val PREKEY_PATH = "/v2/keys/%s"
    private const val PREKEY_METADATA_PATH = "/v2/keys/"
    private const val SIGNED_PREKEY_PATH = "/v2/keys/signed"
    private const val PROFILE_PATH = "/v1/profile/%s"
    private const val UPLOAD_MY_FEATURE = "/v1/accounts/features"

    fun register(params: AmeRegisterParams): Observable<AmeEmpty> {
        return RxIMHttp.put(BcmHttpApiHelper.getApi(INTERFACE_REGISTER),
                null, Gson().toJson(params).toString(), object : TypeToken<AmeEmpty>() {
        }.type)
    }

    fun login(params: AmeLoginParams):Observable<AmeEmpty> {
        return RxIMHttp.put<AmeEmpty>(BcmHttpApiHelper.getApi(INTERFACE_LOGIN),
                null, Gson().toJson(params).toString(), object : TypeToken<AmeEmpty>() {
        }.type)
    }

    fun unregister(uid: String, sign: String): Observable<AmeEmpty> {
        return RxIMHttp.delete(BcmHttpApiHelper.getApi(String.format(Locale.US, INTERFACE_UNREGISTER, uid, sign)), null, null, object : TypeToken<AmeEmpty>() {}.type)
    }

    fun uploadMyFunctionSupport(support: BcmFeatureSupport): Observable<AmeEmpty> {
        val req = UploadMyFunctionReq(support.toString())
        return RxIMHttp.put(BcmHttpApiHelper.getApi(UPLOAD_MY_FEATURE),
                null, Gson().toJson(req), AmeEmpty::class.java)
    }

    fun refreshPreKeys(identityKey: IdentityKey,
                       signedPreKey: SignedPreKeyRecord,
                       records: List<PreKeyRecord>): Boolean {
        val entities = LinkedList<PreKeyEntity>()

        for (record in records) {
            val entity = PreKeyEntity(record.id,
                    record.keyPair.publicKey)

            entities.add(entity)
        }

        val signedPreKeyEntity = SignedPreKeyEntity(signedPreKey.id,
                signedPreKey.keyPair.publicKey,
                signedPreKey.signature)

        return try {
            val wrapper = SyncHttpWrapper(IMHttp)
            wrapper.put<AmeEmpty>(BcmHttpApiHelper.getApi(String.format(PREKEY_PATH, "")),
                    GsonUtils.toJson(PreKeyState(entities, signedPreKeyEntity, identityKey)),
                    AmeEmpty::class.java)
            true
        } catch (e: Throwable) {
            ALog.e("AmeLoginCore", "upload prekey failed", e)
            false
        }
    }

    fun getAvailablePreKeys(): Int {
        return try {
            val wrapper = SyncHttpWrapper(IMHttp)

            wrapper.get<PreKeyStatus>(BcmHttpApiHelper.getApi(PREKEY_METADATA_PATH)
                    , null
                    , PreKeyStatus::class.java).count
        } catch (e: Throwable) {
            ALog.e("AmeLoginCore", "upload prekey failed", e)
            -1
        }
    }


    fun refreshSignedPreKey(signedPreKey: SignedPreKeyRecord): Boolean {
        return try {
            val wrapper = SyncHttpWrapper(IMHttp)

            val signedPreKeyEntity = SignedPreKeyEntity(signedPreKey.id,
                    signedPreKey.keyPair.publicKey,
                    signedPreKey.signature)

            wrapper.put<AmeEmpty>(BcmHttpApiHelper.getApi(SIGNED_PREKEY_PATH)
                    , GsonUtils.toJson(signedPreKeyEntity)
                    , AmeEmpty::class.java)
            true
        } catch (e: Throwable) {
            ALog.e("AmeLoginCore", "refreshSignedPreKey failed", e)
            false
        }
    }

    fun updateAllowReceiveStrangers(allow: Boolean): Observable<Boolean> {
        val privacy = ProfilePrivacy().apply {
            allowStrangerMessage = allow
        }

        return RxIMHttp.put<AmeEmpty>(BcmHttpApiHelper.getApi(String.format(PROFILE_PATH, "privacy"))
                , privacy.toString()
                , AmeEmpty::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    val recipient = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, false)
                    val privacyProfile = recipient.privacyProfile
                    privacyProfile.allowStranger = privacy.allowStrangerMessage
                    Repository.getRecipientRepo()?.setPrivacyProfile(recipient, privacyProfile)

                    true
                }
    }


    private data class UploadMyFunctionReq(val features: String) : NotGuard
}