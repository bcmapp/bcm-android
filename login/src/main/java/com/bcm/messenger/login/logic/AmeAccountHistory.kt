package com.bcm.messenger.login.logic

import android.os.Environment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.event.AccountLoginStateChangedEvent
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.utility.storage.SPEditor
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap
import kotlin.math.min

/**
 * Created by bcm.social.01 on 2018/9/6.
 */
class AmeAccountHistory {

    companion object {
        private const val TAG = "AmeAccountHistory"

        private const val AME_LAST_LOGIN = "AME_LAST_LOGIN"
        private const val AME_ACCOUNT_LIST = "AME_ACCOUNT_LIST"
        private const val AME_MAJOR_LOGIN_ACCOUNT = "AME_CURRENT_LOGIN"
        private const val AME_MINOR_LOGIN_ACCOUNT = "AME_MINOR_ACCOUNT_LIST"
        private const val AME_ADHOC_UID = "AME_ADHOC_UID"

        const val LIMIT_SIZE = 50
    }

    private var accountMap = Collections.synchronizedMap(HashMap<String, AmeAccountData>())
    private var majorAccountUid: String = ""
    private var minorAccountUids: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val accountContextMap = ConcurrentHashMap<String, AccountContext>() //all account context, include log out account

    private val storage = SPEditor(SuperPreferences.LOGIN_PROFILE_PREFERENCES)

    fun init() {
        ALog.i(TAG, "init")
        try {
            val minorUidListString = storage.get(AME_MINOR_LOGIN_ACCOUNT, "")
            if (minorUidListString.isNotEmpty()) {
                minorAccountUids.addAll(Gson().fromJson(minorUidListString, object : TypeToken<List<String>>() {}.type))
            }

            majorAccountUid = storage.get(AME_MAJOR_LOGIN_ACCOUNT, "")

            val accountListString = storage.get(AME_ACCOUNT_LIST, "")
            if (accountListString.isNotEmpty()) {
                accountMap.putAll(Gson().fromJson(accountListString, object : TypeToken<HashMap<String, AmeAccountData>>() {}.type))
            }

            var changed = 0
            for ((_, account) in accountMap) {
                changed = changed.or(fixLoginState(account))
                changed = changed.or(fixBackupTime(account))
                changed = changed.or(fixGenTime(account))
            }

            if (changed > 0) {
                saveAccountHistory()
            }

            for ((uid, account) in accountMap) {
                accountContextMap[uid] = AccountContext(uid, genToken(uid, account.signalPassword), account.signalPassword)
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "init error", e)
        }
    }

    private fun fixLoginState(account: AmeAccountData): Int {
        val accountContext = AccountContext(account.uid, "", "")
        if (account.uid == majorAccountUid() && account.signalPassword.isEmpty()) {
            ALog.i(TAG, "fix login state")
            account.registrationId = TextSecurePreferences.getLocalRegistrationId(accountContext)
            account.gcmDisabled = TextSecurePreferences.isGcmDisabled(accountContext)
            account.signalPassword = TextSecurePreferences.getPushServerPassword(accountContext)
            account.signalingKey = TextSecurePreferences.getSignalingKey(accountContext)
            account.signedPreKeyRegistered = TextSecurePreferences.isSignedPreKeyRegistered(accountContext)
            account.signedPreKeyFailureCount = TextSecurePreferences.getSignedPreKeyFailureCount(accountContext)
            account.signedPreKeyRotationTime = TextSecurePreferences.getSignedPreKeyRotationTime(accountContext)
            return 1
        }
        return 0
    }

