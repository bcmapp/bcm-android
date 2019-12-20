package com.bcm.messenger.login.logic

import android.os.Environment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.bean.KeyBoxAccountItem
import com.bcm.messenger.login.bean.LoginProfile
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Created by bcm.social.01 on 2018/9/6.
 */
class AmeAccountHistory {

    companion object {
        private const val TAG = "AmeAccountHistory"
        private const val AME_LAST_LOGIN_UID: String = "AME_LAST_LOGIN_UID"
        private const val AME_ACCOUNT_LIST: String = "AME_ACCOUNT_LIST"
        private const val AME_LAST_BACKUP_UID: String = "AME_LAST_BACKUP_UID"
        private const val AME_CURRENT_LOGIN: String = "AME_CURRENT_LOGIN"

        const val LIMIT_SIZE = 50
    }

    private var accountMap: HashMap<String, AmeAccountData> = HashMap()

    private var mCurAccount: AmeAccountData? = null
    private var mLastAccount: AmeAccountData? = null

    fun init() {
        ALog.i(TAG, "init")
        fun fixDataComplement(): Boolean {
            var needUpdate = false
            val context = AppContextHolder.APP_CONTEXT
            val lastVersion = TextSecurePreferences.getIntegerPreference(context, TextSecurePreferences.ACCOUNT_DATA_VERSION, 0)
            if (lastVersion != AmeAccountData.V4) {

                ALog.i(TAG, "fixDataComplement")
                synchronized(this) {
                    mCurAccount?.let {
                        it.registrationId = TextSecurePreferences.getLocalRegistrationId(context)
                        it.gcmToken = TextSecurePreferences.getGcmRegistrationId(context)
                        it.gcmTokenLastSetTime = TextSecurePreferences.getGcmRegistrationIdLastSetTime(context)
                        it.gcmDisabled = TextSecurePreferences.isGcmDisabled(context)
                        it.pushRegistered = TextSecurePreferences.isPushRegistered(context)
                        it.signalPassword = TextSecurePreferences.getPushServerPassword(context) ?: ""
                        it.signalingKey = TextSecurePreferences.getSignalingKey(context) ?: ""
                        it.signedPreKeyRegistered = TextSecurePreferences.isSignedPreKeyRegistered(context)
                        it.signedPreKeyFailureCount = TextSecurePreferences.getSignedPreKeyFailureCount(context)
                        it.signedPreKeyRotationTime = TextSecurePreferences.getSignedPreKeyRotationTime(context)
                        it.lastAppVersion = AppUtil.getVersionCode(context).toLong()
                        needUpdate = true
                    }
                }

            }else {
                synchronized(this) {
                    mCurAccount?.let {
                        val nowVersion = AppUtil.getVersionCode(context).toLong()
                        if (it.lastAppVersion != nowVersion) {
                            ALog.i(TAG, "fixDataComplement app version changed, reset gcmToken")

                            it.gcmToken = null
                            it.lastAppVersion = nowVersion
                            needUpdate = true
                        }
                    }
                }
            }

            if (needUpdate) {
                TextSecurePreferences.setIntegerPrefrence(context, TextSecurePreferences.ACCOUNT_DATA_VERSION, AmeAccountData.V4)
                saveAccountHistory()
            }
            return needUpdate
        }

        fun loadAccountFromOldVersion(accountMap: MutableMap<String, AmeAccountData>): Boolean {

            var needUpdate = false
            val v2List = SuperPreferences.getAccountsProfileIntoSet(AppContextHolder.APP_CONTEXT)
            if (v2List.isEmpty()) {
                val v1List = SuperPreferences.getAccountsProfileIntoSetV1(AppContextHolder.APP_CONTEXT)
                v1List.forEach {

                    val item = GsonUtils.fromJson<KeyBoxAccountItem>(it, KeyBoxAccountItem::class.java)
                    if (item.profile.e164number.isNullOrEmpty()) {
                        val map = GsonUtils.fromJson<HashMap<String, Any>>(it, object : TypeToken<HashMap<String, Any>>() {}.type)
                        if (map.isNotEmpty()) {
                            for (data in map.values) {
                                if ((data as? Map<String, Any>)?.size ?: 0 > 1) {
                                    val text = GsonUtils.toJson(data)
                                    val profile = GsonUtils.fromJson(text, LoginProfile::class.java)
                                    profile.privateKey?.let { privKey ->
                                        accountMap[privKey] = AmeAccountData.fromLoginProfile(profile, "")
                                        needUpdate = true
                                    }
                                }
                            }
                        }

                    } else {
                        item.backupTime = item.profile.backupTime
                        item.profile.privateKey?.let { privKey ->
                            accountMap[privKey] = AmeAccountData.fromLoginProfile(item.profile, "")
                            needUpdate = true
                        }
                    }

                }

            } else {

                v2List.forEach {
                    val item = GsonUtils.fromJson<KeyBoxAccountItem>(it, KeyBoxAccountItem::class.java)
                    item.profile.privateKey?.let { privKey ->
                        accountMap[privKey] = AmeAccountData.fromLoginProfile(item.profile, "")
                        needUpdate = true
                    }

                }

            }

            return needUpdate
        }

        fun fixBackupState(): Boolean {
            var needUpdate = false
            for ((k, v) in accountMap) {

                if (0L == v.genKeyTime) {
                    needUpdate = true
                    v.genKeyTime = System.currentTimeMillis() / 1000
                }

                val json = SuperPreferences.getAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, k)
                var state: OldBackupState?
                if (!json.isNullOrEmpty()) {
                    SuperPreferences.setAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, AMESelfData.uid, "")
                    state = OldBackupState.fromJson(json)

                    if (null != state && state.time != 0L) {
                        needUpdate = true
                        v.backupTime = state.time
                    }
                }

                v.backupTime = min(v.backupTime, System.currentTimeMillis() / 1000)
            }

