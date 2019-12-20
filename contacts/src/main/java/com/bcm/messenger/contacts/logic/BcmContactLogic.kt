package com.bcm.messenger.contacts.logic

import android.content.Context
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.p
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.contacts.net.BcmContactCore
import com.bcm.messenger.contacts.net.BcmHash
import com.bcm.messenger.contacts.search.BcmContactFinder
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.callback.OriginCallback
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.whispersystems.signalservice.internal.websocket.FriendProtos
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by bcm.social.01 on 2019/3/12.
 */
object BcmContactLogic: AppForeground.IForegroundEvent {

    private const val TAG = "BcmContactLogic"
    private const val DELAY_CONTACT_UPDATE = 500L

    private const val VERSION_REQUEST = 1

    private val mBackgroundRequestQueue: LinkedBlockingQueue<Recipient> = LinkedBlockingQueue(500)
    private var mBackgroundRequestJob: Job? = null
        @Synchronized get
        @Synchronized set

    const val CONTACT_PART_MAX = 20

    private val mContactSyncFlag: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(0))
    private var mQuitSyncFlag: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(0))

    private val contactFilter = BcmContactFilter()
    internal val coreApi = BcmContactCore()

    private val mCurrentContactListRef = AtomicReference<List<RecipientSettings>>()
    val contactFinder = BcmContactFinder()

    private val cache = BcmContactCache()

    private var mObserver: Observer<List<RecipientSettings>> = Observer {
        onContactListChanged(it)
    }

    private var mLocalLiveData: LiveData<List<RecipientSettings>>? = null
    val contactLiveData = MutableLiveData<List<Recipient>>()
    private var mContactListDisposable: Disposable? = null
    private var mContactSyncDisposable: Disposable? = null

    init {
        AppForeground.listener.addListener(this)
        EventBus.getDefault().register(this)

    }

    private fun init(callback: (() -> Unit)? = null) {
        ALog.i(TAG, "init")
        AmeDispatcher.mainThread.dispatch {
            cache.initCache()
            BcmFinderManager.get().registerFinder(contactFinder)
            if (mLocalLiveData == null) {
                mLocalLiveData = Repository.getRecipientRepo()?.getRecipientsLiveData()
                mLocalLiveData?.observeForever(mObserver)
            }
            checkAndSync()
            callback?.invoke()
        }

    }

    private fun unInit(callback: (() -> Unit)? = null) {
        ALog.i(TAG, "unInit")
        mBackgroundRequestJob?.cancel()
        mBackgroundRequestJob = null
        mBackgroundRequestQueue.clear()
        mContactListDisposable?.dispose()
        mContactListDisposable = null
        contactFinder.cancel()
        mCurrentContactListRef.set(listOf())
        AmeDispatcher.mainThread.dispatch {
            BcmFinderManager.get().unRegisterFinder(contactFinder)
            mLocalLiveData?.removeObserver(mObserver)
            mLocalLiveData = null
            cache.clearCache()
            callback?.invoke()
        }

    }

    fun checkRequestFriendForOldVersion(recipient: Recipient) {
        if (!AmeConfigure.isContactTransformEnable()) {
            ALog.d(TAG, "checkRequestFriendForOldVersion fail, config is unable")
            return
        }

        if (recipient.featureSupport?.isSupportBidirectionalContact() != true) {
            return
        }

        if (recipient.relationship == RecipientRepo.Relationship.FOLLOW && !recipient.isBackgroundRequestAddFriendFlag && !recipient.isSelf) {
            recipient.isBackgroundRequestAddFriendFlag = true
            ALog.d(TAG, "checkRequestFriendForOldVersion recipient: ${recipient.address} is follow")
            if (!mBackgroundRequestQueue.contains(recipient)) {
                val success = mBackgroundRequestQueue.offer(recipient)
                if (success) {
                    GlobalScope.launch {
                        if (mBackgroundRequestJob == null || mBackgroundRequestJob?.isCompleted == true) {
                            mBackgroundRequestJob = GlobalScope.launch {
                                try {
                                    var retry = 0
                                    var target: Recipient?
                                    do {
                                        target = mBackgroundRequestQueue.poll()
                                        ALog.d(TAG, "checkRequestFriendForOldVersion poll target: ${target?.address}")
                                        if (target != null) {
                                            retry = 0
                                            val r = target
                                            addFriend(r.address.serialize(), "", true) { success ->
                                                try {
                                                    ALog.d(TAG, "checkRequestFriendForOldVersion recipient: ${r.address} result: $success")
                                                    if (!success) {
                                                        recipient.isBackgroundRequestAddFriendFlag = false
                                                    }
                                                }catch (ex: Exception) {

                                                }
                                            }

                                        } else {
                                            retry++
                                        }

                                        delay(1000)
                                        ALog.d(TAG, "checkRequestFriendForOldVersion delay after 3000ms, retry: $retry")

                                    } while (target != null || retry <= 3)

                                    ALog.d(TAG, "checkRequestFriendForOldVersion finish")

                                }catch (ex: Exception) {
                                    ALog.logForSecret(TAG, "checkRequestFriendForOldVersion error", ex)
                                }
                            }

                        }else {
                            ALog.d(TAG, "checkRequestFriendForOldVersion recipient: ${recipient.address}, backgroundRequestJob exist")

                        }
                    }

                }else {
                    ALog.d(TAG, "checkRequestFriendForOldVersion recipient: ${recipient.address} fail, queue is full")
                }
            }else {
                ALog.d(TAG, "checkRequestFriendForOldVersion recipient: ${recipient.address}, queue contain")
            }
        }
    }

    private fun onContactListChanged(newRecipientList: List<RecipientSettings>) {

        mCurrentContactListRef.set(newRecipientList)
        if (mContactListDisposable?.isDisposed == false) {
            ALog.i(TAG, "onContactListChanged has task handling")
            return
        }
        mContactListDisposable = Observable.create<List<Recipient>> {

            ALog.i(TAG, "onContactListChanged begin, check contactSyncFlag")
            checkFirstLogin()

            ALog.i(TAG, "onContactListChanged run")

            val bcmList = mutableListOf<Recipient>()
            try {
                bcmList.add(Recipient.fromSelf(AppContextHolder.APP_CONTEXT, false))
                bcmList.addAll(mCurrentContactListRef.get().map {settings ->
                    Recipient.fromSnapshot(AppContextHolder.APP_CONTEXT, Address.fromSerialized(settings.uid), settings)
                })

                contactFinder.updateContact(bcmList, Recipient.getRecipientComparator())
                ALog.i(TAG, "onContactListChanged end, bcmList: ${bcmList.size}")

                it.onNext(contactFinder.getContactList())

            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }
                .delaySubscription(DELAY_CONTACT_UPDATE, TimeUnit.MILLISECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mContactListDisposable = null
                    contactLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "onContactListChanged error", it)
                    mContactListDisposable = null
                    contactLiveData.postValue(listOf())
                })


    }

    fun getPartKey(uid: String): String {
        return (BcmHash.hash(uid.toByteArray()) % CONTACT_PART_MAX).toString()
    }

    fun doForLogin() {
        ALog.i(TAG, "doForLogin")
        val lastFlag = mContactSyncFlag.get()
        if (lastFlag.count <= 0) {
            mContactSyncFlag.set(CountDownLatch(1))
        }
        init()
    }

    fun doForLogout() {
        ALog.i(TAG, "doForLogout")
        mQuitSyncFlag.set(CountDownLatch(1))
        checkAndSync(true) { success ->
            ALog.i(TAG, "doForLogout checkAndSync result: $success")
            unInit {
                mQuitSyncFlag.get().countDown()
            }
        }
        mQuitSyncFlag.get().await()

    }

    fun checkAndSync(force: Boolean = false, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "checkAndSync")

        if (mContactSyncDisposable?.isDisposed == false && !force) {
            ALog.w(TAG, "checkAndSync fail, has sync disposable")
            return
        }
        mContactSyncDisposable = Observable.create<Boolean> {

            ALog.i(TAG, "checkAnsSync begin")
            syncContact {result ->
                it.onNext(result)
                it.onComplete()
            }

        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .subscribe({
                    ALog.i(TAG, "checkAndSync finish: $it")
                    callback?.invoke(it)

                }, {
                    ALog.e(TAG, "checkAndSync error", it)
                    callback?.invoke(false)
                })
    }

    private fun syncContact(callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "syncContact")
        if (!cache.waitCacheReady()) {
            ALog.w(TAG, "syncContact fail, cache is not ready")
            callback?.invoke(false)
            return
        }
        ALog.i(TAG, "syncContact begin")
        val handlingList = cache.getHandlingList()
        val handlingMap = cache.localHandlingMap(handlingList)
        coreApi.syncFriendList(cache.getContactMap(), cache.getLastUploadHashMap(), handlingMap)
                .observeOn(Schedulers.io())
                .doOnNext {
                    cache.updateSyncContactMap(it.uploadMap, it.hashResult, handlingList)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "syncContact succeed")
                    mContactSyncFlag.get().countDown()
                    contactFilter.updateContact(AppContextHolder.APP_CONTEXT) { success ->
                        if (success) {
                            ALog.i(TAG, "updateContactFilter success")
                        }else {
                            ALog.e(TAG, "updateContactFilter fail")
                        }
                        callback?.invoke(success)
                    }

                }, {
                    if (it is NoContentException) {
                        ALog.w(TAG, "syncContact fail, noContentChanged, just syncPatch")

                        AmeDispatcher.io.dispatch {

                            val uploadMap = mutableMapOf<String, Set<RecipientSettings>>()
                            for (i in 0 until CONTACT_PART_MAX) {
                                val k = i.toString()
                                uploadMap[k] = cache.getContactSet(k) ?: setOf()
                            }
                            syncPatch(uploadMap, handlingList) {result ->
                                mContactSyncFlag.get().countDown()
                                callback?.invoke(result)
                            }
                        }

                    }else {
                        ALog.e(TAG, "syncContact fail", it)
                        mContactSyncFlag.get().countDown()
                        contactFilter.checkHandleLastFail(AppContextHolder.APP_CONTEXT) {
                            callback?.invoke(false)
                        }
                    }
                })

    }

    private fun syncPatch(uploadMap: Map<String, Set<RecipientSettings>>,
                          handlingList: Set<BcmFriend>,
                          callback: ((result: Boolean) -> Unit)? = null) {

        coreApi.uploadFriendList(uploadMap)
                .observeOn(Schedulers.io())
                .map {
                    cache.updateSyncContactMap(it.uploadMap, it.hashResult, handlingList)
                    it
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "uploadFriends succeed")
                    mContactSyncFlag.get().countDown()
                    contactFilter.updateContact(AppContextHolder.APP_CONTEXT) { success ->
                        if (success) {
                            ALog.i(TAG, "updateContactFilter success")
                        }else {
                            ALog.e(TAG, "updateContactFilter fail")
                        }
                        callback?.invoke(success)
                    }

                }, {
                    ALog.e(TAG, "uploadFriends failed", it)
                    mContactSyncFlag.get().countDown()
                    contactFilter.checkHandleLastFail(AppContextHolder.APP_CONTEXT) {
                        callback?.invoke(false)
                    }
                })
    }

    private fun checkFirstLogin() {
        try {
            if (cache.waitCacheReady()) {
                mContactSyncFlag.get().await()
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "checkFirstLogin error", ex)
        }
    }

    fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)? = null) {

        checkFirstLogin()

        fun doSendAddFriendRequest(recipient: Recipient, callback: ((result: Boolean) -> Unit)?) {
            try {
                val payload = createRequestBody(AppContextHolder.APP_CONTEXT, handleBackground, recipient, memo)
                coreApi.sendAddFriendReq(targetUid, payload, object : OriginCallback() {
                    override fun onError(call: Call?, e: Exception?, id: Long) {
                        ALog.e(TAG, "Add friend error, ${e?.message}", e)
                        call?.cancel()
                        callback?.invoke(false)
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        ALog.i(TAG, "Add friend success = ${response?.code() in 200..299}")
                        if (response?.code() in 200..299) {
                            try {
                                handleRequestAddFriend(recipient) {
                                    callback?.invoke(it)
                                }
                            } catch (ex: Exception) {
                                ALog.e(TAG, "handleRequestAddFriend error", ex)
                                callback?.invoke(false)
                            }
                        } else {
                            callback?.invoke(false)
                        }
                    }
                })

            } catch (ex: Exception) {
                ALog.e(TAG, "addHandling error", ex)
                callback?.invoke(false)
            }
        }

        val targetRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(targetUid), false)
        if (targetRecipient.identityKey.isNullOrEmpty()) {
            RecipientProfileLogic.fetchProfileFeatureWithNoQueue(targetRecipient, callback = object : RecipientProfileLogic.ProfileDownloadCallback {

                override fun onDone(recipient: Recipient, isThrough: Boolean) {
                    if (recipient == targetRecipient) {
                        AmeDispatcher.io.dispatch {
                            doSendAddFriendRequest(recipient, callback)
                        }

                    }
                }

            })
        } else {
            doSendAddFriendRequest(targetRecipient, callback)
        }
    }

    fun replyAddFriend(targetUid: String, approved: Boolean, proposer: String, addFriendSignature: String, callback: ((result: Boolean) -> Unit)? = null) {

        checkFirstLogin()

        fun doSendAddFriendReply(recipient: Recipient, approved: Boolean, proposer: String, addFriendSignature: String, callback: ((result: Boolean) -> Unit)?) {
            try {
                val payload = createRequestBody(AppContextHolder.APP_CONTEXT, false, recipient, "")
                coreApi.sendAddFriendReply(approved, proposer, payload, addFriendSignature, object : OriginCallback() {
                    override fun onError(call: Call?, e: Exception?, id: Long) {
                        ALog.logForSecret(TAG, "Reply add friend error, ${e?.message}")
                        call?.cancel()
                        callback?.invoke(false)
                    }

                    override fun onResponse(response: Response?, id: Long) {
                        ALog.i(TAG, "Reply add friend approved $approved success = ${response?.code() in 200..299}")
                        if (response?.code() in 200..299) {
                            try {
                                if (approved) {
                                    val otherRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(proposer), false)
                                    handleBecomeFriend(otherRecipient) {
                                        callback?.invoke(it)
                                    }
                                }else {
                                    callback?.invoke(true)
                                }

                            } catch (ex: Exception) {
                                ALog.e(TAG, "handleBecomeFriend error", ex)
                                callback?.invoke(false)
                            }

                        } else {
                            callback?.invoke(false)
                        }
                    }
                })
            } catch (ex: Exception) {
                ALog.e(TAG, "replyAddFriend error", ex)
                callback?.invoke(false)
            }
        }

        val targetRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(targetUid), false)
        if (targetRecipient.identityKey.isNullOrEmpty()) {
            ALog.i(TAG, "replyAddFriend approved: $approved identityKey is null, need update profile")
            RecipientProfileLogic.fetchProfileFeatureWithNoQueue(targetRecipient, callback = object : RecipientProfileLogic.ProfileDownloadCallback {
                override fun onDone(recipient: Recipient, isThrough: Boolean) {
                    if (targetRecipient == recipient) {
                        AmeDispatcher.io.dispatch {
                            ALog.i(TAG, "replyAddFriend approved: $approved after update profile, doSendAddFriendReply")
                            doSendAddFriendReply(recipient, approved, proposer, addFriendSignature, callback)
                        }

                    }
                }

            })
        } else {
            doSendAddFriendReply(targetRecipient, approved, proposer, addFriendSignature, callback)
        }
    }

    fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)? = null) {

        checkFirstLogin()

        coreApi.sendDeleteFriendReq(targetUid, object : OriginCallback() {
            override fun onError(call: Call?, e: Exception?, id: Long) {
                ALog.logForSecret(TAG, "Delete friend error, ${e?.message}")
                call?.cancel()
                callback?.invoke(false)
            }

            override fun onResponse(response: Response?, id: Long) {
                ALog.i(TAG, "Delete friend success = ${response?.code() in 200..299}")
                if (response?.code() in 200..299) {
                    val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(targetUid), false)
                    handleDeleteFriend(recipient, false) {
                        callback?.invoke(it)
                    }
                } else {
                    callback?.invoke(false)
                }
            }
        })
    }

    fun handleAddFriendRequest(request: FriendProtos.FriendRequest) {
        ALog.i(TAG, "Handle add friend request")

        checkFirstLogin()

        fun handleAfterRecipientUpdated(recipient: Recipient, decryptProposer: String) {
            try {
                // Triple<DoInBackground, Memo, toSync>
                val pair = parseRequestBody(AppContextHolder.APP_CONTEXT, recipient, request.payload)
                if (pair.first) {
                    ALog.i(TAG, "Auto reply message, check if recipient is follow status")
                    replyAddFriend(recipient.address.serialize(), true, decryptProposer, request.signature)
                } else {
                    ALog.i(TAG, "Not an auto reply message, save to database")
                    val friendDao = UserDatabase.getDatabase().friendRequestDao()
                    val dbRequest = BcmFriendRequest(decryptProposer, request.timestamp, pair.second, request.signature)
                    when (recipient.relationship) {
                        RecipientRepo.Relationship.BREAK,
                        RecipientRepo.Relationship.FRIEND -> {
                            replyAddFriend(recipient.address.serialize(), true, decryptProposer, request.signature)
                        }
                        else -> {
                            if (pair.third) {
                                AmeProvider.get<IContactModule>(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE)?.handleFriendPropertyChanged(recipient.address.serialize())
                            }

                            UserDatabase.getDatabase().runInTransaction {
                                val existsRequests = friendDao.queryExistsRequests(decryptProposer)
                                if (existsRequests.isNotEmpty()) {
                                    dbRequest.id = existsRequests[0].id
                                    if (existsRequests.size > 1) {
                                        friendDao.delete(existsRequests.subList(1, existsRequests.size))
                                    }
                                }
                                friendDao.insert(dbRequest)
                            }

                            val unreadCount = friendDao.queryUnreadCount()
                            RxBus.post(HomeTabEvent(HomeTabEvent.TAB_CONTACT, showFigure = unreadCount))
                            RxBus.post(FriendRequestEvent(unreadCount))

                            AmePushProcess.processPush(AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.FRIEND_NOTIFY, null, null,
                                    AmePushProcess.FriendNotifyData(recipient.address.serialize()), null, null)))
                        }
                    }
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "handleAddFriendRequest error", ex)
            }
        }

        try {
            val decryptProposer = BCMEncryptUtils.decryptSource(request.proposerBytes.toByteArray())
            val targetRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(decryptProposer), false)
            if (targetRecipient.identityKey.isNullOrEmpty()) {
                RecipientProfileLogic.fetchProfileFeatureWithNoQueue(targetRecipient, callback = object : RecipientProfileLogic.ProfileDownloadCallback {
                    override fun onDone(recipient: Recipient, isThrough: Boolean) {
                        if (targetRecipient == recipient) {
                            AmeDispatcher.io.dispatch {
                                handleAfterRecipientUpdated(recipient, decryptProposer)

                            }
                        } else {
                            ALog.i(TAG, "handleAddFriendRequest fail, identityKey is null, no response")
                        }
                    }
                })
            } else {
                handleAfterRecipientUpdated(targetRecipient, decryptProposer)
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "Handle request failed. ${e.message}", e)
        }
    }

    fun handleFriendReply(reply: FriendProtos.FriendReply) {
        ALog.i(TAG, "Handle add friend reply")
        checkFirstLogin()
        try {

            fun doForApproved(targetRecipient: Recipient, approved: Boolean) {
                if (approved) {
                    ALog.i(TAG, "Reply is accepted")
                    handleBecomeFriend(targetRecipient)
                } else {
                    handleRefuseToFriend(targetRecipient)
                }
            }

            val decryptTarget = BCMEncryptUtils.decryptSource(reply.targetBytes.toByteArray())
            val targetRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(decryptTarget), false)
            if (targetRecipient.identityKey.isNullOrEmpty()) {
                RecipientProfileLogic.fetchProfileFeatureWithNoQueue(targetRecipient, callback = object : RecipientProfileLogic.ProfileDownloadCallback {
                    override fun onDone(recipient: Recipient, isThrough: Boolean) {
                        if (targetRecipient == recipient) {
                            AmeDispatcher.io.dispatch {
                                try {
                                    parseRequestBody(AppContextHolder.APP_CONTEXT, targetRecipient, reply.payload)
                                }catch (ex: Exception) {
                                    ALog.e(TAG, "handleFriendReply parseRequestBody error", ex)
                                }
                                doForApproved(targetRecipient, reply.approved)
                            }
                        }
                    }
                })

            }else {
                try {
                    parseRequestBody(AppContextHolder.APP_CONTEXT, targetRecipient, reply.payload)
                }catch (ex: Exception) {
                    ALog.e(TAG, "handleFriendReply parseRequestBody error", ex)
                }
                doForApproved(targetRecipient, reply.approved)
            }

        } catch (e: Throwable) {
            ALog.e(TAG, "Handle add friend reply failed", e)
        }
    }

    fun handleDeleteFriend(delete: FriendProtos.DeleteFriend) {
        ALog.i(TAG, "Handle delete friend request")
        checkFirstLogin()
        try {
            val decryptProposer = BCMEncryptUtils.decryptSource(delete.proposerBytes.toByteArray())
            val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(decryptProposer), false)
            handleDeleteFriend(recipient, true)

        } catch (e: Throwable) {
            ALog.e(TAG, "Handle delete friend request error", e)
        }
    }

    fun handleFriendPropertyChanged(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.logForSecret(TAG, "handleFriendPropertyChanged uid: ${recipient.address}")
        val key = cache.addPropertyChangedHandling(recipient.settings)
        handleAfterCache(key)
        callback?.invoke(true)
    }

    @Throws(Exception::class)
    private fun handleRequestAddFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleRequestAddFriend old relationship: ${recipient.relationship}")
        recipient.relationship = when (recipient.relationship) {
            RecipientRepo.Relationship.FOLLOW -> RecipientRepo.Relationship.FOLLOW_REQUEST
            RecipientRepo.Relationship.STRANGER -> RecipientRepo.Relationship.REQUEST
            else -> {
                callback?.invoke(true)
                return
            }
        }
        val key = cache.addHandling(recipient.settings)
        handleAfterCache(key)
        callback?.invoke(true)
    }

    @Throws(Exception::class)
    private fun handleBecomeFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleBecomeFriend old relationship: ${recipient.relationship}")
        recipient.relationship = RecipientRepo.Relationship.FRIEND
        val key = cache.addHandling(recipient.settings)
        handleAfterCache(key)
        callback?.invoke(true)
    }

    private fun handleRefuseToFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleRefuseToFriend old relationship: ${recipient.relationship}")
        if (recipient.relationship == RecipientRepo.Relationship.REQUEST) {
            recipient.relationship = RecipientRepo.Relationship.STRANGER
            val key = cache.addHandling(recipient.settings)
            handleAfterCache(key)
        }
        callback?.invoke(true)
    }

    private fun handleDeleteFriend(recipient: Recipient, isPassive: Boolean, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleDeleteFriend isPassive: $isPassive, old relationship: ${recipient.relationship}")

        if (recipient.relationship == RecipientRepo.Relationship.FOLLOW || recipient.relationship == RecipientRepo.Relationship.FRIEND || recipient.relationship == RecipientRepo.Relationship.BREAK) {
            if (isPassive) {
                recipient.relationship = RecipientRepo.Relationship.BREAK
                val key = cache.addHandling(recipient.settings)
                handleAfterCache(key)
            } else {
                recipient.relationship = RecipientRepo.Relationship.STRANGER
                val key = cache.addHandling(recipient.settings)
                handleAfterCache(key)
            }

        }
        callback?.invoke(true)
    }

    private fun handleAfterCache(key: String) {
        Observable.create<Boolean> {

            ALog.i(TAG, "handleAfterCache begin")
            syncContact {result ->
                it.onNext(result)
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .subscribe({
                    ALog.i(TAG, "handleAfterCache finish: $it")
                }, {
                    ALog.e(TAG, "handleAfterCache error", it)
                })
    }

    private fun createRequestBody(context: Context, handleBackground: Boolean, recipient: Recipient, memo: String): String {
        ALog.i(TAG, "createRequestBody")
        val self = Recipient.fromSelf(context, false)
        val identityKey = Base64.decode(recipient.identityKey)
        val publicKey = ByteArray(32)
        var index = 0
        if (identityKey.size >= 33) {
            index = 1
        }
        System.arraycopy(identityKey, index, publicKey, 0, 32)
        val key = BCMEncryptUtils.calculateAgreementKeyWithMe(context, publicKey)
        val nameKeySource = self.privacyProfile.nameKey
        val nameKey = if (nameKeySource.isNullOrEmpty()) {
            ""
        } else {
            Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(Base64.decode(nameKeySource), key))
        }
        val avatarKeySource = self.privacyProfile.avatarKey
        val avatarKey = if (avatarKeySource.isNullOrEmpty()) {
            ""
        } else {
            Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(Base64.decode(avatarKeySource), key))
        }
        val memoEncrypt = if (memo.isNullOrEmpty()) {
            ""
        } else {
            Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(memo.toByteArray(), key))
        }
        val version = VERSION_REQUEST
        val requestBody = BcmContactCore.FriendRequestBody(handleBackground, nameKey, avatarKey, memoEncrypt, version)
        return GsonUtils.toJson(requestBody)

    }

    private fun parseRequestBody(context: Context, recipient: Recipient, requestBodyString: String): Triple<Boolean, String, Boolean> {

        ALog.d(TAG, "parseRequestBody requestBodyString: $requestBodyString")
        val identityKey = Base64.decode(recipient.identityKey)
        val publicKey = ByteArray(32)
        var index = 0
        if (identityKey.size >= 33) {
            index = 1
        }
        System.arraycopy(identityKey, index, publicKey, 0, 32)
        val key = BCMEncryptUtils.calculateAgreementKeyWithMe(context, publicKey) ?: throw Exception("calculateAgreementKeyWithMe fail")
        val requestBody = GsonUtils.fromJson<BcmContactCore.FriendRequestBody>(requestBodyString, object : TypeToken<BcmContactCore.FriendRequestBody>(){}.type)

        val memo = if (requestBody.requestMemo.isEmpty()) {
            ""
        } else {
            String(BCMEncryptUtils.decryptByAES256(Base64.decode(requestBody.requestMemo), key))
        }
        val nameKeyTarget = requestBody.nameKey
        val nameKey = if (nameKeyTarget.isEmpty()) {
            ""
        } else {
            Base64.encodeBytes(BCMEncryptUtils.decryptByAES256(Base64.decode(nameKeyTarget), key))
        }
        val avatarKeyTarget = requestBody.avatarKey
        val avatarKey = if (avatarKeyTarget.isEmpty()) {
            ""
        } else {
            Base64.encodeBytes(BCMEncryptUtils.decryptByAES256(Base64.decode(avatarKeyTarget), key))
        }
        var toSync = false
        val privacyProfile = recipient.privacyProfile
        if (privacyProfile.nameKey.isNullOrEmpty() && nameKey.isNotEmpty()) {
            privacyProfile.nameKey = nameKey
            toSync = true
        }
        if (privacyProfile.avatarKey.isNullOrEmpty() && avatarKey.isNotEmpty()) {
            privacyProfile.avatarKey = avatarKey
            toSync = true
        }

        RecipientProfileLogic.handlePrivacyProfileChanged(context, recipient, privacyProfile, privacyProfile.encryptedName,
                privacyProfile.encryptedAvatarLD, privacyProfile.encryptedAvatarHD, privacyProfile.allowStranger)
        Repository.getRecipientRepo()?.setPrivacyProfile(recipient, privacyProfile)

        return Triple(requestBody.handleBackground, memo, toSync)
    }


    override fun onForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            ALog.i(TAG, "receive foreground event")
            checkAndSync()

            val o = p()
            if (Build.VERSION.SDK_INT >= 28) {
                o.b(AppContextHolder.APP_CONTEXT)
            } else {
                o.a(AppContextHolder.APP_CONTEXT)
            }

        }
    }

    @Subscribe
    fun onEvent(event: ServiceConnectEvent) {
        if (event.state == ServiceConnectEvent.STATE.CONNECTED) {
            ALog.i(TAG, "receive service connected event")
            checkAndSync()
        }
    }
}