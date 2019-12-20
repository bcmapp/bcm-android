package com.bcm.messenger.common.core.corebean

/**
 * Created by bcm.social.01 on 2019/3/26.
 */
enum class GroupMemberSyncState {
    FINISH, //同步完成
    SYNING, //正在同步
    DIRTY,  //列表需要同步
}