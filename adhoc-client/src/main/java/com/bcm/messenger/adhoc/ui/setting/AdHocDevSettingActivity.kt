package com.bcm.messenger.adhoc.ui.setting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.sdk.AdHocConnState
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import kotlinx.android.synthetic.main.adhoc_dev_setting_view.*
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.utility.QuickOpCheck
import java.lang.StringBuilder

class AdHocDevSettingActivity: SwipeBaseActivity(), AdHocSDK.IAdHocSDKEventListener {
    companion object {
        fun router(context:Context) {
            val intent = Intent(context, AdHocDevSettingActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_dev_setting_view)
        adhoc_dev_setting_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                super.onClickLeft()
                finish()
            }
        })

        AdHocSDK.addEventListener(this)

        adhoc_dev_setting_search.setSwitchEnable(false)
        adhoc_dev_setting_broadcast.setSwitchEnable(false)
        adhoc_dev_setting_start_server.setSwitchEnable(false)

        adhoc_dev_setting_broadcast.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val broadcast = !adhoc_dev_setting_broadcast.getSwitchStatus()
            if (broadcast) {
                AdHocSDK.startBroadcast()
            } else {
                AdHocSDK.stopBroadcast()
            }
        }

        adhoc_dev_setting_start_server.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val service = !adhoc_dev_setting_start_server.getSwitchStatus()
            if (service) {
                AdHocSDK.startAdHocServer()
            } else {
                AdHocSDK.stopAdHocServer()
            }
        }

        adhoc_dev_setting_search.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val searching = !adhoc_dev_setting_search.getSwitchStatus()
            if (searching) {
                AdHocSDK.startScan()
            } else {
                AdHocSDK.stopScan()
            }
        }

        adhoc_dev_setting_log.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            AdHocLogActivity.router(this)
        }

        updateState()
    }


    private fun updateState() {
        adhoc_dev_setting_search.setSwitchStatus(AdHocSDK.isScanning())
        adhoc_dev_setting_broadcast.setSwitchStatus(AdHocSDK.isBroadcasting())
        adhoc_dev_setting_start_server.setSwitchStatus(AdHocSDK.isAdHocServerRunning())


        val snapshot = AdHocSDK.getNetSnapshot()
        val connStateBuiler = StringBuilder()

        connStateBuiler.append("My ID: ").append(AdHocSDK.myNetId())
        connStateBuiler.append("\nState: ").append(snapshot.connState.toString())
        if (snapshot.connState == AdHocConnState.CONNECT_FAILED) {
            connStateBuiler.append("   error:${snapshot.errorCode}")
        }


        if (!snapshot.myNetGroupId.isBlank() || AdHocSDK.nodeCount() > 0) {
            connStateBuiler.append("\n\n").append("NetGroup: ")
            connStateBuiler.append("\n\t\t").append("Node Count: ").append(AdHocSDK.nodeCount())
            connStateBuiler.append("\n\t\t").append("Group ID: ").append(snapshot.myNetGroupId)
        }

        connStateBuiler.append("\n\n").append("Parent SSID: ").append(snapshot.serverName)
        connStateBuiler.append("\n").append("Parent Ip: ").append(snapshot.serverIp)
        connStateBuiler.append("\n").append("MyIp: ").append(snapshot.myIp)

        connStateBuiler.append("\n\n").append("Child List ")

        val clientIps = snapshot.clientSet.toList()
        connStateBuiler.append("\n\t\t").append("Node Count: ").append(snapshot.clientSet.size)
        for (i in clientIps) {
            connStateBuiler.append("\n\t\t").append(i)
        }

        adhoc_dev_setting_conn_state.setSubName(connStateBuiler.toString())
    }

    override fun onAdHockStateChanged() {
        super.onAdHockStateChanged()
        AmeDispatcher.mainThread.dispatch {
            updateState()
        }
    }
}