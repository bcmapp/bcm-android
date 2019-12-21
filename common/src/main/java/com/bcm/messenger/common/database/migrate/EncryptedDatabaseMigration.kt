package com.bcm.messenger.common.database.migrate

import com.bcm.messenger.common.attachments.DatabaseAttachment
import com.bcm.messenger.common.crypto.MasterCipher
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.database.RecipientDatabase
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.model.MediaMmsMessageRecord
import com.bcm.messenger.common.database.model.PrivateChatDbModel
import com.bcm.messenger.common.database.model.PushDbModel
import com.bcm.messenger.common.database.records.*
import com.bcm.messenger.common.database.repositories.DraftRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.database.GroupDatabase
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Created by Kin on 2019/10/28
 */
object EncryptedDatabaseMigration : IDatabaseMigration {
    private val TAG = "EncryptedDatabaseMigration"

    private val masterSecret = BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
    private val threadIdMap = hashMapOf<Long, Long>()
    private var isUpgrading = false

    override fun doMigrate(callback: (finishCount: Int) -> Unit) {
        if (masterSecret != null && !isUpgrading) {
            isUpgrading = true
            Observable.create<Int> {
                ALog.i(TAG, "Start migrate")
                ALog.i(TAG, "Clear new db tables")
//                UserDatabase.getDatabase().clearAllTables()
                it.onNext(0)

                ALog.i(TAG, "Start migrate threads")
                doMigrateThreads()
                it.onNext(1)

                ALog.i(TAG, "Start migrate drafts")
                doMigrateDrafts()
                it.onNext(2)

                ALog.i(TAG, "Start migrate push")
                doMigratePush()
                it.onNext(3)

                ALog.i(TAG, "Start migrate private messages")
                doMigrateMessages()
                it.onNext(4)

                ALog.i(TAG, "Start migrate identity key")
                doMigrateIdentityKey()
                it.onNext(5)

                ALog.i(TAG, "Start migrate recipient")
//                doMigrateRecipient()
                it.onNext(6)

                ALog.i(TAG, "Start migrate adhoc channel info")
                doMigrateAdHocChannelInfo()
                it.onNext(7)

                ALog.i(TAG, "Start migrate adhoc messages")
                doMigrateAdHocMessage()
                it.onNext(8)

                ALog.i(TAG, "Start migrate adhoc session info")
                doMigrateSessionInfo()
                it.onNext(9)

                ALog.i(TAG, "Start migrate bcm friend")
                doMigrateBcmFriend()
                it.onNext(10)

                ALog.i(TAG, "Start migrate friend request")
                doMigrateBcmFriendRequest()
                it.onNext(11)

                ALog.i(TAG, "Start migrate hide messages")
                doMigrateChatHideMessage()
                it.onNext(12)

                ALog.i(TAG, "Start migrate avatar param")
                doMigrateGroupAvatarParam()
                it.onNext(13)

                ALog.i(TAG, "Start migrate group info")
                doMigrateGroupInfo()
                it.onNext(14)

                ALog.i(TAG, "Start migrate group join request")
                doMigrateGroupJoinRequest()
                it.onNext(15)

                ALog.i(TAG, "Start migrate live info")
                doMigrateLiveInfo()
                it.onNext(16)

                ALog.i(TAG, "Start migrate group member")
                doMigrateGroupMember()
                it.onNext(17)

                ALog.i(TAG, "Start migrate group message")
                doMigrateGroupMessage()
                it.onNext(18)

                ALog.i(TAG, "Start migrate note record")
                doMigrateNoteRecord()
                it.onNext(19)

                // ，
                ALog.i(TAG, "Migrate success, clear old db tables")
//                DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT).deleteAllDatabase()
//                GroupDatabase.getInstance().clearAllTables()
                it.onNext(20)

//                ALog.i(TAG, "Close all database to encrypt")
//                doEncryptDatabase()
                it.onNext(21)
                it.onNext(22)

                ALog.i(TAG, "Migrate completed")
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        callback(it)
                    }, {
                        // ，，
                        ALog.e(TAG, "Migrate failed", it)
//                        UserDatabase.getDatabase().clearAllTables()
                        val returnCode = doMigrateFailed()
                        isUpgrading = false
                        callback(returnCode)
                    })
        } else {
            ALog.e(TAG, "MasterSecret is null!!")
            val returnCode = doMigrateFailed()
            callback(returnCode)
        }
    }

    private fun doMigrateThreads() {
        val threadDatabase = DatabaseFactory.getThreadDatabase(AppContextHolder.APP_CONTEXT)
        val threadRepo = Repository.getThreadRepo()

        val cursor = threadDatabase.conversationList
        val reader = threadDatabase.readerFor(cursor, MasterCipher(masterSecret))
        while (reader.nextForMigrate != null) {
            val oldRecord = reader.currentForMigrate

            val newId = threadRepo.getThreadIdIfExist(oldRecord.uid)
            if (newId > 0) {
                threadIdMap[oldRecord.threadId] = newId
            } else {
                val newRecord = ThreadRecord()
                newRecord.timestamp = oldRecord.date
                newRecord.messageCount = oldRecord.count
                newRecord.unreadCount = oldRecord.unreadCount
                newRecord.uid = oldRecord.uid
                newRecord.snippetContent = oldRecord.body.body
                newRecord.snippetType = oldRecord.type
                newRecord.snippetUri = oldRecord.snippetUri
                newRecord.read = if (oldRecord.unreadCount == 0) 1 else 0
                newRecord.hasSent = if (threadDatabase.getLastSeenAndHasSent(oldRecord.threadId).second()) 1 else 0
                newRecord.distributionType = oldRecord.distributionType
                newRecord.expiresTime = oldRecord.expiresIn
                newRecord.lastSeenTime = oldRecord.lastSeen
                newRecord.pinTime = oldRecord.pin
                newRecord.liveState = oldRecord.live_state
                newRecord.decryptFailData = threadDatabase.getDecryptFailData(oldRecord.threadId).orEmpty()
                newRecord.profileRequest = if (threadDatabase.hasProfileRequest(oldRecord.threadId)) 1 else 0

                val id = threadRepo.insertThread(newRecord)
                threadIdMap[oldRecord.threadId] = id
            }
        }
    }

    private fun doMigrateDrafts() {
        val draftDatabase = DatabaseFactory.getDraftDatabase(AppContextHolder.APP_CONTEXT)
        val draftRepo = Repository.getDraftRepo()
        val masterCipher = MasterCipher(masterSecret)

        threadIdMap.keys.forEach {
            val drafts = draftDatabase.getDrafts(masterCipher, it)
            val newId = threadIdMap[it] ?: throw RuntimeException("Cannot found new thread ID")
            val newDrafts = LinkedList<DraftRepo.Draft>()
            drafts.forEach { oldDraft ->
                                val newDraft = DraftRepo.Draft(oldDraft.type, oldDraft.value)
                newDrafts.add(newDraft)
            }
            draftRepo.insertDrafts(newId, newDrafts)
        }
    }

    private fun doMigratePush() {
        val pushDatabase = DatabaseFactory.getPushDatabase(AppContextHolder.APP_CONTEXT)
        val pushRepo = Repository.getPushRepo()

        val cursor = pushDatabase.pending
        val reader = pushDatabase.readerFor(cursor)
        var pair = reader.nextEnvelop
        do {
            if (pair != null) {
                    val model = PushDbModel()
                    val envelope = pair.second
                    model.id = pair.first
                    model.type = envelope.type.number
                    model.sourceUid = envelope.source
                    model.deviceId = envelope.sourceDevice
                    model.legacyMessage = if (envelope.hasLegacyMessage()) Base64.encodeBytes(envelope.legacyMessage.toByteArray()) else ""
                    model.content = if (envelope.hasContent()) Base64.encodeBytes(envelope.content.toByteArray()) else ""
                    model.timestamp = envelope.timestamp
                    model.sourceRegistrationId = envelope.sourceRegistration
                    pushRepo.insert(model)
            }
            pair = reader.nextEnvelop
        } while (pair != null)
    }

    private fun doMigrateMessages() {
        val mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(AppContextHolder.APP_CONTEXT)
        val database = UserDatabase.getDatabase()

        threadIdMap.keys.forEach {
            val newId = threadIdMap[it] ?: throw RuntimeException("Cannot found new thread ID")
            val cursor = mmsSmsDatabase.getConversationAsc(it)
            val reader = mmsSmsDatabase.readerFor(cursor, masterSecret)
            while (reader.nextMigrate != null) {
                val oldRecord = reader.currentMigrate
                val newRecord = MessageRecord()

                newRecord.threadId = newId
                newRecord.uid = oldRecord.uid
                newRecord.addressDevice = oldRecord.recipientDeviceId
                newRecord.dateReceive = oldRecord.dateReceived
                newRecord.dateSent = oldRecord.dateSent
                newRecord.read = oldRecord.read.toInt()
                newRecord.type = oldRecord.type
                newRecord.body = oldRecord.body.body
                newRecord.expiresTime = oldRecord.expiresIn
                newRecord.expiresStartTime = oldRecord.expireStarted
                newRecord.readRecipientCount = oldRecord.readReceiptCount
                newRecord.deliveryReceiptCount = oldRecord.deliveryReceiptCount
                newRecord.callType = oldRecord.communicationType
                newRecord.callDuration = oldRecord.duration
                newRecord.payloadType = oldRecord.payloadType
                when {
                    oldRecord.isLocation -> {
                        newRecord.messageType = PrivateChatDbModel.MessageType.LOCATION.type
                        newRecord.attachmentCount = 0
                    }
                    oldRecord.isCallLog -> {
                        newRecord.messageType = PrivateChatDbModel.MessageType.CALL.type
                        newRecord.attachmentCount = 0
                    }
                    oldRecord.isExpirationTimerUpdate -> {
                        newRecord.messageType = PrivateChatDbModel.MessageType.TEXT.type
                        newRecord.attachmentCount = 0
                    }
                    oldRecord.isMms -> {
                        val mmsRecord = oldRecord as MediaMmsMessageRecord
                        if (mmsRecord.slideDeck.thumbnailSlide != null) {
                            newRecord.messageType = PrivateChatDbModel.MessageType.MEDIA.type
                        } else if (mmsRecord.slideDeck.documentSlide != null) {
                            newRecord.messageType = PrivateChatDbModel.MessageType.DOCUMENT.type
                        }

                        val attachmentList = mutableListOf<AttachmentRecord>()
                        newRecord.attachmentCount = mmsRecord.partCount
                        oldRecord.slideDeck.slides.forEach { slide ->
                            val oldAttachment = slide.asAttachment() as DatabaseAttachment
                            val newAttachment = AttachmentRecord()
                            newAttachment.contentType = oldAttachment.contentType
                            newAttachment.name = oldAttachment.relay
                            newAttachment.fileName = oldAttachment.fileName
                            newAttachment.contentKey = oldAttachment.key.orEmpty()
                            newAttachment.contentLocation = oldAttachment.location.orEmpty()
                            newAttachment.transferState = oldAttachment.transferState
                            newAttachment.dataUri = oldAttachment.realDataUri
                            newAttachment.dataSize = oldAttachment.size
                            newAttachment.thumbnailUri = oldAttachment.realThumbnailUri
                            newAttachment.thumbnailAspectRatio = oldAttachment.aspectRatio
                            newAttachment.uniqueId = oldAttachment.attachmentId.uniqueId
                            newAttachment.digest = oldAttachment.digest
                            newAttachment.fastPreflightId = oldAttachment.fastPreflightId
                            newAttachment.duration = oldAttachment.duration
                            newAttachment.url = oldAttachment.url
                            newAttachment.attachmentType = Repository.getAttachmentRepo().getMediaType(oldAttachment.contentType).type
                            attachmentList.add(newAttachment)
                        }
                        newRecord.attachments = attachmentList
                    }
                    else -> newRecord.messageType = PrivateChatDbModel.MessageType.TEXT.type
                }

                database.runInTransaction {
                    val id = database.getPrivateChatDao().insertChatMessage(newRecord)
                    newRecord.attachments.forEach { attachment ->
                        attachment.mid = id
                    }
                    database.getAttachmentDao().insertAttachments(newRecord.attachments)
                }
            }
        }
    }

    private fun doMigrateIdentityKey() {
        val identityDatabase = DatabaseFactory.getIdentityDatabase(AppContextHolder.APP_CONTEXT)
        val identityRepo = Repository.getIdentityRepo()

        val cursor = identityDatabase.identities
        val reader = identityDatabase.readerFor(cursor)
        val newIdentities = mutableListOf<IdentityRecord>()
        var identityKey = reader?.next
        do {
            if (identityKey != null) {
                    val newIdentity = IdentityRecord()
                    newIdentity.uid = identityKey.address.serialize()
                    newIdentity.key = Base64.encodeBytes(identityKey.identityKey.serialize())
                    newIdentity.firstUse = if (identityKey.isFirstUse) 1 else 0
                    newIdentity.timestamp = identityKey.timestamp
                    newIdentity.verified = identityKey.verifiedStatus.toInt()
                    newIdentity.nonBlockingApproval = if (identityKey.isApprovedNonBlocking) 1 else 0
                    newIdentities.add(newIdentity)

                identityKey = reader?.next
            }
        } while (identityKey != null)

        identityRepo.insertIdentities(newIdentities)
    }

    private fun doMigrateRecipient() {
        val recipientDatabase = DatabaseFactory.getRecipientDatabase(AppContextHolder.APP_CONTEXT) ?: throw RuntimeException("Recipient database is NULL !!")
        val recipientRepo = Repository.getRecipientRepo()

        val cursor = recipientDatabase.allDatabaseRecipients
        val settingsList = mutableListOf<RecipientSettings>()
        while (cursor?.moveToNext() == true) {
            val oldSettings = RecipientDatabase.getRecipientSettings(cursor) ?: continue
            val newSettings = RecipientSettings()

            newSettings.uid = oldSettings.uid
            newSettings.block = if (oldSettings.isBlocked) 1 else 0
            newSettings.muteUntil = oldSettings.muteUntil
            newSettings.expiresTime = oldSettings.expireMessages.toLong()
            newSettings.localName = oldSettings.localName
            newSettings.localAvatar = oldSettings.localAvatar
            newSettings.profileKey = if (oldSettings.profileKey == null) null else Base64.encodeBytes(oldSettings.profileKey)
            newSettings.profileName = oldSettings.profileName
            newSettings.profileAvatar = oldSettings.profileAvatar
            newSettings.profileSharingApproval = if (oldSettings.isProfileSharing) 0 else 1
            newSettings.relationship = oldSettings.relationship.type
            newSettings.supportFeature = oldSettings.featureSupport?.toString().orEmpty()
            newSettings.privacyProfile = PrivacyProfile().apply {
                encryptedName = oldSettings.privacyProfile.encryptedName
                name = oldSettings.privacyProfile.name
                encryptedAvatarLD = oldSettings.privacyProfile.encryptedAvatarLD
                avatarLD = oldSettings.privacyProfile.avatarLD
                avatarLDUri = oldSettings.privacyProfile.avatarLDUri
                isAvatarLdOld = oldSettings.privacyProfile.isAvatarLdOld
                encryptedAvatarHD = oldSettings.privacyProfile.encryptedAvatarHD
                avatarHD = oldSettings.privacyProfile.avatarHD
                avatarHDUri = oldSettings.privacyProfile.avatarHDUri
                isAvatarHdOld = oldSettings.privacyProfile.isAvatarHdOld
                namePubKey = oldSettings.privacyProfile.namePubKey
                nameKey = oldSettings.privacyProfile.nameKey
                avatarPubKey = oldSettings.privacyProfile.avatarPubKey
                avatarKey = oldSettings.privacyProfile.avatarKey
                allowStranger = oldSettings.privacyProfile.isAllowStranger
            }

            settingsList.add(newSettings)
        }

        recipientRepo?.insertRecipients(settingsList)
    }

    private fun doMigrateAdHocChannelInfo() {
        val oldDao = GroupDatabase.getInstance().adHocChannelDao()
        val newDao = UserDatabase.getDatabase().adHocChannelDao()

        newDao.insertChannels(oldDao.loadAllChannel())
    }

    private fun doMigrateAdHocMessage() {
        val oldDao = GroupDatabase.getInstance().adHocMessageDao()
        val newDao = UserDatabase.getDatabase().adHocMessageDao()

        var page = 0
        while (true) {
            val messageList = oldDao.loadByPage(page)
            newDao.insertMessages(messageList)

            if (messageList.isEmpty() || messageList.size < 100) break
            page++
        }
    }

    private fun doMigrateSessionInfo() {
        val oldDao = GroupDatabase.getInstance().adHocSessionDao()
        val newDao = UserDatabase.getDatabase().adHocSessionDao()

        newDao.saveSessions(oldDao.loadAllSession())
    }

    private fun doMigrateBcmFriend() {
        val oldDao = GroupDatabase.getInstance().bcmFriendDao()
        val newDao = UserDatabase.getDatabase().bcmFriendDao()

        newDao.saveFriends(oldDao.queryAll())
    }

    private fun doMigrateBcmFriendRequest() {
        val oldDao = GroupDatabase.getInstance().friendRequestDao()
        val newDao = UserDatabase.getDatabase().friendRequestDao()

        newDao.insert(oldDao.queryAll())
    }

    private fun doMigrateChatHideMessage() {
        val oldDao = GroupDatabase.getInstance().chatControlMessageDao()
        val newDao = UserDatabase.getDatabase().chatControlMessageDao()

        var page = 0
        while (true) {
            val messageList = oldDao.queryByPage(page * 100)
            newDao.saveHideMessages(messageList)

            if (messageList.isEmpty() || messageList.size < 100) break
            page++
        }
    }

    private fun doMigrateGroupAvatarParam() {
        val oldDao = GroupDatabase.getInstance().groupAvatarParamsDao()
        val newDao = UserDatabase.getDatabase().groupAvatarParamsDao()

        var page = 0
        while (true) {
            val paramsList = oldDao.queryByPage(page * 100)
            newDao.saveAvatarParams(paramsList)

            if (paramsList.isEmpty() || paramsList.size < 100) break
            page++
        }
    }

    private fun doMigrateGroupInfo() {
        val oldDao = GroupDatabase.getInstance().groupInfo()
        val newDao = UserDatabase.getDatabase().groupInfoDao()

        newDao.insertOrUpdateAll(oldDao.loadAll())
    }

    private fun doMigrateGroupJoinRequest() {
        val oldDao = GroupDatabase.getInstance().groupJoinInfoDao()
        val newDao = UserDatabase.getDatabase().groupJoinInfoDao()

        var page = 0
        while (true) {
            val joinInfoList = oldDao.queryByPage(page * 100)
            newDao.saveJoinInfos(joinInfoList)

            if (joinInfoList.isEmpty() || joinInfoList.size < 100) break
            page++
        }
    }

    private fun doMigrateLiveInfo() {
        val oldDao = GroupDatabase.getInstance().groupLiveInfo()
        val newDao = UserDatabase.getDatabase().groupLiveInfoDao()

        var page = 0
        while (true) {
            val liveInfoList = oldDao.queryByPage(page * 100)
            newDao.insertLiveInfoList(liveInfoList)

            if (liveInfoList.isEmpty() || liveInfoList.size < 100) break
            page++
        }
    }

    private fun doMigrateGroupMember() {
        val oldDao = GroupDatabase.getInstance().groupMemberDao()
        val newDao = UserDatabase.getDatabase().groupMemberDao()

        var page = 0
        while (true) {
            val memberList = oldDao.queryGroupMemberByPage(page * 500)
            newDao.insertGroupMember(memberList)

            if (memberList.isEmpty() || memberList.size < 500) break
            page++
        }
    }

    private fun doMigrateGroupMessage() {
        val oldDao = GroupDatabase.getInstance().GroupMessage()
        val newDao = UserDatabase.getDatabase().groupMessageDao()

        var page = 0
        while (true) {
            val messageList = oldDao.loadMessageByPage(page * 500)
            newDao.insertMessages(messageList)

            if (messageList.isEmpty() || messageList.size < 500) break
            page++
        }
    }

    private fun doMigrateNoteRecord() {
        val oldDao = GroupDatabase.getInstance().noteRecordDao()
        val newDao = UserDatabase.getDatabase().noteRecordDao()

        newDao.saveNoteList(oldDao.queryNoteList())
    }

//    private fun doEncryptDatabase() {
////        DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT).close()
////        GroupDatabase.getInstance().close()
//
//        // Do encryption if version is release build.
//        if (isReleaseBuild()) {
//            SQLCipherUtils.encrypt(AppContextHolder.APP_CONTEXT, "user_${AMESelfData.uid}.db", masterSecret!!.encryptionKey.encoded)
//        }
//
//        UserDatabase.getDatabase()
//    }

    private fun doMigrateFailed(): Int {
        // Set failed count
        var migrateFailedCount = TextSecurePreferences.getMigrateFailedCount(AppContextHolder.APP_CONTEXT)
        if (migrateFailedCount < 3) {
            migrateFailedCount++
        }
        TextSecurePreferences.setMigrateFailedCount(AppContextHolder.APP_CONTEXT, migrateFailedCount)

        // Upload feedback for resolving bugs
        AmeModuleCenter.user().feedback("Migrate","DatabaseMigrateFailed", emptyList())

        return if (migrateFailedCount == 3) -2 else -1
//        return -2
    }

    override fun clearFlag() {
        isUpgrading = false
    }
}