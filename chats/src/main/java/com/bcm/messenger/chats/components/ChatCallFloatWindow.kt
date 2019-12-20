package com.bcm.messenger.chats.components

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcViewModel
import com.bcm.messenger.chats.provider.ChatModuleImp
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference


/**
 * Created by wjh on 2018/4/19
 */
object ChatCallFloatWindow {

    private const val TAG = "ChatCallFloatWindow"
    private const val SHOW_DELAY = 100
    private const val HIDE_DELAY = 100

    private var mController: FloatController? = null

    internal class FloatController(context: Context) {

        private var mContextReference: WeakReference<Context>? = null
        private var mWM: WindowManager? = null
        private var mScreen: ChatRtcCallScreen? = null

        private var mShowing = false
        private var mHandler: Handler

        init {
            mHandler = Handler(Looper.getMainLooper())
            mContextReference = if (context is Application) {
                WeakReference(context)
            } else {
                WeakReference(context.applicationContext)
            }
        }

        internal fun isShowing(): Boolean {
            return mShowing
        }

        private fun getWindowParams(context: Context): WindowManager.LayoutParams {
            val mParams = WindowManager.LayoutParams()
            mParams.width = context.resources.getDimensionPixelSize(R.dimen.chats_webrtc_call_mini_width)
            mParams.height = context.resources.getDimensionPixelSize(R.dimen.chats_webrtc_call_mini_height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                mParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            mParams.gravity = Gravity.START or Gravity.TOP
            mParams.flags = (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            return mParams
        }

        internal fun show(event: WebRtcViewModel?, delay: Int, callback: ActionCallback? = null) {
            if (mShowing) {
                return
            }
            val d = if (delay <= 0) {
                0
            } else {
                delay
            }
            mHandler.postDelayed({
                handleShow(event)

                callback?.onComplete()

            }, d.toLong())
        }

        internal fun hide(delay: Int, callback: ActionCallback? = null) {
            if (!mShowing) {
                return
            }
            val d = if (delay <= 0) {
                0
            } else {
                delay
            }
            mHandler.postDelayed({
                handleHide()

                callback?.onComplete()

            }, d.toLong())
        }

        private fun handleShow(event: WebRtcViewModel?) {
            try {
                val context = mContextReference?.get() ?: return
                val wmParams = getWindowParams(context)
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val dm = DisplayMetrics()
                windowManager.defaultDisplay?.getMetrics(dm)
                wmParams.x = dm.widthPixels - wmParams.width - context.resources.getDimensionPixelSize(R.dimen.chats_webrtc_call_mini_x)
                wmParams.y = context.resources.getDimensionPixelSize(R.dimen.chats_webrtc_call_mini_y)

                mWM = windowManager
                val screen = ChatRtcCallScreen(context)
                mScreen = screen
                screen.setCallState(event, true)

                windowManager.addView(screen, wmParams)
                screen.setWindowManagerAndParams(windowManager, wmParams)
                screen.setOnChatRtcCallActionListener(object : ChatRtcCallScreen.OnChatRtcCallActionListener {
                    override fun onMinimize() {
                    }

                    override fun onVideoChanged(localVideoStatus: CameraState, remoteVideoStatus: Boolean) {
                    }

                    override fun onEnd(recipient: Recipient?) {
                        screen.postDelayed({
                            hide(0)
                        }, WebRtcCallService.FINISH_DELAY)
                    }

                    override fun onConnect(recipient: Recipient) {

                    }

                    override fun onUnknown(recipient: Recipient?) {

                    }

                    override fun onUntrusted(recipient: Recipient?) {

                    }

                })
                screen.setOnClickListener {
                    val currentCallState = screen.getCurrentCallEvent() ?: return@setOnClickListener
                    ALog.i(TAG, "current call event: ${currentCallState.state}")
                    if (currentCallState.state == WebRtcViewModel.State.CALL_CONNECTED ||
                            currentCallState.state == WebRtcViewModel.State.CALL_INCOMING ||
                            currentCallState.state == WebRtcViewModel.State.CALL_OUTGOING ||
                            currentCallState.state == WebRtcViewModel.State.CALL_RINGING) {
                        hide(HIDE_DELAY, object : ActionCallback {
                            override fun onComplete() {
                                mHandler.post {
                                    ALog.i(TAG, "ChatCallFloatWindow got click")
                                    ChatModuleImp().startRtcCallActivity(AppContextHolder.APP_CONTEXT, currentCallState.localCameraState.activeDirection.ordinal)
                                }

                            }

                        })
                    }
                }

                mShowing = true

            } catch (ex: Exception) {
                ALog.e(TAG, "ChatCallFloatWindow show error", ex)
                mScreen?.hangup()
                mScreen = null
                mShowing = false
            }

        }

        private fun handleHide() {
            try {
                val screen = mScreen
                val windowManager = mWM
                if (screen != null && windowManager != null) {
                    if (screen.parent != null) {
                        windowManager.removeView(screen)
                    }
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "ChatCallFloatWindow hide error", ex)
            } finally {
                mWM = null
                mScreen = null
                mShowing = false
            }
        }

    }

    interface ActionCallback {
        fun onComplete()
    }

    fun show(event: WebRtcViewModel?) {
        if (mController == null) {
            mController = FloatController(AppContextHolder.APP_CONTEXT)
        }
        if (mController?.isShowing() == false) {
            mController?.show(event, SHOW_DELAY)
        }
    }

    fun hide() {
        mController?.hide(HIDE_DELAY)
    }

    fun hasWebRtcCalling(): Boolean {
        return mController?.isShowing() ?: false
    }


}