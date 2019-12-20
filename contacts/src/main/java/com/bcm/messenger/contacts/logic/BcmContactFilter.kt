package com.bcm.messenger.contacts.logic

import android.content.Context
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.contacts.net.BcmContactCore
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import org.whispersystems.libsignal.ecc.ECKeyPair

/**
 * Created by wjh on 2019/5/29
 */
class BcmContactFilter() {

    companion object {
        private const val TAG = "BcmContactFilter"

        private const val LAST_CONTACT_FILTER = "pref_last_contact_filter"
        private const val LAST_VIRTUAL_FRIEND = "pref_last_virtual_friend"
        private const val LAST_FILTER_VERSION = "pref_contact_filter_version"
        private const val LAST_UPLOAD_RESULT = "pref_last_upload_result"
        private const val VIRTUAL_FRIEND_NUM = 200
        private const val DEFAULT_CONTACT_BEGIN = 256
        private const val DEFAULT_BLOOM_BEGIN = 1536
        private const val DEFAULT_CONTACT_ADD = 64
        private const val DEFAULT_BLOOM_ADD = 320
        private const val DEFAULT_CONTACT_BUFF = 10
    }

    private val mAlgoEntity: BcmContactCore.AlgoEntity
    private val mBloomFilter: BcmBloomFilter

    init {
        mAlgoEntity = BcmContactCore.AlgoEntity.ofZero()
        mBloomFilter = BcmBloomFilter(mAlgoEntity.seed, mAlgoEntity.func, mAlgoEntity.tweek)
    }


