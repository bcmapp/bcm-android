package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.finder.BcmGroupFinder
import com.bcm.messenger.chats.finder.BcmGroupMemberFinder
import com.bcm.messenger.chats.finder.BcmThreadFinder
import com.bcm.messenger.chats.group.core.CreateGroupRequest
import com.bcm.messenger.chats.group.core.GroupManagerCore
import com.bcm.messenger.chats.group.core.GroupMemberCore
import com.bcm.messenger.chats.group.core.group.*
import com.bcm.messenger.chats.group.logic.bean.BcmGroupReviewAccept
import com.bcm.messenger.chats.group.logic.cache.GroupCache
import com.bcm.messenger.chats.group.logic.secure.*
import com.bcm.messenger.chats.group.logic.sync.GroupMemberSyncManager
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.common.core.corebean.*
import com.bcm.messenger.common.crypto.ECCCipher
import com.bcm.messenger.common.crypto.GroupProfileDecryption
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.GroupInfoCacheReadyEvent
import com.bcm.messenger.common.event.GroupListChangedEvent
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.grouprepository.events.*
import com.bcm.messenger.common.grouprepository.manager.BcmGroupJoinManager
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.manager.GroupMemberManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMemberChanged
import com.bcm.messenger.common.grouprepository.modeltransform.GroupInfoTransform
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMemberTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo
import com.bcm.messenger.common.grouprepository.room.entity.JoinGroupReqComment
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import com.example.bleserver.Base62
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.libsignal.util.guava.Optional
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * Created by bcm.social.01 on 2018/5/23.
 */
