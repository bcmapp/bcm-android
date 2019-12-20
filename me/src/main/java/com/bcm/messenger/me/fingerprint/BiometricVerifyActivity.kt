package com.bcm.messenger.me.fingerprint

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import com.bcm.messenger.utility.foreground.AppForeground
import java.lang.ref.WeakReference
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/**
 * This activity is set SingleTask to prevent having multiple instance.
 * This activity is certain leak because Android Biometric API has an unfix bug
 *
 * @see <a href="https://issuetracker.google.com/issues/37109416">Bug issue</a>
 *
 * Created by Kin on 2019/8/1
 */
class BiometricVerifyActivity : AppCompatActivity(), AppForeground.IForegroundEvent {
    private val KEY_STORE_PROVIDER = "AndroidKeyStore"
    private val KEY_STORE_ALIAS = "com.bcm.messenger.fingerprint"

    private var weakConfig: WeakReference<BiometricVerifyUtil.BiometricConfig>? = null

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val weakCfg = weakConfig?.get()
            if (weakCfg != null) {
                weakCfg.callback?.invoke(false, errorCode != BiometricPrompt.ERROR_HW_NOT_PRESENT, errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
                BiometricVerifyUtil.authenticateFinish()
            }
            finish()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val weakCfg = weakConfig?.get()
            if (weakCfg != null) {
                weakCfg.callback?.invoke(true, true, false)
                BiometricVerifyUtil.authenticateFinish()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppForeground.listener.addListener(this)

        startAuthenticate()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        startAuthenticate()
    }

    @SuppressLint("NewApi")
    private fun startAuthenticate() {
        val config = BiometricVerifyUtil.currentConfig
        if (config == null) {
            finish()
            return
        }

        weakConfig = WeakReference(config)

        val prompt = BiometricPrompt(this, BiometricVerifyUtil.executor, authenticationCallback)

        try {
            val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER)
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_PROVIDER)
            keyStore.load(null)
            val builder = KeyGenParameterSpec.Builder(KEY_STORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }
            keyGenerator.init(builder.build())
            val key = keyGenerator.generateKey()
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle(config.title)
                    .setSubtitle(config.subtitle)
                    .setDescription(config.description)
                    .setNegativeButtonText(config.cancelTitle)
                    .build(), BiometricPrompt.CryptoObject(cipher))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        if (!isForeground) {
            finish()
        }
    }
}