    private fun fixBackupTime(account: AmeAccountData): Int {
        val json = SuperPreferences.getAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, account.uid)
        val state: OldBackupState?
        if (!json.isNullOrEmpty()) {
            SuperPreferences.setAccountBackupWithPublicKey2(AppContextHolder.APP_CONTEXT, account.uid, "")
            state = OldBackupState.fromJson(json)
            if (null != state && state.time != 0L) {
                account.backupTime = state.time
                return 1
            }
        }
        return 0
    }

    private fun fixGenTime(account: AmeAccountData): Int {
        if (account.genKeyTime == 0L) {
            account.genKeyTime = System.currentTimeMillis() / 1000
            return 1
        }
        return 0
    }

    private fun saveAccountHistory() {
        storage.set(AME_ACCOUNT_LIST, GsonUtils.toJson(accountMap))
    }

    fun getBackupTime(uid: String): Long {
        return getAccount(uid)?.backupTime ?: 0L
    }

    fun getGenKeyTime(uid: String): Long {
        return getAccount(uid)?.genKeyTime ?: System.currentTimeMillis() / 1000
    }

    fun majorAccountUid(): String {
        if (majorAccountUid.isEmpty()) {
            majorAccountUid = storage.get(AME_MAJOR_LOGIN_ACCOUNT, "")
        }
        return majorAccountUid
    }

    fun minorAccountList(): List<String> {
        return minorAccountUids.toList()
    }

    fun majorAccountData(): AmeAccountData? {
        return accountMap[majorAccountUid()]
    }

    fun isLogin(uid: String): Boolean {
        if (uid.isEmpty()) {
            return false
        }
        return uid == majorAccountUid || minorAccountUids.contains(uid)
    }

    fun setMinorAccountUid(uid: String) {
        if (minorAccountUids.contains(uid)) {
            return
        }

        minorAccountUids.add(uid)
        storage.set(AME_MINOR_LOGIN_ACCOUNT, GsonUtils.toJson(minorAccountUids))

        EventBus.getDefault().post(AccountLoginStateChangedEvent())
    }

    fun setMajorLoginAccountUid(uid: String) {
        ALog.logForSecret(TAG, "setMajorLoginAccountUid current uid :$uid")
        val accountData = getAccount(uid)

        if (accountData == null) {
            ALog.logForSecret(TAG, "setMajorLoginAccountUid account data not found")
            return
        }

        val lastMajorUid = majorAccountUid
        if (uid == lastMajorUid) {
            return
        }

        //set current major account to minor account
        if (lastMajorUid.isNotEmpty()) {
            minorAccountUids.add(lastMajorUid)
            minorAccountUids.remove(uid)
            storage.set(AME_MINOR_LOGIN_ACCOUNT, GsonUtils.toJson(minorAccountUids))
        }

        majorAccountUid = uid
        storage.set(AME_MAJOR_LOGIN_ACCOUNT, uid)

        val token = genToken(majorAccountUid, accountData.signalPassword)
        accountContextMap[majorAccountUid] = AccountContext(majorAccountUid, token, accountData.signalPassword)

        saveLastLoginUid(majorAccountUid)
        EventBus.getDefault().post(AccountLoginStateChangedEvent())
    }

    fun removeLoginAccountUid(uid: String) {
        val logoutUid = if (uid.isEmpty()) {
            majorAccountUid
        } else {
            uid
        }

        if (majorAccountUid == logoutUid) {
            majorAccountUid = ""

            //set the first minor account to major account
            if (minorAccountUids.isNotEmpty()) {
                majorAccountUid = minorAccountUids.removeAt(0)
                storage.set(AME_MINOR_LOGIN_ACCOUNT, GsonUtils.toJson(minorAccountUids))
            }
            storage.set(AME_MAJOR_LOGIN_ACCOUNT, majorAccountUid)
        } else {
            minorAccountUids.remove(uid)
            storage.set(AME_MINOR_LOGIN_ACCOUNT, GsonUtils.toJson(minorAccountUids))
        }

        accountContextMap[uid] = AccountContext(uid, "", "")

        saveLastLoginUid(majorAccountUid)
        EventBus.getDefault().post(AccountLoginStateChangedEvent())
    }

    private fun saveLastLoginUid(uid: String) {
        if (uid.isNotEmpty()) {
            storage.set(AME_LAST_LOGIN, uid)
        }
    }

    fun getLastLoginUid(): String {
        return storage.get(AME_LAST_LOGIN,"")
    }

    fun setAdHocUid(uid:String) {
        storage.set(AME_ADHOC_UID, uid)
    }

    fun getAdHocUid(): String {
        val uid = storage.get(AME_ADHOC_UID, "")
        if (uid.isEmpty()) {
            return majorAccountUid
        }
        return uid
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
        val exist = accountMap[key]
        if (exist != null && exist.pubKey == data.pubKey) {
            if (!replace) {
                return false
            }
        }

        if (data.genKeyTime == 0L) {
            data.genKeyTime = System.currentTimeMillis() / 1000
        }
        if (exist == null) {
            accountMap[key] = data
        } else {
            val newBackupTime = min(data.backupTime, AmeTimeUtil.localTimeSecond())
            if (newBackupTime > 0) {
                exist.backupTime = newBackupTime
            }
            exist.genKeyTime = data.genKeyTime
            if (data.name.isNotEmpty()) {
                exist.name = data.name
            }
        }
        saveAccountHistory()
        return true
    }

    @Synchronized
    fun deleteAccount(uid: String) {
        ALog.logForSecret(TAG, "delete account uid :$uid")
        val accountData = accountMap[uid]
        if (accountData != null) {
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

    fun genToken(uid: String, password: String): String {
        try {
            if (password.isNotEmpty()) {
                return "Basic " + Base64.encodeBytes(("$uid:$password").toByteArray(charset("UTF-8")))
            }
        } catch (e: Exception) {
            ALog.logForSecret(TAG, "getAuthorizationHeader fail", e)
        }
        return ""
    }

    fun getAccountContext(uid: String): AccountContext? {
        return accountContextMap[uid]
    }

    fun getAllLoginContext(): List<AccountContext> {
        val loginContextList = mutableListOf<AccountContext>()
        val majorContext = getAccountContext(majorAccountUid)
        if (null != majorContext) {
            loginContextList.add(majorContext)
        }

        val minorList = minorAccountUids
        for (i in minorList) {
            loginContextList.add(getAccountContext(i)?:continue)
        }

        return loginContextList
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