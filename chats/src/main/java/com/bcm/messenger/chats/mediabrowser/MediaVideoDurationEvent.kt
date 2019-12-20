package com.bcm.messenger.chats.mediabrowser

import com.bcm.messenger.common.attachments.AttachmentId

/**
 * 媒体视频播放时长广播事件
 * Created by wjh on 2018/10/18
 */
class MediaVideoDurationEvent(val attachmentId: AttachmentId, val duration: Long) {
}