            return needUpdate
        }

        fun fixCurrentAndLastAccount(): Boolean {
            var needUpdate = false
            var fixCur = mCurAccount == null
            var fixLast = mLastAccount == null
            if (fixCur || fixLast) {
                needUpdate = true
                val oldCur = SuperPreferences.getStringPreference(AppContextHolder.APP_CONTEXT, AME_CURRENT_LOGIN)
                val oldLast = SuperPreferences.getStringPreference(AppContextHolder.APP_CONTEXT, AME_LAST_LOGIN_UID)
                for ((k, v) in accountMap) {
                    if (fixCur) {
                        if (v.uid == oldCur) {
                            v.curLogin = true
                            mCurAccount = v
                            fixCur = false
                        }
                    }
                    if (fixLast) {
                        if (v.uid == oldLast) {
                            v.lastLogin = true
                            mLastAccount = v
                            fixLast = false
                        }
                    }
                    if (!fixCur && !fixLast) {
                        break
                    }
                }
            }

            if (mCurAccount != null && BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT) == null) {
                mCurAccount?.curLogin = false
                mCurAccount = null
                needUpdate = true
            }
            return needUpdate
        }

        try {
            var needUpdate = false
            synchronized(this) {

                accountMap.clear()
                val accountListString = SuperPreferences.getStringPreference(AppContextHolder.APP_CONTEXT, AME_ACCOUNT_LIST)
                if (!accountListString.isNullOrEmpty()) {
                    accountMap.putAll(Gson().fromJson(accountListString, object : TypeToken<HashMap<String, AmeAccountData>>() {}.type))
                }

                if (loadAccountFromOldVersion(accountMap)) {
                    ALog.i(TAG, "loadAccountFromOldVersion return: true")
                    needUpdate = true
                }

                for ((key, data) in accountMap) {
                    if (data.curLogin) {
                        mCurAccount = data
                    }
                    if (data.lastLogin) {
                        mLastAccount = data
                    }
                }

                if (fixCurrentAndLastAccount()) {
                    ALog.i(TAG, "fixCurrentAndLastAccount return: true")
                    needUpdate = true
                }

                if (fixBackupState()) {
                    ALog.i(TAG, "fixBackupState return: true")
                    needUpdate = true
                }
            }

            if (needUpdate) {
                saveAccountHistory()
                SuperPreferences.clearAccountsV1Profile(AppContextHolder.APP_CONTEXT)
                SuperPreferences.clearAccountsV2Profile(AppContextHolder.APP_CONTEXT)
                SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, AME_LAST_LOGIN_UID, "")
                SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, AME_CURRENT_LOGIN, "")
            }

            if (fixDataComplement()) {
                ALog.i(TAG, "fixDataComplement return: true")
            }

            ALog.logForSecret(TAG, "init end, currentLogin: ${mCurAccount?.uid}, lastLogin: ${mLastAccount?.uid}")
        } catch (e: Exception) {
            ALog.e(TAG, "init error", e)
        }
    }

    @Synchronized
    private fun saveAccountHistory() {
        SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, AME_ACCOUNT_LIST, GsonUtils.toJson(accountMap))
    }

    fun getBackupTime(uid: String): Long {
        return getAccount(uid)?.backupTime ?: 0L
    }

    fun getGenKeyTime(uid: String): Long {
        return getAccount(uid)?.genKeyTime ?: System.currentTimeMillis() / 1000
    }

    @Synchronized
    fun currentLoginUid(): String {
        return mCurAccount?.uid ?: ""
    }

    @Synchronized
    fun currentLoginData(): AmeAccountData? {
        return mCurAccount
    }

    @Synchronized
    fun lastLoginUid(): String {
        return mLastAccount?.uid ?: ""
    }

    @Synchronized
    fun saveLastLoginUid(uid: String) {
        ALog.logForSecret(TAG, "last uid :$uid")
        val accountData = accountMap[uid]
        if (accountData != mLastAccount) {
            mLastAccount?.lastLogin = false
            mLastAccount = accountData
            mLastAccount?.lastLogin = true
            saveAccountHistory()
        }
    }

    @Synchronized
    fun saveCurrentLoginUid(uid: String) {
        ALog.logForSecret(TAG, "current uid :$uid")
        val accountData = accountMap[uid]
        if (accountData != mCurAccount) {
            mCurAccount?.curLogin = false
            mCurAccount = accountData
            mCurAccount?.curLogin = true
            saveAccountHistory()
            upgradeOldAccountFlags()
        }
    }

    fun resetBackupState(uid: String) {
        getAccount(uid)?.backupTime = 0
        saveAccountHistory()
    }

    fun saveAccount(data: AmeAccountData, replace: Boolean = true): Boolean {
        return saveAccount(data.uid, data, replace)
    }

    @Synchronized
    fun saveAccount(key: String, data: AmeAccountData, replace: Boolean = true): Boolean {
        var exist = accountMap[key]
        if (exist != null && exist.pubKey == data.pubKey) {
            if (!replace) {
                return false
            }
        }

        if (data.genKeyTime == 0L) {
            data.genKeyTime = System.currentTimeMillis() / 1000
        }
        if (exist == null) {
            exist = data
            accountMap[key] = data

        }else {
            val newBackupTime = min(data.backupTime, AmeTimeUtil.localTimeSecond())
            if (newBackupTime > 0) {
                exist.backupTime = newBackupTime
            }
            exist.genKeyTime = data.genKeyTime
            if (data.name.isNotEmpty()) {
                exist.name = data.name
            }
            exist.curLogin = data.curLogin
            exist.lastLogin = data.lastLogin
        }
        if (exist.curLogin && exist != mCurAccount) {
            mCurAccount?.curLogin = false
            mCurAccount = exist
        }
        if (exist.lastLogin && exist != mLastAccount) {
            mLastAccount?.lastLogin = false
            mLastAccount = exist
        }
        saveAccountHistory()
        return true
    }

    @Synchronized
    fun updateAccount(data: AmeAccountData) {
        if (data.uid.isNotEmpty()) {
            accountMap.remove(data.priKey)
            accountMap[data.uid] = data
        }else {
            accountMap[data.priKey] = data
        }
        saveAccountHistory()
    }

    @Synchronized
    fun deleteAccount(uid: String) {
        ALog.logForSecret(TAG, "delete account uid :$uid")
        val accountData = accountMap[uid]
        if (accountData != null) {
            if (accountData.curLogin) {
                accountData.curLogin = false
                mCurAccount = null
            }
            if (accountData.lastLogin) {
                accountData.lastLogin = false
                mLastAccount = null
            }
            accountMap.remove(uid)
            saveAccountHistory()
        }
    }

    @Synchronized
    fun getAccount(uid: String): AmeAccountData? {
        return accountMap[uid]
    }

    @Synchronized
    fun getAccountList(): List<AmeAccountData> {
        val result = accountMap.values.toList()
        result.forEach { data ->
            if (!BcmFileUtils.isExist(data.avatar)) {
                data.avatar = ""
            }
        }
        return result
    }

    @Synchronized
    fun getCurrentAccountSize(): Int {
        return accountMap.size
    }

    fun getPrivateKeyWithPassword(accountData: AmeAccountData, password: String): ByteArray? {
        if (accountData.version < AmeAccountData.V4) {
            val priKeyArray = BCMPrivateKeyUtils.decodePrivateKey(accountData.priKey, password)
            accountData.priKey = BCMPrivateKeyUtils.encryptPrivateKey(priKeyArray, password.toByteArray())
            val pubKeyArray = BCMPrivateKeyUtils.generatePublicKey(priKeyArray) ?: return null
            accountData.pubKey = Base64.encodeBytes(pubKeyArray)
            accountData.version = AmeAccountData.V4
            saveAccount(accountData)
            return priKeyArray
        } else {

            return BCMPrivateKeyUtils.decryptPrivateKey(accountData.priKey, password.toByteArray()).second
        }
    }

    private fun upgradeOldAccountFlags() {
        if (IdentityKeyUtil.hasIdentityKey(AppContextHolder.APP_CONTEXT)) {
            val idKey = IdentityKeyUtil.getIdentityKey(AppContextHolder.APP_CONTEXT)
            val publicKey = HexUtil.toString(idKey.publicKey.serialize())
            val idKeyUid = BCMPrivateKeyUtils.provideUid(idKey.publicKey.serialize())
            if (idKeyUid == currentLoginUid()) {
                val json = SuperPreferences.getAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, publicKey)
                if (!json.isNullOrEmpty()) {
                    SuperPreferences.setAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, publicKey, "")
                    SuperPreferences.setAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, AMESelfData.uid, json)
                }

                if (SuperPreferences.isAccountBackupWithPublicKey(AppContextHolder.APP_CONTEXT, publicKey)) {
                    SuperPreferences.setAccountBackupWithPublicKey(AppContextHolder.APP_CONTEXT, publicKey, false)
                    SuperPreferences.setAccountBackupWithPublicKey(AppContextHolder.APP_CONTEXT, AMESelfData.uid, true)
                }

                if (SuperPreferences.isAccountBackupWithRedPoint(AppContextHolder.APP_CONTEXT, publicKey)) {
                    SuperPreferences.setAccountBackupRedPoint(AppContextHolder.APP_CONTEXT, publicKey, false)
                    SuperPreferences.setAccountBackupRedPoint(AppContextHolder.APP_CONTEXT, AMESelfData.uid, true)
                }
            }
        }
    }

    fun export() {
        if (accountMap.isEmpty()) {
            return
        }

        val diskPath = Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar +
                ARouterConstants.SDCARD_ROOT_FOLDER + File.separatorChar
        val file = File(folder, "account_backup.txt")
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(file)
            out.write(GsonUtils.toJson(accountMap).toByteArray())
        } catch (e: Exception) {
            ALog.e("AccountExport", e)
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                ALog.e("AccountExport", e)
            }
        }

    }

    fun import() {
        val diskPath = Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar +
                ARouterConstants.SDCARD_ROOT_FOLDER + File.separatorChar

        val file = File(folder, "account_backup.txt")
        var input: FileInputStream? = null
        try {
            input = FileInputStream(file)
            val json = String(input.readBytes())
            val map = GsonUtils.fromJson<HashMap<String, AmeAccountData>>(json, object : TypeToken<HashMap<String, AmeAccountData>>() {}.type)
            this.accountMap.putAll(map)
            saveAccountHistory()
        } catch (e: Exception) {
            ALog.e("AccountImport", e)
        } finally {
            try {
                input?.close()
            } catch (e: IOException) {
                ALog.e("AccountImport", e)
            }
        }
    }

    data class OldBackupState(val time: Long, val version: Int) : NotGuard {
        companion object {
            fun fromJson(json: String?): OldBackupState? {
                if (json?.isNotEmpty() == true) {
                    try {
                        return Gson().fromJson(json, OldBackupState::class.java)
                    } catch (e: JsonSyntaxException) {
                        ALog.e("OldBackupState", e)
                    }
                }
                return null
            }
        }
    }
}