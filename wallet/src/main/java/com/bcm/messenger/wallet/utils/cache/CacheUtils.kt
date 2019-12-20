package com.bcm.messenger.wallet.utils.cache

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 10/03/2018 17:22
 * @description
 */
object CacheUtils {

    private val M_SEPARATOR = ' '

    internal fun isDue(str: String): Boolean {
        return isDue(str.toByteArray())
    }

    internal fun isDue(data: ByteArray): Boolean {
        val strs = getDateInfoFromDate(data)
        if (strs != null && strs.size == 2) {
            var saveTimeStr = strs[0]
            while (saveTimeStr.startsWith("0")) {
                saveTimeStr = saveTimeStr
                        .substring(1, saveTimeStr.length)
            }
            val saveTime = java.lang.Long.parseLong(saveTimeStr)
            val deleteAfter = java.lang.Long.parseLong(strs[1])
            if (System.currentTimeMillis() > saveTime + deleteAfter * 1000) {
                return true
            }
        }
        return false
    }

    internal fun newStringWithDateInfo(second: Int, strInfo: String): String {
        return createDateInfo(second) + strInfo
    }

    internal fun newByteArrayWithDateInfo(second: Int, data2: ByteArray): ByteArray {
        val data1 = createDateInfo(second).toByteArray()
        val retdata = ByteArray(data1.size + data2.size)
        System.arraycopy(data1, 0, retdata, 0, data1.size)
        System.arraycopy(data2, 0, retdata, data1.size, data2.size)
        return retdata
    }

    internal fun clearDateInfo(strInfo: String?): String? {
        var tempInfo = strInfo
        if (tempInfo != null && hasDateInfo(tempInfo.toByteArray())) {
            tempInfo = tempInfo.substring(tempInfo.indexOf(tempInfo, M_SEPARATOR.toInt(), false) + 1,
                    tempInfo.length)
        }
        return tempInfo
    }

    internal fun clearDateInfo(data: ByteArray): ByteArray {
        return if (hasDateInfo(data)) {
            copyOfRange(data, indexOf(data, M_SEPARATOR) + 1,
                    data.size)
        } else data
    }

    private fun hasDateInfo(data: ByteArray?): Boolean {
        return (data != null && data.size > 15 && data[13] == '-'.toByte()
                && indexOf(data, M_SEPARATOR) > 14)
    }

    private fun getDateInfoFromDate(data: ByteArray): Array<String>? {
        if (hasDateInfo(data)) {
            val saveDate = String(copyOfRange(data, 0, 13))
            val deleteAfter = String(copyOfRange(data, 14,
                    indexOf(data, M_SEPARATOR)))
            return arrayOf(saveDate, deleteAfter)
        }
        return null
    }

    private fun indexOf(data: ByteArray, c: Char): Int {
        for (i in data.indices) {
            if (data[i] == c.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun copyOfRange(original: ByteArray, from: Int, to: Int): ByteArray {
        val newLength = to - from
        if (newLength < 0) {
            throw IllegalArgumentException(from.toString() + " > " + to)
        }
        val copy = ByteArray(newLength)
        System.arraycopy(original, from, copy, 0,
                Math.min(original.size - from, newLength))
        return copy
    }

    private fun createDateInfo(second: Int): String {
        val currentTime = StringBuilder(System.currentTimeMillis().toString() + "")
        while (currentTime.length < 13) {
            currentTime.insert(0, "0")
        }
        return currentTime.toString() + "-" + second + M_SEPARATOR
    }
}