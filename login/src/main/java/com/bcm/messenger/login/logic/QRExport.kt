package com.bcm.messenger.login.logic

import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.login.R
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.bean.LoginProfile
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.HexUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Created by bcm.social.01 on 2018/9/20.
 */
object QRExport {
    class ExportModel(val private: String?, val phone: String?, version: Int, val openID: String?, val keyGenTime: Long, val nickName: String?, val public: String?) : ExportModelBase(version) {

        fun toAccountData(): AmeAccountData {
            val accountData = AmeAccountData()
            accountData.version = AmeAccountData.V2
            accountData.mode = AmeAccountData.ACCOUNT_MODE_BACKUP
            accountData.priKey = private ?: ""
            accountData.pubKey = public ?: ""
            if (accountData.pubKey.isNotEmpty()) {
                try {
                    accountData.uid = BCMPrivateKeyUtils.provideUid(Base64.decode(accountData.pubKey))
                }catch (ex: Exception) {
                    ALog.e("QRExport", ex)
                    accountData.uid = ""
                }
            }
            accountData.phone = phone ?: ""
            accountData.name = nickName ?: ""
            accountData.genKeyTime = keyGenTime
            accountData.backupTime = AmeTimeUtil.localTimeSecond()
            return accountData
        }
    }

    //v3 version account qr data
    //private key Hex code
    class ExportModelV3(version: Int, val public: String, val private: String, val keyGenTime: Long, val nickName: String) : ExportModelBase(version) {
        fun toAccountData(): AmeAccountData {
            val accountData = AmeAccountData()
            accountData.version = AmeAccountData.V3
            accountData.mode = AmeAccountData.ACCOUNT_MODE_BACKUP
            accountData.priKey = private
            accountData.uid = BCMPrivateKeyUtils.provideUid(public)
            accountData.name = nickName
            accountData.genKeyTime = keyGenTime
            accountData.backupTime = AmeTimeUtil.localTimeSecond()
            return accountData
        }
    }

    //V4 version account qr data
    //private key
    class ExportModelV4(version: Int, val public: String, val private: String, val keyGenTime: Long, val nickName: String) : ExportModelBase(version) {
        fun toAccountData(): AmeAccountData {
            val accountData = AmeAccountData()
            accountData.version = AmeAccountData.V4
            accountData.mode = AmeAccountData.ACCOUNT_MODE_BACKUP
            accountData.priKey = private
            accountData.pubKey = public
            accountData.uid = BCMPrivateKeyUtils.provideUid(public)
            accountData.name = nickName
            accountData.genKeyTime = keyGenTime
            accountData.backupTime = AmeTimeUtil.localTimeSecond()
            return accountData
        }
    }

    open class ExportModelBase(var version: Int) : NotGuard

    fun profileToAccountJson(profile: LoginProfile): String {
        val nick: String? = profile.nickname
        val model = ExportModel(profile.privateKey, profile.e164number, AmeAccountData.V2, profile.openId, profile.keyGenTime, nick, null)
        return Base64.encodeBytes(Gson().toJson(model).toByteArray())
    }


    fun accountDataToAccountJson(accountData: AmeAccountData): String {
        if (accountData.version < AmeAccountData.V3){
            val profile = LoginProfile()
            profile.nickname = accountData.name
            profile.privateKey = HexUtil.toString(Base64.decode(accountData.priKey))
            profile.e164number = accountData.phone
            profile.openId = ""
            profile.keyGenTime = accountData.genKeyTime
            profile.publicKey = accountData.pubKey
            profile.backupTime = accountData.backupTime
            profile.loginMode = accountData.mode
            return profileToAccountJson(profile)
        }
        val model = ExportModelV4(AmeAccountData.V4, accountData.pubKey, accountData.priKey, accountData.genKeyTime, accountData.name)
        return Gson().toJson(model)
    }

    fun parseAccountDataFromString(str: String): ExportModelBase? {
        try {
            return parseAccountDataFromJsonString(str)
        } catch (e: JsonSyntaxException) {
            ALog.e("QRExport", "parseAccountDataFromString error", e)
        }
        val decode = try {
            String(Base64.decode(str))
        }catch (ex: Exception) {
            throw Exception(AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description), ex)
        }
        return parseAccountDataFromJsonString(decode)
    }


    private fun parseVersion(accountString: String): Int {
        return Gson().fromJson<ExportModelBase>(accountString, QRExport.ExportModelBase::class.java).version
    }

    private fun parseAccountDataFromJsonString(str: String): ExportModelBase? {
        return when (parseVersion(str)) {
            1 -> Gson().fromJson<ExportModel>(str, QRExport.ExportModel::class.java)
            2 -> Gson().fromJson<ExportModel>(str, QRExport.ExportModel::class.java)
            3 -> Gson().fromJson<ExportModelV3>(str, QRExport.ExportModelV3::class.java)
            4 -> Gson().fromJson<ExportModelV4>(str, QRExport.ExportModelV4::class.java)
            else -> throw Exception(AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description))
        }
    }
}