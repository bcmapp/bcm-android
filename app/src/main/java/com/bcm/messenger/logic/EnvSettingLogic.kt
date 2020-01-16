package com.bcm.messenger.logic

import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.logic.bean.EnvSetting
import com.bcm.messenger.utility.AppContextHolder
import com.google.gson.Gson

/**
 * bcm.social.01 2018/10/15.
 */
object EnvSettingLogic {
    private const val ENV_SETTING = "bcm_dev_env_setting"

    const val RELEASE_HOST = "bcm-social.com:8080"
    private val DefaultEnv = EnvSetting(false, RELEASE_HOST, walletDev = false, lbsEnable = true, httpsEnable = true)

//    const val RELEASE_HOST = "157.255.232.207:8080"
//    private val DefaultEnv = EnvSetting(true, RELEASE_HOST, false, false)

    private var curEnv = DefaultEnv
    val EnvList = arrayOf(
            RELEASE_HOST,
            "39.108.124.60:8080",
            "47.75.146.142:8070")

    fun getEnvSetting(): EnvSetting {
        return getEnvSetting(AppUtil.isReleaseBuild())
    }

    fun getEnvSetting(isRelease: Boolean): EnvSetting {
        if (!isRelease){
            if (curEnv != DefaultEnv){
                return curEnv
            }

            val envJson = SuperPreferences.getStringPreference(AppContextHolder.APP_CONTEXT, ENV_SETTING)
            if (envJson.isNotEmpty()){
                try {
                    val env = Gson().fromJson(envJson, EnvSetting::class.java)
                    curEnv = env
                    return curEnv
                } catch (e:Exception){

                }
            }
        }
        curEnv = DefaultEnv
        return DefaultEnv
    }

    fun setEnvSetting(setting: EnvSetting){
        EnvSettingLogic.setEnvSetting(AppUtil.isReleaseBuild(), setting)
    }

    fun setEnvSetting(isRelease: Boolean, setting: EnvSetting){
        if (!isRelease) {
            val envJson = Gson().toJson(setting)
            SuperPreferences.setStringPreferenceNow(AppContextHolder.APP_CONTEXT, ENV_SETTING, envJson)
            curEnv = setting
        }
    }

    fun indexOfHost(host:String):Int?{
        for (i in 0 until EnvList.size){
            if (EnvList[i] == host){
                return i
            }
        }
        return null
    }

    fun hostFromIndex(index:Int):String{
        if (index >= 0 && index < EnvList.size){
            return EnvList[index]
        }
        return RELEASE_HOST
    }
}