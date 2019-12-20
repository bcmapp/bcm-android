package com.bcm.messenger.common.provider.bean

/**
 * Created by bcm.social.01 on 2018/11/21.
 * 会话的占用的存储容量 单位byte
 */
class ConversationStorage(var videoSize:Long, var imageSize:Long, var fileSize:Long) {
    companion object {
        //storage type define
        const val TYPE_IMAGE = 0x1
        const val TYPE_VIDEO = 0x2
        const val TYPE_FILE = 0x4
        const val TYPE_UN_SUPPORT = 0x800000
        const val TYPE_ALL = 0x7

        fun testFlag(type:Int, flag:Int): Boolean{
            return (flag.and(type) == flag)
        }
    }

    fun append(size:ConversationStorage){
        this.fileSize += size.fileSize
        this.imageSize += size.imageSize
        this.videoSize += size.videoSize
    }

    fun storageUsed():Long{
        return videoSize + imageSize + fileSize
    }
}