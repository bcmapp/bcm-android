package com.bcm.messenger.adhoc.util

import android.util.Base64
import com.bcm.messenger.adhoc.logic.AdHocChannel
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.EncryptUtils
import org.whispersystems.signalservice.internal.util.Util
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.min

object AdHocUtil {

    private val TAG = "AdHocUtil"

    fun toCid(channelName: String, passwd: String): String {
        return String(EncryptUtils.base64Encode(EncryptUtils.sha256hash160("$channelName$passwd".toByteArray())))
    }

    fun officialCid(): String {
        return toCid(AdHocChannel.OFFICIAL_CHANNEL, AdHocChannel.OFFICIAL_PWD)
    }

    fun officialSessionId(): String {
       return "/AqyuDtce+KsOcv1Nuyfo82TKkBaszDh8BV0AdVozYA=\n"
    }

    fun digest(file: File): String {
        var fin: FileInputStream? = null
        try {
            val digestInstance = MessageDigest.getInstance("SHA256")
            fin = FileInputStream(file)
            var remainingData = Util.toIntExact(file.length())
            val buffer = ByteArray(4096)

            while (remainingData > 0) {
                val read = fin.read(buffer, 0, min(buffer.size, remainingData))
                digestInstance.update(buffer, 0, read)
                remainingData -= read
            }

            return String(Base64.encode(digestInstance.digest(), Base64.DEFAULT))
        } catch (e:Throwable) {
            ALog.e(TAG, "digest", e)
        } finally {
            try {
                fin?.close()
            } catch (e:Throwable) { }
        }

        return ""
    }
}