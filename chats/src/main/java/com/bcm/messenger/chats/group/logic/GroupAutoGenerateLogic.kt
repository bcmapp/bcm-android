package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.GroupNameOrAvatarChanged
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.GroupMemberManager
import com.bcm.messenger.common.grouprepository.room.dao.GroupAvatarParamsDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupAvatarParams
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.CombineBitmapUtil
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.min

class GroupAutoGenerateLogic(private val accountContext: AccountContext) {
    companion object {
        const val TAG = "GroupAutoGenerateLogic"
    }

    private val generatingSet = HashSet<Long>()

    @SuppressLint("CheckResult")
    fun autoGenAvatarOrName(gid: Long) {
        Observable.create<Void> {
            try {
                val params = paramsDao()?.queryAvatarParams(gid)
                val gInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid) ?: return@create

                if (gInfo.name.isNotEmpty() && gInfo.iconUrl.isNotEmpty()) {
                    return@create
                }

                if (gInfo.role == AmeGroupMemberInfo.VISITOR) {
                    return@create
                }

                val memberInfoList = if (null != params) {
                    val list = params.toUserList()
                    val mList = GroupMemberManager.queryGroupMemberList(accountContext, gid, list).filter { member -> member.role != AmeGroupMemberInfo.VISITOR }.toMutableList()
                    if (mList.size < 4) {
                        val existList = mList.map { m -> m.uid }
                        val dbTop4List = GroupMemberManager.queryTopNGroupMember(accountContext, gid, 4).filter { m -> !existList.contains(m.uid) }
                        mList.addAll(dbTop4List.subList(0, min(dbTop4List.size, 4 - mList.size)))
                        if (mList.isEmpty()) {
                            ACLog.i(accountContext, TAG, "$gid member list is empty")
                            return@create
                        }
                    }

                    if (!isHashChanged(accountContext, mList, params)) {
                        ACLog.i(accountContext, TAG, "$gid hash state matched 2")
                        if (isAvatarAndNameReady(gInfo)) {
                            return@create
                        }
                        ACLog.i(accountContext, TAG, "$gid hash state matched but info not ready")
                    }
                    mList.map { m -> m.uid }.toMutableList()
                } else {
                    ACLog.i(accountContext, TAG, "$gid params is null")
                    mutableListOf<String>()
                }

                if (generatingSet.contains(gid)) {
                    return@create
                }
                generatingSet.add(gid)

                ACLog.i(accountContext, TAG, "refreshing group avatar and name $gid")
                var combineName = ""
                var chnCombineName = ""
                var newParams: GroupAvatarParams? = null

                val queryMember = if (memberInfoList.isEmpty()) {
                    GroupLogic.get(accountContext).queryTopMemberInfoList(gid, 4)
                } else {
                    GroupLogic.get(accountContext).getGroupMemberInfos(gid, memberInfoList)
                }

                queryMember.subscribeOn(AmeDispatcher.singleScheduler)
                        .observeOn(AmeDispatcher.singleScheduler)
                        .map { list ->
                            combineName = combineGroupName(list, 0)
                            chnCombineName = combineGroupName(list, 1)

                            ACLog.i(accountContext, TAG, "name:$combineName cnName:$chnCombineName")
                            newParams = memberList2Params(accountContext, gid, list)
                            if (gInfo.iconUrl.isEmpty()) {
                                list.map { m ->
                                    val recipient = Recipient.from(accountContext, m.uid, false)
                                    val name = BcmGroupNameUtil.getGroupMemberName(recipient, m)
                                    CombineBitmapUtil.RecipientBitmapUnit(recipient, name)
                                }
                            } else {
                                if (null != newParams) {
                                    paramsDao()?.saveAvatarParams(listOf(newParams!!))
                                }
                                throw Exception("not need update group avatar")
                            }

                        }.flatMap {
                            CombineBitmapUtil.combineBitmap(it, 160, 160)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        }.observeOn(AmeDispatcher.ioScheduler)
                        .map {
                            val oldPath = gInfo.spliceAvatar
                            val path = BcmFileUtils.saveBitmap2File(it, "group_avatar_${gid}_${System.currentTimeMillis()}.jpg", AmeFileUploader.get(accountContext).TEMP_DIRECTORY)
                            gInfo.spliceName = combineName
                            gInfo.chnSpliceName = chnCombineName
                            gInfo.spliceAvatar = path
                            GroupLogic.get(accountContext).updateAutoGenGroupNameAndAvatar(gid, combineName, chnCombineName, path)

                            if (TextUtils.isEmpty(oldPath)) {
                                BcmFileUtils.delete(accountContext, oldPath)
                            }
                            path ?: throw Exception("bitmap save failed")
                        }.observeOn(AmeDispatcher.singleScheduler)
                        .doOnError {
                            ACLog.e(accountContext, TAG, "autoGenAvatarOrName", it)

                            generatingSet.remove(gid)

                            GroupLogic.get(accountContext).updateAutoGenGroupNameAndAvatar(gid, combineName, chnCombineName, "")

                            val newInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                                    ?: return@doOnError

                            if (newInfo.spliceName == gInfo.spliceName && newInfo.iconUrl == gInfo.iconUrl) {
                                return@doOnError
                            }

                            if (isAvatarAndNameReady(newInfo)) {
                                if (null != newParams) {
                                    paramsDao()?.saveAvatarParams(listOf(newParams!!))
                                }
                            }

                            AmeDispatcher.mainThread.dispatch {
                                ACLog.i(accountContext, TAG, "post name:${groupName(newInfo)} ")
                                EventBus.getDefault().post(GroupNameOrAvatarChanged(gid, groupName(newInfo), groupAvatar(newInfo)))
                            }
                        }.subscribe {
                            if (null != newParams) {
                                paramsDao()?.saveAvatarParams(listOf(newParams!!))
                            }
                            generatingSet.remove(gid)
                            AmeDispatcher.mainThread.dispatch {
                                ACLog.i(accountContext, TAG, "post name:${groupName(gInfo)} ")
                                EventBus.getDefault().post(GroupNameOrAvatarChanged(gid, groupName(gInfo), groupAvatar(gInfo)))
                            }
                        }
            } finally {
                it.onComplete()
            }

        }.subscribeOn(AmeDispatcher.singleScheduler)
                .doOnError { }
                .subscribe { }
    }

    private fun memberList2Params(accountContext: AccountContext, gid: Long, list: List<AmeGroupMemberInfo>): GroupAvatarParams {
        val params = GroupAvatarParams()
        params.gid = gid

        if (list.isNotEmpty()) {
            params.uid1 = list[0].uid
            params.user1Hash = hashOfUser(accountContext, list[0])
        }

        if (list.size > 1) {
            params.uid2 = list[1].uid
            params.user2Hash = hashOfUser(accountContext, list[1])
        }

        if (list.size > 2) {
            params.uid3 = list[2].uid
            params.user3Hash = hashOfUser(accountContext, list[2])
        }

        if (list.size > 3) {
            params.uid4 = list[3].uid
            params.user4Hash = hashOfUser(accountContext, list[3])
        }
        return params
    }

    private fun isHashChanged(accountContext: AccountContext, memberInfoList: List<AmeGroupMemberInfo>, params: GroupAvatarParams): Boolean {
        if (memberInfoList.isEmpty()) {
            return false
        }

        if (memberInfoList.size != params.toUserList().size) {
            return true
        }

        if (!isHashInParams(hashOfUser(accountContext, memberInfoList[0]), params)) {
            return true
        }

        if (memberInfoList.size == 1) {
            return false
        }

        if (!isHashInParams(hashOfUser(accountContext, memberInfoList[1]), params)) {
            return true
        }

        if (memberInfoList.size == 2) {
            return false
        }

        if (!isHashInParams(hashOfUser(accountContext, memberInfoList[2]), params)) {
            return true
        }

        if (memberInfoList.size == 3) {
            return false
        }

        if (!isHashInParams(hashOfUser(accountContext, memberInfoList[3]), params)) {
            return true
        }

        return false
    }

    private fun isHashInParams(hash: String, params: GroupAvatarParams): Boolean {
        if (params.user1Hash == hash) {
            return true
        }

        if (params.user2Hash == hash) {
            return true
        }

        if (params.user3Hash == hash) {
            return true
        }

        if (params.user4Hash == hash) {
            return true
        }
        return false
    }

    private fun hashOfUser(accountContext: AccountContext, memberInfo: AmeGroupMemberInfo): String {
        val recipient = Recipient.from(accountContext, memberInfo.uid, true)
        val name = BcmGroupNameUtil.getGroupMemberName(recipient, memberInfo)
        val avatar = recipient.avatar
        return EncryptUtils.encryptSHA1ToString("$name$avatar")
    }

    // language: 0-ENG, 1-CHN
    private fun combineGroupName(memberList: List<AmeGroupMemberInfo>, language: Int): String {
        ACLog.i(accountContext, TAG, "List count = ${memberList.size}")
        var spliceName = ""
        var index = 0
        for (member in memberList) {
            val uid = member.uid.toString()
            if (uid.isNotBlank() && uid != accountContext.uid) {
                val recipient = Recipient.from(accountContext, member.uid, true)
                val name = BcmGroupNameUtil.getGroupMemberName(recipient, member)
                spliceName += InputLengthFilter.filterSpliceName(name, 10)
                spliceName += if (language == 1) "、" else ", "
                index++
            }
            if (index == 4) break
        }

        return if (spliceName.isEmpty()) {
            getString(R.string.chats_group_setting_default_name)
        } else {
            if (language == 1) {
                spliceName.substring(0, spliceName.length - 1)
            } else {
                spliceName.substring(0, spliceName.length - 2)
            }
        }
    }

    private fun groupName(gInfo: GroupInfo): String {
        return if (gInfo.name.isEmpty()) {
            if (getSelectedLocale(AppContextHolder.APP_CONTEXT).language == Locale.CHINESE.language) {
                gInfo.chnSpliceName ?: getString(R.string.chats_group_setting_default_name)
            } else {
                gInfo.spliceName ?: getString(R.string.chats_group_setting_default_name)
            }
        } else {
            gInfo.name
        }
    }

    private fun groupAvatar(gInfo: GroupInfo): String {
        return if (gInfo.iconUrl.isEmpty()) {
            gInfo.spliceAvatar ?: ""
        } else {
            gInfo.iconUrl
        }
    }

    private fun isAvatarAndNameReady(gInfo: GroupInfo): Boolean {
        return !TextUtils.isEmpty(groupName(gInfo)) && !TextUtils.isEmpty(groupAvatar(gInfo))
    }

    private fun paramsDao(): GroupAvatarParamsDao? {
        return Repository.getGroupAvatarParamsRepo(accountContext)
    }
}