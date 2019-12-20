package com.bcm.messenger.chats.util

/**
 * Event for history message files
 *
 * Created by wjh on 2018/12/29
 */
class HistoryGroupAttachmentProgressEvent(val url: String?, action: Int, progress: Float, total: Long): GroupAttachmentProgressEvent(0, 0, action, progress, total)