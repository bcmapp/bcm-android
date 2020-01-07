package com.bcm.messenger.contacts.logic

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.text.TextUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.common.core.AddressUtil
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.core.*
import com.bcm.messenger.common.database.model.ProfileKeyModel
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.jobs.ContextJob
import com.bcm.messenger.common.profiles.PlaintextServiceProfile
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.IdentityUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.bcmhttp.facade.SyncHttpWrapper
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.route.api.BcmRouter
import com.example.bleserver.Base62
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.signalservice.api.profiles.ISignalProfile
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.*
import kotlin.math.max


class BcmProfileLogic(val mAccountContext: AccountContext) {

    companion object {
        const val TYPE_PROFILE = 1
        const val TYPE_AVATAR_HD = 2
        const val TYPE_AVATAR_LD = 3
        const val TYPE_AVATAR_BOTH = 4
        private const val INDIVIDUAL_SHORT_SHARE_PATH = "/v1/opaque_data"
        private const val UPLOAD_ENCRYPT_NAME_PATH = "/v1/profile/nickname/%s"
        private const val UPLOAD_ENCRYPT_AVATAR_PATH = "/v1/profile/avatar"
        private const val PROFILES_PLAINTEXT_PATH = "/v1/profile"
        private const val PROFILE_KEY_PATH = "/v1/profile/keys"

        private const val TAG = "BcmProfileLogic"

        const val PROFILE_FETCH_MAX = 500
        const val AVATAR_FETCH_MAX = 4

        const val TASK_HANDLE_DELAY = 1500L

        fun getAvatarFileName(recipient: Recipient, isHd: Boolean, isTemp: Boolean): String {
            return "${recipient.address.serialize()}_${System.currentTimeMillis()}_avatar${if (isHd) "HD" else "LD"}${if (isTemp) "_tmp" else ""}.jpg"
        }

        fun clearAvatarResource(avatarUri: String?) {
            try {
                if (avatarUri == null) return
                val uri = Uri.parse(avatarUri)
                val path = uri.path
                File(path).apply {
                    if (exists()) {
                        delete()
                    }
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "clearAvatarResource fail", ex)
            }
        }
    }

    interface ProfileFetchCallback {
        fun onDone(recipient: Recipient, viaJob: Boolean)
    }

    data class TaskData(val recipient: Recipient,
                        val type: Int,
                        var forceUpdate: Boolean = false,
                        var callbackSet: MutableSet<ProfileFetchCallback>? = null) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TaskData

            if (recipient != other.recipient) return false
            if (type != other.type) return false

