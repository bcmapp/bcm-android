package com.bcm.messenger.common.event

/**
 * 
 * Created by wjh on 2019/7/29
 */
data class MessageDeletedEvent(val threadId: Long, val messageIndexList: List<Long>) {
}