package com.bcm.messenger.common.crypto

import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import org.whispersystems.curve25519.Curve25519

object GroupProfileDecryption {
    private const val TAG = "GroupProfileDecryption"

    @JvmStatic
    fun decryptProfile(encryptedProfile: String, groupPrivateKey: ByteArray): String? {
        try {
            val bean = GsonUtils.fromJson(String(encryptedProfile.base64Decode()), EncryptedProfileBean::class.java)
            if (bean.isValid) {
                val decodeContent = bean.content.base64Decode()
                val publicKey = bean.key.base64Decode()

                val realPubKey = if (publicKey.size == 33) {
                    val tempKey = ByteArray(32)
                    System.arraycopy(publicKey, 1, tempKey, 0, 32)
                    tempKey
                } else {
                    publicKey
                }

                val aesKey = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(realPubKey, groupPrivateKey)
                val decryptName = BCMEncryptUtils.decryptByAES256(decodeContent, aesKey)
                return String(decryptName)
            }
        } catch (tr: Throwable) {
            ALog.e(TAG, "Decrypt name failed", tr)
        }
        return null
    }

    class EncryptedProfileBean : NotGuard {
        var content = ""
        var key = ""
        var version = 1

        val isValid
            get() = content.isNotBlank() && key.isNotBlank()
    }
}