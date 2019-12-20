package com.bcm.messenger.me.fingerprint

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.utility.AppContextHolder
import java.util.concurrent.Executor

/**
 * Created by Kin on 2019/8/1
 */
object BiometricVerifyUtil {
    val executor = MainExecutor()

    var currentConfig: BiometricConfig? = null
        private set

    fun canUseBiometricFeature() = BiometricManager.from(AppContextHolder.APP_CONTEXT).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticateFinish() {
        currentConfig = null
    }

    class BiometricConfig {
        var title = ""
        var subtitle = ""
        var description = ""
        var cancelTitle = ""
        var callback: ((success: Boolean, hasBiometricDevice: Boolean, isLocked: Boolean) -> Unit)? = null
    }

    class BiometricBuilder(private val fragmentActivity: FragmentActivity) {
        private val config = BiometricConfig()

        fun setTitle(title: String): BiometricBuilder {
            config.title = title
            return this
        }

        fun setSubtitle(subtitle: String): BiometricBuilder {
            config.subtitle = subtitle
            return this
        }

        fun setDescription(description: String): BiometricBuilder {
            config.description = description
            return this
        }

        fun setCancelTitle(cancelTitle: String): BiometricBuilder {
            config.cancelTitle = cancelTitle
            return this
        }

        fun setCallback(callback: (success: Boolean, hasBiometricDevice: Boolean, isLocked: Boolean) -> Unit): BiometricBuilder {
            config.callback = callback
            return this
        }

        fun build() {
            currentConfig = config
            fragmentActivity.startActivity(Intent(fragmentActivity, BiometricVerifyActivity::class.java))
        }
    }

    class MainExecutor : Executor {
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable?) {
            mainHandler.post(command)
        }
    }
}