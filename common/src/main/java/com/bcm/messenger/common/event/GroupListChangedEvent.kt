package com.bcm.messenger.common.event

/**
 * Created by bcm.social.01 on 2018/11/27.
 */
/**
 * gid 变更的群id, -1 所有群信息的更新的
 * leave true 我离开了群, false 我还在群里
 */
class GroupListChangedEvent(val gid:Long, val leave: Boolean)