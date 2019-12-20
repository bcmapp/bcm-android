package com.bcm.messenger.chats.util

object TTSUtil {

    private fun isChinese(c: Char): Boolean {
        return (c >= 0x4E00.toChar()) and (c <= 0x9FA5.toChar())
    }

    fun isChinese(str: String): Boolean {
        for (c in str) {
            if (isChinese(c))
                return true
        }
        return false
    }
}