@SuppressLint("CheckResult")
object GroupLogic : AccountContextMap<GroupLogic.GroupLogicImpl>({
    GroupLogicImpl(it)
}) {
    private const val TAG = "GroupLogic"

    class GroupLogicImpl(private val accountContext: AccountContext) {
        private val groupCache = GroupCache(accountContext) {
            updateGroupFinderSource()
            EventBus.getDefault().post(GroupInfoCacheReadyEvent())
        }

        private val groupMemberSync = GroupMemberSyncManager(accountContext)

        private val mGroupFinder = BcmGroupFinder()

        private val memberLoader = GroupMemberInfoNetLoader(accountContext)
        private val groupInfoLoader = GroupInfoNetLoader(accountContext)
        private val groupAvatarLogic = GroupAutoGenerateLogic(accountContext)

        private var mGroupFinderDispose: Disposable? = null
        private var modelRef = WeakReference<GroupViewModel>(null)

        private val noneListener = object : IGroupListener {}
        private var listenerRef = WeakReference<IGroupListener>(noneListener)

        private val groupKeyRotate = GroupKeyRotate(accountContext)

        init {
            EventBus.getDefault().register(this)
        }

        private fun setGroupListener(listener: IGroupListener?) {
            this.listenerRef = WeakReference(listener ?: noneListener)
        }


        internal fun updateGroupFinderSource() {
            mGroupFinderDispose?.dispose()
            mGroupFinderDispose = Observable.create<Boolean> {
                mGroupFinder.updateSource(groupCache.getGroupInfoList())
                it.onNext(true)
                it.onComplete()
            }.delaySubscription(600, TimeUnit.MILLISECONDS, Schedulers.io())
                    .subscribe({
                        if (it) {
                            EventBus.getDefault().post(GroupListChangedEvent(0L, false))
                        }
                    }, {
                        ALog.e(TAG, "updateGroupFinderSource error", it)
                    })
        }

        fun init() {
            GroupMessageLogic.get(accountContext).init()
            groupCache.init()
            groupKeyRotate.initRotate()

            BcmFinderManager.get(accountContext).registerFinder(mGroupFinder)
            BcmFinderManager.get(accountContext).registerFinder(BcmGroupMemberFinder())
            BcmFinderManager.get(accountContext).registerFinder(BcmThreadFinder())
        }

        fun unInit() {
            GroupMessageLogic.get(accountContext).unInit()
            groupCache.clearCache()
            groupKeyRotate.clearRotate()
        }

        fun getModel(gid: Long): GroupViewModel? {
            return if (modelRef.get()?.groupId() == gid) {
                modelRef.get()
            } else {
                null
            }
        }

        fun newModel(gid: Long): GroupViewModel {
            val model = modelRef.get()
            if (model != null && model.groupId() == gid) {
                return model
            }
            val newModel = GroupViewModel(accountContext, gid)
            setGroupListener(newModel)
            this.modelRef = WeakReference(newModel)
            return newModel
        }

        fun isCurrentModel(gid: Long): Boolean {
            return modelRef.get()?.groupId() == gid
        }

        fun getKeyRotate(): GroupKeyRotate {
            return groupKeyRotate
        }

        fun refreshGroupAvatar(gid: Long) {
            groupAvatarLogic.autoGenAvatarOrName(gid)
        }


        fun getGroupFinder(): BcmGroupFinder {
            return mGroupFinder
        }

        @SuppressLint("CheckResult")
        fun createGroup(name: String,
                        icon: String,
                        subscribeEnable: Boolean,
                        shareContent: String,
                        members: List<Recipient>,
                        result: (groupInfo: AmeGroupInfo?, succeed: Boolean, error: String?) -> Unit) {

            AmeConfigure.queryGroupSecureV3Enable()
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .subscribe {
                        if (it) {
                            createGroupV3(name, icon, subscribeEnable, shareContent, members, result)
                        } else {
                            createGroupV2(name, icon, subscribeEnable, shareContent, members, result)
                        }
                    }
        }


        @SuppressLint("CheckResult")
        private fun createGroupV3(name: String,
                                  icon: String,
                                  subscribeEnable: Boolean,
                                  shareContent: String,
                                  members: List<Recipient>,
                                  result: (groupInfo: AmeGroupInfo?, succeed: Boolean, error: String?) -> Unit) {
            val broadcast = if (subscribeEnable) {
                1
            } else {
                0
            }


            data class ParamStash(var inviteList: List<String>,
                                  var strangersList: List<String> = listOf(),
                                  var validList: List<IdentityKeyInfo> = listOf(),
                                  var keys: GroupKeysContent = GroupKeysContent(),
                                  var shareSetting: String = "",
                                  var shareSettingSign: String = "",
                                  var shareConfirmSign: String = "",
                                  val groupInfo: GroupInfo = GroupInfo()
            ) {
                var groupKeyPlainBytes: ByteArray = BCMEncryptUtils.generate64BitKey()
                var groupInfoSecretPlainBytes: ByteArray = BCMEncryptUtils.generate64BitKey()
                var groupEphemeralKeyPlainBytes: ByteArray = BCMEncryptUtils.generate64BitKey()

                init {
                    groupInfo.infoSecret = groupInfoSecretPlainBytes.base64Encode().format()
                    groupInfo.ephemeralKey = groupEphemeralKeyPlainBytes.base64Encode().format()
                    groupInfo.currentKey = groupKeyPlainBytes.base64Encode().format()
                    groupInfo.currentKeyVersion = 0
                }
            }


            val inviteList = members.map { it.address.serialize() }
            GroupManagerCore.queryRecipientsInfo(accountContext, inviteList)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map { list ->
                        val stash = ParamStash(inviteList)

                        stash.validList = list.filter {
                            val recipient = Recipient.from(accountContext, it.uid, true)
                            recipient.isFriend && it.getSupport()?.isSupportGroupSecureV3() == true
                        }


                        if (stash.validList.size != inviteList.size) {
                            val filterList = stash.validList.map { it.uid }
                            stash.strangersList = inviteList.filter { !filterList.contains(it) }
                        }

                        ALog.i(TAG, "create group invited ${stash.inviteList.size} stranger:${stash.strangersList.size}")

                        if (stash.validList.isEmpty()) {
                            throw GroupException(AppContextHolder.APP_CONTEXT.getString(R.string.chats_invalid_user_in_create_list))
                        }

                        stash
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { stash ->
                        StrongKeyExchangeParam.getStrongKeysContent(accountContext, stash.validList.map { it.uid }, stash.groupKeyPlainBytes, stash.groupInfo.groupPrivateKey)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .map {
                                    stash.keys = it
                                    stash
                                }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { stash ->
                        val ownerSecretString = BCMEncryptUtils.generateMyEncryptKeyString(accountContext, stash.groupInfoSecretPlainBytes)

                        val memberSecretStrings = BCMEncryptUtils.generateMembersEncryptKeys(stash.validList.map { it.identityKey }, stash.groupInfoSecretPlainBytes)

                        val channelKey = GroupMessageEncryptUtils.generateChannelKey(Base64.encodeBytes(stash.groupInfoSecretPlainBytes))
                        val self = Recipient.from(accountContext, accountContext.uid, true)
                        val myName = self.bcmName
                        val nickname = EncryptUtils.aes256EncryptAndBase64(myName, channelKey.toByteArray())
                        val keyConfig = AmeGroupMemberInfo.KeyConfig()
                        keyConfig.avatarKey = self.privacyProfile.avatarKey
                        keyConfig.version = self.privacyProfile.version
                        val profileKeys = EncryptUtils.aes256EncryptAndBase64(keyConfig.toString(), channelKey.toByteArray())

                        val shareCode= EncryptUtils.base64Encode(EncryptUtils.getSecretBytes(16)).format()
                        val shareSettingJson = GsonUtils.toJson(GroupShareSettingEntity(1, shareCode, 1))
                        stash.shareSetting = EncryptUtils.aes256EncryptAndBase64(shareSettingJson, stash.groupInfoSecretPlainBytes)

                        val shareSettingSignArray = BCMEncryptUtils.signWithMe(accountContext, EncryptUtils.base64Decode(stash.shareSetting.toByteArray()))
                        stash.shareSettingSign = EncryptUtils.base64Encode(shareSettingSignArray).format()

                        val format = ByteArrayOutputStream()
                        format.write(stash.shareSetting.toByteArray().base64Decode())
                        format.write("1".toByteArray())
                        val shareConfirmSignByteArray = BCMEncryptUtils.signWithMe(accountContext, format.toByteArray())
                                ?: throw Exception("sign confirm failed")
                        stash.shareConfirmSign = shareConfirmSignByteArray.base64Encode().format()


                        val memberProofList = stash.validList.map {
                            val proof = GroupProof.signMember(stash.groupInfo, it.uid)
                            val bytes = EncryptUtils.base64Encode(GsonUtils.toJson(proof).toByteArray())
                            String(bytes)
                        }

                        val ownerProof = GroupProof.encodeMemberProof(GroupProof.signMember(stash.groupInfo, accountContext.uid))
                        val encInfoSecret = stash.groupInfoSecretPlainBytes.aesEncode(stash.groupEphemeralKeyPlainBytes)!!.base64Encode().format()
                        val encEphemeralKey = stash.groupEphemeralKeyPlainBytes.aesEncode(stash.groupInfoSecretPlainBytes)!!.base64Encode().format()

                        val req = CreateGroupRequest()
                        req.name = name
                        req.icon = icon
                        req.broadcast = broadcast
                        req.intro = shareContent
                        req.members = stash.validList.map { it.uid }
                        req.memberSecrets = memberSecretStrings.toList()
                        req.ownerSecret = ownerSecretString
                        req.ownerName = nickname
                        req.profileKeys = profileKeys
                        req.shareSetting = stash.shareSetting
                        req.shareConfirmSign = stash.shareConfirmSign
                        req.shareSettingSign = stash.shareSettingSign
                        req.ownerProof = ownerProof
                        req.encryptedSecret = encInfoSecret
                        req.encryptedEphemeralKey = encEphemeralKey
                        req.memberProofs = memberProofList
                        req.groupKeys = stash.keys
                        GroupManagerCore.createGroupV3(accountContext, req)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .map {
                                    stash.groupInfo.gid = it.gid
                                    stash
                                }

                    }.observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { stash ->
                        val dbGroupInfo = stash.groupInfo
                        dbGroupInfo.member_count = stash.validList.size
                        dbGroupInfo.subscriber_count = 0

                        dbGroupInfo.permission = 0
                        dbGroupInfo.createTime = 0
                        dbGroupInfo.iconUrl = icon
                        dbGroupInfo.name = name
                        dbGroupInfo.owner = accountContext.uid
                        dbGroupInfo.broadcast = broadcast
                        dbGroupInfo.share_content = shareContent
                        dbGroupInfo.role = AmeGroupMemberInfo.OWNER
                        dbGroupInfo.member_sync_state = GroupMemberSyncState.DIRTY.toString()
                        dbGroupInfo.illegal = GroupInfo.LEGITIMATE_GROUP
                        dbGroupInfo.needOwnerConfirm = 1
                        dbGroupInfo.shareEpoch = 1
                        dbGroupInfo.shareEnabled = 1
                        dbGroupInfo.shareCode = ""
                        dbGroupInfo.version = 3

                        groupCache.saveGroupInfo(dbGroupInfo)
                        GroupInfoDataManager.saveGroupKeyParam(accountContext, dbGroupInfo.gid, dbGroupInfo.currentKeyVersion, dbGroupInfo.currentKey)

                        getThreadDB()?.getThreadIdFor(Recipient.recipientFromNewGroupId(accountContext, stash.groupInfo.gid))

                        if (stash.strangersList.isNotEmpty()) {
                            inviteStrangerNotify(stash.groupInfo.gid, stash.strangersList)
                        }
                        queryGroupInfoImpl(stash.groupInfo.gid)
                                .subscribeOn(AmeDispatcher.ioScheduler)

                    }.observeOn(AmeDispatcher.ioScheduler)
                    .map<GroupInfoResult> {
                        GroupMessageLogic.get(accountContext).syncOfflineMessage(it.info.gid, it.ackState.lastMid, it.ackState.lastAckMid)
                        it
                    }.observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        EventBus.getDefault().post(GroupListChangedEvent(it.info.gid, false))
                        result(it.info, true, "")
                    }, {
                        ALog.e(TAG, it)
                        val error = if (GroupException.code(it) == 460) {
                            AppUtil.getString(R.string.chats_create_failed_daily_limit)
                        } else {
                            GroupException.error(it, AppUtil.getString(R.string.common_error_failed))
                        }
                        result(null, false, error)
                    })
        }


        @SuppressLint("CheckResult")
        private fun createGroupV2(name: String,
                                  icon: String,
                                  subscribeEnable: Boolean,
                                  shareContent: String,
                                  members: List<Recipient>,
                                  result: (groupInfo: AmeGroupInfo?, succeed: Boolean, error: String?) -> Unit) {
            val broadcast = if (subscribeEnable) {
                1
            } else {
                0
            }

            val inviteList = members.map { it.address.serialize() }
            val unknownUserList = ArrayList<String>()
            val strangerList = ArrayList<String>()

            val groupPassword = BCMEncryptUtils.generate64BitKey()
            val groupSecret = BCMEncryptUtils.generate64BitKey()

            var shareSetting = ""
            var shareSettingSign = ""
            var shareConfirmSign = ""
            GroupManagerCore.queryRecipientsInfo(accountContext, inviteList)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map<List<IdentityKeyInfo>> { list ->
                        unknownUserList.addAll(inviteList)
                        unknownUserList.removeAll(list.map { it.uid })

                        val validUserList = list.filter {
                            val recipient = Recipient.from(accountContext, it.uid, true)
                            recipient.isFriend
                        }

                        strangerList.addAll(list.map { it.uid })
                        strangerList.removeAll(validUserList.map { it.uid })

                        ALog.i(TAG, "create group invited ${validUserList.size} unknown:${unknownUserList.size} stranger:${strangerList.size}")

                        if (validUserList.isEmpty()) {
                            throw GroupException(AppContextHolder.APP_CONTEXT.getString(R.string.chats_invalid_user_in_create_list))
                        }

                        validUserList
                    }.flatMap<ServerResult<CreateGroupResult>> { list ->
                        val ownerKeySpecString = BCMEncryptUtils.generateMyEncryptKeyString(accountContext, groupPassword)
                        val ownerSecretString = BCMEncryptUtils.generateMyEncryptKeyString(accountContext, groupSecret)

                        val memberIdentityList = list.map { it.identityKey }
                        val memberKeySpecStrings = BCMEncryptUtils.generateMembersEncryptKeys(memberIdentityList, groupPassword)
                        val memberSecretStrings = BCMEncryptUtils.generateMembersEncryptKeys(memberIdentityList, groupSecret)
                        val realInviteList = list.map { it.uid }

                        val channel_key = GroupMessageEncryptUtils.generateChannelKey(Base64.encodeBytes(groupPassword))

                        val self = Recipient.from(accountContext, accountContext.uid, true)
                        val myName = self.bcmName
                        val nickname = EncryptUtils.aes256EncryptAndBase64(myName, channel_key.toByteArray())
                        val keyConfig = AmeGroupMemberInfo.KeyConfig()
                        keyConfig.avatarKey = self.privacyProfile.avatarKey
                        keyConfig.version = self.privacyProfile.version
                        val profileKeys = EncryptUtils.aes256EncryptAndBase64(keyConfig.toString(), channel_key.toByteArray())

                        val shareCode= EncryptUtils.base64Encode(EncryptUtils.getSecretBytes(16)).format()
                        val shareSettingJson = GsonUtils.toJson(GroupShareSettingEntity(1, shareCode, 1))
                        shareSetting = EncryptUtils.aes256EncryptAndBase64(shareSettingJson, groupSecret)

                        val shareSettingSignArray = BCMEncryptUtils.signWithMe(accountContext, EncryptUtils.base64Decode(shareSetting.toByteArray()))
                        shareSettingSign = String(EncryptUtils.base64Encode(shareSettingSignArray))

                        val format = ByteArrayOutputStream()
                        format.write(EncryptUtils.base64Decode(shareSetting.toByteArray()))
                        format.write("1".toByteArray())
                        val shareConfirmSignByteArray = BCMEncryptUtils.signWithMe(accountContext, format.toByteArray())
                        shareConfirmSign = String(EncryptUtils.base64Encode(shareConfirmSignByteArray))

                        GroupManagerCore.createGroupV2(accountContext, name,
                                icon,
                                broadcast,
                                shareContent,
                                realInviteList,
                                memberKeySpecStrings,
                                memberSecretStrings,
                                ownerKeySpecString,
                                ownerSecretString,
                                nickname,
                                profileKeys, shareSetting, shareSettingSign, shareConfirmSign)
                                .subscribeOn(Schedulers.io())
                                .observeOn(Schedulers.io())

                    }.flatMap<ServerResult<CreateGroupResult>> {
                        if (it.isSuccess) {
                            val dbGroupInfo = GroupInfo()
                            dbGroupInfo.member_count = inviteList.size - unknownUserList.size - strangerList.size
                            dbGroupInfo.subscriber_count = 0

                            dbGroupInfo.permission = 0
                            dbGroupInfo.createTime = 0
                            dbGroupInfo.iconUrl = icon
                            dbGroupInfo.name = name
                            dbGroupInfo.owner = accountContext.uid
                            dbGroupInfo.broadcast = broadcast
                            dbGroupInfo.share_content = shareContent
                            dbGroupInfo.gid = it.data.gid
                            dbGroupInfo.role = AmeGroupMemberInfo.OWNER
                            dbGroupInfo.member_sync_state = GroupMemberSyncState.DIRTY.toString()
                            dbGroupInfo.illegal = GroupInfo.LEGITIMATE_GROUP
                            dbGroupInfo.currentKey = Base64.encodeBytes(groupPassword)
                            dbGroupInfo.infoSecret = Base64.encodeBytes(groupSecret)
                            dbGroupInfo.needOwnerConfirm = 1
                            dbGroupInfo.shareEpoch = 1
                            dbGroupInfo.shareEnabled = 1
                            dbGroupInfo.shareCode = ""
                            dbGroupInfo.shareCodeSetting = shareSetting
                            dbGroupInfo.shareCodeSettingSign = shareSettingSign
                            dbGroupInfo.shareSettingAndConfirmSign = shareConfirmSign

                            groupCache.saveGroupInfo(dbGroupInfo)

                            getThreadDB()?.getThreadIdFor(Recipient.recipientFromNewGroupId(accountContext, it.data.gid))

                            if (strangerList.isNotEmpty()) {
                                inviteStrangerNotify(it.data.gid, strangerList)
                            }

                            val gid = it.data.gid
                            genShareLink(gid)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .map { _ ->
                                        it
                                    }
                        } else {
                            throw GroupException(it.msg)
                        }
                    }
                    .flatMap {
                        queryGroupInfoImpl(it.data.gid)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .map<GroupInfoResult> {
                        GroupMessageLogic.get(accountContext).syncOfflineMessage(it.info.gid, it.ackState.lastMid, it.ackState.lastAckMid)
                        it
                    }.observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        EventBus.getDefault().post(GroupListChangedEvent(it.info.gid, false))
                        result(it.info, true, "")
                    }, {
                        ALog.e(TAG, "createGroup error", it)
                        val error = if (GroupException.code(it) == 460) {
                            AppUtil.getString(R.string.chats_create_failed_daily_limit)
                        } else {
                            GroupException.error(it, AppUtil.getString(R.string.common_error_failed))
                        }
                        result(null, false, error)
                    })
        }


        private fun inviteStrangerNotify(gid: Long, strangers: List<String>) {
            val message = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_GROUP_INVITE_STRANGER, "", strangers, "")
            GroupMessageLogic.get(accountContext).systemNotice(gid, message)
        }


        @SuppressLint("CheckResult")
        fun queryGroupInfo(groupId: Long, result: ((ameGroup: AmeGroupInfo?, ackState: GroupAckState?, error: String?) -> Unit)?) {
            queryGroupInfoImpl(groupId)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .map {
                        GroupMessageLogic.get(accountContext).syncOfflineMessage(it.info.gid, it.ackState.lastMid, it.ackState.lastAckMid)
                        it
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        ALog.i(TAG, "getGroupInfo group id $groupId")
                        EventBus.getDefault().post(GroupListChangedEvent(groupId, it.info.role == AmeGroupMemberInfo.VISITOR))
                        result?.invoke(it.info, it.ackState, "")
                    }, {
                        ALog.e(TAG, "getGroupInfo error", it)
                        result?.invoke(groupCache.getGroupInfo(groupId), null, "Failed")
                    })
        }

        fun queryGroupInfo(groupId: Long): Observable<GroupInfo> {
            return queryGroupInfoImpl(groupId)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .map {
                        GroupMessageLogic.get(accountContext).syncOfflineMessage(it.info.gid, it.ackState.lastMid, it.ackState.lastAckMid)
                        GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                                ?: throw Exception("no group info")
                    }
        }


        @SuppressLint("CheckResult")
        fun queryGroupInfoByGids(gidList: List<Long>, result: (succeed: Boolean) -> Unit) {
            if (gidList.isEmpty()) {
                result(true)
                return
            }

            ALog.i(TAG, "queryGroupInfoByGids")
            GroupManagerCore.getGroupInfoByGids(accountContext, gidList)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map<List<GroupInfoEntity>> {
                        if (it.isSuccess) {
                            ALog.i(TAG, "queryGroupInfoByGids succeed ${it.data?.groups?.size}")
                            if (it.data?.groups?.isNotEmpty() == true) {
                                return@map it.data.groups
                            }
                        }
                        throw GroupException(it.msg)
                    }.flatMap { groupList ->
                        GroupManagerCore.queryRecipientsInfo(accountContext, groupList.mapNotNull { it.owner })
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .map {
                                    val map = HashMap<String, IdentityKeyInfo>()
                                    for (i in it) {
                                        map[i.uid] = i
                                    }
                                    Pair<List<GroupInfoEntity>, Map<String, IdentityKeyInfo>>(groupList, map)
                                }
                    }
                    .map {
                        val gList = ArrayList<GroupInfo>()
                        for (groupInfo in it.first) {
                            val dbGroupInfo = parseGroupInfo(groupInfo, it.second[groupInfo.owner]?.identityKey, false)
                            if (TextUtils.isEmpty(dbGroupInfo.channel_key)
                                    && TextUtils.isEmpty(dbGroupInfo.currentKey)
                                    && !dbGroupInfo.isNewGroup) {
                                ALog.i(TAG, "error group: key not found, waiting key")
                            }
                            gList.add(dbGroupInfo)
                        }
                        groupCache.saveGroupInfos(gList)

                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .map {
                        EventBus.getDefault().post(GroupListChangedEvent(-1, false))
                    }
                    .observeOn(Schedulers.io())
                    .subscribe({
                        result(true)
                    }, {
                        ALog.e(TAG, "queryGroupInfoByGids", it)
                        result(false)
                    })
        }

        private fun queryGroupInfoImpl(groupId: Long): Observable<GroupInfoResult> {
            return groupInfoLoader.loadGroup(groupId)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .flatMap { groupEntity ->
                        if (groupEntity.isSuccess) {
                            memberLoader.loadMember(groupId, accountContext.uid)
                                    .map {
                                        Pair(groupEntity.data, it)
                                    }
                        } else {
                            val gInfo = groupCache.getGroupInfo(groupId)
                            if (null != gInfo && (groupEntity.code == 110024 || groupEntity.code == 11006)) {//kicked out
                                if (gInfo.role != AmeGroupMemberInfo.VISITOR) {
                                    groupCache.updateRole(groupId, AmeGroupMemberInfo.VISITOR)
                                    GroupMessageLogic.get(accountContext).systemNotice(groupId, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_GROUP_ILLEGAL))
                                }
                            }
                            throw GroupException(groupEntity.msg ?: "Failed")
                        }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { pair ->
                        GroupManagerCore.queryRecipientsInfo(accountContext, listOf(pair.first.owner!!))
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .map {
                                    Triple(it.takeIf { it.isNotEmpty() }?.first(), pair.first, pair.second)
                                }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        val dbGroupInfo = parseGroupInfo(it.second, it.first?.identityKey, true)
                        if (TextUtils.isEmpty(dbGroupInfo.currentKey) && TextUtils.isEmpty(dbGroupInfo.channel_key)) {
                            ALog.i(TAG, "error group: key not found")
                        }

                        if ((it.third.status and 0x1L) == 0L) {
                            dbGroupInfo.notification_enable = GroupInfo.NOTIFICATION_ENABLE
                        } else {
                            dbGroupInfo.notification_enable = GroupInfo.NOTIFICATION_DISABLE
                        }

                        val recipient = Recipient.recipientFromNewGroupId(accountContext, dbGroupInfo.gid)
                        if ((dbGroupInfo.notification_enable == GroupInfo.NOTIFICATION_DISABLE)) {
                            Repository.getRecipientRepo(accountContext)?.setMuted(recipient, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10))
                        } else {
                            Repository.getRecipientRepo(accountContext)?.setMuted(recipient, 0)
                        }

                        groupCache.saveGroupInfo(dbGroupInfo)

                        val ameGroup = GroupInfoTransform.transformToModel(dbGroupInfo)
                        AmeDispatcher.mainThread.dispatch {
                            listenerRef.get()?.onGroupInfoChanged(ameGroup)
                        }

                        val messageAckState = GroupAckState(it.second.gid, it.second.last_mid, it.second.last_ack_mid)

                        checkGroupKeyValidState(listOf(groupId))

                        checkGroupShareLink(groupId, dbGroupInfo.role)
                        GroupInfoResult(ameGroup, messageAckState)
                    }
        }

        private fun checkGroupShareLink(groupId: Long, role: Long) {
            getShareLink(groupId) {
                succeed, link ->
                if (succeed) {
                    if (role == AmeGroupMemberInfo.OWNER) {
                        if (link.isEmpty() || link != getGroupInfo(groupId)?.shareLink) {
                            ACLog.i(accountContext, TAG, "checkGroupShareLink regen link")
                            genShareLink(groupId)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .subscribe({},{})
                        }
                    } else {
                        AmeDispatcher.io.dispatch {
                            groupCache.updateShareLink(groupId, link)
                        }
                    }
                }
            }
        }


        private data class GroupInfoResult(val info: AmeGroupInfo, val ackState: GroupAckState)

        private fun parseGroupInfo(info: GroupInfoEntity, ownerIdentityKey: String?, parseCount: Boolean): GroupInfo {
            val dbGroupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, info.gid)
                    ?: GroupInfo()

            val oldSecretKey = dbGroupInfo.infoSecret
            info.toDbGroup(accountContext, dbGroupInfo, ownerIdentityKey, parseCount)

            if (dbGroupInfo.role == AmeGroupMemberInfo.OWNER) {
                if ((oldSecretKey != dbGroupInfo.infoSecret || TextUtils.isEmpty(dbGroupInfo.shareCodeSetting))) {
                    updateShareSetting(dbGroupInfo.gid, false) { succeed, shareCode, error ->
                        ALog.i(TAG, "parseGroupInfo adjust group info succeed:$succeed $error")
                    }
                } else if (groupCache.isBroadcastSharingData(info.gid)) {
                    broadcastShareSettingRefresh(info.gid)
                }
            }

            if (dbGroupInfo.isNewGroup) {
                val lastKeyVersion = GroupInfoDataManager.queryLastGroupKeyVersion(accountContext, dbGroupInfo.gid)
                if (null != lastKeyVersion) {
                    dbGroupInfo.currentKeyVersion = lastKeyVersion.version
                    dbGroupInfo.currentKey = lastKeyVersion.key
                }
            }

            return dbGroupInfo
        }

        @SuppressLint("CheckResult")
        fun inviteMember(groupId: Long, memberList: List<String>, result: (succeed: Boolean, succeedList: ArrayList<AmeGroupMemberInfo>?, resultMessage: String?) -> Unit) {
            val recipientList = ArrayList<Recipient>()


            data class ParamStash(
                    var strangerList: List<String> = listOf(),
                    var friendList: List<Recipient> = listOf(),
                    var failedList: List<String>? = null,
                    var groupInfo: GroupInfo? = null,
                    var result: String = "",
                    var succeedList: ArrayList<AmeGroupMemberInfo> = ArrayList()
            )

            val stashParam = ParamStash()
            GroupManagerCore.queryRecipientsInfo(accountContext, memberList)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map { list ->

                        val dbGroupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                                ?: throw GroupException(AppUtil.getString(R.string.common_error_failed))
                        if (TextUtils.isEmpty(dbGroupInfo.infoSecret)) {
                            ALog.e(TAG, "inviteMember failed, secret is empty")
                            throw GroupException(AppUtil.getString(R.string.common_error_failed))
                        }

                        stashParam.groupInfo = dbGroupInfo

                        recipientList.addAll(list.map {
                            val recipient = Recipient.from(accountContext, it.uid, true)
                            recipient.identityKey = it.identityKey
                            recipient.featureSupport = it.getSupport()
                            recipient
                        })

                        val tmp = recipientList.filter {
                            if (dbGroupInfo.isNewGroup && it.featureSupport?.isSupportGroupSecureV3() != true) {
                                return@filter false
                            }
                            return@filter (!it.isFriend)
                        }

                        recipientList.removeAll(tmp)
                        stashParam.strangerList = tmp.map { it.address.serialize() }

                        stashParam.friendList = recipientList
                        stashParam
                    }.flatMap { stash ->
                        if (stash.friendList.isNotEmpty()) {
                            val dbGroupInfo = stash.groupInfo!!
                            val password = dbGroupInfo.currentKey.toByteArray().base64Decode()

                            val infoSecret = GroupMessageEncryptUtils.decodeGroupPassword(dbGroupInfo.infoSecret)
                            val listSecret = BCMEncryptUtils.generateMembersEncryptKeys(recipientList.mapNotNull { it.identityKey }, infoSecret)
                            val proofList = if (dbGroupInfo.isNewGroup) {
                                recipientList.map { GroupProof.encodeMemberProof(GroupProof.signMember(dbGroupInfo, it.address.serialize())) }
                            } else {
                                null
                            }

                            val listKeys = if (!dbGroupInfo.isNewGroup) {
                                BCMEncryptUtils.generateMembersEncryptKeys(recipientList.mapNotNull { it.identityKey }, password)
                            } else {
                                null
                            }

                            val members = stash.friendList.map { it.address.serialize() }

                            val timestamp = AmeTimeUtil.serverTimeMillis()
                            val signatures = ArrayList<String>()
                            for (i in stash.friendList) {
                                val inviteData = GroupInviteDataEntity(groupId, i.address.serialize(), timestamp, i.bcmName
                                        ?: i.address.format())
                                val inviteSignData = GroupInviteSignDataEntity.inviteData2SignData(accountContext, inviteData, dbGroupInfo.infoSecret)
                                signatures.add(inviteSignData)
                            }

                            GroupMemberCore.inviteMemberJoinGroup(accountContext, groupId, members, listKeys, proofList, listSecret, signatures)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map {
                                        stash.failedList = it.failed_members
                                        stash
                                    }
                        } else {
                            Observable.create {
                                it.onNext(stash)
                                it.onComplete()
                            }
                        }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .map { stash ->
                        ALog.i(TAG, "inviteGroup succeed")

                        if (stash.friendList.isNotEmpty()) {
                            val memberInfos = ArrayList<AmeGroupMemberInfo>()
                            val dbGroupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                                    ?: throw GroupException(AppUtil.getString(R.string.common_error_failed))

                            var time = System.currentTimeMillis()
                            for (u in recipientList) {
                                if (stash.failedList?.contains(u.address.serialize()) == true) {
                                    continue
                                }

                                val member = AmeGroupMemberInfo()
                                member.uid = u.address.serialize()
                                member.role = AmeGroupMemberInfo.MEMBER
                                member.gid = groupId
                                member.nickname = u.bcmName
                                member.createTime = time++
                                memberInfos.add(member)
                            }

                            stash.succeedList = memberInfos

                            if (dbGroupInfo.role == AmeGroupMemberInfo.OWNER || dbGroupInfo.needOwnerConfirm == 0) {
                                GroupMemberManager.insertGroupMembers(accountContext, memberInfos)
                            }
                        }


                        if (stash.strangerList.isNotEmpty()) {
                            inviteStrangerNotify(groupId, stash.strangerList)
                        }

                        stash.result = if (stash.groupInfo!!.role == AmeGroupMemberInfo.OWNER || stash.groupInfo!!.needOwnerConfirm == 0) {
                            AppUtil.getString(R.string.chats_group_member_add_success)
                        } else {
                            AppUtil.getString(R.string.chats_group_member_invite_wating_review)
                        }

                        stash
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        result(true, it.succeedList, it.result)
                    }, {
                        ALog.e(TAG, "invite member", it)
                        result(false, null, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
        }

        fun getGroupInfo(groupId: Long): AmeGroupInfo? {
            return groupCache.getGroupInfo(groupId)
        }

        private fun getGroupList(): List<Long> {
            return groupCache.getGroupList()
        }

        fun getGroupInfoList(): List<AmeGroupInfo> {
            return groupCache.getGroupInfoList()
        }


        fun updateGroupAvatar(gid: Long, avatar: String, result: (isSuccess: Boolean, error: String?) -> Unit): Boolean {
            if (gid <= 0) {
                ALog.w(TAG, "Gid is smaller than or equals 0")
                return false
            }

            GroupManagerCore.updateGroup(accountContext, gid, null, avatar)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        if (it.isSuccess) {
                            groupCache.updateGroupAvatar(gid, avatar)
                            genShareLink(gid)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        } else {
                            if (110005 == it.code) {
                                throw GroupException(AppContextHolder.APP_CONTEXT.getString(R.string.chats_group_name_too_long))
                            } else {
                                throw GroupException(it.msg)
                            }
                        }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({
                        result(true, "")
                    }, {
                        ALog.e(TAG, "updateGroupAvatar error", it)
                        result(false, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
            return true
        }

        @SuppressLint("CheckResult")
        fun leaveGroup(groupId: Long, newOwnerUid: String?, result: (succeed: Boolean, error: String) -> Unit) {
            val groupInfo = getGroupInfo(groupId) ?: return
            GroupManagerCore.leaveGroup(accountContext, groupId, groupInfo.newGroup, newOwnerUid)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        ALog.i(TAG, "leaveGroup succeed")
                        leaveSucceed(groupId)
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        EventBus.getDefault().post(GroupListChangedEvent(groupId, true))
                        result(true, "")
                    }, {
                        ALog.e(TAG, "leaveGroup", it)
                        if (ServerCodeUtil.getNetStatusCode(it) == 110071) {
                            leaveSucceed(groupId)
                            result(true, "")
                            return@subscribe
                        }
                        result(false, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
        }

        private fun leaveSucceed(groupId: Long) {
            AmeDispatcher.io.dispatch {
                groupCache.removeGroupInfo(groupId)
                groupCache.updateRole(groupId, AmeGroupMemberInfo.VISITOR)

                val recipientRepo = Repository.getRecipientRepo(accountContext) ?: return@dispatch
                recipientRepo.leaveGroup(listOf(groupId))

                val threadRepo = Repository.getThreadRepo(accountContext) ?: return@dispatch
                threadRepo.cleanConversationContentForGroup(threadRepo.getThreadIdIfExist(Recipient.recipientFromNewGroupId(accountContext, groupId)), groupId)
            }
        }


        fun updateGroupInfoPinMid(groupId: Long, mid: Long) {
            groupCache.updatePinState(groupId, mid)
        }

        @SuppressLint("CheckResult")
        fun getGroupMemberInfo(groupId: Long, uid: String, result: (member: AmeGroupMemberInfo?, error: String?) -> Unit) {
            memberLoader.loadMember(groupId, uid)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map<AmeGroupMemberInfo> {
                        ALog.i(TAG, "getGroupMemberInfo succeed")

                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                        val channelKey = groupInfo?.channel_key
                        if (channelKey != null) {
                            val dbMember = it.toDbMember(groupId, channelKey, groupInfo)
                                    ?: throw GroupException("proof failed")
                            val member = GroupMemberTransform.transToModel(dbMember)

                            groupCache.saveMember(listOf(dbMember))
                            member
                        } else {
                            throw GroupException(AppUtil.getString(R.string.common_error_failed))
                        }
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .map {
                        if (isCurrentModel(groupId)) {
                            getModel(groupId)?.updateMember2Cache(listOf(it))
                        }
                        it
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({
                        result(it, "")
                    }, {
                        ALog.e(TAG, "getGroupMemberInfo error", it)
                        result(null, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
        }

        fun getGroupMemberInfos(groupId: Long, uidList: List<String>): Observable<List<AmeGroupMemberInfo>> {
            return Observable.just(uidList.toMutableList())
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { queryList ->
                        val localList = GroupMemberManager.queryGroupMemberList(accountContext, groupId, uidList)
                        queryList.removeAll(localList.map { it.uid })
                        if (queryList.isEmpty()) {
                            Observable.just(localList)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        } else {
                            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                                    ?: throw GroupException(AppUtil.getString(R.string.common_error_failed))
                            if (groupInfo.channel_key.isNullOrEmpty()) {
                                throw GroupException(AppUtil.getString(R.string.common_error_failed))
                            }

                            memberLoader.loadMembers(groupId, queryList)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map { mlist ->
                                        queryList.removeAll(mlist.map { it.uid })

                                        if (queryList.isNotEmpty()) {
                                            groupCache.deleteMembers(groupId, queryList)
                                        }

                                        val members = mutableListOf<AmeGroupMemberInfo>()
                                        val dbMembers = mlist.mapNotNull { it.toDbMember(groupId, groupInfo.channel_key, groupInfo) }
                                        val l = dbMembers.map { GroupMemberTransform.transToModel(it) }

                                        groupCache.saveMember(dbMembers)

                                        members.addAll(localList)
                                        members.addAll(l)

                                        members
                                    }
                        }
                    }.observeOn(AmeDispatcher.mainScheduler)
        }


        @SuppressLint("CheckResult")
        fun muteGroup(groupId: Long, mute: Boolean, callback: (success: Boolean, msg: String?) -> Unit) {

            GroupManagerCore.updateMyNameAndMuteSetting(accountContext, groupId, mute, null, null, null)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .doOnNext {
                        if (it.isSuccess) {
                            groupCache.updateEnableNotify(groupId, !mute)
                        }
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({ result ->
                        ALog.i(TAG, "muteGroup result ${result.code}")
                        callback.invoke(result.isSuccess, result.msg)
                    }, { throwable ->
                        ALog.e(TAG, "muteGroup", throwable)
                        callback.invoke(false, throwable.message)
                    })
        }

        fun updateMyMemberInfo(gid: Long, name: String?, customName: String?, keyConfig: AmeGroupMemberInfo.KeyConfig?, result: (succeed: Boolean, error: String) -> Unit) {
            val channelKey = getGroupInfo(gid)?.channelKey

            if (null == channelKey || channelKey.isEmpty()) {
                ALog.i(TAG, "updateMyMemberInfo failed, key is null or empty")
                return
            }

            var encryptProfileKey: String? = null
            var encryptName: String? = null
            var encryptCustomName: String? = null

            if (null != keyConfig) {
                encryptProfileKey = EncryptUtils.aes256EncryptAndBase64(keyConfig.toString(), channelKey.toByteArray())
            }

            if (null != name) {
                encryptName = EncryptUtils.aes256EncryptAndBase64(name, channelKey.toByteArray())
            }

            if (null != customName) {
                encryptCustomName = EncryptUtils.aes256EncryptAndBase64(customName, channelKey.toByteArray())
            }

            GroupManagerCore.updateMyNameAndMuteSetting(accountContext, gid, null, encryptName, encryptCustomName, encryptProfileKey)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        if (it.isSuccess) {
                            true
                        } else {
                            throw GroupException(it.msg)
                        }
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({
                        ALog.i(TAG, "updateMyMemberInfo succeed")
                        result(true, "")
                    }, {
                        ALog.logForSecret(TAG, "updateMyMemberInfo failed", it)
                        result(false, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
        }

        fun getUserRole(groupId: Long, uid: String, result: (role: Long) -> Unit) {
            getGroupMemberInfo(groupId, uid) { member, error ->
                if (member != null) {
                    result(member.role)
                } else {
                    result(AmeGroupMemberInfo.VISITOR)
                }
            }
        }

        @SuppressLint("CheckResult")
        fun deleteMember(groupId: Long, list: ArrayList<AmeGroupMemberInfo>, result: (succeed: Boolean, succeedList: ArrayList<AmeGroupMemberInfo>?, error: String?) -> Unit) {
            val uidList = ArrayList<String>()
            for (u in list) {
                if (null != u.uid) {
                    uidList.add(u.uid)
                }
            }

            val groupInfo = getGroupInfo(groupId)
            if (null == groupInfo) {
                result(false, null, AppUtil.getString(R.string.common_error_failed))
                return
            }
            GroupMemberCore.kickGroupMember(accountContext, groupId, groupInfo.newGroup, uidList)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        ALog.i(TAG, "deleteMember result:succeed")
                        val deletedList = ArrayList<AmeGroupMemberInfo>()
                        for (u in list) {
                            if (uidList.contains(u.uid)) {
                                deletedList.add(u)
                            }
                        }

                        for (delete in uidList) {
                            GroupMemberManager.deleteMember(accountContext, groupId, delete)
                        }
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        result(true, list, "")
                    }, {
                        ALog.e(TAG, "deleteMember", it)
                        result(false, null, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })

        }

        private fun getThreadDB(): ThreadRepo? {
            return Repository.getThreadRepo(accountContext)
        }

        @Subscribe
        fun onEvent(e: GroupInfoUpdateNotify) {
            listenerRef.get()?.onGroupInfoChanged(e.groupInfo)
            getShareLink(e.groupInfo.gid){_,_->}
        }

        @Subscribe
        fun onEvent(e: GroupMemberChangedNotify) {
            val change = e.changed
            when {
                change.isMyJoin() -> {
                    ALog.i(TAG, "join group")
                    queryGroupInfo(change.groupId) { ameGroup, ackState, error ->
                        AmeDispatcher.io.dispatch {
                            if (null != ameGroup && null != ackState) {
                                getModel(change.groupId)?.checkSync()
                                GroupMessageLogic.get(accountContext).syncOfflineMessage(ameGroup.gid, ackState.lastMid, ackState.lastAckMid)
                                getThreadDB()?.getThreadIdFor(Recipient.recipientFromNewGroup(accountContext, ameGroup))
                            } else {
                                ALog.e(TAG, "join new group, query group info failed $error")
                            }
                        }
                    }
                }
                change.isMyLeave() -> {
                    Observable.just(change.groupId)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .map {
                                groupCache.updateRole(it, AmeGroupMemberInfo.VISITOR)
                                updateNoticeShowState(it, true)
                            }.observeOn(AmeDispatcher.mainScheduler)
                            .subscribe({
                                if (e.changed.fromUid != accountContext.uid) {
                                    listenerRef.get()?.onMemberLeave(change.groupId, change.memberList)
                                }
                            }, {
                                ALog.e(TAG, it)
                            })
                }
            }

            AmeDispatcher.io.dispatch {
                when (change.action) {
                    AmeGroupMemberChanged.JOIN -> {
                        getGroupMemberInfos(change.groupId, change.memberList.map {
                            it.uid
                        }).delaySubscription(1, TimeUnit.SECONDS)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .map {
                                    GroupMemberManager.insertGroupMembers(accountContext, change.memberList)
                                    it
                                }
                                .observeOn(AmeDispatcher.mainScheduler)
                                .subscribe({
                                    listenerRef.get()?.onMemberUpdate(change.groupId, it)
                                }, {})
                        GroupMemberManager.insertGroupMembers(accountContext, change.memberList)
                        GroupInfoDataManager.increaseMemberCount(accountContext, change.groupId, 1L)
                        groupCache.setGroupMemberState(change.groupId, GroupMemberSyncState.DIRTY)
                    }
                    AmeGroupMemberChanged.UPDATE -> {
                        queryGroupInfo(change.groupId, null)
                        GroupMemberManager.updateGroupMembers(accountContext, change.memberList)
                    }
                    AmeGroupMemberChanged.LEAVE -> {
                        GroupInfoDataManager.increaseMemberCount(accountContext, change.groupId, -1L)
                        GroupMemberManager.deleteMember(accountContext, change.memberList)
                        groupCache.setGroupMemberState(change.groupId, GroupMemberSyncState.DIRTY)
                    }
                }
            }

            AmeDispatcher.mainThread.dispatch {
                val model = getModel(change.groupId)
                if (null != model) {
                    when (change.action) {
                        AmeGroupMemberChanged.JOIN -> {
                            GroupMessageLogic.get(accountContext).syncJoinReqMessage(change.groupId)
                            listenerRef.get()?.onMemberJoin(change.groupId, change.memberList)
                        }
                        AmeGroupMemberChanged.UPDATE -> {
                            listenerRef.get()?.onMemberUpdate(change.groupId, change.memberList)
                        }
                        AmeGroupMemberChanged.LEAVE -> {
                            listenerRef.get()?.onMemberLeave(change.groupId, change.memberList)
                        }
                    }
                }
            }
        }

        @Subscribe
        fun onEvent(event: GroupRefreshKeyEvent) {
            ALog.i(TAG, "GroupRefreshKeyEvent ${event.gid} coming")
            queryGroupInfo(event.gid) { ameGroup, ackState, error ->
                AmeDispatcher.io.dispatch {
                    if (null != ameGroup && null != ackState) {
                        val groupKey = BCMEncryptUtils.decryptGroupPassword(accountContext, event.groupKey).first
                        val groupSecret = BCMEncryptUtils.decryptGroupPassword(accountContext, event.groupInfoSecret).first
                        if (groupKey.isEmpty() || groupSecret.isEmpty()) {
                            ALog.i(TAG, "GroupRefreshKeyEvent ${event.gid} keysize:${groupKey.length} secret size:${groupSecret.length}")
                        } else {
                            groupCache.updateKey(event.gid, 0, groupKey)
                            groupCache.updateGroupInfoKey(event.gid, groupSecret)
                            ALog.i(TAG, "GroupRefreshKeyEvent ${event.gid} done")
                        }

                        GroupMessageLogic.get(accountContext).syncOfflineMessage(ameGroup.gid, ackState.lastMid, ackState.lastAckMid)

                    } else {
                        ALog.e(TAG, "join new group, query group info failed $error")
                    }
                }
            }
        }

        @Subscribe
        fun onEvent(event: GroupJoinReviewRequestEvent) {
            ALog.i(TAG, "GroupJoinReviewRequestEvent ${event.gid}")
            GroupMessageLogic.get(accountContext).syncJoinReqMessage(event.gid)
        }

        @Subscribe
        fun onEvent(event: GroupShareSettingRefreshEvent) {
            val owner = GroupInfoDataManager.queryOneGroupInfo(accountContext, event.gid)?.owner
            if (owner == null || owner.isEmpty()) {
                ALog.w(TAG, "GroupShareSettingRefreshEvent ${event.gid}  group info is null")
                return
            }

            GroupManagerCore.queryRecipientsInfo(accountContext, listOf(owner))
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        getShareLink(event.gid){_,_->

                        }
                        if (it.isNotEmpty()) {
                            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, event.gid)
                                    ?: return@map
                            if (GroupShareSettingUtil.parseIntoGroupInfo(it[0].identityKey,
                                            event.shareSetting,
                                            event.shareSettingSign,
                                            event.shareConfirmSign,
                                            groupInfo.infoSecret, event.needConfirm, groupInfo)) {

                                groupCache.updateShareSetting(groupInfo.gid,
                                        groupInfo.shareEnabled,
                                        groupInfo.shareEpoch,
                                        groupInfo.shareCode,
                                        groupInfo.shareCodeSetting,
                                        groupInfo.shareCodeSettingSign,
                                        groupInfo.shareSettingAndConfirmSign,
                                        groupInfo.ephemeralKey?:"")

                                groupCache.updateNeedConfirm(groupInfo.gid,
                                        groupInfo.needOwnerConfirm,
                                        groupInfo.shareSettingAndConfirmSign)

                                AmeDispatcher.mainThread.dispatch {
                                    listenerRef.get()?.onGroupShareSettingChanged(groupInfo.gid
                                            , groupInfo.shareEnabled == 1
                                            , groupInfo.needOwnerConfirm == 1)
                                }
                                return@map
                            }
                        }
                        throw GroupException("GroupShareSettingRefreshEvent update group info failed")
                    }
                    .subscribe({
                        ALog.i(TAG, "GroupShareSettingRefreshEvent ${event.gid} succeed")
                    }, {
                        ALog.e(TAG, "GroupShareSettingRefreshEvent ", it)
                    })

        }

        fun autoReviewJoinRequest(gid: Long, list: List<GroupJoinRequestInfo>, result: (succeed: Boolean, error: String) -> Unit) {
            AmeDispatcher.io.dispatch {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                        ?: throw GroupException("group info is null")
                if (groupInfo.role == AmeGroupMemberInfo.OWNER && groupInfo.needOwnerConfirm == 1) {
                    return@dispatch
                }

                for (i in list) {
                    i.status = BcmGroupJoinStatus.OWNER_APPROVED.status
                }

                if (list.isEmpty()) {
                    return@dispatch
                }

                val reviewList = list.map {
                    val memberKeyString = BCMEncryptUtils.generateMemberEncryptKey(it.uidIdentityKey, EncryptUtils.base64Decode(groupInfo.currentKey.toByteArray()))
                    val memberSecretString = BCMEncryptUtils.generateMemberEncryptKey(it.uidIdentityKey, EncryptUtils.base64Decode(groupInfo.infoSecret.toByteArray()))
                    BcmGroupReviewAccept(it.uid, it.status == BcmGroupJoinStatus.OWNER_APPROVED.status, memberSecretString, it.inviter, memberKeyString, null)
                }

                GroupManagerCore.autoReviewJoinRequest(accountContext, groupInfo.gid, reviewList)
                        .subscribeOn(AmeDispatcher.ioScheduler)
                        .observeOn(AmeDispatcher.ioScheduler)
                        .subscribe({
                            result(true, "")
                        }, {
                            ALog.e(TAG, "autoReviewJoinRequest failed", it)
                            result(false, "")
                        })
            }
        }

        private val SHARE_LINK = "qr_code";
        private fun updateShareLink(gid: Long, link: String) {
            updateGroupExtension(gid, SHARE_LINK, link.toByteArray()) {
                if (it) {
                    listenerRef.get()?.onGroupShareLinkChanged(gid, link)
                }
            }
        }

        private fun getShareLink(gid: Long, result: (succeed: Boolean, link: String) -> Unit) {
            getGroupExtension(gid, listOf(SHARE_LINK)) { succeed, data ->
                val link = data[SHARE_LINK]?.format() ?: ""
                if (succeed) {
                    groupCache.updateShareLink(gid, link)
                }
                AmeDispatcher.mainThread.dispatch {
                    listenerRef.get()?.onGroupShareLinkChanged(gid, link)
                    result(succeed, link)
                }
            }
        }

        @SuppressLint("CheckResult")
        fun genShareLink(groupId: Long): Observable<String> {
            return Observable.create<Pair<String, Long>> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                        ?: throw Exception("groupInfo is null")
                val eKey = if (groupInfo.isNewGroup) {
                    groupInfo.ephemeralKey
                } else {
                    null
                }

                if (groupInfo.role != AmeGroupMemberInfo.OWNER) {
                    throw GroupException("I'm not the owner")
                }

                if (TextUtils.isEmpty(groupInfo.shareLink) && groupInfo.needOwnerConfirm != 1 && groupInfo.shareEnabled == 1 ) {
                    updateNeedConfirm(groupId, true){
                        succeed, _ ->
                        if (succeed) {
                            AmeDispatcher.mainThread.dispatch {
                                listenerRef.get()?.onGroupShareSettingChanged(groupInfo.gid
                                        , shareEnable = true
                                        , needConfirm = true)
                            }
                        }
                    }
                }

                val shareContent = AmeGroupMessage.GroupShareContent(groupId, groupInfo.name, groupInfo.iconUrl, groupInfo.shareCode
                        ?: "", groupInfo.shareCodeSettingSign
                        ?: "", eKey, System.currentTimeMillis(), null)
                val jsonString = shareContent.toShortJson()
                if (jsonString.isEmpty()) {
                    throw Exception("createGroupShareShortUrl fail")
                }
                ALog.d(TAG, "createGroupShareShortUrl jsonString: $jsonString")
                val lKey = BCMEncryptUtils.murmurHash3(0xFBA4C795, jsonString.toByteArray())
                ALog.d(TAG, "createGroupShareShortUrl hash: $lKey")

                val cipherData = Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(jsonString.toByteArray(), lKey.toString().toByteArray()))

                it.onNext(Pair(cipherData, lKey))
                it.onComplete()
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { pair ->
                        GroupManagerCore.createGroupShareShortUrl(accountContext, pair.first)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .map { Pair(it, pair.second) }
                    }
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map { pair ->
                        ALog.d(TAG, "createGroupShareShortUrl index: ${pair.first}")
                        val shareLink = AmeGroupMessage.GroupShareContent.toShortLink(pair.first, Base62.encode(pair.second))
                        groupCache.updateShareLink(groupId, shareLink)
                        updateShareLink(groupId, shareLink)
                        shareLink
                    }
                    .observeOn(AmeDispatcher.ioScheduler)
        }

        private fun updateGroupExtension(gid: Long, key: String, data: ByteArray?, result: (succeed: Boolean) -> Unit) {
            Observable.create<ByteArray> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                        ?: throw GroupException("group info not exist")

                if (groupInfo.role != AmeGroupMemberInfo.OWNER) {
                    throw GroupException("I'm not the owner")
                }

                if (groupInfo.groupPublicKey == null) {
                    throw GroupException("info secret not exist")
                }

                val cipherData = if (null != data) {
                    ECCCipher.encrypt(groupInfo.groupPublicKey.publicKey(), data)
                } else {
                    ByteArray(0)
                }
                it.onNext(cipherData)
                it.onComplete()
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.updateGroupExtension(accountContext, gid, key, it)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        ACLog.i(accountContext, TAG, "updateGroupExtension succeed:$it")
                        result(true)
                    }, {
                        ACLog.e(accountContext, TAG, "updateGroupExtension", it)
                        result(false)
                    })
        }

        private fun getGroupExtension(gid: Long, keys: List<String>, result: (succeed: Boolean, map: Map<String, ByteArray>) -> Unit) {
            GroupManagerCore.getGroupExtension(accountContext, gid, keys)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                ?: throw GroupException("group info not exist")
                        val keyMap = mutableMapOf<String, ByteArray>()
                        it.forEach { i ->
                            try {
                                val data = ECCCipher.decrypt(groupInfo.groupPrivateKey.privateKey(), i.value)
                                keyMap[i.key] = data
                            } catch (e: IOException) {
                                ACLog.e(accountContext, TAG, "$gid parse extension failed ${i.key}")
                            }
                        }

                        keyMap
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({
                        ACLog.i(accountContext, TAG, "getGroupExtension succeed:$it")
                        result(true, it)
                    }, {
                        ACLog.e(accountContext, TAG, "getGroupExtension", it)
                        result(false, mapOf())
                    })
        }

        fun reviewJoinRequest(gid: Long, list: List<BcmReviewGroupJoinRequest>, result: (succeed: Boolean, error: String) -> Unit) {
            AmeDispatcher.io.dispatch {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)

                if (null != groupInfo && !TextUtils.isEmpty(groupInfo.infoSecret)) {
                    val approvedStatus = HashMap<String, Int>()
                    val reqUidList = list.map {
                        if (it.accepted) {
                            approvedStatus[it.uid] = BcmGroupJoinStatus.OWNER_APPROVED.status
                        } else {
                            approvedStatus[it.uid] = BcmGroupJoinStatus.OWNER_REJECTED.status
                        }
                        it.uid
                    }

                    val reqList = BcmGroupJoinManager.getJoinInfoByUidList(accountContext, gid, reqUidList)
                    for (i in reqList) {
                        i.status = approvedStatus[i.uid] ?: BcmGroupJoinStatus.OWNER_REJECTED.status
                    }

                    val reduceSet = mutableSetOf<String>()
                    val reviewList = reqList.filter {
                        if (!reduceSet.contains(it.uid)) {
                            reduceSet.add(it.uid)
                            true
                        } else {
                            false
                        }
                    }.map {
                        val proof = if (groupInfo.isNewGroup) {
                            GroupProof.encodeMemberProof(GroupProof.signMember(groupInfo, it.uid))
                        } else {
                            null
                        }

                        val memberKeypecString = if (!groupInfo.isNewGroup) {
                            BCMEncryptUtils.generateMemberEncryptKey(it.uidIdentityKey, EncryptUtils.base64Decode(groupInfo.currentKey.toByteArray()))
                        } else {
                            null
                        }

                        val memberSecretString = BCMEncryptUtils.generateMemberEncryptKey(it.uidIdentityKey, EncryptUtils.base64Decode(groupInfo.infoSecret.toByteArray()))
                        BcmGroupReviewAccept(it.uid, it.status == BcmGroupJoinStatus.OWNER_APPROVED.status, memberSecretString, it.inviter, memberKeypecString, proof)
                    }

                    GroupManagerCore.reviewJoinRequestByOwner(accountContext, groupInfo.gid, reviewList, groupInfo.isNewGroup)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .map {
                                BcmGroupJoinManager.updateJoinRequests(accountContext, reqList)
                            }
                            .doOnError {
                                ALog.e(TAG, "reviewJoinRequest failed", it)
                                result(false, AppUtil.getString(R.string.common_error_failed))
                            }
                            .subscribe {
                                result(true, "")
                            }

                } else {
                    ALog.e(TAG, "reviewJoinRequest failed ")
                    result(false, AppUtil.getString(R.string.common_error_failed))
                }
            }
        }

        fun fetchJoinRequestList(gid: Long, delay: Long = 0, startUid: String, count: Long, fetchResult: (succeed: Boolean, list: List<GroupJoinRequestInfo>) -> Unit) {
            val pendingList = ArrayList<GroupJoinPendingUserEntity>()
            GroupManagerCore.queryPendingList(accountContext, gid, startUid, count)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .delaySubscription(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                    .flatMap { result ->
                        ALog.i(TAG, "fetchJoinRequestList $gid review list:${result.size} succeed:true")
                        if (result.isEmpty()) {
                            throw GroupException("pending list is empty")
                        }

                        pendingList.addAll(result)

                        val uidList = result.filter { !it.inviter.isNullOrEmpty() }.mapNotNull { it.inviter }.toMutableSet()
                        uidList.addAll(result.map { it.uid })

                        GroupManagerCore.queryRecipientsInfo(accountContext, uidList.toList())
                                .map { list ->
                                    val map = mutableMapOf<String, IdentityKeyInfo>()
                                    list.forEach { map[it.uid] = it }
                                    map
                                }
                    }.subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map {
                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                ?: throw GroupException("group info is null")

                        val list = ArrayList<GroupJoinRequestInfo>()
                        for (i in pendingList) {
                            val inviterKey = it[i.inviter ?: ""]
                            val uidKey = it[i.uid] ?: continue
                            val validRequest = i.toJoinRequest(groupInfo, uidKey, inviterKey)
                            if (null != validRequest) {
                                list.add(validRequest)
                            }
                        }

                        if (list.isNotEmpty()) {
                            if (groupInfo.needOwnerConfirm == 1 && groupInfo.role == AmeGroupMemberInfo.OWNER) {
                                saveJoinRequestList(groupInfo.gid, list)
                                if (isCurrentModel(gid)) {
                                    val model = getModel(gid)
                                    model?.refreshJoinRequestCache()
                                }
                            }
                        }
                        list
                    }
                    .observeOn(Schedulers.io())
                    .subscribe({
                        fetchResult(true, it)
                    }, {
                        if (it is GroupException) {
                            ALog.i(TAG, "fetchJoinRequestList ${it.err}")
                        } else {
                            ALog.e(TAG, "$gid fetchJoinRequestList failed", it)
                        }
                        fetchResult(false, listOf())
                    })
        }

        private fun saveJoinRequestList(gid: Long, list: ArrayList<GroupJoinRequestInfo>) {
            val map = HashMap<String, ArrayList<GroupJoinRequestInfo>>()
            for (i in list) {
                val key = if (!TextUtils.isEmpty(i.inviter)) {
                    "${i.timestamp}${i.inviter}"
                } else {
                    i.uid
                }

                val array = map[key] ?: ArrayList()
                array.add(i)

                map[key] = array
            }

            for (v in map.values) {
                val inviter = v[0].inviter
                val joinList = v.map { it.uid }

                if (!TextUtils.isEmpty(inviter)) {

                    val exist = BcmGroupJoinManager.getJoinInfoByInviterData(accountContext, gid, inviter, v[0].uid, v[0].timestamp)
                    if (null != exist) {
                        continue
                    }
                } else if (joinList.size == 1) {
                    val exist = BcmGroupJoinManager.getJoinInfoByDetail(accountContext, gid, joinList[0], v[0].status, v[0].inviter)
                    if (exist != null) {
                        exist.comment = v[0].comment
                        exist.timestamp = v[0].timestamp
                        BcmGroupJoinManager.updateJoinRequests(accountContext, listOf(exist))
                        ALog.i(TAG, "join request exist 1, ignore $gid")
                        continue
                    }
                }

                for (j in v) {
                    saveJoinRequestName(j)
                }

                val message = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_JOIN_GROUP_REQUEST, inviter, joinList)
                val mid = GroupMessageLogic.get(accountContext).systemNotice(gid, message, false)

                for (r in v) {
                    r.updateMid(mid)
                }
            }
            BcmGroupJoinManager.saveJoinGroupInfos(accountContext, list.filter { it.mid != 0L })
        }

        private fun saveJoinRequestName(req: GroupJoinRequestInfo) {
            val recipient = Recipient.from(accountContext, req.uid, false)
            if (recipient.bcmName == null) {
                try {
                    val comment = GsonUtils.fromJson(req.comment, JoinGroupReqComment::class.java)
                    if (!TextUtils.isEmpty(comment.name)) {
                        Repository.getRecipientRepo(accountContext)?.updateProfileName(recipient, comment.name)
                    }
                } catch (e: Exception) {
                    ALog.e("saveJoinRequestName", "wrong json format ${req.gid}")
                }

            }
        }

        /**
         * Update encrypted group notice.
         *
         * @param groupId Group id will be modified
         * @param notice
         * @param timestamp Notice last update time.
         * @param result Callback after updated to server
         */
        fun updateGroupNotice(groupId: Long, notice: String, timestamp: Long, result: (succeed: Boolean, error: String) -> Unit) {

            Observable.create<String> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                        ?: throw GroupException("GroupInfo is null.")
                val tempKeyPair = BCMPrivateKeyUtils.generateKeyPair()
                val groupPublicKey = groupInfo.groupPublicKey?.publicKey32()
                if (groupPublicKey == null || groupPublicKey.isEmpty()) {
                    throw GroupException("GroupPublicKey is null.")
                }

                val updateTime = System.currentTimeMillis()
                val noticeBean = GroupInfoEntity.NoticeBean().apply {
                    content = notice
                    this.updateTime = updateTime
                }
                val noticeJson = GsonUtils.toJson(noticeBean)
                val aesKey = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(groupPublicKey, tempKeyPair.privateKey.serialize())
                val encryptedBean = BCMEncryptUtils.encryptByAES256(noticeJson.toByteArray(), aesKey)

                val profileBean = GroupProfileDecryption.EncryptedProfileBean().apply {
                    content = Base64.encodeBytes(encryptedBean)
                    key = Base64.encodeBytes((tempKeyPair.publicKey as DjbECPublicKey).publicKey)
                }
                val profileJson = GsonUtils.toJson(profileBean)
                it.onNext(Base64.encodeBytes(profileJson.toByteArray()))
                it.onComplete()
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.updateGroupNotice(accountContext, groupId, it)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        groupCache.updateNotice(groupId, notice, timestamp)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        result(true, "")
                    }, {
                        ALog.e(TAG, "updateGroupNotice", it)
                        result(false, "")
                    })
        }

        fun updateNoticeShowState(groupId: Long, noticeShowState: Boolean) {
            AmeDispatcher.io.dispatch {
                groupCache.updateNoticeShowState(groupId, noticeShowState)
            }
        }

        fun doOnLogin() {
            GroupMessageLogic.get(accountContext).doOnLogin()
        }

        private fun syncGroupMember(gid: Long, fromUid: String, fromTime: Long, sync: (isFirstPage: Boolean, allFinished: Boolean) -> Unit) {
            groupCache.setGroupMemberState(gid, GroupMemberSyncState.SYNING)
            groupMemberSync.syncGroupMember(gid, fromUid, fromTime, listOf(AmeGroupMemberInfo.OWNER, AmeGroupMemberInfo.MEMBER, AmeGroupMemberInfo.ADMIN)) { firstPage, finish, list ->
                if (firstPage) {
                    GroupMemberManager.clear(accountContext, gid)

                    val owner = groupCache.getGroupInfo(gid)?.owner
                    if (owner?.isNotEmpty() == true) {
                        val ownerMember = AmeGroupMemberInfo()
                        ownerMember.gid = gid
                        ownerMember.uid = owner
                        ownerMember.role = AmeGroupMemberInfo.OWNER
                        ownerMember.createTime = 0
                        GroupMemberManager.insertGroupMember(accountContext, ownerMember)
                    }
                }

                if (list.isNotEmpty()) {
                    GroupMemberManager.insertGroupDbMembers(accountContext, list)
                }

                if (finish) {
                    groupCache.setGroupMemberState(gid, GroupMemberSyncState.FINISH)
                    ALog.i(TAG, "syncGroupMember finish $gid")
                }

                if (firstPage || finish) {
                    sync(firstPage, finish)
                }
            }
        }

        fun checkAndSyncGroupMemberList(gid: Long, sync: (isFirstPage: Boolean, allFinished: Boolean) -> Unit) {
            AmeDispatcher.io.dispatch {
                var fromUid = ""
                var fromTime = 0L
                if (groupCache.getGroupInfo(gid)?.memberSyncState == GroupMemberSyncState.SYNING) {
                    val groupMember = GroupMemberManager.getLastMember(accountContext, gid)
                    fromUid = groupMember?.uid ?: ""
                    fromTime = groupMember?.joinTime ?: 0L
                }

                if (isGroupMemberDirty(gid) && !groupMemberSync.isSyncing(gid)) {
                    try {
                        syncGroupMember(gid, fromUid, fromTime, sync)
                    } catch (e: Exception) {
                        ALog.e(TAG, "checkAndSyncGroupMemberList", e)
                        setGroupMemberDirty(gid)
                    }
                }
            }
        }

        fun cancelSyncGroupMemberList(gid: Long) {
            if (groupMemberSync.isSyncing(gid)) {
                groupCache.setGroupMemberState(gid, GroupMemberSyncState.DIRTY)
                groupMemberSync.cancelSync(gid)
            }
        }

        fun setGroupMemberDirty(gid: Long) {
            groupCache.setGroupMemberState(gid, GroupMemberSyncState.DIRTY)
        }

        fun isGroupMemberDirty(gid: Long): Boolean {
            return groupCache.getGroupInfo(gid)?.memberSyncState == GroupMemberSyncState.DIRTY
        }

        fun updateMyGroupList(serverList: List<Long>) {
            val localList = getGroupList()
            val diffList = localList.toMutableList()
            diffList.removeAll(serverList)
            if (diffList.isNotEmpty()) {
                for (kicked in diffList) {
                    groupCache.updateRole(kicked, AmeGroupMemberInfo.VISITOR)
                    if (isCurrentModel(kicked)) {
                        getModel(kicked)?.checkSync()
                    }
                    if (getThreadDB()?.getThreadIdIfExist(GroupUtil.addressFromGid(accountContext, kicked).serialize()) ?: 0 > 0) {
                        MessageDataManager.systemNotice(accountContext, kicked, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_GROUP_ILLEGAL))
                    }
                }
            }
        }

        fun checkShareCodeStatus(gid: Long,
                                 shareCode: String,
                                 shareSig: String,
                                 result: (succeed: Boolean, status: BcmShareCodeStatus) -> Unit) {
            GroupManagerCore.checkQrCodeValid(accountContext, gid, shareSig)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        if (it) {
                            result(true, BcmShareCodeStatus.VALID_CODE)
                        } else {
                            result(true, BcmShareCodeStatus.INVALID_CODE)
                        }
                    }, {
                        result(false, BcmShareCodeStatus.NET_ERROR)
                    })
        }

        fun checkJoinGroupNeedConfirm(gid: Long,
                                      result: (succeed: Boolean, needConfirm: Boolean) -> Unit) {
            GroupManagerCore.checkJoinGroupNeedConfirm(accountContext, gid)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnError {
                        ALog.e(TAG, "checkJoinGroupNeedConfirm failed", it)
                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                        AmeDispatcher.mainThread.dispatch {
                            if (null != groupInfo && !TextUtils.isEmpty(groupInfo.infoSecret)) {
                                ALog.i(TAG, "checkJoinGroupNeedConfirm return local flag ${groupInfo.needOwnerConfirm}")
                                result(false, groupInfo.needOwnerConfirm == 1)
                            } else {
                                result(false, false)
                            }
                        }
                    }
                    .subscribe {
                        ALog.i(TAG, "checkJoinGroupNeedConfirm succeed $it")
                        AmeDispatcher.mainThread.dispatch {
                            result(true, it)
                        }
                    }
        }


        fun joinGroupByShareCode(gid: Long, shareCode: String, shareSig: String, ephemeralKey: ByteArray? = null, comment: String? = null, result: (succeed: Boolean, error: String) -> Unit) {
            Observable.create<ByteArray> {
                if (null != ephemeralKey && ephemeralKey.size != 64) {
                    throw GroupException(AppUtil.getString(com.bcm.messenger.common.R.string.common_unsupported_qr_code_description))
                }
                it.onNext(ephemeralKey ?: ByteArray(0))
                it.onComplete()
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.joinGroupByCode(accountContext, gid, shareCode, shareSig, comment
                                ?: "", it.size == 64)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                    }.flatMap {
                        if (it.isEmpty() || null == ephemeralKey) {
                            Observable.just(true)
                        } else {
                            val infoSecretPlainBytes = it.toByteArray().base64Decode().aesDecode(ephemeralKey)
                                    ?: throw GroupException(AppUtil.getString(R.string.common_error_failed))

                            val groupInfo = GroupInfo()
                            groupInfo.gid = gid
                            groupInfo.infoSecret = infoSecretPlainBytes.base64Encode().format()

                            val proof = GroupProof.encodeMemberProof(GroupProof.signMember(groupInfo, accountContext.uid))
                            val mySecretString = BCMEncryptUtils.generateMyEncryptKeyString(accountContext, infoSecretPlainBytes)
                            GroupManagerCore.addMe(accountContext, gid, mySecretString, proof)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map {
                                        true
                                    }
                        }
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .doOnError {
                        ALog.e(TAG, "joinGroupByShareCode failed", it)
                        when (ServerCodeUtil.getNetStatusCode(it)) {
                            ServerCodeUtil.CODE_LOW_VERSION -> {
                                result(false, it.message
                                        ?: AppUtil.getString(R.string.common_error_failed))
                            }
                            400 -> {
                                result(false, AppUtil.getString(R.string.chats_this_link_revoked_text))
                            }
                            else -> {
                                result(false, AppUtil.getString(R.string.common_error_failed))
                            }
                        }

                    }
                    .subscribe ({
                        ALog.i(TAG, "joinGroupByShareCode succeed")
                        result(true, "")
                    }, {})
        }

        fun updateNeedConfirm(gid: Long, needConfirm: Boolean, result: (succeed: Boolean, error: String?) -> Unit) {
            AmeDispatcher.io.dispatch {
                val confirm = if (needConfirm) {
                    1
                } else {
                    0
                }

                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)

                if (null != groupInfo) {
                    if (TextUtils.isEmpty(groupInfo.shareCodeSetting)) {
                        groupInfo.needOwnerConfirm = confirm
                        groupCache.saveGroupInfo(groupInfo)
                        updateShareSetting(gid, false) { succeed, shareCode, error ->
                            if (!succeed) {
                                AmeDispatcher.io.dispatch {
                                    val tmp = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                    if (null != tmp) {
                                        tmp.needOwnerConfirm = if (needConfirm) {
                                            0
                                        } else {
                                            1
                                        }
                                        groupCache.saveGroupInfo(tmp)
                                    }
                                }
                            }
                            result(succeed, error)
                        }
                        return@dispatch
                    }

                    val format = ByteArrayOutputStream()
                    format.write(EncryptUtils.base64Decode(groupInfo.shareCodeSetting.toByteArray()))
                    format.write(confirm.toString().toByteArray())
                    val shareConfirmSignByteArray = BCMEncryptUtils.signWithMe(accountContext, format.toByteArray())
                    val shareConfirmSign = String(EncryptUtils.base64Encode(shareConfirmSignByteArray))
                    GroupManagerCore.updateJoinConfirmSetting(accountContext, gid, confirm, shareConfirmSign)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnNext {
                                groupCache.updateNeedConfirm(gid, confirm, shareConfirmSign)
                                broadcastShareSettingRefresh(gid)
                            }
                            .observeOn(AmeDispatcher.mainScheduler)
                            .subscribe({
                                result(true, "")

                            }, {

                                ALog.e(TAG, "updateNeedConfirm", it)
                                result(false, AppUtil.getString(R.string.common_error_failed))
                            })
                } else {
                    AmeDispatcher.mainThread.dispatch {
                        result(false, AppUtil.getString(R.string.common_error_failed))
                    }
                }

            }
        }

        private fun broadcastShareSettingRefresh(gid: Long) {
            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
            if (groupInfo?.role == AmeGroupMemberInfo.OWNER) {
                groupCache.setBroadcastSharingData(gid, true)
                val message = AmeGroupMessage.GroupShareSettingRefreshContent(groupInfo.shareCode,
                        groupInfo.shareCodeSetting, groupInfo.shareCodeSettingSign, groupInfo.shareSettingAndConfirmSign, groupInfo.needOwnerConfirm)
                GroupMessageLogic.get(accountContext).messageSender.sendMessage(gid, AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_SETTING_REFRESH, message), false) {
                    ALog.i(TAG, "broadcastShareSettingRefresh succeed: $it")
                    if (it) {
                        AmeDispatcher.io.dispatch {
                            groupCache.setBroadcastSharingData(gid, false)
                        }
                    }
                }
            }
        }

        fun updateShareSetting(gid: Long, enable: Boolean, result: (succeed: Boolean, shareCode: String, error: String?) -> Unit) {

            AmeDispatcher.io.dispatch {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)

                if (null != groupInfo && !TextUtils.isEmpty(groupInfo.infoSecret)) {
                    val shareEnable = if (enable) {
                        1
                    } else {
                        0
                    }

                    var shareCode = ""
                    if (enable) {
                        shareCode = String(EncryptUtils.base64Encode(EncryptUtils.getSecretBytes(16)))
                    }
                    val shareEpoch = groupInfo.shareEpoch + 1

                    val shareSettingJson = GsonUtils.toJson(GroupShareSettingEntity(shareEnable, shareCode, shareEpoch))
                    val shareSetting = EncryptUtils.aes256EncryptAndBase64(shareSettingJson, EncryptUtils.base64Decode(groupInfo.infoSecret.toByteArray()))
                    val shareSettingSignArray = BCMEncryptUtils.signWithMe(accountContext, EncryptUtils.base64Decode(shareSetting.toByteArray()))
                    val shareSettingSign = String(EncryptUtils.base64Encode(shareSettingSignArray))

                    val format = ByteArrayOutputStream()
                    format.write(EncryptUtils.base64Decode(shareSetting.toByteArray()))
                    format.write(groupInfo.needOwnerConfirm.toString().toByteArray())
                    val shareConfirmSignByteArray = BCMEncryptUtils.signWithMe(accountContext, format.toByteArray())
                    val shareConfirmSign = String(EncryptUtils.base64Encode(shareConfirmSignByteArray))

                    val ek = BCMEncryptUtils.generate64BitKey()
                    val encInfoSecret = groupInfo.infoSecret.toByteArray().base64Decode().aesEncode(ek)!!.base64Encode().format()
                    val encEphemeralKey = ek.aesEncode(groupInfo.infoSecret.toByteArray().base64Decode())!!.base64Encode().format()


                    GroupManagerCore.updateShareCodeSetting(accountContext, gid,
                            groupInfo.needOwnerConfirm,
                            shareSetting,
                            shareSettingSign,
                            shareConfirmSign,
                            encEphemeralKey,
                            encInfoSecret, groupInfo.isNewGroup)
                            .subscribeOn(AmeDispatcher.ioScheduler)
                            .observeOn(AmeDispatcher.ioScheduler)
                            .flatMap {
                                groupCache.updateShareSetting(gid, shareEnable, shareEpoch, shareCode, shareSetting, shareSettingSign, shareConfirmSign, ek.base64Encode().format())
                                broadcastShareSettingRefresh(gid)

                                genShareLink(gid)
                                        .subscribeOn(AmeDispatcher.ioScheduler)
                            }
                            .observeOn(AmeDispatcher.mainScheduler)
                            .subscribe({
                                result(true, shareCode, "")
                            }, {
                                ALog.e(TAG, "updateShareSetting", it)
                                result(false, "", AppUtil.getString(R.string.common_error_failed))
                            })
                } else {
                    AmeDispatcher.mainThread.dispatch {
                        result(false, "", AppUtil.getString(R.string.common_error_failed))
                    }
                }

            }
        }


        fun queryTopMemberInfoList(groupId: Long, count: Long): Observable<ArrayList<AmeGroupMemberInfo>> {
            return GroupMemberCore.getGroupMemberByPage(accountContext, groupId, listOf(AmeGroupMemberInfo.OWNER, AmeGroupMemberInfo.MEMBER), "", 0, count)
                    .subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AmeDispatcher.singleScheduler)
                    .map { result ->
                        if (result.isSuccess) {
                            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                            if (null != groupInfo) {
                                val dbList = result.data.members.mapNotNull {
                                    it.toDbMember(groupId, groupInfo?.channel_key, groupInfo)
                                }

                                groupCache.saveMember(dbList)

                                return@map GroupMemberTransform.transToModelList(dbList)
                            }
                        }
                        throw GroupException(result.msg)
                    }

        }

        fun updateAutoGenGroupNameAndAvatar(gid: Long, combineName: String, chnCombineName: String, path: String?) {
            groupCache.updateAutoGenGroupNameAndAvatar(gid, combineName, chnCombineName, path)
            updateGroupFinderSource()
            AmeDispatcher.mainThread.dispatch {
                val groupInfo = groupCache.getGroupInfo(gid) ?: return@dispatch
                listenerRef.get()?.onGroupInfoChanged(groupInfo)
            }
        }

        fun syncGroupKeyList(gid: Long, versions: List<Long>): Observable<Boolean> {
            val syncList = versions.split(10).map {
                Observable.just(it)
                        .subscribeOn(AmeDispatcher.ioScheduler)
                        .observeOn(AmeDispatcher.ioScheduler)
                        .flatMap { list ->
                            GroupManagerCore.getGroupKeys(accountContext, gid, list)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        }
                        .observeOn(AmeDispatcher.ioScheduler)
                        .flatMap {
                            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                            if (groupInfo != null) {
                                Observable.just(Pair(groupInfo, it))
                            } else {
                                queryGroupInfo(gid)
                                        .map { gInfo ->
                                            Pair(gInfo, it)
                                        }
                            }
                        }.subscribeOn(AmeDispatcher.ioScheduler)
                        .observeOn(AmeDispatcher.ioScheduler)
                        .map {
                            val keys = it.second.keys
                            val groupInfo = it.first

                            if (!groupInfo.isNewGroup) {
                                throw Exception("v2 version group, key not need changed")
                            }

                            if (keys?.isNotEmpty() == true) {
                                for (i in keys) {
                                    saveGroupKey(groupInfo, i)
                                }
                            }
                            true
                        }
                        .observeOn(AmeDispatcher.ioScheduler)
            }

            return Observable.zip(syncList) {
                true
            }
        }

        @Throws(Exception::class)
        private fun saveGroupKey(groupInfo: GroupInfo, keyContent: GroupKeysContent.GroupKeyContent) {
            val gid = keyContent.gid
            if (keyContent.keyEncryptedVersion != GroupKeysContent.KEY_ENCRYPTED_VERSION) {
                ALog.e(TAG, "unknown key encrypt version $gid")
                return
            }

            when (GroupKeyMode.ofValue(keyContent.keyMode)) {
                GroupKeyMode.STRONG_MODE -> {
                    val contentKey = keyContent.strongModeKey
                            ?: throw Exception("strong key is null")

                    val key = StrongKeyExchangeParam.strongKeyContentToGroupKey(accountContext, contentKey, groupInfo.groupPublicKey)
                    if (null != key) {
                        GroupInfoDataManager.saveGroupKeyParam(accountContext, gid, keyContent.version, key.base64Encode().format())

                        val g = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                ?: throw Exception("group info is null")
                        if (g.currentKeyVersion == 0L || g.currentKeyVersion < keyContent.version) {
                            g.currentKeyVersion = keyContent.version
                            g.currentKey = key.base64Encode().format()
                            groupCache.updateKey(g.gid, keyContent.version, g.currentKey)

                            groupInfo.currentKeyVersion = keyContent.version
                            groupInfo.currentKey = g.currentKey
                        }
                        ALog.i(TAG, "saveGroupKey strong key mode")
                    } else {
                        ALog.e(TAG, "saveGroupKey strong key decode failed ${groupInfo.gid} new:${groupInfo.isNewGroup}")
                    }
                }
                GroupKeyMode.NORMAL_MODE -> {
                    val contentKey = keyContent.normalModeKey
                            ?: throw Exception("normal key is null")

                    val key = NormalKeyExchangeParam.normalKeyContentToGroupKey(contentKey, groupInfo.infoSecret.base64Decode(), groupInfo.groupPublicKey)
                    if (null != key) {
                        GroupInfoDataManager.saveGroupKeyParam(accountContext, gid, keyContent.version, key.base64Encode().format())

                        val g = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                ?: throw Exception("group info is null")
                        if (g.currentKeyVersion == 0L || g.currentKeyVersion < keyContent.version) {
                            g.currentKeyVersion = keyContent.version
                            g.currentKey = key.base64Encode().format()

                            groupInfo.currentKeyVersion = keyContent.version
                            groupInfo.currentKey = g.currentKey

                            groupCache.updateKey(g.gid, keyContent.version, g.currentKey)
                        }
                        ALog.i(TAG, "saveGroupKey normal key mode")
                    } else {
                        ALog.e(TAG, "saveGroupKey normal key decode failed")
                    }
                }
                else -> {
                    ALog.i(TAG, "saveGroupKey unknown key mode")
                }
            }
        }

        private fun refreshGroupKey(keyLostList: List<Long>) {
            groupKeyRotate.rotateGroup(keyLostList)
        }

        @SuppressLint("CheckResult")
        fun uploadGroupKeys(gid: Long, mid: Long, mode: Int) {
            ALog.i(TAG, "prepareUploadGroupKeys request start $gid")
            val newGroupKey = BCMEncryptUtils.generate64BitKey()
            GroupManagerCore.prepareUploadGroupKeys(accountContext, gid, mid, mode)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { res ->
                        if (GroupKeyMode.STRONG_MODE == GroupKeyMode.ofValue(mode)) {
                            getGroupMemberInfos(gid, res.list?.map { e -> e.uid } ?: listOf())
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map { mlist ->
                                        val members = mlist.map { it.uid }.toSet()
                                        res.getPreKeyBundleList(members)
                                    }
                        } else {
                            Observable.just(listOf())
                        }
                    }
                    .flatMap {
                        ALog.i(TAG, "uploadGroupKeys request start $gid")

                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                ?: throw java.lang.Exception("group info not found")

                        if (!groupInfo.isNewGroup) {
                            throw Exception("upload key call, but not new group")
                        }

                        when (GroupKeyMode.ofValue(mode)) {
                            GroupKeyMode.STRONG_MODE -> {
                                Observable.just(StrongKeyExchangeParam.getStrongKeysContent(accountContext, it, newGroupKey, groupInfo.groupPrivateKey))
                            }
                            GroupKeyMode.NORMAL_MODE -> {
                                NormalKeyExchangeParam.getNormalKeysContent(newGroupKey, mid, groupInfo.infoSecret.base64Decode(), groupInfo.groupPrivateKey)
                            }
                            else -> {
                                throw java.lang.Exception("unknown key mode")
                            }
                        }
                    }
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.uploadGroupKeys(accountContext, gid, mid, it, GroupKeyMode.ofValue(mode))
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        ALog.i(TAG, "uploadGroupKeys complete $gid")
                        syncGroupKeyList(gid, listOf(mid))
                    }
                    .subscribe({
                        ALog.i(TAG, "uploadGroupKeys complete and sync succeed $gid")
                    }, {
                        ALog.e(TAG, "uploadGroupKeys", it)
                        if (ServerCodeUtil.getNetStatusCode(it) == 409) {
                            syncGroupKeyList(gid, listOf(mid))
                        }
                    })
        }


        @SuppressLint("CheckResult")
        fun checkLastGroupKeyValid(gid: Long) {
            ALog.i(TAG, "checkLastGroupKeyValid refresh start $gid")

            Observable.create<GroupInfo> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                if (groupInfo?.isNewGroup == true) {
                    it.onNext(groupInfo)
                    it.onComplete()
                } else {
                    it.onError(GroupException("v2 version group"))
                }
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.getGroupLatestKeys(accountContext, listOf(gid))
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({ lastKey ->
                        val key = lastKey.keys.takeIf { !it.isNullOrEmpty() }?.first()
                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                        if (null != key && null != groupInfo) {
                            try {
                                saveGroupKey(groupInfo, key)
                            } catch (e: Exception) {
                                ALog.e(TAG, "checkLastGroupKeyValid.saveGroupKey", e)
                            }

                            ALog.i(TAG, "checkLastGroupKeyValid ${groupInfo.gid} ${key.version} ${groupInfo.currentKeyVersion}")

                            if (TextUtils.isEmpty(groupInfo.currentKey) || key.version != groupInfo.currentKeyVersion) {
                                refreshGroupKey(listOf(gid))
                            }
                        }

                        ALog.i(TAG, "checkLastGroupKeyValid finished")
                    }, {
                        if (it is GroupException) {
                            ALog.e(TAG, "checkLastGroupKeyValid ${it.err}")
                        } else {
                            ALog.e(TAG, "checkLastGroupKeyValid", it)
                        }
                    })
        }

        @SuppressLint("CheckResult")
        fun checkGroupKeyValidState(list: List<Long>) {
            ALog.i(TAG, "checkGroupKeyValidState refresh start ${list.size}")

            Observable.just(list)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        val keyLostList = GroupInfoDataManager.queryGroupInfoList(accountContext, list)
                                .filter { group ->
                                    group.isNewGroup && group.currentKey.isNullOrEmpty()
                                }.map { group -> group.gid }

                        if (keyLostList.isEmpty()) {
                            throw GroupException("checkGroupKeyValidState no need sync")
                        }

                        Observable.zip(keyLostList.split(5).map {
                            Observable.just(it)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .flatMap { list ->
                                        GroupManagerCore.getGroupLatestKeys(accountContext, list)
                                                .subscribeOn(AmeDispatcher.ioScheduler)
                                    }
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map { lastKey ->
                                        for (i in lastKey.keys ?: listOf()) {
                                            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, i.gid)
                                                    ?: continue
                                            try {
                                                saveGroupKey(groupInfo, i)
                                            } catch (e: Exception) {
                                                ALog.e(TAG, "checkGroupKeyValidState.saveGroupKey", e)
                                            }
                                        }
                                        true
                                    }
                        }) {
                            true
                        }.subscribeOn(AmeDispatcher.ioScheduler)
                    }
                    .observeOn(AmeDispatcher.ioScheduler)
                    .doOnError { }
                    .subscribe({
                        val keyLostList = GroupInfoDataManager.queryGroupInfoList(accountContext, list)
                                .filter { group ->
                                    group.isNewGroup && group.currentKey.isNullOrEmpty()
                                }.map { group -> group.gid }

                        if (keyLostList.isNotEmpty()) {
                            refreshGroupKey(keyLostList)
                        }

                        ALog.i(TAG, "checkGroupKeyValidState finished ${list.size}")
                    }, {
                        if (it is GroupException) {
                            ALog.i(TAG, "checkGroupKeyValidState ${it.err}")
                        } else {
                            ALog.e(TAG, "checkGroupKeyValidState", it)
                        }
                    })
        }

        fun updateGroupNameAndIcon(gid: Long, newName: String, newIcon: String) {
            groupCache.updateGroupAvatar(gid, newIcon)
            groupCache.updateGroupName(gid, newName)
        }

        fun uploadEncryptedNameAndNotice(groupId: Long, result: (succeed: Boolean) -> Unit) {
            Observable.create<Pair<Optional<String>, Optional<String>>> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, groupId)
                        ?: throw Exception()

                val tempKey = BCMPrivateKeyUtils.generateKeyPair()
                val groupPublicKey = groupInfo.groupPublicKey?.publicKey32()
                if (null == groupPublicKey || groupPublicKey.isEmpty()) {
                    it.onError(AssertionError("GroupPublicKey is null"))
                    return@create
                }

                val aesKey = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(groupPublicKey, tempKey.privateKey.serialize())
                val encryptedName = if (!groupInfo.name.isNullOrBlank()) {
                    val newName = Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(groupInfo.name.toByteArray(), aesKey))
                    val bean = GroupProfileDecryption.EncryptedProfileBean().apply {
                        content = newName
                        key = Base64.encodeBytes(tempKey.publicKey.serialize())
                    }
                    val beanJson = GsonUtils.toJson(bean)
                    Base64.encodeBytes(beanJson.toByteArray())
                } else {
                    null
                }

                val encryptedNotice = if (!groupInfo.notice_content.isNullOrBlank()) {
                    val newContent = Base64.encodeBytes(BCMEncryptUtils.encryptByAES256(groupInfo.notice_content.toByteArray(), aesKey))
                    val bean = GroupProfileDecryption.EncryptedProfileBean().apply {
                        content = newContent
                        key = Base64.encodeBytes(tempKey.publicKey.serialize())
                    }
                    val beanJson = GsonUtils.toJson(bean)
                    Base64.encodeBytes(beanJson.toByteArray())
                } else {
                    null
                }

                Pair(Optional.fromNullable(encryptedName), Optional.fromNullable(encryptedNotice))
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.setEncryptedGroupProfile(accountContext, groupId, it.first.orNull(), it.second.orNull())
                                .subscribeOn(AmeDispatcher.ioScheduler)
                                .observeOn(AmeDispatcher.ioScheduler)
                                .map { result ->
                                    if (result.isSuccess) {
                                        groupCache.setProfileEncrypted(groupId, true)
                                    } else {
                                        if (110005 == result.code) {
                                            throw GroupException(AppContextHolder.APP_CONTEXT.getString(R.string.chats_group_name_too_long))
                                        } else {
                                            throw GroupException(result.msg)
                                        }
                                    }
                                }
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.observeOn(AmeDispatcher.ioScheduler)
                    .subscribe({
                        ALog.i(TAG, "uploadEncryptedNameAndNotice succeed")
                        result(true)
                    }, {
                        result(false)
                    })
        }

        fun updateGroupName(gid: Long, name: String, result: (succeed: Boolean, error: String?) -> Unit) {
            Observable.create<String> {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                        ?: throw GroupException("GroupInfo is null.")

                val tempKey = BCMPrivateKeyUtils.generateKeyPair()
                val groupPublicKey = groupInfo.groupPublicKey?.publicKey32()
                if (groupPublicKey == null || groupPublicKey.isEmpty()) {
                    throw GroupException("GroupPublicKey is null or empty.")
                }

                val aesKey = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(groupPublicKey, tempKey.privateKey.serialize())
                val encryptedName = BCMEncryptUtils.encryptByAES256(name.toByteArray(), aesKey)
                val bean = GroupProfileDecryption.EncryptedProfileBean().apply {
                    content = Base64.encodeBytes(encryptedName)
                    key = Base64.encodeBytes((tempKey.publicKey as DjbECPublicKey).publicKey)
                }
                val beanJson = GsonUtils.toJson(bean)
                val encodedJson = Base64.encodeBytes(beanJson.toByteArray())
                it.onNext(encodedJson)
                it.onComplete()
            }.subscribeOn(AmeDispatcher.ioScheduler)
                    .flatMap {
                        GroupManagerCore.updateGroup(accountContext, gid, it, null)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }.flatMap {
                        if (it.isSuccess) {
                            groupCache.updateGroupName(gid, name)
                            genShareLink(gid)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        } else {
                            if (110005 == it.code) {
                                throw GroupException(AppContextHolder.APP_CONTEXT.getString(R.string.chats_group_name_too_long))
                            } else {
                                throw GroupException(it.msg)
                            }
                        }
                    }
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        result(true, "")
                    }, {
                        ALog.e(TAG, "updateGroupAvatar error", it)
                        result(false, GroupException.error(it, AppUtil.getString(R.string.common_error_failed)))
                    })
        }
    }
}