    fun updateContact(context: Context, contactSet: List<BcmContactCore.ContactItem>, callback: (success: Boolean) -> Unit) {
        Observable.create<Boolean> {
            ALog.d(TAG, "updateContact")
            var uploadAll = false
            val nowList = contactSet.filter { it.relationship != RecipientRepo.Relationship.STRANGER.type }

            //last uploaded bloom
            val lastBloomData = findLastBloomData(context)
            val lastBloomSize = (lastBloomData.size * 8)
            //current bloom length
            val newBloomSize = checkNewBloomSize(nowList.size)
            ALog.d(TAG, "lastBloomSize: $lastBloomSize, newBloomSize: $newBloomSize")
            //diff bloom
            if (newBloomSize != lastBloomSize) {
                uploadAll = true
            }

            var virtualFriendList = findLastVirtualFriend(context)
            if (virtualFriendList.isEmpty()) {
                virtualFriendList = createNewVirtualFriend(context)
            }
            mBloomFilter.updateDataArray(newBloomSize)
            mBloomFilter.insert(AMESelfData.uid.toByteArray())
            for (uid in virtualFriendList) {
                mBloomFilter.insert(uid.toByteArray())
            }
            for (s in nowList) {
                mBloomFilter.insert(s.uid.toByteArray())
            }

            val contactCore: BcmContactCore = BcmContactLogic.coreApi
            if (uploadAll) {
                uploadAllFilter(context, contactCore, Base64.encodeBytes(mBloomFilter.getDataArray()), it)
            } else {
                val lastBloomFilter = BcmBloomFilter(mAlgoEntity.seed, mAlgoEntity.func, mAlgoEntity.tweek)
                lastBloomFilter.updateDataArray(lastBloomData)
                val requestList = getDifferencePatch(lastBloomFilter, mBloomFilter)
                if (requestList.isNotEmpty()) {
                    uploadPatchFilter(context, contactCore, Base64.encodeBytes(mBloomFilter.getDataArray()), requestList, it)
                }else {
                    ALog.d(TAG, "no need updaterContact, isChanged: false")
                    it.onNext(true)
                    it.onComplete()
                }
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "updaterContact error", it)
                    callback.invoke(false)
                })
    }

    private fun getDifferencePatch(lastBloomFilter: BcmBloomFilter, currentBloomFilter: BcmBloomFilter): List<BcmContactCore.ContactFilterPatchRequest> {
        val bitSize = currentBloomFilter.getDataSize()
        if (lastBloomFilter.getDataSize() != bitSize) {
            throw Exception("bloom data size is not same")
        }

        val result = ArrayList<BcmContactCore.ContactFilterPatchRequest>(bitSize)
        var current: Boolean
        var last: Boolean
        for (i in 0 until bitSize) {
            current = currentBloomFilter.getBit(i)
            last = lastBloomFilter.getBit(i)
            if (current != last) {
                result.add(BcmContactCore.ContactFilterPatchRequest(i, current))
            }
        }
        return result
    }

    private fun uploadAllFilter(context: Context, contactCore: BcmContactCore, bloomDataBase64: String, emitter: ObservableEmitter<Boolean>) {
        ALog.d(TAG, "uploadAllFilter")
        contactCore.uploadContactFilters(bloomDataBase64, mAlgoEntity.id) {result ->
            ALog.d(TAG, "uploadAllFilter result: ${result != null}")
            if (result == null) {
                TextSecurePreferences.setBooleanPreference(context, LAST_UPLOAD_RESULT, false)
            }else {
                TextSecurePreferences.setBooleanPreference(context, LAST_UPLOAD_RESULT, true)
                TextSecurePreferences.setStringPreference(context, LAST_CONTACT_FILTER, bloomDataBase64)
                TextSecurePreferences.setStringPreference(context, LAST_FILTER_VERSION, result.version)
                ALog.d(TAG, "uploadAllFilter response success, bloomData: $bloomDataBase64, version: ${result.version}")
            }
            emitter.onNext(result != null)
            emitter.onComplete()
        }
    }

    private fun uploadPatchFilter(context: Context, contactCore: BcmContactCore, bloomDataBase64: String, patchList: List<BcmContactCore.ContactFilterPatchRequest>, emitter: ObservableEmitter<Boolean>) {
        ALog.d(TAG, "uploadPatchFilter patchList: ${patchList.size}")
        if (patchList.isEmpty()) {
            emitter.onNext(true)
            emitter.onComplete()
            return
        }
        val version = TextSecurePreferences.getStringPreference(context, LAST_FILTER_VERSION, "")
        contactCore.patchContactFilters(version, patchList) {result ->
            ALog.d(TAG, "uploadPatchFilter result: ${result != null}")
            if (result == null) {
                TextSecurePreferences.setBooleanPreference(context, LAST_UPLOAD_RESULT, false)
                emitter.onNext(false)
                emitter.onComplete()
            }else if (result.uploadAll){
                uploadAllFilter(context, contactCore, bloomDataBase64, emitter)
            }else {
                TextSecurePreferences.setBooleanPreference(context, LAST_UPLOAD_RESULT, true)
                TextSecurePreferences.setStringPreference(context, LAST_CONTACT_FILTER, bloomDataBase64)
                TextSecurePreferences.setStringPreference(context, LAST_FILTER_VERSION, result.version)
                ALog.d(TAG, "uploadPatchFilter response success, bloomData: $bloomDataBase64")

                emitter.onNext(true)
                emitter.onComplete()
            }
        }
    }

    private fun createNewVirtualFriend(context: Context): Set<String> {
        ALog.d(TAG, "createNewVirtualFriend")
        val newVirtualList = mutableSetOf<String>()
        var keyPair: ECKeyPair
        for (i in 0 until VIRTUAL_FRIEND_NUM) {
            keyPair = BCMPrivateKeyUtils.generateKeyPair()
            newVirtualList.add(BCMPrivateKeyUtils.provideUid(keyPair.publicKey.serialize()))
        }
        TextSecurePreferences.setStringSetPreference(context, LAST_VIRTUAL_FRIEND, newVirtualList)
        return newVirtualList
    }

    private fun findLastBloomData(context: Context): ByteArray {
        try {
            val contentString = TextSecurePreferences.getStringPreference(context, LAST_CONTACT_FILTER, "")
            if (!contentString.isEmpty()) {
                return Base64.decode(contentString)
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "findLastBloomData error", ex)
        }
        return ByteArray(0)
    }

    private fun findLastVirtualFriend(context: Context): Set<String> {
        try {
            return TextSecurePreferences.getStringSetPreference(context, LAST_VIRTUAL_FRIEND) ?: setOf()
        }catch (ex: Exception) {
            ALog.e(TAG, "findLastVirtualFriend error", ex)
        }
        return setOf()
    }

    private fun checkNewBloomSize(contactSize: Int): Int {
        val difference = contactSize + 1 + VIRTUAL_FRIEND_NUM - DEFAULT_CONTACT_BEGIN
        return if (difference <= 0) {
            DEFAULT_BLOOM_BEGIN
        }else {
            val count = difference / DEFAULT_CONTACT_ADD
            val buff = difference % DEFAULT_CONTACT_ADD
            if (buff < DEFAULT_CONTACT_BUFF) {
                DEFAULT_BLOOM_BEGIN + count * DEFAULT_BLOOM_ADD
            }else {
                DEFAULT_BLOOM_BEGIN + (count + 1) * DEFAULT_BLOOM_ADD
            }
        }
    }
}