package com.bcm.messenger.chats.provider

import android.annotation.SuppressLint
import android.content.Context
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageReceiver
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.provider.accountmodule.IGroupModule
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by zjl on 2018/3/14.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_GROUP_BASE)
class GroupModuleImp : IGroupModule {
    private val groupMessageReceiver = GroupMessageReceiver()

    private val TAG = "GroupProviderImp"

    private lateinit var accountContext: AccountContext
    override val context: AccountContext
        get() = accountContext

    override fun setContext(context: AccountContext) {
        this.accountContext = context
    }

    override fun initModule() {
        GroupLogic.get(accountContext).init()
        AmeModuleCenter.serverDispatcher().addListener(groupMessageReceiver)
    }

    override fun uninitModule() {
        GroupLogic.get(accountContext).unInit()
        AmeModuleCenter.serverDispatcher().removeListener(groupMessageReceiver)

        GroupLogic.remove(accountContext)
    }

    override fun doGroupJoin(context: Context, gid: Long, name: String?, icon: String?, code: String, signature: String, timestamp: Long, eKey:ByteArray?,callback: ((success: Boolean) -> Unit)?) {
        ALog.i(TAG, "doGroupJoin gid: $gid")
        AmeAppLifecycle.showLoading()
        val groupShareContent: AmeGroupMessage.GroupShareContent = AmeGroupMessage.GroupShareContent(gid, name, icon, code, signature, eKey?.base64Encode()?.format(), timestamp, null)
        GroupLogic.get(accountContext).queryGroupInfo(gid) { ameGroupInfo, _, _ ->
            ALog.d(TAG, "doGroupJoin queryGroupInfo is null: ${ameGroupInfo == null}, role: ${ameGroupInfo?.role ?: 0}")
            if (ameGroupInfo != null && ameGroupInfo.role != AmeGroupMemberInfo.VISITOR) {
                AmeAppLifecycle.hideLoading()
                AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                        HomeTopEvent.ConversationEvent.fromGroupConversation(null, gid)))
                callback?.invoke(true)
            }else {
                GroupLogic.get(accountContext).checkJoinGroupNeedConfirm(groupShareContent.groupId) {succeed, needConfirm ->
                    ALog.d(TAG, "doGroupJoin checkJoinGroupNeedConfirm success: $succeed, needConfirm: $needConfirm")
                    if (!succeed || needConfirm) {
                        AmeAppLifecycle.hideLoading()
                        BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_JOIN_REQUEST)
                                .putString(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT, groupShareContent.toString()).navigation(context)
                        callback?.invoke(true)
                    }else {
                        GroupLogic.get(accountContext).joinGroupByShareCode(groupShareContent.groupId, groupShareContent.shareCode, groupShareContent.shareSignature, eKey) {succeed, error ->
                            ALog.d(TAG, "doGroupJoin success: $succeed, error: $error")
                            AmeAppLifecycle.hideLoading()
                            if (succeed) {
                                // After sending the grouping request successfully without the grouping review, you need to query the group info, and you can jump if it is not empty
                                GroupLogic.get(accountContext).queryGroupInfo(groupShareContent.groupId) { ameGroupInfo, _, _ ->
                                    ALog.d(TAG, "after joinGroupByShareCode queryGroupInfo is null: ${ameGroupInfo == null}, role: ${ameGroupInfo?.role ?: 0}")
                                    if (ameGroupInfo != null && ameGroupInfo.role != AmeGroupMemberInfo.VISITOR) {
                                        AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                                                HomeTopEvent.ConversationEvent.fromGroupConversation(null, gid)))

                                    }
                                    callback?.invoke(true)
                                }

                            }else {
                                var description = context.getString(R.string.chats_group_share_join_action_fail)
                                if (!error.isNullOrEmpty()) {
                                    description += "\n" + error
                                }
                                AmeAppLifecycle.failure(description, true) {
                                    callback?.invoke(false)
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    override fun getMember(groupId: Long, uid: String): AmeGroupMemberInfo? {
        return GroupLogic.get(accountContext).getModel(groupId)?.getGroupMember(uid)
    }

    @SuppressLint("CheckResult")
    override fun getMembers(groupId: Long, uidList: List<String>, result: (List<AmeGroupMemberInfo>) -> Unit) {
        GroupLogic.get(accountContext).getGroupMemberInfos(groupId, uidList)
                .observeOn(AmeDispatcher.mainScheduler)
                .doOnError {
                    result(listOf())
                }
                .subscribe {
                    result(it)
                }
    }

    override fun getMembersFromCache(groupId: Long): List<String> {
        val groupModel = GroupLogic.get(accountContext).getModel(groupId)?:return listOf()
        return groupModel.getGroupMemberList().map { it.uid.serialize() }
    }

    override fun queryMember(groupId: Long, uid:String, result: (memberInfo: AmeGroupMemberInfo?) -> Unit) {
        GroupLogic.get(accountContext).getGroupMemberInfo(groupId, uid) {
            member, _ ->
            AmeDispatcher.mainThread.dispatch {
                result(member)
            }
        }
    }

    override fun getJoinedList(): List<AmeGroupInfo> {
        return GroupLogic.get(accountContext).getGroupInfoList()
    }

    override fun getJoinedListBySort(): List<AmeGroupInfo> {
        return GroupLogic.get(accountContext).getGroupFinder().getSourceList()
    }

    override fun getGroupInfo(groupId: Long): AmeGroupInfo? {
        return GroupLogic.get(accountContext).getGroupInfo(groupId)
    }

    override fun queryGroupInfo(groupId: Long, result: (groupInfo: AmeGroupInfo?) -> Unit) {
        return GroupLogic.get(accountContext).queryGroupInfo(groupId) { ameGroup, _, _ ->
            result(ameGroup)
        }
    }

    override fun doOnLogin() {
        GroupLogic.get(accountContext).doOnLogin()
    }

    @SuppressLint("CheckResult")
    override fun getAvatarMemberList(groupId: Long, result: (gid: Long, list: List<AmeGroupMemberInfo>) -> Unit)  {
        GroupLogic.get(accountContext).queryTopMemberInfoList(groupId, 4)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {

                }
                .subscribe {
                    result(groupId, it)
                }
    }

    override fun refreshGroupNameAndAvatar(gid: Long) {
        GroupLogic.get(accountContext).refreshGroupAvatar(gid)
    }
}