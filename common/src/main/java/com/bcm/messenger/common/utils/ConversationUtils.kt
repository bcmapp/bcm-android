package com.bcm.messenger.common.utils

import android.annotation.SuppressLint
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.LRUCache
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * 
 * Created by wjh on 2018/8/16
 */
object ConversationUtils {

    const val TAG = "ConversationUtils"

    /**
     * 
     */
    private val mCache: LRUCache<Any, Any> = LRUCache(200)

    private fun addCache(key: Any, value: Any) {
        ALog.d(TAG, "addCache key: $key value: $value")
        mCache[key] = value
    }

    private fun removeCache(key: Any) {
        ALog.d(TAG, "removeCache key: $key")
        mCache.remove(key)
    }

    private fun getCache(key: Any): Any? {
        return mCache[key]
    }

    fun clearCache() {
        ALog.d(TAG, "clearCache")
        mCache.clear()
    }

    /**
     * 
     */
    fun checkConversationStatus(threadId: Long, callback: (isPin: Boolean, isAtMe: Boolean, hasNewJoinRequest: Boolean) -> Unit) {
        Observable.create(ObservableOnSubscribe<ConversationStatus> {
            try {
                val status = getConversationStatus(threadId)
                it.onNext(status)
            }
            catch (ex: Exception) {
                it.onError(ex)
            }
            finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it.isPinned, it.isAtMe, it.hasJoinRequest)
                }, {
                    ALog.e(TAG, "isAtMe error", it)
                    callback.invoke(false, false, false)
                })
    }


    /**
     * @me
     */
    fun checkHasAtMe(groupMessage: AmeGroupMessageDetail?): Boolean {
        try {
            return groupMessage?.extContent?.isAtAll == true || groupMessage?.extContent?.atList?.contains(AMESelfData.uid) == true
        } catch (ex: Exception) {
            ALog.e(TAG, "checkHasAtMe error", ex)
        }
        return false
    }


    /**
     * id（）
     * @param recipient
     * @param callback
     */
    @SuppressLint("CheckResult")
    fun getThreadId(recipient: Recipient, callback: (threadId: Long) -> Unit) {
        val value = getCache(recipient.address)
        if (value != null) {
            callback.invoke(value as Long)
        } else {
            //ALog.i(TAG, ClassHelper.getCallStack())
            Observable.create(ObservableOnSubscribe<Long> {

                try {
                    it.onNext(Repository.getThreadRepo().getThreadIdFor(recipient))
                }
                catch (ex: Exception) {
                    it.onError(ex)
                }
                finally {
                    it.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({threadId ->
                        if(threadId > 0L) {
                            addCache(recipient.address, threadId)
                        }
                        callback.invoke(threadId)
                    }, {
                        ALog.e(TAG, "getThreadId error", it)
                        callback.invoke(0)
                    })
        }
    }

    @SuppressLint("CheckResult")
    fun getExistThreadId(recipient: Recipient, callback: (threadId: Long) -> Unit) {
        val value = getCache(recipient.address)
        if (value != null) {
            callback.invoke(value as Long)
        } else {
            Observable.create(ObservableOnSubscribe<Long> {
                try {
                    it.onNext(Repository.getThreadRepo().getThreadIdIfExist(recipient))
                }
                catch (ex: Exception) {
                    it.onError(ex)
                }
                finally {
                    it.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.d(TAG, "getExistThreadId: $it")
                        if (it > 0){
                            addCache(recipient.address, it)
                        }
                        callback.invoke(it)
                    }, {
                        ALog.e(TAG, "getThreadId error", it)
                        callback.invoke(0L)
                    })
        }
    }

    /**
     * 
     * @param threadId
     * @param callback
     */
    @SuppressLint("CheckResult")
    fun checkPin(threadId: Long, callback: (isPinned: Boolean) -> Unit) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                if (threadId <= 0L) {
                    it.onNext(false)
                }else {
                    val status = getConversationStatus(threadId)
                    it.onNext(status.isPinned)
                }
            }
            catch (ex: Exception) {
                it.onError(ex)
            }
            finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pin ->
                    callback.invoke(pin)
                }, {
                    ALog.e(TAG, "checkPin error", it)
                    callback.invoke(false)
                })
    }

    /**
     * 
     */
    fun setPin(recipient: Recipient, isPinned: Boolean, callback: ((success: Boolean) -> Unit)? = null) {
        getThreadId(recipient) { threadId ->
            setPin(threadId, isPinned, callback)
        }
    }

    /**
     * 
     */
    @SuppressLint("CheckResult")
    fun setPin(threadId: Long, isPinned: Boolean, callback: ((success: Boolean) -> Unit)? = null) {

        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                if (threadId <= 0L) {
                    it.onNext(false)
                }else {
                    val status = getConversationStatus(threadId)
                    status.isPinned = isPinned
                    it.onNext(true)
                }
            }
            catch (ex: Exception) {
                it.onError(ex)
            }
            finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ success ->
                    callback?.invoke(success)
                }, {
                    ALog.e(TAG, "setPin error", it)
                    callback?.invoke(false)
                })
    }

    /**
     * 
     */
    @SuppressLint("CheckResult")
    fun deleteConversation(recipient: Recipient?, threadId: Long, callback: ((success: Boolean) -> Unit)? = null) {
        deleteConversationCache(recipient, threadId)
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                val actualThreadId = if (threadId <= 0L) {
                    Repository.getThreadRepo().getThreadIdIfExist(recipient?.address?.serialize().orEmpty())
                }else {
                    threadId
                }
                if (recipient?.isGroupRecipient == true) {
                    Repository.getThreadRepo().cleanConversationContentForGroup(actualThreadId, recipient.groupId)
                } else {
                    Repository.getThreadRepo().deleteConversationContent(actualThreadId)
                }
                it.onNext(true)
            }
            catch (ex: Exception) {
                it.onError(ex)
            }
            finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    callback?.invoke(true)
                }, {
                    ALog.e(TAG, "deleteConversation error", it)
                    callback?.invoke(false)
                })
    }

    /**
     * 
     */
    fun checkHasConversationFromCache(recipient: Recipient): Boolean {
        return getCache(recipient.address) != null
    }

    /**
     * threadId
     */
    fun addConversationCache(recipient: Recipient?, threadId: Long) {
        val address = recipient?.address
        if (address != null) {
            addCache(address, threadId)
        }
    }

    /**
     * 
     */
    fun deleteConversationCache(recipient: Recipient?, threadId: Long) {
        val address = recipient?.address
        if(address != null) {
            removeCache(address)
        }
        removeCache(threadId.toString())
    }

    @SuppressLint("CheckResult")
    fun deleteGroupConversation(groupId: Long, threadId: Long, callback: ((success: Boolean, groupId: Long) -> Unit)? = null) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                val actualThreadId = if (threadId <= 0L) {
                    Repository.getThreadRepo().getThreadIdIfExist(GroupUtil.addressFromGid(groupId).serialize())
                }else {
                    threadId
                }
                Repository.getThreadRepo().deleteConversationForGroup(groupId, actualThreadId)
                it.onNext(true)
            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ success ->
                    callback?.invoke(success, groupId)
                }, {
                    ALog.e(TAG, it.toString())
                    callback?.invoke(false, groupId)
                })
    }

    /**
     * 
     */
    @Synchronized
    private fun getConversationStatus(threadId: Long): ConversationStatus {
        var status = getCache(threadId.toString()) as? ConversationStatus
        return if (status != null) {
            status
        } else {
            status = ConversationStatus(threadId)
            addCache(threadId.toString(), status)
            status
        }
    }

    /**
     * 
     */
    internal class ConversationStatus(val threadId: Long) {

        var isPinned: Boolean = Repository.getThreadRepo().getPinTime(threadId) > 0L
            //
            @Synchronized set(value) {
                if (field != value) {
                    if (value) {
                        Repository.getThreadRepo().setPinTime(threadId)
                    } else {
                        Repository.getThreadRepo().removePinTime(threadId)
                    }
                    field = value
                }
            }


        var isAtMe: Boolean = false//@
            @Synchronized set
            @Synchronized get

        var hasJoinRequest: Boolean = false//
            @Synchronized set
            @Synchronized get

    }

}