            return true
        }

        override fun hashCode(): Int {
            var result = recipient.hashCode()
            result = 31 * result + type
            return result
        }
    }

    private val mHandingMap: MutableMap<String, TaskData> = mutableMapOf()
    private var mHighProfileQueue: Deque<TaskData> = LinkedList()
    private var mProfileQueue: Deque<TaskData> = LinkedList()
    private var mHighAvatarQueue: Deque<TaskData> = LinkedList()
    private var mAvatarQueue: Deque<TaskData> = LinkedList()

    private var mProfileJob: WeakReference<ProfileFetchJob>? = null
    private var mAvatarJob: WeakReference<AvatarDownloadJob>? = null

    private var mShortLinkCreating: Boolean = false


    fun updateProfileKey(context: Context, recipient: Recipient, profileKeyModel: ProfileKeyModel) {

        val nameKey = profileKeyModel.nickNameKey
        val avatarKey = profileKeyModel.avatarKey
        val version = profileKeyModel.version
        val privacyProfile = recipient.privacyProfile
        var nameKeyUpdate = false
        var avatarKeyUpdate = false

        if (nameKey.isNotEmpty() && nameKey != privacyProfile.nameKey && privacyProfile.name.isNullOrEmpty()) {
            nameKeyUpdate = true
        }
        if (avatarKey.isNotEmpty() && avatarKey != privacyProfile.avatarKey && (privacyProfile.avatarHD.isNullOrEmpty() || privacyProfile.avatarLD.isNullOrEmpty())) {
            avatarKeyUpdate = true
        }

        if (nameKeyUpdate || avatarKeyUpdate) {
            Observable.create(ObservableOnSubscribe<Boolean> {

                ALog.d(TAG, "updateProfileKey uid: ${recipient.address}, profileKeyModel: $profileKeyModel")
                var needFetchProfile = false

                if (nameKeyUpdate) {
                    try {
                        if (!privacyProfile.encryptedName.isNullOrEmpty()) {
                            privacyProfile.name = getContentFromPrivacy(privacyProfile.encryptedName, nameKey, version)

                        } else {
                            needFetchProfile = true
                        }

                    } catch (ex: Exception) {
                        ALog.e(TAG, "updateProfileKey decrypt name fail, uid: ${recipient.address}", ex)
                        needFetchProfile = true

                    } finally {
                        privacyProfile.nameKey = nameKey
                    }
                }

                if (avatarKeyUpdate) {
                    try {
                        if (!privacyProfile.encryptedAvatarLD.isNullOrEmpty()) {
                            val newAvatarLD = getContentFromPrivacy(privacyProfile.encryptedAvatarLD, avatarKey, version)
                            if (newAvatarLD != privacyProfile.avatarLD) {
                                privacyProfile.isAvatarLdOld = true
                                privacyProfile.avatarLD = newAvatarLD
                            } else {
                                privacyProfile.isAvatarLdOld = false
                            }

                        } else {
                            needFetchProfile = true
                        }

                        if (!privacyProfile.encryptedAvatarHD.isNullOrEmpty()) {
                            val newAvatarHD = getContentFromPrivacy(privacyProfile.encryptedAvatarHD, avatarKey, version)
                            if (newAvatarHD != privacyProfile.avatarHD) {
                                privacyProfile.isAvatarHdOld = true
                                privacyProfile.avatarHD = newAvatarHD
                            } else {
                                privacyProfile.isAvatarHdOld = false
                            }

                        } else {
                            needFetchProfile = true
                        }

                    } catch (ex: Exception) {
                        ALog.e(TAG, "updateProfileKey decrypt avatar fail, uid: ${recipient.address}", ex)
                        needFetchProfile = true

                    } finally {
                        privacyProfile.avatarKey = avatarKey
                    }
                }

                Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)

                it.onNext(needFetchProfile)
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        AmeModuleCenter.contact(mAccountContext)?.handleFriendPropertyChanged(recipient.address.serialize())
                        if (it) {
                            ALog.i(TAG, "updateProfileKey after forceFetchProfile")
                            forceToFetchProfile(recipient)
                        } else {
                            ALog.i(TAG, "updateProfileKey after checkDownloadAvatar")
                            checkNeedDownloadAvatar(recipient, isHd = false)
                        }
                    }, {
                        ALog.e(TAG, "updateProfileKey error", it)
                    })
        }

    }


    private fun checkUploadPrepare(handledRecipient: Recipient, forName: Boolean): Observable<Boolean> {
        ALog.d(TAG, "checkUploadPrepare uid: ${handledRecipient.address}, forName: $forName")
        return Observable.create<Recipient> {
            it.onNext(handledRecipient.resolve())
            it.onComplete()
        }.observeOn(AmeDispatcher.ioScheduler)
                .flatMap { recipient ->
                    val privacyProfile = recipient.privacyProfile
                    if (recipient.needRefreshProfile() || (if (forName) privacyProfile.nameKey.isNullOrEmpty() else privacyProfile.avatarKey.isNullOrEmpty())) {
                        getProfiles(listOf(TaskData(recipient, TYPE_PROFILE, true)))
                    }
                    else {
                        Observable.just(true)
                    }
                }
    }

    @SuppressLint("CheckResult")
    fun uploadNickName(context: Context, handledRecipient: Recipient, name: String, callback: (success: Boolean) -> Unit) {

        checkUploadPrepare(handledRecipient, true)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .flatMap { toUpload ->
            val recipient = handledRecipient.resolve()
            val privacyProfile = recipient.privacyProfile
            if (toUpload) {
                val encryptName = getPrivacyContent(name, privacyProfile.nameKey, privacyProfile.version)
                ALog.d(TAG, "setEncryptName: $encryptName")

                val path = String.format(UPLOAD_ENCRYPT_NAME_PATH, URLEncoder.encode(encryptName))
                RxIMHttp.get(mAccountContext).put<AmeEmpty>(BcmHttpApiHelper.getApi(path), "", AmeEmpty::class.java)
                        .subscribeOn(AmeDispatcher.ioScheduler)
                        .observeOn(AmeDispatcher.ioScheduler)
                        .map {
                            privacyProfile.name = name
                            privacyProfile.encryptedName = encryptName
                            Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)
                            true
                        }
            } else {
                Observable.just(false)
            }
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                    if (it) {
                        updateShareLink(context, handledRecipient) {}
                    }
                }, {
                    ALog.e(TAG, "uploadNickName error", it)
                    callback.invoke(false)
                })
    }

    @SuppressLint("CheckResult")
    fun uploadAvatar(context: Context, handledRecipient: Recipient, avatarBitmap: Bitmap, callback: (success: Boolean) -> Unit) {

        class PrepareData(val privacyProfile: PrivacyProfile,
                          val hdPath: File, val hdEncryptPath: File, val ldPath: File, val ldEncryptPath: File, var useSame: Boolean,
                          var hdAvatarBitmap: Bitmap?, var ldAvatarBitmap: Bitmap?)

        fun clearDiscardResources(prepareData: PrepareData, isAll: Boolean = true) {

            if (isAll) {
                prepareData.hdPath.delete()
                prepareData.ldPath.delete()
            }
            prepareData.hdEncryptPath.delete()
            prepareData.ldEncryptPath.delete()
            try {
                prepareData.hdAvatarBitmap?.recycle()
                prepareData.ldAvatarBitmap?.recycle()
            } catch (ex: Exception) {
                ALog.e(TAG, "clearDiscardResources", ex)
            }
        }

        fun doPrepare(context: Context, recipient: Recipient, avatarBitmap: Bitmap): PrepareData? {

            val prepareData = PrepareData(recipient.privacyProfile,
                    File(AmeFileUploader.DECRYPT_DIRECTORY, getAvatarFileName(recipient, true, true)),
                    File(AmeFileUploader.ENCRYPT_DIRECTORY, getAvatarFileName(recipient, true, true)),
                    File(AmeFileUploader.DECRYPT_DIRECTORY, getAvatarFileName(recipient, false, true)),
                    File(AmeFileUploader.ENCRYPT_DIRECTORY, getAvatarFileName(recipient, false, true)),
                    true, avatarBitmap, null)

            try {
                Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, prepareData.privacyProfile)

                val keyBytes = Base64.decode(prepareData.privacyProfile.avatarKey)

                FileOutputStream(prepareData.hdPath).use {
                    avatarBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    it.flush()
                }
                BCMEncryptUtils.encryptFileByAES256(prepareData.hdPath.absolutePath, prepareData.hdEncryptPath.absolutePath, keyBytes)

                val width = avatarBitmap.width
                val height = avatarBitmap.height
                var desireWidth = width
                var desireHeight = height
                val compareSize = max(width, height)

                val maxLDSize = PrivacyProfile.getMaxLDSize()
                if (compareSize > maxLDSize) {
                    if (compareSize == width) {
                        desireHeight = maxLDSize * height / compareSize
                        desireWidth = maxLDSize
                    } else {
                        desireHeight = maxLDSize
                        desireWidth = maxLDSize * width / compareSize
                    }
                    prepareData.ldAvatarBitmap = ThumbnailUtils.extractThumbnail(avatarBitmap, desireWidth, desireHeight, 0)

                    FileOutputStream(prepareData.ldPath).use {
                        prepareData.ldAvatarBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        it.flush()
                    }
                    BCMEncryptUtils.encryptFileByAES256(prepareData.ldPath.absolutePath, prepareData.ldEncryptPath.absolutePath, keyBytes)

                    prepareData.useSame = false
                }

                return prepareData

            } catch (ex: Exception) {
                ALog.e(TAG, "uploadAvatar error", ex)
                clearDiscardResources(prepareData)
            }
            return null

        }

        fun doUploadAvatar(privacyProfile: PrivacyProfile, hdAvatar: String?, ldAvatar: String?): Observable<Boolean> {
            try {
                val avatarKey = privacyProfile.avatarKey
                if (avatarKey?.isNotEmpty() == true) {
                    val encryptLdAvatar = getPrivacyContent(ldAvatar ?: hdAvatar
                    ?: throw Exception("avatarLD is null"), avatarKey, privacyProfile.version)
                    val encryptHdAvatar = getPrivacyContent(hdAvatar
                            ?: throw Exception("avatarHD is null"), avatarKey, privacyProfile.version)

                    ALog.d(TAG, "setEncryptAvatar hd: $encryptHdAvatar ld: $encryptLdAvatar")
                    return RxIMHttp.get(mAccountContext).put<AmeEmpty>(BcmHttpApiHelper.getApi(UPLOAD_ENCRYPT_AVATAR_PATH)
                            , String.format("{\"hdAvatar\":\"%s\", \"ldAvatar\":\"%s\"}", encryptHdAvatar, encryptLdAvatar)
                            , AmeEmpty::class.java)
                            .subscribeOn(AmeDispatcher.ioScheduler)
                            .observeOn(AmeDispatcher.ioScheduler)
                            .map {
                                privacyProfile.encryptedAvatarHD = encryptHdAvatar
                                privacyProfile.encryptedAvatarLD = encryptLdAvatar
                                privacyProfile.avatarHD = hdAvatar
                                privacyProfile.avatarLD = ldAvatar ?: hdAvatar
                                true
                            }
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "uploadAvatar error", ex)
            }

            return Observable.just(false)
        }

        fun doAfterUploadAvatarBitmap(recipient: Recipient, prepareData: PrepareData, avatarHd: String?, avatarLd: String?): Observable<Boolean> {
            try {
                if (avatarHd == null && avatarLd == null) {
                    ALog.d(TAG, "doAfterUploadAvatarBitmap avatarHd or avatarLd is null")
                    clearDiscardResources(prepareData)
                } else {
                    return doUploadAvatar(prepareData.privacyProfile, avatarHd, avatarLd)
                            .observeOn(AmeDispatcher.ioScheduler)
                            .map {
                                if (it) {
                                    val finalHdPath = File(AmeFileUploader.DECRYPT_DIRECTORY, getAvatarFileName(recipient, true, false))
                                    prepareData.hdPath.renameTo(finalHdPath)
                                    clearAvatarResource(prepareData.privacyProfile.avatarHDUri)
                                    clearAvatarResource(prepareData.privacyProfile.avatarLDUri)
                                    prepareData.privacyProfile.avatarHDUri = BcmFileUtils.getFileUri(finalHdPath.absolutePath).toString()
                                    prepareData.privacyProfile.avatarLDUri = if (prepareData.useSame) prepareData.privacyProfile.avatarHDUri else {
                                        val finalLdPath = File(AmeFileUploader.DECRYPT_DIRECTORY, getAvatarFileName(recipient, false, false))
                                        prepareData.ldPath.renameTo(finalLdPath)
                                        BcmFileUtils.getFileUri(finalLdPath.absolutePath).toString()
                                    }

                                    Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, prepareData.privacyProfile)
                                    clearDiscardResources(prepareData, false)

                                    ALog.d(TAG, "doUploadAvatar success avatarHDUri: ${prepareData.privacyProfile.avatarHDUri}, avatarLDUri: ${prepareData.privacyProfile.avatarLDUri}")
                                    true
                                } else {
                                    throw Exception("upload avatar failed")
                                }
                            }
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "doAfterUploadAvatarBitmap error", ex)
            }
            return Observable.just(false)
        }

        fun run(): Observable<Boolean> {
            return Observable.create(ObservableOnSubscribe<Boolean> { uploadEmitter ->

                val recipient = handledRecipient.resolve()
                val prepareData = doPrepare(context, recipient, avatarBitmap)
                if (prepareData == null) {
                    uploadEmitter.onError(Exception("uploadAvatar error, prepare fail"))
                } else {
                    val uploadFileList = mutableListOf<String>()
                    uploadFileList.add(prepareData.hdEncryptPath.absolutePath)
                    if (!prepareData.useSame) {
                        uploadFileList.add(prepareData.ldEncryptPath.absolutePath)
                    }

                    AmeFileUploader.uploadMultiFileToAws(mAccountContext, context, AmeFileUploader.AttachmentType.PROFILE, uploadFileList, object : AmeFileUploader.MultiFileUploadCallback {

                        override fun onFailed(resultMap: MutableMap<String, AmeFileUploader.FileUploadResult>?) {
                            doAfterUploadAvatarBitmap(recipient, prepareData, null, null)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.mainScheduler)
                                    .subscribe({
                                        uploadEmitter.onNext(it)
                                        uploadEmitter.onComplete()
                                    }, {
                                        clearDiscardResources(prepareData)
                                        uploadEmitter.onError(it)
                                    })
                        }

                        override fun onSuccess(resultMap: MutableMap<String, AmeFileUploader.FileUploadResult>?) {
                            val avatarHd = resultMap?.get(prepareData.hdEncryptPath.absolutePath)?.location
                            val avatarLd = if (prepareData.useSame) {
                                avatarHd
                            } else {
                                resultMap?.get(prepareData.ldEncryptPath.absolutePath)?.location
                            }

                            doAfterUploadAvatarBitmap(recipient, prepareData, avatarHd, avatarLd)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.mainScheduler)
                                    .subscribe({
                                        uploadEmitter.onNext(it)
                                        uploadEmitter.onComplete()
                                    }, {
                                        clearDiscardResources(prepareData)
                                        uploadEmitter.onError(it)
                                    })
                        }
                    })

                }

            })
        }

        checkUploadPrepare(handledRecipient, false)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .flatMap {
                    if (it) {
                        run()
                    }else {
                        Observable.just(false)
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    callback.invoke(result)
                }, {
                    ALog.e(TAG, "uploadAvatar error", it)
                    callback.invoke(false)
                })

    }

    private data class UpdateShareLinkReq(val content: String) : NotGuard

    private data class UpdateShareLinkRes(val index: String?) : NotGuard

    @SuppressLint("CheckResult")
    fun updateShareLink(context: Context, handledRecipient: Recipient, callback: (success: Boolean) -> Unit) {
        if (mShortLinkCreating) {
            ALog.i(TAG, "updateShareLink fail, shortLink is creating")
            callback.invoke(false)
            return
        }
        mShortLinkCreating = true

        val recipient = handledRecipient.resolve()
        val privacyProfile = recipient.privacyProfile
        val sourceByteArray = Recipient.toQRCode(recipient).toByteArray()
        val hash = BCMEncryptUtils.murmurHash3(0xFBA4C795, sourceByteArray)
        val content = Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(sourceByteArray, hash.toString().toByteArray()))
        val req = UpdateShareLinkReq(content)
        RxIMHttp.get(mAccountContext).put<UpdateShareLinkRes>(BcmHttpApiHelper.getApi(INDIVIDUAL_SHORT_SHARE_PATH)
                , GsonUtils.toJson(req)
                , UpdateShareLinkRes::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    if (it.index.isNullOrEmpty()) {
                        throw Exception("index failed")
                    }

                    privacyProfile.setShortLink(it.index, Base62.encode(hash))

                    ALog.d(TAG, "updateShareLink newShortLink: ${privacyProfile.shortLink}")

                    Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)

                }.observeOn(AmeDispatcher.mainScheduler)
                .subscribe({
                    mShortLinkCreating = false
                    callback.invoke(true)
                }, {
                    ALog.e(TAG, "updateShareLink error", it)
                    mShortLinkCreating = false
                    callback.invoke(false)
                })
    }


    private data class UserShareLinkRes(val content: String?) : NotGuard

    fun checkShareLink(context: Context, shareLink: String, callback: (qrData: Recipient.RecipientQR?) -> Unit) {
        if (shareLink.isEmpty() || !PrivacyProfile.isShortLink(shareLink)) {
            callback.invoke(null)
            return
        }

        val i1 = shareLink.lastIndexOf("/")
        val i2 = shareLink.lastIndexOf("#")
        if (i1 == -1 || i2 == -1) {
            throw Exception("checkShareLink shareLink is invalid")
        }
        val index = shareLink.substring(i1 + 1, i2)
        val hashString = shareLink.substring(i2 + 1)
        ALog.d(TAG, "checkShareLink shareLink: $shareLink, index: $index")
        val url = BcmHttpApiHelper.getApi("$INDIVIDUAL_SHORT_SHARE_PATH/$index")
        RxIMHttp.get(mAccountContext).get<UserShareLinkRes>(url, null, UserShareLinkRes::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    if (it.content.isNullOrEmpty()) {
                        throw Exception("no link data")
                    }
                    val targetContent = String(BCMEncryptUtils.decryptByAES256(Base64.decode(it.content), Base62.decode(hashString).toString().toByteArray()))
                    val recipientQR = Recipient.RecipientQR.fromJson(targetContent)
                            ?: throw Exception("get content from shortLink error")
                    Recipient.from(mAccountContext, recipientQR.uid, false)
                    recipientQR
                }
                .observeOn(AmeDispatcher.mainScheduler)
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "checkShareLink error", it)
                    callback.invoke(null)
                })
    }

    /**
     * update nick by other situation
     */
    fun updateNickFromOtherWay(recipient: Recipient, nick: String) {
        AmeDispatcher.io.dispatch {
            ALog.logForSecret(TAG, "updateNickFromOtherWay uid: ${recipient.address}, nick: $nick")
            if (recipient.resolve().profileName != nick) {
                Repository.getRecipientRepo(mAccountContext)?.setProfileName(recipient, nick)
            }
        }
    }

    private fun getHandlingMapKey(recipient: Recipient, type: Int): String {
        return "${recipient.address}_$type"
    }

    fun getAvailableTaskDataListOfProfile(): List<TaskData> {
        return getAvailableTaskDataList(mHighProfileQueue, mProfileQueue, PROFILE_FETCH_MAX)
    }

    fun getAvailableTaskDataListOfAvatar(): List<TaskData> {
        return getAvailableTaskDataList(mHighAvatarQueue, mAvatarQueue, AVATAR_FETCH_MAX)
    }

    @Synchronized
    private fun getAvailableTaskDataList(highQueue: Deque<TaskData>, queue: Deque<TaskData>, max: Int): List<TaskData> {
        val result = mutableListOf<TaskData>()
        var data: TaskData? = null
        var isWaited = false
        do {
            data = highQueue.poll()
            if (data != null) {
                val k = getHandlingMapKey(data.recipient, data.type)
                if (mHandingMap.containsKey(k)) {
                    result.add(data)
                    mHandingMap.remove(k)
                }
            } else {
                data = queue.poll()
                if (data != null) {
                    val k = getHandlingMapKey(data.recipient, data.type)
                    if (mHandingMap.containsKey(k)) {
                        result.add(data)
                        mHandingMap.remove(k)
                    }
                }
            }
            if (data == null && !isWaited && result.size < 10) {
                isWaited = true
                Thread.sleep(TASK_HANDLE_DELAY)
            } else if (data == null || result.size >= max) {
                break
            }

        } while (true)

        return result
    }

    @Synchronized
    private fun checkTaskQueueAdded(recipient: Recipient, type: Int, force: Boolean, callback: ProfileFetchCallback?): Boolean {

        val k = getHandlingMapKey(recipient, type)
        var t = mHandingMap[k]
        var add = false
        var hq: Queue<TaskData>? = null
        var q: Queue<TaskData>? = null
        when (type) {
            TYPE_PROFILE -> {
                hq = mHighProfileQueue
                q = mProfileQueue
            }
            TYPE_AVATAR_LD -> {
                hq = mHighAvatarQueue
                q = mAvatarQueue
            }
            TYPE_AVATAR_HD -> {
                hq = mHighAvatarQueue
                q = mAvatarQueue
            }
            TYPE_AVATAR_BOTH -> {
                hq = mHighAvatarQueue
                q = mAvatarQueue
            }
        }

        if (t == null) {
            t = if (callback != null) {
                TaskData(recipient, type, force, mutableSetOf(callback))
            } else {
                TaskData(recipient, type, force, null)

            }
            if (force) {
                hq?.offer(t)
            } else {
                q?.offer(t)
            }
            mHandingMap[k] = t
            add = true
        } else {
            if (callback != null) {
                val set = t.callbackSet ?: mutableSetOf()
                set.add(callback)
                t.callbackSet = set
            }
            if (t.forceUpdate) {

            } else if (!t.forceUpdate && force) {
                t.forceUpdate = true
                hq?.offer(t)
                add = true
            }

        }
        return add
    }

    fun checkNeedDownloadAvatar(vararg recipients: Recipient, isHd: Boolean) {
        ALog.i(TAG, "checkNeedDownloadAvatar recipient size: ${recipients.size}")
        AmeDispatcher.io.dispatch {
            var need = false
            for (r in recipients) {
                if (doAvatarDownloadCheck(r, if (isHd) TYPE_AVATAR_HD else TYPE_AVATAR_LD)) {
                    need = true
                }
            }
            if (need) {
                handleDownloadAvatar()
            }
        }
    }

    fun checkNeedDownloadAvatarWithAll(vararg recipients: Recipient) {
        ALog.i(TAG, "checkNeedDownloadAvatarWithAll recipient size: ${recipients.size}")
        AmeDispatcher.io.dispatch {
            var need = false
            for (r in recipients) {
                if (doAvatarDownloadCheck(r, TYPE_AVATAR_LD)) {
                    need = true
                }
                if (doAvatarDownloadCheck(r, TYPE_AVATAR_HD)) {
                    need = true
                }
            }
            if (need) {
                handleDownloadAvatar()
            }
        }
    }

    private fun doAvatarDownloadCheck(r: Recipient, type: Int): Boolean {
        if (r.isGroupRecipient) {
            return false
        }
        r.privacyProfile.let {
            val hdNeed = !it.avatarKey.isNullOrEmpty() && (it.isAvatarHdOld || (!it.encryptedAvatarHD.isNullOrEmpty() && it.avatarHDUri.isNullOrEmpty()))
            val ldNeed = !it.avatarKey.isNullOrEmpty() && (it.isAvatarLdOld || (!it.encryptedAvatarLD.isNullOrEmpty() && it.avatarLDUri.isNullOrEmpty()))
            val targetType = when(type) {
                TYPE_AVATAR_BOTH -> if (hdNeed && ldNeed) {
                    TYPE_AVATAR_BOTH
                }else if (hdNeed) {
                    TYPE_AVATAR_HD
                }else if (ldNeed) {
                    TYPE_AVATAR_LD
                }else {
                    null
                }
                TYPE_AVATAR_HD -> if (hdNeed) {
                    type
                }else {
                    null
                }
                TYPE_AVATAR_LD -> if (ldNeed) {
                    type
                }else {
                    null
                }
                else -> null
            }
            if (targetType != null) {
                if (checkTaskQueueAdded(r, targetType, false, null)) {
                    return true
                }
            }
        }
        return false
    }

    fun checkNeedFetchProfile(vararg recipients: Recipient, callback: ProfileFetchCallback? = null) {
        ALog.i(TAG, "checkNeedFetchProfile recipients size: ${recipients.size}")
        AmeDispatcher.io.dispatch {
            var need = false
            for (r in recipients) {
                if (r.isGroupRecipient) {
                    callback?.onDone(r, false)
                    continue
                }
                if (r.privacyProfile.encryptedName.isNullOrEmpty() && r.privacyProfile.encryptedAvatarLD.isNullOrEmpty() && r.privacyProfile.encryptedAvatarHD.isNullOrEmpty()) {
                    if (checkTaskQueueAdded(r, TYPE_PROFILE, false, callback)) {
                        need = true
                    }
                } else {
                    callback?.onDone(r, false)
                }
            }
            if (need) {
                handleFetchProfile()
            }
        }

    }

    @SuppressLint("CheckResult")
    fun forceToFetchProfile(vararg recipients: Recipient, callback: ProfileFetchCallback? = null) {
        ALog.i(TAG, "forceToFetchProfile recipients size: ${recipients.size}")
        AmeDispatcher.io.dispatch {
            var need = false
            for (r in recipients) {
                if (r.isGroupRecipient) {
                    callback?.onDone(r, false)
                    continue
                }
                if (checkTaskQueueAdded(r, TYPE_PROFILE, true, callback)) {
                    need = true
                }
            }
            if (need) {
                handleFetchProfile()
            }
        }
    }

    @Throws(Exception::class)
    private fun checkProfileKeyCompleteOrUpload(context: Context, recipient: Recipient, privacyProfile: PrivacyProfile, forName: Boolean): Boolean {
        var keyUpload = false
        try {
            if (forName) {
                if (privacyProfile.nameKey.isNullOrEmpty()) {
                    val oneTimeKeyPair = BCMPrivateKeyUtils.generateKeyPair()
                    val nameKey = Base64.encodeBytes(BCMEncryptUtils.calculateMySelfAgreementKey(mAccountContext, oneTimeKeyPair.privateKey.serialize()))
                    privacyProfile.nameKey = nameKey
                    privacyProfile.namePubKey = Base64.encodeBytes((oneTimeKeyPair.publicKey as DjbECPublicKey).publicKey)
                    ALog.d(TAG, "checkProfileKeyCompleteOrUpload nameKey: ${privacyProfile.nameKey}")
                    keyUpload = true
                }
            }
            else {
                if (privacyProfile.avatarKey.isNullOrEmpty()) {
                    val oneTimeKeyPair = BCMPrivateKeyUtils.generateKeyPair()
                    val avatarKey = Base64.encodeBytes(BCMEncryptUtils.calculateMySelfAgreementKey(mAccountContext, oneTimeKeyPair.privateKey.serialize()))
                    privacyProfile.avatarKey = avatarKey
                    privacyProfile.avatarPubKey = Base64.encodeBytes((oneTimeKeyPair.publicKey as DjbECPublicKey).publicKey)
                    ALog.d(TAG, "checkProfileKeyCompleteOrUpload avatarKey: ${privacyProfile.avatarKey}")
                    keyUpload = true
                }
            }

            if (keyUpload) {
                if (!uploadProfileKeys(privacyProfile.getUploadKeys())) {
                    if (forName) {
                        privacyProfile.nameKey = ""
                        privacyProfile.namePubKey = ""
                    }else {
                        privacyProfile.avatarKey = ""
                        privacyProfile.avatarPubKey = ""
                    }
                    Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)
                    return false
                } else {
                    Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)
                    if (forName) {
                        uploadNickName(context, recipient, recipient.name) {}
                    }
                }
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "checkProfileKeyCompleteOrUpload error", ex)
        }
        return true
    }

    private fun downloadProfileKeys(context:Context, recipient: Recipient, privacyProfile: PrivacyProfile): Boolean {
        var profileKeyString = ""
        try {
            val wrapper = SyncHttpWrapper(IMHttp.get(mAccountContext))
            profileKeyString = wrapper.get<String>(BcmHttpApiHelper.getApi(PROFILE_KEY_PATH), null, String::class.java)

        }catch (ex: NoContentException) {
            ALog.e(TAG, "downloadProfileKeys getProfileKeys error", ex)
        }catch (ex: Exception) {
            return false
        }

        try {
            ALog.d(TAG, "downloadProfileKeys getProfileKey: $profileKeyString")
            val json = if (profileKeyString.isEmpty()) {
                JSONObject()
            }else {
                JSONObject(JSONObject(profileKeyString).optString("encrypt")
                        ?: throw Exception("profileKey is null"))
            }
            val namePubKeyString = json.optString("namePubKey", "")
            val avatarPubKeyString = json.optString("avatarPubKey", "")
            val version = json.optInt("version", PrivacyProfile.CURRENT_VERSION)

            if ((namePubKeyString != privacyProfile.namePubKey && namePubKeyString.isNotEmpty()) || privacyProfile.nameKey.isNullOrEmpty()) {
                try {
                    privacyProfile.nameKey = if (namePubKeyString.isEmpty()) {
                        throw Exception("namePubKeyString is null")
                    } else {
                        var otherPubKeyBytes = Base64.decode(namePubKeyString)
                        if (otherPubKeyBytes.size >= 33) {
                            val pb = ByteArray(32)
                            System.arraycopy(otherPubKeyBytes, 1, pb, 0, pb.size)
                            otherPubKeyBytes = pb
                        }
                        Base64.encodeBytes(BCMEncryptUtils.calculateAgreementKeyWithMe(mAccountContext, otherPubKeyBytes))
                    }
                    privacyProfile.namePubKey = namePubKeyString

                    if (!privacyProfile.nameKey.isNullOrEmpty()) {
                        privacyProfile.name = getContentFromPrivacy(privacyProfile.encryptedName, privacyProfile.nameKey, privacyProfile.version)
                        ALog.d(TAG, "handlePrivacyProfileChanged uid: ${recipient.address}, name: ${privacyProfile.name}")
                    }

                } catch (ex: Exception) {
                    privacyProfile.nameKey = ""
                    privacyProfile.namePubKey = ""

                    if (!checkProfileKeyCompleteOrUpload(context, recipient, privacyProfile, true)) {
                        throw Exception("checkProfileKeyCompleteOrUpload fail")
                    }
                }
            }

            if ((avatarPubKeyString != privacyProfile.avatarPubKey && avatarPubKeyString.isNotEmpty()) || privacyProfile.avatarKey.isNullOrEmpty()) {
                try {
                    privacyProfile.avatarKey = if (avatarPubKeyString.isEmpty()) {
                        throw Exception("avatarPubKeyString is null")
                    } else {
                        var otherPubKeyBytes = Base64.decode(avatarPubKeyString)
                        if (otherPubKeyBytes.size >= 33) {
                            val pb = ByteArray(32)
                            System.arraycopy(otherPubKeyBytes, 1, pb, 0, pb.size)
                            otherPubKeyBytes = pb
                        }
                        Base64.encodeBytes(BCMEncryptUtils.calculateAgreementKeyWithMe(mAccountContext, otherPubKeyBytes))
                    }
                    privacyProfile.avatarPubKey = avatarPubKeyString

                    if (!privacyProfile.avatarKey.isNullOrEmpty()) {
                        privacyProfile.avatarLD = getContentFromPrivacy(privacyProfile.encryptedAvatarLD, privacyProfile.avatarKey, privacyProfile.version)

                        privacyProfile.isAvatarLdOld = true

                        privacyProfile.avatarHD = getContentFromPrivacy(privacyProfile.encryptedAvatarHD, privacyProfile.avatarKey, privacyProfile.version)

                        privacyProfile.isAvatarHdOld = true
                    }

                } catch (ex: Exception) {
                    privacyProfile.avatarKey = ""
                    privacyProfile.avatarPubKey = ""

                    if (!checkProfileKeyCompleteOrUpload(context, recipient, privacyProfile, false)) {
                        throw Exception("checkProfileKeyCompleteOrUpload fail")
                    }
                }
            }

            if (privacyProfile.version != version) {
                privacyProfile.version = version
            }
            return true

        } catch (ex: Exception) {
            ALog.e(TAG, "downloadProfileKeys error", ex)
        }
        return false
    }

    private fun uploadProfileKeys(profileKeyJson: String): Boolean {
        try {
            ALog.d(TAG, "uploadProfileKeys: $profileKeyJson")

            val wrapper = SyncHttpWrapper(IMHttp.get(mAccountContext))
            wrapper.put<AmeEmpty>(BcmHttpApiHelper.getApi(PROFILE_KEY_PATH), profileKeyJson, AmeEmpty::class.java)

            ALog.d(TAG, "uploadProfileKeys succeed")
            return true

        } catch (ex: Throwable) {
            ALog.e(TAG, "uploadProfileKeys fail", ex)
        }
        return false
    }

    private fun getPrivacyContent(content: String?, key: String?, version: Int): String {
        ALog.d(TAG, "getPrivacyContent content: $content, key: $key version: $version")
        val newContent = EncryptUtils.encryptMD5ToString("[BCM_PRIVACY:$version]") + content
        return Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(newContent.toByteArray(), Base64.decode(key)))
    }

    private fun getContentFromPrivacy(content: String?, key: String?, version: Int): String {
        try {
            ALog.d(TAG, "getContentFromPrivacy content: $content, key: $key, version: $version")
            val finalContent = String(BCMEncryptUtils.decryptByAES256(Base64.decode(content), Base64.decode(key)))
            val mac = EncryptUtils.encryptMD5ToString("[BCM_PRIVACY:$version]")
            ALog.d(TAG, "getContentFromPrivacy finalContent: $finalContent, mac: $mac")
            if (finalContent.length < mac.length) {
                throw Exception("invalid key")
            }
            val sub = finalContent.substring(0, mac.length)
            if (sub.toUpperCase(Locale.getDefault()) == mac) {
                return finalContent.substring(mac.length)
            } else {
                throw Exception("invalid key")
            }

        } catch (ex: Exception) {
            throw Exception("getContentFromPrivacy fail", ex)
        }
    }




    @Synchronized
    fun finishJob(job: ContextJob) {

        if (job is ProfileFetchJob) {
            ALog.i(TAG, "finish profile job")
            mProfileJob = null
        } else {
            ALog.i(TAG, "finish avatar job")
            mAvatarJob = null
        }
    }

    @Synchronized
    private fun handleDownloadAvatar() {

        if (mAvatarJob?.get() == null) {
            val accountJobManager = AmeModuleCenter.accountJobMgr(mAccountContext)
            if (accountJobManager != null) {
                ALog.i(TAG, "handleDownloadAvatar")
                val job = AvatarDownloadJob(AppContextHolder.APP_CONTEXT, mAccountContext,this)
                mAvatarJob = WeakReference(job)
                accountJobManager.add(job)
            }
        } else {
            ALog.i(TAG, "handleDownloadAvatar exist")
        }
    }

    @Synchronized
    private fun handleFetchProfile() {

        if (mProfileJob?.get() == null) {
            val accountJobManager = AmeModuleCenter.accountJobMgr(mAccountContext)
            if (accountJobManager != null) {
                ALog.i(TAG, "handleFetchProfile")
                val job = ProfileFetchJob(AppContextHolder.APP_CONTEXT, mAccountContext,this)
                mProfileJob = WeakReference(job)
                accountJobManager.add(job)
            }
        } else {
            ALog.i(TAG, "handleFetchProfile exist")
        }
    }

    fun doneHandling(taskDataList: List<TaskData>, isSuccess: Boolean) {
        ALog.d(TAG, "releaseHanding list: ${taskDataList.size},  isSuccess: $isSuccess")

        for (data in taskDataList) {
            val recipient = data.recipient
            if (recipient.isLogin) {
                recipient.setNeedRefreshProfile(!isSuccess)
            } else {
                recipient.setNeedRefreshProfile(if (isSuccess) false else recipient.needRefreshProfile())
            }

            val callbackList = data.callbackSet
            if (callbackList != null && callbackList.isNotEmpty()) {
                callbackList.forEach {
                    it.onDone(recipient, true)
                }
            }

            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
            provider.checkNeedRequestAddFriend(AppContextHolder.APP_CONTEXT, recipient)

        }
    }

    internal fun handlePrivacyProfileChanged(context: Context, recipient: Recipient, privacyProfile: PrivacyProfile,
                                    newEncryptName: String?, newEncryptAvatarLD: String?, newEncryptAvatarHD: String?, allowStranger: Boolean): Boolean {

        var needUpdateProfileKey = false
        var nameChanged = false
        var avatarHdChanged = false
        var avatarLdChanged = false
        var allowStrangerChanged = false

        if (!newEncryptName.isNullOrEmpty() && newEncryptName != privacyProfile.encryptedName) {
            privacyProfile.encryptedName = newEncryptName
            nameChanged = true
        }
        if (privacyProfile.nameKey.isNullOrEmpty()) {
            ALog.i(TAG, "handlePrivacyProfileChanged nameKey is null")
            needUpdateProfileKey = true

        } else if (nameChanged || privacyProfile.name.isNullOrEmpty()) {
            try {
                privacyProfile.name = getContentFromPrivacy(newEncryptName, privacyProfile.nameKey, privacyProfile.version)
                ALog.d(TAG, "handlePrivacyProfileChanged uid: ${recipient.address}, name: ${privacyProfile.name}")
                nameChanged = true
            } catch (ex: Exception) {
                ALog.e(TAG, "fetchProfile getNickName error, uid: ${recipient.address}", ex)
                needUpdateProfileKey = true
                privacyProfile.name = ""
                privacyProfile.nameKey = ""
            }
        }

        if (!newEncryptAvatarLD.isNullOrEmpty() && newEncryptAvatarLD != privacyProfile.encryptedAvatarLD) {
            privacyProfile.encryptedAvatarLD = newEncryptAvatarLD
            avatarLdChanged = true
        }
        if (privacyProfile.avatarKey.isNullOrEmpty()) {
            ALog.i(TAG, "handlePrivacyProfileChanged avatarKey is null")
            needUpdateProfileKey = true
        } else if (avatarLdChanged || privacyProfile.avatarLD.isNullOrEmpty()) {
            try {
                privacyProfile.avatarLD = getContentFromPrivacy(newEncryptAvatarLD, privacyProfile.avatarKey, privacyProfile.version)
                ALog.d(TAG, "handlePrivacyProfileChanged uid: ${recipient.address}, avatarLD: ${privacyProfile.avatarLD}")
                privacyProfile.isAvatarLdOld = true
                avatarLdChanged = true

            } catch (ex: Exception) {
                ALog.e(TAG, "fetchProfile getAvatarLD error, uid: ${recipient.address}", ex)
                privacyProfile.avatarLD = ""
                privacyProfile.avatarKey = ""
                needUpdateProfileKey = true
            }
        }


        if (!newEncryptAvatarHD.isNullOrEmpty() && newEncryptAvatarHD != privacyProfile.encryptedAvatarHD) {
            privacyProfile.encryptedAvatarHD = newEncryptAvatarHD
            avatarHdChanged = true
        }
        if (privacyProfile.avatarKey.isNullOrEmpty()) {
            ALog.i(TAG, "handlePrivacyProfileChanged avatarKey is null")
            needUpdateProfileKey = true

        } else if (avatarHdChanged || privacyProfile.avatarHD.isNullOrEmpty()) {
            try {
                privacyProfile.avatarHD = getContentFromPrivacy(newEncryptAvatarHD, privacyProfile.avatarKey, privacyProfile.version)
                ALog.d(TAG, "handlePrivacyProfileChanged uid: ${recipient.address}, avatarHD: ${privacyProfile.avatarHD}")
                privacyProfile.isAvatarHdOld = true
                privacyProfile.avatarHDUri = ""
                avatarHdChanged = true

            } catch (ex: Exception) {
                ALog.e(TAG, "fetchProfile getAvatarHD error, uid: ${recipient.address}", ex)
                privacyProfile.avatarHD = ""
                privacyProfile.avatarKey = ""
                needUpdateProfileKey = true
            }
        }

        if (privacyProfile.allowStranger != allowStranger) {
            privacyProfile.allowStranger = allowStranger
            allowStrangerChanged = true
        }

        if (recipient.isLogin && needUpdateProfileKey) {
            ALog.i(TAG, "need download ProfileKeys")
            if (downloadProfileKeys(context, recipient, privacyProfile)) {
                return true
            }

        } else {
            ALog.d(TAG, "need transfer ProfileKeys: $needUpdateProfileKey")
        }

        return nameChanged || avatarHdChanged || avatarLdChanged || allowStrangerChanged || needUpdateProfileKey
    }

    @Throws(Exception::class)
    private fun handleIndividualRecipient(context: Context, recipient: Recipient, profile: ISignalProfile?, force: Boolean = false): Boolean {

        var privacyChanged = false
        var plaintextChanged = false
        try {
            if (profile == null) {
                return false
            }

            val privacyProfile = recipient.resolve().privacyProfile
            var newProfileKey = recipient.profileKey
            val newProfileName: String? = recipient.profileName
            var newProfileAvatar: String? = recipient.profileAvatar
            val newEncryptName: String? = profile.getEncryptName()
            val newEncryptAvatarLD: String? = profile.getEncryptAvatarLD()
            val newEncryptAvatarHD: String? = profile.getEncryptAvatarHD()
            val supportFeatures: String? = profile.getSupportFeatures()
            val identityKey: String? = profile.getIdentityKey()

            if (!identityKey.isNullOrEmpty()) {
                if (!AddressUtil.isValid(recipient.address, identityKey)) {
                    if (recipient.identityKey.isNullOrEmpty()) {
                        return false
                    } else {
                        recipient.identityKey = ""
                        plaintextChanged = true
                    }
                } else {
                    if (identityKey != recipient.identityKey) {
                        recipient.identityKey = identityKey
                        plaintextChanged = true
                    }
                    if (force) {
                        try {
                            IdentityUtil.saveIdentity(context, mAccountContext, recipient.address.serialize(), IdentityKey(Base64.decode(identityKey), 0), true)
                        } catch (ex: Exception) {
                            ALog.e("RetrieveRecipientProfile", "post identityKey fail")
                        }
                    }
                }
            }

            if (!TextUtils.isEmpty(supportFeatures) && supportFeatures != recipient.featureSupport.toString()) {
                Repository.getRecipientRepo(mAccountContext)?.setSupportFeatures(recipient, supportFeatures.orEmpty())
            }

            if (recipient.isLogin) {
                if (supportFeatures != AMELogin.mySupport.toString()) {
                    AmeModuleCenter.login().refreshMySupport2Server(mAccountContext)
                    ALog.i(TAG, "refreshMySupport2Server")
                }
            }

            if (handlePrivacyProfileChanged(context, recipient, privacyProfile, newEncryptName, newEncryptAvatarLD, newEncryptAvatarHD, profile.isAllowStrangerMessages())) {
                ALog.d(TAG, "handlePrivacyProfileChanged isChanged: true, uid: ${recipient.address}")
                privacyChanged = true
            }

            if (!TextUtils.isEmpty(profile.getProfileKey())) {
                if (newProfileKey == null || profile.getProfileKey() != Base64.encodeBytes(newProfileKey)) {
                    newProfileKey = profile.getProfileKeyArray()
                    plaintextChanged = true
                }
            }

            if (!privacyProfile.avatarHDUri.isNullOrEmpty() && !BcmFileUtils.isExist(privacyProfile.avatarHDUri)) {
                privacyProfile.avatarHDUri = ""
                privacyChanged = true
            }
            if (!privacyProfile.avatarLDUri.isNullOrEmpty() && !BcmFileUtils.isExist(privacyProfile.avatarLDUri)) {
                privacyProfile.avatarLDUri = ""
                privacyChanged = true
            }

            if (!newProfileAvatar.isNullOrEmpty() && newProfileAvatar.startsWith(ContentResolver.SCHEME_FILE) && !BcmFileUtils.isExist(newProfileAvatar)) {
                newProfileAvatar = ""
                plaintextChanged = true
            }

            if (privacyChanged) {
                if (recipient.isLogin) {
                    val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
                    provider.saveAccount(recipient, privacyProfile)

                    updateShareLink(AppContextHolder.APP_CONTEXT, recipient) {
                        ALog.i(TAG, "privacyChanged, updateShareLink")
                    }
                }
                Repository.getRecipientRepo(mAccountContext)?.setPrivacyProfile(recipient, privacyProfile)
            }

            if (plaintextChanged) {
                Repository.getRecipientRepo(mAccountContext)?.setProfile(recipient, newProfileKey, newProfileName, newProfileAvatar)
            }

        } catch (e: Exception) {
            ALog.e(TAG, "handleIndividualRecipient error", e)
        } finally {
            if (recipient.isLogin) {
                PrivacyProfileUpgrader(this).checkNeedUpgrade(recipient)
            }
        }

        return privacyChanged || plaintextChanged
    }

    fun fetchProfileWithNoQueue(recipient: Recipient, callback: (success: Boolean) -> Unit): Disposable {
        val taskData = TaskData(recipient, TYPE_PROFILE, true)
        return getProfiles(listOf(taskData))
                .observeOn(AmeDispatcher.mainScheduler)
                .subscribe({
                    ALog.i(TAG, "fetchProfileWithNoQueue checkNeedDownloadAvatar")
                    recipient.setNeedRefreshProfile(if (it) false else recipient.needRefreshProfile())
                    checkNeedDownloadAvatarWithAll(recipient)
                    callback.invoke(true)
                }, {
                    ALog.e(TAG, "fetchProfileWithNoQueue error", it)
                    callback.invoke(false)
                })
    }

    internal fun getProfiles(dataList: List<TaskData>): Observable<Boolean> {
        val builder = StringBuilder()
        for (e164Number in dataList) {
            builder.append("\"").append(e164Number.recipient.address.serialize()).append("\",")
        }
        if (builder.isNotEmpty()) {
            builder.deleteCharAt(builder.length - 1)
        }

        return RxIMHttp.get(mAccountContext).put<String>(BcmHttpApiHelper.getApi(PROFILES_PLAINTEXT_PATH)
                , String.format("{\"contacts\":[%s]}", builder.toString())
                , String::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {

                    val profileJson = if (it.isEmpty()) null else JSONObject(it).optJSONObject("profiles")
                    if (profileJson == null) {
                        ALog.d(TAG, "job run result: $it")
                        throw Exception("handleTaskData getProfileJson null")
                    } else {
                        ALog.d(TAG, "getProfiles json: $profileJson")
                        dataList.forEach { data ->
                            val recipient = data.recipient.resolve()
                            val detail = profileJson.optString(recipient.address.serialize())
                            ALog.d(TAG, "fetch uid: ${recipient.address}, profile : $detail")
                            val profileData = if (!detail.isNullOrEmpty()) {
                                GsonUtils.fromJson(detail, PlaintextServiceProfile::class.java)
                            }else {
                                PlaintextServiceProfile()
                            }
                            handleIndividualRecipient(AppContextHolder.APP_CONTEXT, recipient, profileData, data.forceUpdate)
                            recipient.setNeedRefreshProfile(false)
                        }
                    }
                    true
                }.doOnError {
                    dataList.forEach {
                        it.recipient.setNeedRefreshProfile(true)
                    }
                }
    }


}

