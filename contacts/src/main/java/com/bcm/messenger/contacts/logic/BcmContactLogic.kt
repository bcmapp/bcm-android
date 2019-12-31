package com.bcm.messenger.contacts.logic

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.contacts.net.BcmContactCore
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
class BcmContactLogic(private val mAccountContext: AccountContext): AppForeground.IForegroundEvent {

    companion object {
        private const val TAG = "BcmContactLogic"
        private const val DELAY_CONTACT_UPDATE = 500L
        private const val VERSION_REQUEST = 1
    }

    private val mBackgroundRequestQueue: LinkedBlockingQueue<Recipient> = LinkedBlockingQueue(500)
    @Volatile
    private var mBackgroundRequestJob: Job? = null

    private val mContactSyncFlag: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))
    private val mInitFlag: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))
    private val mQuitSyncFlag: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))

    private val coreApi = BcmContactCore()

    private val contactFilter = BcmContactFilter(coreApi)

    private val contactFinder = BcmContactFinder()

    private val cache = BcmContactCache()

    private var mObserver: Observer<List<RecipientSettings>> = Observer {
        onContactListChanged(it)
    }

    private var mLocalLiveData: LiveData<List<RecipientSettings>>? = null

    private val mCurrentContactListRef = AtomicReference<List<RecipientSettings>>()
    @Volatile
    private var mContactListDisposable: Disposable? = null
    @Volatile
    private var mContactSyncDisposable: Disposable? = null

    init {
        AppForeground.listener.addListener(this)
        EventBus.getDefault().register(this)

    }

    private fun init() {
        ALog.i(TAG, "init")

        fun doAfterFirstSync() {
            BcmFinderManager.get().registerFinder(contactFinder)
            if (mLocalLiveData == null) {
                mLocalLiveData = Repository.getRecipientRepo()?.getRecipientsLiveData()
                mLocalLiveData?.observeForever(mObserver)
            }
            mInitFlag.get().countDown()
        }

        if (mInitFlag.get().count <= 0) {
            mInitFlag.set(CountDownLatch(1))
        }
        cache.initCache()
        Observable.create<Boolean> {

            ALog.i(TAG, "init begin")
            handleRemoteSync { result ->
                it.onNext(result)
                it.onComplete()
            }

        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "init finish: $it")
                    doAfterFirstSync()
                }, {
                    ALog.e(TAG, "init error", it)
                    doAfterFirstSync()
                })

    }

    /**
     * 清理回收
     */
    private fun unInit() {
        ALog.i(TAG, "unInit")

        fun doAfterFirstLogout() {
            mBackgroundRequestJob?.cancel()
            mBackgroundRequestJob = null
            mBackgroundRequestQueue.clear()
            mContactListDisposable?.dispose()
            mContactListDisposable = null
            mCurrentContactListRef.set(listOf())
            contactFinder.cancel()
            BcmFinderManager.get().unRegisterFinder(contactFinder)
            mLocalLiveData?.removeObserver(mObserver)
            mLocalLiveData = null
            mQuitSyncFlag.get().countDown()
        }

        if (mQuitSyncFlag.get().count <= 0) {
            mQuitSyncFlag.set(CountDownLatch(1))
        }
        Observable.create<Boolean> {

            handleRemoteSync { result ->
                cache.clearCache()
                it.onNext(result)
                it.onComplete()
            }

        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "unInit finish: $it")
                    doAfterFirstLogout()
                }, {
                    ALog.e(TAG, "unInit error", it)
                    doAfterFirstLogout()
                })

        mQuitSyncFlag.get().await()
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
            waitForReady()

            ALog.i(TAG, "onContactListChanged run")

            val bcmList = mutableListOf<Recipient>()
            try {
                bcmList.add(Recipient.fromSelf(AppContextHolder.APP_CONTEXT, false))
                bcmList.addAll(mCurrentContactListRef.get().map {settings ->
                    Recipient.fromSnapshot(AppContextHolder.APP_CONTEXT, Address.fromSerialized(settings.uid), settings)
                })

                contactFinder.updateContact(bcmList, Recipient.getRecipientComparator())
                ALog.i(TAG, "onContactListChanged end, bcmList: ${bcmList.size}")

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
                }, {
                    ALog.e(TAG, "onContactListChanged error", it)
                    mContactListDisposable = null
                })


    }

    fun getContactListWithWait(): List<Recipient> {
        return contactFinder.getContactList()
    }

    fun updateThreadRecipientSource(threadRecipientList: List<Recipient>) {
        contactFinder.updateSourceWithThread(threadRecipientList, Recipient.getRecipientComparator())
    }

    fun doForLogin() {
        ALog.i(TAG, "doForLogin")
        init()
    }

    fun doForLogout() {
        ALog.i(TAG, "doForLogout")
        unInit()
    }

    private fun checkNeedSync() {

        if (cache.getLastSyncResult() == BcmContactCache.CONTACT_SUCCESS) {
            ALog.i(TAG, "last sync action success, no need sync")
            return
        }

        if (mContactSyncDisposable?.isDisposed == false) {
            ALog.w(TAG, "checkNeedSync ignore, has sync running")
            return
        }

        mContactSyncDisposable = Observable.create<Boolean> {

            mInitFlag.get().await()
            ALog.i(TAG, "checkNeedSync begin")
            handleRemoteSync { result ->
                it.onNext(result)
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "checkNeedSync finish: $it")
                    mContactSyncDisposable = null

                }, {
                    ALog.e(TAG, "checkNeedSync error", it)
                    mContactSyncDisposable = null
                })
    }

    private fun handleRemoteSync(callback: ((result: Boolean) -> Unit)? = null) {

        if (!cache.waitCacheReady()) {
            ALog.w(TAG, "handleRemoteSync fail, cache is not ready")
            callback?.invoke(false)
            return
        }

        if (cache.getLastSyncResult() != BcmContactCache.SYNC_FAIL) {
            val handlingMap = cache.getHandlingUploadMap()
            if (handlingMap.isEmpty()) {
                mContactSyncFlag.get().countDown()
                cache.setSyncResult(BcmContactCache.CONTACT_SUCCESS)
                callback?.invoke(true)
            }else {
                patch(handlingMap, false) {
                    callback?.invoke(it)
                }
            }

        }else {
            sync {
                callback?.invoke(it)
            }
        }
    }

    private fun sync(callback: (result: Boolean) -> Unit) {
        ALog.i(TAG, "sync")
        if (mContactSyncFlag.get().count <= 0) {
            mContactSyncFlag.set(CountDownLatch(1))
        }
        coreApi.syncFriendList(cache.getUploadContactMap(false))
                .observeOn(Schedulers.io())
                .subscribe({
                    ALog.i(TAG, "sync succeed, different: ${it.different.size}")
                    if (it.different.isNotEmpty()) {
                        cache.updateSyncContactMap(it.different, it.contactVersion == BcmContactCore.CONTACT_SYNC_VERSION)
                    }
                    val handlingMap = cache.getHandlingUploadMap()
                    if (handlingMap.isNotEmpty()) {
                        patch(handlingMap, false) { success ->
                            callback.invoke(success)
                        }
                    }else {
                        cache.setSyncResult(BcmContactCache.CONTACT_SUCCESS)
                        mContactSyncFlag.get().countDown()
                        callback.invoke(true)
                    }
                }, {

                    if (it is NoContentException) { //如果抛AmeNoContent错误，表示当前可能是新号，云端没有通讯录，这时候需要全量提交，否则会有问题（后续更新可以分片）
                        ALog.w(TAG, "sync fail, no remote contact data, just patch")
                        patch(cache.getUploadContactMap(true), true) { result ->
                            callback.invoke(result)
                        }

                    } else {
                        ALog.e(TAG, "sync fail", it)
                        cache.setSyncResult(BcmContactCache.SYNC_FAIL)
                        mContactSyncFlag.get().countDown()
                        callback.invoke(false)

                    }
                })
    }

    private fun patch(uploadMap: Map<String, List<BcmContactCore.ContactItem>>, checkFull: Boolean, callback: (result: Boolean) -> Unit) {
        ALog.i(TAG, "patch")
        if (mContactSyncFlag.get().count <= 0) {
            mContactSyncFlag.set(CountDownLatch(1))
        }
        coreApi.uploadFriendList(uploadMap, checkFull)
                .observeOn(Schedulers.io())
                .subscribe({
                    ALog.i(TAG, "patch succeed")
                    if (it.uploadMap.isNotEmpty()) {
                        cache.updateSyncContactMap(it.uploadMap, true)
                        cache.doneHandling(it.uploadMap)
                    }
                    cache.setSyncResult(BcmContactCache.CONTACT_SUCCESS)
                    mContactSyncFlag.get().countDown()
                    callback(true)

                }, {
                    ALog.e(TAG, "patch failed", it)
                    cache.setSyncResult(if (checkFull) BcmContactCache.SYNC_FAIL else BcmContactCache.PATCH_FAIL)
                    mContactSyncFlag.get().countDown()
                    callback(false)
                })
    }

    private fun waitForReady() {
        try {
            if (cache.waitCacheReady()) {
                mContactSyncFlag.get().await()
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "waitForReady error", ex)
        }
    }

    fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)? = null) {

        waitForReady()

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
                ALog.e(TAG, "addRelationHandling error", ex)
                callback?.invoke(false)
            }
        }

        val targetRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(targetUid), false)
        if (targetRecipient.identityKey.isNullOrEmpty()) {
            AmeModuleCenter.contact(mAccountContext)?.fetchProfile(targetRecipient) {

                AmeDispatcher.io.dispatch {
                    doSendAddFriendRequest(targetRecipient, callback)
                }
            }
        } else {
            doSendAddFriendRequest(targetRecipient, callback)
        }
    }

    fun replyAddFriend(targetUid: String, approved: Boolean, proposer: String, addFriendSignature: String, callback: ((result: Boolean) -> Unit)? = null) {

        waitForReady()

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
            AmeModuleCenter.contact(mAccountContext)?.fetchProfile(targetRecipient) {
                AmeDispatcher.io.dispatch {
                    ALog.i(TAG, "replyAddFriend approved: $approved after update profile, doSendAddFriendReply")
                    doSendAddFriendReply(targetRecipient, approved, proposer, addFriendSignature, callback)
                }
            }
        } else {
            doSendAddFriendReply(targetRecipient, approved, proposer, addFriendSignature, callback)
        }
    }

    fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)? = null) {

        waitForReady()

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

        waitForReady()

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
                                AmeModuleCenter.contact(mAccountContext)?.handleFriendPropertyChanged(recipient.address.serialize())
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
                AmeModuleCenter.contact(mAccountContext)?.fetchProfile(targetRecipient) {
                    AmeDispatcher.io.dispatch {
                        handleAfterRecipientUpdated(targetRecipient, decryptProposer)
                    }
                }
            } else {
                handleAfterRecipientUpdated(targetRecipient, decryptProposer)
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "Handle request failed. ${e.message}", e)
        }
    }

    fun handleFriendReply(reply: FriendProtos.FriendReply) {
        ALog.i(TAG, "Handle add friend reply")
        waitForReady()
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
                AmeModuleCenter.contact(mAccountContext)?.fetchProfile(targetRecipient) {
                    AmeDispatcher.io.dispatch {
                        try {
                            parseRequestBody(AppContextHolder.APP_CONTEXT, targetRecipient, reply.payload)
                        }catch (ex: Exception) {
                            ALog.e(TAG, "handleFriendReply parseRequestBody error", ex)
                        }
                        doForApproved(targetRecipient, reply.approved)
                    }
                }

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
        waitForReady()
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
        waitForReady()
        cache.addPropertyChangedHandling(recipient) { doAfter ->
            handleRemoteSync { success ->
                doAfter(success)
                callback?.invoke(success)
            }
        }

    }

    @Throws(Exception::class)
    private fun handleRequestAddFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleRequestAddFriend old relationship: ${recipient.relationship}")
        val relationship = when (recipient.relationship) {
            RecipientRepo.Relationship.FOLLOW -> RecipientRepo.Relationship.FOLLOW_REQUEST
            RecipientRepo.Relationship.STRANGER -> RecipientRepo.Relationship.REQUEST
            else -> {
                callback?.invoke(true)
                return
            }
        }
        cache.addRelationHandling(recipient, relationship) { bloomList, doAfter ->
            handleAfterCache(bloomList) { success ->
                doAfter.invoke(success)
                callback?.invoke(success)
            }

        }
    }

    @Throws(Exception::class)
    private fun handleBecomeFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleBecomeFriend old relationship: ${recipient.relationship}")
        val oldRelationship = recipient.relationship
        cache.addRelationHandling(recipient, RecipientRepo.Relationship.FRIEND) { bloomList, doAfter ->
            handleAfterCache(bloomList) { success ->
                doAfter.invoke(success)
                if (success) {
                    if (oldRelationship == RecipientRepo.Relationship.REQUEST) {
                        Repository.getRecipientRepo()?.createFriendMessage(recipient, true)
                    }
                    if (recipient.isBlocked) {
                        Repository.getRecipientRepo()?.setBlocked(recipient, false)
                    }
                }
                callback?.invoke(success)
            }

        }
    }

    private fun handleRefuseToFriend(recipient: Recipient, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleRefuseToFriend old relationship: ${recipient.relationship}")
        if (recipient.relationship == RecipientRepo.Relationship.REQUEST) {
            val relationship = RecipientRepo.Relationship.STRANGER
            cache.addRelationHandling(recipient, relationship) { bloomList, doAfter ->
                handleAfterCache(bloomList) { success ->
                    doAfter.invoke(success)
                    callback?.invoke(success)
                }

            }
        }else {
            callback?.invoke(true)
        }
    }

    private fun handleDeleteFriend(recipient: Recipient, isPassive: Boolean, callback: ((result: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "handleDeleteFriend isPassive: $isPassive, old relationship: ${recipient.relationship}")

        if (recipient.relationship == RecipientRepo.Relationship.FOLLOW || recipient.relationship == RecipientRepo.Relationship.FRIEND || recipient.relationship == RecipientRepo.Relationship.BREAK) {
            val relationship = if (isPassive) {
                RecipientRepo.Relationship.BREAK
            } else {
                RecipientRepo.Relationship.STRANGER
            }
            cache.addRelationHandling(recipient, relationship) { bloomList, doAfter ->
                handleAfterCache(bloomList) { success ->
                    doAfter.invoke(success)
                    if (success) {
                        if (relationship == RecipientRepo.Relationship.STRANGER && !recipient.isBlocked) {
                            Repository.getRecipientRepo()?.setBlocked(recipient, true)
                        }
                    }
                    callback?.invoke(success)

                }
            }

        }else {
            callback?.invoke(true)
        }
    }

    private fun handleAfterCache(bloomList: List<BcmContactCore.ContactItem>, callback: (result: Boolean) -> Unit) {

        contactFilter.updateContact(AppContextHolder.APP_CONTEXT, bloomList) { success ->
            if (success) {
                handleRemoteSync { success ->
                    callback(success)
                }

            }else {
                callback(false)
            }
        }
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

        AmeModuleCenter.contact(mAccountContext)?.updatePrivacyProfile(context, recipient, privacyProfile.encryptedName,
                privacyProfile.encryptedAvatarLD, privacyProfile.encryptedAvatarHD, privacyProfile.allowStranger)

        return Triple(requestBody.handleBackground, memo, toSync)
    }


    override fun onForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            ALog.i(TAG, "receive foreground event")
            checkNeedSync()
        }
    }

    @Subscribe
    fun onEvent(event: ServiceConnectEvent) {
        if (event.state == ServiceConnectEvent.STATE.CONNECTED) {
            ALog.i(TAG, "receive service connected event")
            checkNeedSync()
        }
    }
}