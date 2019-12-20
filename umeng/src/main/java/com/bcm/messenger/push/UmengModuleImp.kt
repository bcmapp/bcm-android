package com.bcm.messenger.push

import android.app.Activity
import android.app.Application
import android.content.Context
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.provider.IUmengModule
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.RomUtil
import com.bcm.route.annotation.Route
import com.umeng.analytics.MobclickAgent
import com.umeng.message.PushAgent
import com.umeng.message.tag.TagManager
import org.android.agoo.huawei.HuaWeiRegister
import org.android.agoo.xiaomi.MiPushRegistar

/**
 * Created by wjh on 2019-09-16
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_UMENG)
class UmengModuleImp : IUmengModule {

    private val TAG = "UmengProviderImp"

    override fun onAppInit(context: Application) {
        ALog.i(TAG, "onAppInit")
        UmengRegisterTimesFix().fix()
        UMengHelper.initUMeng(context)
        UMengHelper.initUpush(context)

        if (AppUtil.isMainProcess()) {
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO)
            if (RomUtil.isEmui()) {
                HuaWeiRegister.register(context)
            }
            else if (MiPushRegistar.checkDevice(context)) {
                MiPushRegistar.register(context, UMengHelper.XIAOMI_ID, UMengHelper.XIAOMI_KEY)
            }
        }
    }

    override fun onActivityCreate(activity: Activity) {
        ALog.i(TAG, "onActivityCreate")
        PushAgent.getInstance(activity).onAppStart()
    }

    override fun onActivityResume(activity: Activity) {
        ALog.i(TAG, "onActivityResume")
        MobclickAgent.onResume(activity)
    }

    override fun onActivityPause(activity: Activity) {
        ALog.i(TAG, "onActivityPause")
        MobclickAgent.onPause(activity)
    }

    override fun getPushToken(context: Context): String? {
        return PushAgent.getInstance(context).registrationId
    }

    override fun registerPushTag(context: Context, tag: String) {
        ALog.i(TAG, "registerPushTag, tag: $tag")
        val mPushAgent = PushAgent.getInstance(context)
        mPushAgent.tagManager.getTags { isSuccess, mutableList ->
            if (isSuccess && !mutableList.contains(tag)) {
                mPushAgent.tagManager.addTags(TagManager.TCallBack { success, result ->
                    ALog.i(TAG, "addTag $tag success = $success, ${result.msg}")
                }, tag)
            }
        }
    }

    override fun unregisterPushTag(context: Context, tag: String) {
        ALog.i(TAG, "unregisterPushTag, tag: $tag")
        val mPushAgent = PushAgent.getInstance(context)
        mPushAgent.tagManager.getTags { isSuccess, mutableList ->
            if (isSuccess && mutableList.contains(tag)) {
                mPushAgent.tagManager.deleteTags(TagManager.TCallBack { success, result ->
                    ALog.i(TAG, "deleteTag $tag success = $success, ${result.msg}")
                }, tag)
            }
        }
    }

    override fun onAccountLogin(context: Context, uid: String) {
        ALog.i(TAG, "onAccountLogin")
        MobclickAgent.onProfileSignIn(uid)
    }

    override fun onAccountLogout(context: Context, uid: String) {
        ALog.i(TAG, "onAccountLogout")
        MobclickAgent.onProfileSignOff()
    }
}