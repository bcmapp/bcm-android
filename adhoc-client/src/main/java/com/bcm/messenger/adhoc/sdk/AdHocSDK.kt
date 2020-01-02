package com.bcm.messenger.adhoc.sdk

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bcm.imcore.IAccountAuth
import com.bcm.imcore.IAdHocBinder
import com.bcm.imcore.IAdHocListener
import com.bcm.imcore.im.ChannelUserInfo
import com.bcm.imcore.im.MessengerService
import com.bcm.imcore.im.util.IDeviceUtilDelegate
import com.bcm.imcore.im.util.ImCoreDeviceUtil
import com.bcm.imcore.im.util.ImCoreLogger
import com.bcm.imcore.im.util.secure.DHUtil
import com.bcm.imcore.im.util.secure.IDHHelper
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.common.AmeNotification
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.wifi.WiFiUtil
import com.orhanobut.logger.Logger
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.libsignal.ecc.ECPrivateKey
import org.whispersystems.libsignal.util.ByteUtil
import java.io.File
import java.util.*

object AdHocSDK {

    private const val TAG = "AdHocSDK"
    private const val FOREGROUND_NOTIFICATION_ID = 9999

    private var binding = false
    private var sdkApi:IAdHocBinder? = null
    private var sdkHelper:AdHocSdkHelper? = null
    private var netSnapshot = NetSnapshot()
    private var logSource =  AdHocLoggerSource()
    private var mTargetClass: Class<out Activity>? = null

    val messengerSdk = AdHocSessionSDK()

