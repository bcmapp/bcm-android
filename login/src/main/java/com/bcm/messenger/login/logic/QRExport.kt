package com.bcm.messenger.login.logic

import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.login.R
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Created by bcm.social.01 on 2018/9/20.
 */
object QRExport {
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

    fun accountDataToAccountJson(accountData: AmeAccountData): String {
        if (accountData.version < AmeAccountData.V3){
            return ""
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
            4 -> Gson().fromJson<ExportModelV4>(str, ExportModelV4::class.java)
            else -> throw Exception(AppContextHolder.APP_CONTEXT.getString(R.string.common_unsupported_qr_code_description))
        }
    }
}