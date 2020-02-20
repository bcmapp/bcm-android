package com.bcm.messenger.chats.privatechat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatCallFloatWindow
import com.bcm.messenger.chats.components.ChatRtcCallScreen
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.checkOverlaysPermission
import com.bcm.messenger.common.utils.requestOverlaysPermission
import com.bcm.messenger.common.utils.startForegroundServiceCompat
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.chats_activity_webrtc_call.*

/**
 *
 * Created by wjh on 2018/04/16
 */
@Route(routePath = ARouterConstants.Activity.CHAT_CALL_PATH)
class ChatRtcCallActivity : AccountSwipeBaseActivity() {

    companion object {
        private const val TAG = "ChatRtcCallActivity"
        private const val REQUEST_OVERLAYS_PERMISSION = 100
    }

    private var mCallType: CameraState = CameraState.UNKNOWN

    private var mCallConnected = false
    private var isEnd = false

    private val mHeadsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            ALog.i(TAG, "receive action: ${intent?.action}")
            if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                chats_rtc_screen?.updateSpeakerState()

            } else if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                chats_rtc_screen?.updateSpeakerState()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAYS_PERMISSION) {
            handleOverlayPermission(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_OVERLAYS_PERMISSION -> handleOverlayPermission(true)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.w(TAG, "onCreate()")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        disableClipboardCheck()
        super.onCreate(savedInstanceState)
        setSwipeBackEnable(false)
        setContentView(R.layout.chats_activity_webrtc_call)
        initializeResources()

        PermissionUtil.checkAudio(this) { granted ->
            if (!granted) {
                chats_rtc_screen?.hangup()
                finish()

            } else {
                val intent = Intent(this, WebRtcCallService::class.java)
                intent.action = WebRtcCallService.ACTION_GRANTED_AUDIO
                intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
                startForegroundServiceCompat(intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        //WebRtcCallService.clearWebRtcCallType()
    }

    override fun onDestroy() {
        super.onDestroy()
        ALog.i(TAG, "onDestroy")
        unregisterReceiver(mHeadsetReceiver)
        chats_rtc_screen?.setOnChatRtcCallActionListener(null)
    }

    override fun onBackPressed() {
        if (isEnd) {
            super.onBackPressed()
        } else {
            handleOverlayPermission(true)
        }
    }


    private fun handleOverlayPermission(isFinish: Boolean) {
        if (checkOverlaysPermission()) {
            if (isFinish) {
                if (!isEnd) {
                    ChatCallFloatWindow.show(accountContext, chats_rtc_screen.getCurrentCallEvent())
                }
                finish()
            }
        } else {
            AlertDialog.Builder(this).setMessage(getString(R.string.chats_call_no_overlay_permission_message))
                    .setPositiveButton(getString(R.string.chats_call_no_overlay_permission_handle)) { dialog, _ ->
                        dialog.dismiss()
                        requestOverlaysPermission(REQUEST_OVERLAYS_PERMISSION)
                    }
                    .setNeutralButton(getString(R.string.chats_call_no_overlay_permission_wait)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.chats_call_no_overlay_permission_cancel)) { dialog, _ ->
                        dialog.dismiss()
                        if (isFinish) {
                            chats_rtc_screen?.hangup()
                            finish()
                        }
                    }
                    .show()
        }
    }


    private fun initializeResources() {
        val callType = intent.getStringExtra(ARouterConstants.PARAM.PRIVATE_CALL.PARAM_CALL_TYPE)?:CameraState.Direction.NONE.toString()
        mCallType = when (CameraState.Direction.valueOf(callType)) {
            CameraState.Direction.FRONT -> CameraState(CameraState.Direction.FRONT, 2)
            CameraState.Direction.BACK -> CameraState(CameraState.Direction.BACK, 2)
            else -> CameraState.UNKNOWN
        }

        chats_rtc_screen.setOnChatRtcCallActionListener(object : ChatRtcCallScreen.OnChatRtcCallActionListener {

            override fun onMinimize() {
                onBackPressed()
            }

            override fun onVideoChanged(localVideoStatus: CameraState, remoteVideoStatus: Boolean) {
                mCallType = localVideoStatus
            }

            override fun onEnd(recipient: Recipient?) {
                isEnd = true
                mCallConnected = false
                chats_rtc_screen?.postDelayed({
                    finish()
                }, WebRtcCallService.FINISH_DELAY)

            }

            override fun onConnect(recipient: Recipient) {
                Log.d(TAG, "onConnect")
                window?.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)
                if (mCallConnected) {
                    return
                } else {
                    mCallConnected = true

                    when (mCallType.activeDirection) {
                        CameraState.Direction.FRONT -> chats_rtc_screen?.openFaceVideo()
                        CameraState.Direction.BACK -> chats_rtc_screen?.openBackVideo()
                        else -> chats_rtc_screen?.closeLocalVideo()
                    }
                }

            }

            override fun onUnknown(recipient: Recipient?) {
                mCallConnected = false
            }

            override fun onUntrusted(recipient: Recipient?) {
                mCallConnected = false
            }
        })

        registerReceiver(mHeadsetReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        })
    }

}