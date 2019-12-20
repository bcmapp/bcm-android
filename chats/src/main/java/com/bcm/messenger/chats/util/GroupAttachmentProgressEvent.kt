package com.bcm.messenger.chats.util


/**
 * Event for group file download progress
 * Created by wjh on 2018/11/22
 *
 * @param gid Maybe null if message is forwarded from private chat
 */
open class GroupAttachmentProgressEvent(val gid: Long?, val indexId: Long?, val action: Int, val progress: Float, val total: Long) {

    companion object {
        val ACTION_THUMBNAIL_DOWNLOADING = 1
        val ACTION_ATTACHMENT_DOWNLOADING = 2
        val ACTION_ATTACHMENT_UPLOADING = 3
    }
}