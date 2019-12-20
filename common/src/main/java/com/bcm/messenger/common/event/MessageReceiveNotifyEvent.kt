package com.bcm.messenger.common.event

/**
 * 私聊收到消息通知事件
 * Created by wjh on 2019-08-22
 */
class MessageReceiveNotifyEvent(val source: String, val threadId: Long) {
}