package com.bcm.messenger.push

import android.content.Context
import android.os.Build
import com.bcm.messenger.common.utils.PushUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.taobao.agoo.TaobaoRegister
import com.umeng.commonsdk.UMConfigure
import com.umeng.message.IUmengRegisterCallback
import com.umeng.message.MsgConstant
import com.umeng.message.PushAgent

/**
 * Created by bcm.social.01 on 2018/6/25.
 */
object UMengHelper {
    private const val TAG = "UMengHelper"
    private const val UMENG_APPKEY = "5b854900f29d981652000038"
    private const val UMENG_MESSAGE_KEY = "f326e9a0450b5941391a5586ee4a3d11"
    const val XIAOMI_ID = "2882303761517844301"
    const val XIAOMI_KEY = "5671784429301"

    fun initUMeng(context: Context){
        UMConfigure.setLogEnabled(true)
        try {
            val aClass = Class.forName("com.umeng.commonsdk.UMConfigure")
            val fs = aClass.declaredFields
            for (f in fs) {
                ALog.e(TAG, "UMConfigure." + f.name + "   " + f.type.name)
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }

        UMConfigure.init(context, UMENG_APPKEY, "Umeng", UMConfigure.DEVICE_TYPE_PHONE,
                UMENG_MESSAGE_KEY)

        ALog.i(TAG, "initUmeng finish")
    }

    fun initUpush(context: Context) {
        val mPushAgent = PushAgent.getInstance(context)
        mPushAgent.notificationPlaySound = MsgConstant.NOTIFICATION_PLAY_SDK_ENABLE
        mPushAgent.register(object : IUmengRegisterCallback {
            override fun onSuccess(deviceToken: String) {
                ALog.d(TAG, "device token: $deviceToken")
                AmeDispatcher.io.dispatch {
                    PushUtil.registerPush()
                }
            }

            override fun onFailure(s: String, s1: String) {
                ALog.e(TAG, "register failed: $s $s1")
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            TaobaoRegister.setAgooMsgReceiveService("com.bcm.messenger.push.BcmUmengIntentService")
        }

        mPushAgent.resourcePackageName = "com.bcm.messenger"

        mPushAgent.setPushIntentServiceClass(UmengNotificationService::class.java)

        ALog.i(TAG, "initUpush finish")

    }
}