    private val eventListenerSet = Collections.newSetFromMap(WeakHashMap<IAdHocSDKEventListener, Boolean>());
    private val sdkConnection = object :ServiceConnection {
        var connected:Boolean = false
        override fun onServiceDisconnected(name: ComponentName?) {
            ALog.i(TAG, "onServiceDisconnected")
            sdkApi = null
            sdkHelper = null
            messengerSdk.unInit()

            if (binding) {
                ALog.i(TAG, "rebinding for adhoc")
                connected = false
                binding = false
                AmeDispatcher.mainThread.dispatch ({
                    init(AppContextHolder.APP_CONTEXT)
                }, 2000)
            }
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            ALog.i(TAG, "onServiceConnected ${service==null}")
            val sdkApi = IAdHocBinder.Stub.asInterface(service ?: return)
            setForeground(sdkApi)
            connected = true
            AmeDispatcher.singleScheduler.scheduleDirect {
                initSdk(sdkApi)
            }
        }

        private fun setForeground(binder: IAdHocBinder) {
            val builder = AmeNotification.getAdHocNotificationBuilder(AppContextHolder.APP_CONTEXT)
            builder.setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.adhoc_amin_airchat_on))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setSmallIcon(R.drawable.icon_notification_alpha)
                        .setColor(AppContextHolder.APP_CONTEXT.getColorCompat(R.color.common_color_black))
            }
            if (mTargetClass != null) {
                builder.setContentIntent(PendingIntent.getActivity(AppContextHolder.APP_CONTEXT, 0, Intent(AppContextHolder.APP_CONTEXT, mTargetClass), PendingIntent.FLAG_UPDATE_CURRENT))
            }
            binder.setForeground(FOREGROUND_NOTIFICATION_ID, builder.build())
        }
    }

    init {
        ImCoreLogger.setLogger {
            level, message ->
            putLogger(level, message)
        }

        ImCoreDeviceUtil.setDelegate(object : IDeviceUtilDelegate {
            override fun restartBle(finished: () -> Unit) {
                putLogger(Logger.INFO, "restartBle invoke")
                BleUtil.restartBLE {
                    putLogger(Logger.INFO, "restartBle result:$it")
                    finished()

                    if (it && WiFiUtil.isEnable()) {
                        sdkHelper?.broadcaster?.stop {
                            sdkHelper?.broadcaster?.start(){}
                        }
                    }
                }
            }
        })

        DHUtil.setDelegate(object :IDHHelper {
            override fun dh(alicePrivateKey: ByteArray, bobPublicKey: ByteArray): ByteArray {
                return Curve25519.getInstance(Curve25519.BEST).calculateAgreement(bobPublicKey, alicePrivateKey)
            }

            override fun getKeyPair(): Pair<ByteArray, ByteArray> {
                val keyPair = BCMPrivateKeyUtils.generateKeyPair()
                val privateKey = keyPair.privateKey.serialize()
                val publicKey = (keyPair.publicKey as DjbECPublicKey).publicKey

                return Pair(privateKey, publicKey)
            }
        })
    }

    private fun putLogger(level: Int, message: String) {
        Logger.log(level, TAG, message, null)

        if (!AppUtil.isReleaseBuild()) {
            AmeDispatcher.mainThread.dispatch {
                logSource.pushLog(LogData(AmeTimeUtil.localTimeMillis(), level, message))
            }
        }
    }

    private fun initSdk(sdkApi: IAdHocBinder) {
        this.sdkApi = sdkApi

        sdkApi.addAdHocListener(adHocListener)
        sdkApi.init(object :IAccountAuth.Stub() {
            override fun sign(data: ByteArray): ByteArray {
                val priKey: ECPrivateKey = Curve.decodePrivatePoint(BCMEncryptUtils.getMyPrivateKey(AppContextHolder.APP_CONTEXT))
                return BCMPrivateKeyUtils.sign(priKey, data)
            }

            override fun verifySign(pubKey:ByteArray, data: ByteArray, sign: ByteArray): Boolean {
                val type = byteArrayOf(Curve.DJB_TYPE.toByte())
                val keyObj = Curve.decodePoint(ByteUtil.combine(type, pubKey), 0)
                return Curve.verifySignature(keyObj, data, sign)
            }

            override fun getPublicKey(): ByteArray {
                return BCMEncryptUtils.getMyPublicKey(AppContextHolder.APP_CONTEXT)
            }

            override fun getUid(): String {
                return AMELogin.majorUid
            }

            override fun getName(): String {
                return Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(AMELogin.majorUid), true).name
            }

            override fun getAccountDir(): String {
                val accountDir = AmeModuleCenter.login().getAccountContext(AMELogin.majorUid).accountDir
                val dir =  File(accountDir, "airchat")
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                return dir.absolutePath
            }

        })
        messengerSdk.init(sdkApi)

        val sdkHelper = AdHocSdkHelper(sdkApi) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                eventListenerSet.forEach {
                    it.onAdHockStateChanged()
                }
            }
        }

        this.sdkHelper = sdkHelper
        startProxy()


        ALog.i(TAG, "initSdk adhoc all ready now")
        eventListenerSet.forEach {
            it.onAdHocReady()
        }
    }

    private val adHocListener = object : IAdHocListener.Stub() {
        override fun onServerStateChanged(serverSSID: String, serverIp: String, myIp: String, status: Int, errorCode: Int) {
            ALog.i(TAG, "onServerStateChanged status:$status error:$errorCode")
            AmeDispatcher.singleScheduler.scheduleDirect {
                netSnapshot.serverName = deviceNameFromSSID(serverSSID)
                netSnapshot.serverIp = serverIp
                netSnapshot.myIp = myIp
                netSnapshot.errorCode = errorCode
                netSnapshot.connState = when(status) {
                    2 -> AdHocConnState.WIFI_CONNECTING
                    3 -> AdHocConnState.UDP_CONNECTING
                    4 -> AdHocConnState.CONNECT_FAILED
                    5 -> AdHocConnState.CONNECTED
                    6 -> AdHocConnState.DISCONNECTED
                    else -> netSnapshot.connState
                }

                eventListenerSet.forEach {
                    it.onAdHockStateChanged()
                }
            }
        }

        private fun deviceNameFromSSID( ssid:String ):String {
            val list = ssid.split("-")
            val nameBuilder = StringBuilder()
            if (list.size > 2) {
                for (i in 2 until list.size) {
                    nameBuilder.append(list[i])
                    if (i != list.size-1) {
                        nameBuilder.append("-")
                    }
                }
            } else if (list.size == 2) {
                nameBuilder.append(list[1])
            } else {
                nameBuilder.append(ssid)
            }

            return nameBuilder.toString()
        }

        override fun onClientStateChanged(clientIp: String, connected: Boolean) {
            ALog.i(TAG, "onClientStateChanged $connected")
            AmeDispatcher.singleScheduler.scheduleDirect {
                if (connected) {
                    netSnapshot.clientSet.add(clientIp)
                } else {
                    netSnapshot.clientSet.remove(clientIp)
                }
                eventListenerSet.forEach {
                    it.onAdHockStateChanged()
                }
            }
        }

        override fun onAdHocNodeCountChanged(count: Long) {
            ALog.i(TAG, "onAdHocNodeCountChanged $count")
            AmeDispatcher.singleScheduler.scheduleDirect {
                eventListenerSet.forEach {
                    it.onAdHockStateChanged()
                }
            }
        }

        override fun onMyAdHocGroupChanged(newGroupId: String) {
            ALog.i(TAG, "onMyAdHocGroupChanged $newGroupId")
            netSnapshot.myNetGroupId = newGroupId
            AmeDispatcher.singleScheduler.scheduleDirect {
                eventListenerSet.forEach {
                    it.onAdHockStateChanged()
                }
            }
        }

        override fun onChannelUserChanged(sessionList: MutableList<String>) {
            ALog.i(TAG, "onChannelUserChanged ${sessionList.size}")
            AmeDispatcher.singleScheduler.scheduleDirect {
                eventListenerSet.forEach {
                    it.onChannelUserChanged(sessionList)
                }
            }
        }
    }


    internal fun setTargetClass(clazz: Class<out Activity>?) {
        mTargetClass = clazz
    }


    fun myNetId():String {
        return sdkApi?.myNetId()?:""
    }

    fun getNetSnapshot():NetSnapshot {
        return netSnapshot
    }

    fun getLogSource():AdHocLoggerSource {
        return logSource
    }


    fun nodeCount(): Long {
        return sdkApi?.nodeCount()?:0L
    }


    fun isReady(): Boolean {
        return sdkConnection.connected
    }


    fun isInited(): Boolean {
        return binding
    }


    fun startProxy() {
        ALog.i(TAG, "startProxy ${isReady()}")
        if (isReady()) {
            sdkHelper?.proxy?.start{
                sdkHelper?.scanner?.start { }
                sdkHelper?.broadcaster?.start { }
            }
        }
    }


    fun init(context: Context): Boolean {
        ALog.i(TAG, "init for adhoc")
        if (binding) {
            ALog.i(TAG, "init already called")
            return true
        }

        netSnapshot.reset()

        binding = true

        val intent = Intent(context, MessengerService::class.java)
        binding = context.bindService(intent, sdkConnection, Service.BIND_AUTO_CREATE)

        ALog.i(TAG, "init binding service for adhoc result: $binding")
        return binding
    }


    fun unInit(context: Context) {
        ALog.i(TAG, "unInit  for adhoc")
        if (!binding) {
            return
        }
        binding  = false

        val sdkBinder = this.sdkApi
        val sdkHelper = this.sdkHelper
        sdkApi = null
        this.sdkHelper = null

        AmeDispatcher.singleScheduler.scheduleDirect {
            this.messengerSdk.unInit()

            netSnapshot.reset()
            try {
                sdkHelper?.proxy?.stop{}
                sdkHelper?.scanner?.stop{}
                sdkHelper?.broadcaster?.stop {  }

                sdkBinder?.removeAdHocListener(adHocListener)
                sdkBinder?.uninit()
                context.unbindService(sdkConnection)
            } catch (e:Throwable) {
                ALog.e(TAG, "unInit", e)
            }
        }
    }


    fun addEventListener(listener: IAdHocSDKEventListener) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.add(listener)
        }
    }

    fun isScanning():Boolean {
        return sdkHelper?.scanner?.isRunning()?:false
    }

    fun startScan():Boolean {
        ALog.i(TAG, "startScan ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.scanner?.start {}
            }
            return true
        }
        return false
    }

    fun stopScan() {
        ALog.i(TAG, "stopScan ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.scanner?.stop {}
            }
        }
    }


    fun startAdHocServer():Boolean {
        ALog.i(TAG, "startAdHocServer ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.proxy?.start {}
            }
            return true
        }
        return false
    }

    fun stopAdHocServer() {
        ALog.i(TAG, "stopAdHocServer ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.proxy?.stop {}
            }
        }
    }

    fun isAdHocServerRunning(): Boolean {
        return sdkHelper?.proxy?.isRunning()?:false
    }

    fun startBroadcast():Boolean {
        ALog.i(TAG, "startBroadcast ${isReady()}")
        if (isReady() && isAdHocServerRunning()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.broadcaster?.start{}
            }
            return true
        }
        return false
    }

    fun stopBroadcast() {
        ALog.i(TAG, "stopBroadcast ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.broadcaster?.stop() {}
            }
        }
    }

    fun isBroadcasting(): Boolean {
        return sdkHelper?.broadcaster?.isRunning()?:false
    }

    fun repairAdHocServer() {
        ALog.i(TAG, "repairAdHocServer ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.proxy?.stop {
                    sdkHelper?.proxy?.start() {}
                }
            }
        }
    }

    fun repairScanner() {
        ALog.i(TAG, "repairScanner ${isReady()}")
        if (isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkHelper?.scanner?.stop {
                    sdkHelper?.scanner?.start() {}
                }

                sdkHelper?.broadcaster?.stop {
                    sdkHelper?.broadcaster?.start { }
                }
            }
        }
    }

    fun getSearchResult(): Int {
        return sdkApi?.searchResult?:0
    }

    fun getChannelUserList(sessionId:String):List<ChannelUserInfo> {
        return sdkApi?.getChannelUserList(sessionId)?: listOf()
    }

    fun getChannelUserCount(sessionId: String):Int {
        return sdkApi?.getChannelUserCount(sessionId)?:0
    }

    interface IAdHocSDKEventListener {
        fun onAdHocReady() {}
        fun onAdHockStateChanged() {}
        fun onChannelUserChanged(sessionId: List<String>){}
    }

    data class NetSnapshot(var serverName:String = "",
                           var myNetId:String = "",
                           var myNetGroupId:String = "",
                           var serverIp:String = "",
                           var myIp:String = "",
                           var connState:AdHocConnState = AdHocConnState.INIT,
                           var errorCode:Int = 0,
                           var clientSet:MutableSet<String> = mutableSetOf()
                           ) {

        fun reset() {
            serverName = ""
            myNetId = ""
            myNetGroupId = ""
            connState = AdHocConnState.INIT
            errorCode = 0
            clientSet = mutableSetOf()
            serverIp = ""
            myIp = ""
        }
    }
}