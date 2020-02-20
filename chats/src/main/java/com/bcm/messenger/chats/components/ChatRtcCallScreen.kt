package com.bcm.messenger.chats.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.android.synthetic.main.chats_webrtc_call_screen.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.webrtc.SurfaceViewRenderer
import kotlin.math.absoluteValue

/**
 *
 * Created by wjh on 2018/04/16
 */
class ChatRtcCallScreen : ConstraintLayout, RecipientModifiedListener {

    companion object {
        private const val TAG = "ChatRtcCallScreen"
    }

    /**
     * callback
     */
    interface OnChatRtcCallActionListener {

        fun onMinimize()
        fun onEnd(recipient: Recipient?)
        fun onConnect(recipient: Recipient)
        fun onUnknown(recipient: Recipient?)
        fun onUntrusted(recipient: Recipient?)
        fun onVideoChanged(localVideoStatus: CameraState, remoteVideoStatus: Boolean)

    }

    private var mRecipient: Recipient? = null

    private var mMiniMode: Boolean = false
    private var mPassTime: Long = 0
    private var mStatus: String? = null

    private var mLocalVideoStatus: CameraState = CameraState.UNKNOWN
    private var mHasRemoteVideo: Boolean = false

    private var mListener: OnChatRtcCallActionListener? = null

    private var mCountRunnable: Runnable? = null

    private var mCallEvent: WebRtcViewModel? = null

    private var mWindowManager: WindowManager? = null
    private var mWindowParams: WindowManager.LayoutParams? = null

    private var mLastSwitchStatus: Boolean = false
    private var mLastSpeaker: Boolean = false

    private val audioManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val accountContext: AccountContext

    constructor(context: Context, accountContext: AccountContext) : super(context, null, 0) {
        this.accountContext = accountContext
        initialize()
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        this.accountContext = (context as AccountSwipeBaseActivity).accountContext
        initialize()
    }

    private var isClick = false
    private var startTime = 0L
    private var endTime = 0L
    private var touchX = 0
    private var touchY = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        ALog.d(TAG, "onTouchEvent: ${event.action}")
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        isClick = false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startTime = System.currentTimeMillis()
                touchX = event.x.toInt()
                touchY = event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {

                val params = mWindowParams ?: return true
                val moveX = event.x.toInt()
                val moveY = event.y.toInt()

                if ((touchX - moveX).absoluteValue > 5 && (touchY - moveY).absoluteValue > 5) {
                    params.x = x - touchX
                    params.y = y - touchY
                    mWindowManager?.updateViewLayout(this, params)
                    return false
                }

            }
            MotionEvent.ACTION_UP -> {
                endTime = System.currentTimeMillis()
                isClick = endTime - startTime <= 200
            }
        }

        if (isClick) {
            ALog.d("TAG", "performClick call event: ${mCallEvent?.state}")
            if (!mMiniMode && mCallEvent?.state == WebRtcViewModel.State.CALL_CONNECTED) {
                if (rtc_video_btn.visibility == View.VISIBLE) {
                    rtc_video_btn.visibility = View.GONE
                    rtc_left_btn.visibility = View.GONE
                    rtc_right_btn.visibility = View.GONE
                    rtc_end_btn.visibility = View.GONE
                    rtc_screen_minimize_iv.visibility = View.GONE
                    rtc_title.visibility = View.GONE
                } else {
                    rtc_video_btn.visibility = View.VISIBLE
                    rtc_left_btn.visibility = View.VISIBLE
                    rtc_right_btn.visibility = View.VISIBLE
                    rtc_end_btn.visibility = View.VISIBLE
                    rtc_screen_minimize_iv.visibility = View.VISIBLE
                    rtc_title.visibility = View.VISIBLE
                }
            }
            performClick()
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ALog.d(TAG, "onAttachedToWindow")
        mPassTime = 0
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ALog.d(TAG, "onDetachedFromWindow")
        EventBus.getDefault().unregister(this)
        if (mCallEvent?.state == WebRtcViewModel.State.CALL_CONNECTED) {
            EventBus.getDefault().postSticky(mPassTime.toString())
        } else {
            EventBus.getDefault().removeStickyEvent(String::class.java)
        }
        mPassTime = 0
        removeCallbacks(mCountRunnable)
    }

    private fun initialize() {
        View.inflate(context, R.layout.chats_webrtc_call_screen, this)

        local_render_layout.setRadius(6f.dp2Px())

        rtc_screen_minimize_iv.setOnClickListener {
            mListener?.onMinimize()
        }

        rtc_video_btn.setType(ChatRtcCallItem.TYPE_VIDEO, false)
        rtc_video_btn.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {
            override fun onChecked(type: Int, isChecked: Boolean): Boolean {
                val granted = PermissionUtil.checkCamera(context)
                if (!granted) {
                    PermissionUtil.checkCamera(context) { granted ->
                        if (granted) {
                            rtc_video_btn.setChecked(isChecked)
                        }
                    }

                } else {
                    handleVideoAction(isChecked)
                }
                return granted
            }
        })

        rtc_left_btn.setType(ChatRtcCallItem.TYPE_MUTE)
        rtc_left_btn.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {
            override fun onChecked(type: Int, isChecked: Boolean): Boolean {
                val granted = PermissionUtil.checkAudio(context)
                if (!granted) {
                    PermissionUtil.checkAudio(context) { granted ->
                        if (granted) {
                            rtc_left_btn.setChecked(isChecked)
                        }
                    }
                } else {
                    handleLeftButtonAction(type, isChecked)
                }

                return granted
            }
        })

        rtc_right_btn.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {
            override fun onChecked(type: Int, isChecked: Boolean): Boolean {

                val granted = if (type == ChatRtcCallItem.TYPE_SPEAKER) {
                    PermissionUtil.checkAudio(context)
                } else {
                    PermissionUtil.checkCamera(context)
                }

                if (!granted) {
                    if (type == ChatRtcCallItem.TYPE_SPEAKER) {
                        PermissionUtil.checkAudio(context) {
                            if (it) {
                                rtc_right_btn.setChecked(isChecked)
                            }
                        }
                    } else {
                        PermissionUtil.checkCamera(context) {
                            if (it) {
                                rtc_right_btn.setChecked(isChecked)
                            }
                        }
                    }
                } else {
                    handleRightButtonAction(type, isChecked)
                }

                return granted
            }
        })

        rtc_end_btn.setType(ChatRtcCallItem.TYPE_END)
        rtc_end_btn.setOnClickListener {
            handleEndCall()
        }

        rtc_accept_btn.setType(ChatRtcCallItem.TYPE_ACCEPT)
        rtc_accept_btn.setOnClickListener {
            handleAcceptCall()
        }

        rtc_decline_btn.setType(ChatRtcCallItem.TYPE_DECLINE)
        rtc_decline_btn.setOnClickListener {
            handleDenyCall()
        }

        rtc_small_control_btn.setOnClickListener {
            handleEndCall()
        }

        rtc_left_btn.isEnabled = false
        rtc_right_btn.isEnabled = false
        rtc_video_btn.isEnabled = false

        local_render_layout.isHidden = true
        remote_render_layout.isHidden = true

        remote_render_layout.isClickable = false
        local_render_layout.isClickable = false

        rtc_video_btn.setIconSize(40.dp2Px())
    }

    fun getCurrentRecipient(): Recipient? {
        return mRecipient
    }

    fun setWindowManagerAndParams(windowManager: WindowManager?, windowParams: WindowManager.LayoutParams?) {
        mWindowManager = windowManager
        mWindowParams = windowParams
    }

    fun getPassTime(): Long {
        return mPassTime
    }

    fun getCurrentCallEvent(): WebRtcViewModel? {
        return mCallEvent
    }

    fun hasLocalVideo(): Boolean {
        return mLocalVideoStatus.isEnabled
    }

    fun isLocalFaceVideo(): Boolean {
        return mLocalVideoStatus.activeDirection == CameraState.Direction.FRONT
    }

    fun isLocalBackVideo(): Boolean {
        return mLocalVideoStatus.activeDirection == CameraState.Direction.BACK
    }

    fun hasRemoteVideo(): Boolean {
        return mHasRemoteVideo
    }

    fun isMiniMode(): Boolean {
        return mMiniMode
    }

    fun setCallState(event: WebRtcViewModel?, miniMode: Boolean) {
        if (event == null) {
            return
        }

        mCallEvent = event
        mHasRemoteVideo = event.isRemoteVideoEnabled
        mLocalVideoStatus = event.localCameraState

        mLastSwitchStatus = mLocalVideoStatus.activeDirection == CameraState.Direction.FRONT
        mMiniMode = miniMode

        rtc_left_btn.setChecked(!event.isMicrophoneEnabled)

        ALog.d(TAG, "setCallState, mini: $miniMode, hasLocalVideo: $mLocalVideoStatus, mute${!event.isMicrophoneEnabled}")
        if (mMiniMode) {
            rtc_screen_minimize_iv.visibility = View.GONE
            rtc_video_btn.visibility = View.GONE
            rtc_photo.visibility = View.GONE
            rtc_name.visibility = View.GONE
            rtc_title.visibility = View.GONE
            rtc_active_layout.visibility = View.GONE
            rtc_passive_layout.visibility = View.GONE

            rtc_small_control_btn.visibility = View.VISIBLE
            val drawable: Drawable = if (mLocalVideoStatus != CameraState.UNKNOWN) {
                getDrawable(R.drawable.chats_message_call_video_sent)
            } else {
                getDrawable(R.drawable.chats_message_call_audio_sent)
            }
            drawable.setBounds(0, 0, 13.dp2Px(), 13.dp2Px())
            rtc_small_control_btn.setCompoundDrawables(drawable, null, null, null)

            setLocalVideoShow(false)
            setRemoteVideoShow(mHasRemoteVideo)
        } else {
            rtc_screen_minimize_iv.visibility = View.VISIBLE
            rtc_video_btn.visibility = View.VISIBLE
            rtc_small_control_btn.visibility = View.GONE

            if (mHasRemoteVideo) {
                rtc_photo.visibility = View.GONE
                rtc_name.visibility = View.GONE
            } else {
                rtc_photo.visibility = View.VISIBLE
                rtc_name.visibility = View.VISIBLE
            }

            if (event.state == WebRtcViewModel.State.CALL_INCOMING) {
                rtc_passive_layout.visibility = View.VISIBLE
                rtc_active_layout.visibility = View.GONE
            } else {
                rtc_passive_layout.visibility = View.GONE
                rtc_active_layout.visibility = View.VISIBLE
            }

            setRemoteVideoShow(mHasRemoteVideo)
            setLocalVideoShow(mLocalVideoStatus.isEnabled)
        }

        post {
            mListener?.onVideoChanged(mLocalVideoStatus, mHasRemoteVideo)
        }

        when (event.state) {
            WebRtcViewModel.State.CALL_CONNECTED -> handleCallConnected(event)
            WebRtcViewModel.State.NETWORK_FAILURE -> handleServerFailure(event)
            WebRtcViewModel.State.CALL_RINGING -> handleCallRinging(event)
            WebRtcViewModel.State.CALL_DISCONNECTED -> handleTerminate(event.recipient)
            WebRtcViewModel.State.NO_SUCH_USER -> handleNoSuchUser(event)
            WebRtcViewModel.State.RECIPIENT_UNAVAILABLE -> handleRecipientUnavailable(event)
            WebRtcViewModel.State.CALL_INCOMING -> handleIncomingCall(event)
            WebRtcViewModel.State.CALL_OUTGOING -> handleOutgoingCall(event)
            WebRtcViewModel.State.CALL_BUSY -> handleCallBusy(event)
            WebRtcViewModel.State.UNTRUSTED_IDENTITY -> handleUntrustedIdentity(event)
        }


        if (!miniMode) {
            if (event.isVideoCall) {
                rtc_video_btn.isEnabled = true
                rtc_video_btn.visibility = View.VISIBLE

                rtc_video_btn.setChecked(event.localCameraState.isEnabled)
            } else {
                rtc_video_btn.visibility = View.GONE
            }
        }
    }

    fun setOnChatRtcCallActionListener(listener: OnChatRtcCallActionListener?) {
        mListener = listener
    }

    fun updateSpeakerState() {

        if (rtc_right_btn.getType() == ChatRtcCallItem.TYPE_SPEAKER) {
            rtc_right_btn.isEnabled = !checkPluginHeadsets()
            ALog.i(TAG, "updateSpeakerState, isEnable: ${rtc_right_btn.isEnabled}")
        } else {
            rtc_right_btn.isEnabled = true
        }
    }

    private fun setLocalVideoShow(show: Boolean) {
        if (mCallEvent?.state != WebRtcViewModel.State.CALL_CONNECTED && show) {
            return
        }
        ALog.d(TAG, "setLocalVideoShow: $show")
        if (show) {
            local_render_layout.isHidden = false
            local_render_layout.getSurface()?.setMirror(true)
            local_render_layout.getSurface()?.setZOrderMediaOverlay(true)

            if (rtc_right_btn.getType() != ChatRtcCallItem.TYPE_SWITCH) {
                rtc_right_btn.setType(ChatRtcCallItem.TYPE_SWITCH)
            }
        } else if (!show && !local_render_layout.isHidden) {
            local_render_layout.isHidden = true

            if (rtc_right_btn.getType() == ChatRtcCallItem.TYPE_SWITCH) {
                rtc_right_btn.setType(ChatRtcCallItem.TYPE_SPEAKER)
            }
        }

        checkUserInfoShow()
        rtc_title.visibility = if (!show && !mMiniMode) View.VISIBLE else View.GONE
        rtc_video_btn.setChecked(show)
        ALog.d(TAG, "setLocalVideoShow render: ${local_render_layout.isHidden}")
    }

    private fun setRemoteVideoShow(show: Boolean) {
        if (mCallEvent?.state != WebRtcViewModel.State.CALL_CONNECTED && show) {
            return
        }
        ALog.d(TAG, "setRemoteVideoShow: $show")
        mHasRemoteVideo = show
        if (show) {
            remote_render_layout.isHidden = false
        } else if (!show && !remote_render_layout.isHidden) {
            remote_render_layout.isHidden = true
        }

        if (mMiniMode) {
            remote_render_layout.getSurface()?.setMirror(true)
        } else {
            remote_render_layout.getSurface()?.setMirror(false)
        }

        checkUserInfoShow()
        rtc_title.visibility = if (!show && !mMiniMode) View.VISIBLE else View.GONE
    }

    private fun checkUserInfoShow() {
        if (!remote_render_layout.isHidden || !local_render_layout.isHidden || mMiniMode) {
            rtc_photo.visibility = View.INVISIBLE
            rtc_name.visibility = View.INVISIBLE

        } else {
            rtc_photo.visibility = View.VISIBLE
            rtc_name.visibility = View.VISIBLE
        }
    }

    private fun setCallPassTime(passTime: Long) {
        mPassTime = passTime
        val timeString = DateUtils.convertTimingMinuteAndSecond(passTime)
        rtc_title.text = timeString
        rtc_small_control_btn.text = timeString
//        rtc_name.text = SpannableStringBuilder(mRecipient?.name ?: "")
//                .append("\n")
//                .append(StringAppearanceUtil.applyAppearance(timeString, 14.dp2Px()))
    }

    private fun doUpdateCallCardInactive(recipient: Recipient) {
        try {
            rtc_inactive_contact_layout.visibility = View.VISIBLE
            rtc_contact_layout.visibility = View.GONE

            val selfRecipient = Recipient.from(accountContext, accountContext.uid, true)
            rtc_to_photo.setPhoto(selfRecipient)
            rtc_to_name.text = selfRecipient.name

            rtc_from_photo.setPhoto(recipient)
            rtc_from_name.text = recipient.name

            setBackgroundPhoto(recipient)
        } catch (tr: Throwable) {
            ALog.e(TAG, "ChatRtcCallScreen show image error", tr)
        }
    }

    private fun doUpdateCallCard(recipient: Recipient, status: String?) {
        try {
            rtc_inactive_contact_layout.visibility = View.GONE
            rtc_contact_layout.visibility = View.VISIBLE

            rtc_photo.setPhoto(recipient)
            rtc_name.text = recipient.name
            setBackgroundPhoto(recipient)
        } catch (ex: Exception) {
            ALog.e(TAG, "ChatRtcCallScreen show image error", ex)
        }
    }

    private fun setBackgroundPhoto(recipient: Recipient) {
        val request = GlideApp.with(context.applicationContext).asBitmap()
        val w = context.getScreenWidth()
        val h = context.getScreenHeight()
        val loadObj = if (!recipient.localAvatar.isNullOrEmpty()) {
            recipient.localAvatar
        } else if (!recipient.bcmAvatar.isNullOrEmpty()) {
            recipient.bcmAvatar
        } else {
            IndividualAvatarView.getDefaultPortraitUrl(recipient.address.serialize())
        }
        request.load(loadObj)
        request.diskCacheStrategy(DiskCacheStrategy.ALL)
        request.override(w, h)

        request.into(object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                ALog.d(TAG, "onResourceReady")
                applyBitmap(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                ALog.d(TAG, "onLoadFailed")
                val errorBitmap = BitmapFactory.decodeResource(resources, R.drawable.common_recipient_photo_default_big)
                applyBitmap(errorBitmap)
            }

            private fun applyBitmap(bitmap: Bitmap) {
                try {
                    val newBitmap = bitmap.copy(bitmap.config, true)
                    if (newBitmap == null) {
                        rtc_photo_background.setImageDrawable(null)
                    } else {
                        rtc_photo_background.setImageBitmap(newBitmap.blurBitmap(context, 25f))
                        rtc_photo_background.setColorFilter(context.getColorCompat(R.color.chats_rct_call_background_filter), PorterDuff.Mode.SRC_OVER)
                    }
                } catch (ex: Exception) {
                    ALog.e(TAG, "doUpdateCallCard error", ex)
                }
            }
        })
    }


    private fun setCard(recipient: Recipient?, status: String? = null) {
        this.mStatus = status
        if (recipient == null) {
            return
        }
        if (mRecipient != recipient) {
            mRecipient?.removeListener(this)
            mRecipient = recipient
            mRecipient?.addListener(this)

            if (recipient.getPrivacyAvatar(true).isNullOrEmpty()) {
                AmeModuleCenter.contact(accountContext)?.checkNeedDownloadAvatar(true, recipient)
            }
        }

        if (status.isNullOrEmpty() && accountContext != AMELogin.majorContext) {
            doUpdateCallCardInactive(recipient)
        } else {
            doUpdateCallCard(recipient, status)
        }
    }

    override fun onModified(recipient: Recipient) {
        if (mRecipient == recipient) {
            if (accountContext != AMELogin.majorContext) {
                doUpdateCallCardInactive(recipient)
            } else {
                doUpdateCallCard(recipient, mStatus)
            }
        }
    }

    private fun handleSetMuteAudio(isMute: Boolean) {
        ALog.d(TAG, "handleSetMuteAudio: $isMute")
        try {
            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_SET_MUTE_AUDIO
            intent.putExtra(WebRtcCallService.EXTRA_MUTE, isMute)
            callRtcService(intent)

        } catch (ex: Exception) {
            ALog.e(TAG, "handleSetMuteAudio error", ex)
        }
    }

    fun closeLocalVideo() {
        ALog.d(TAG, "closeLocalVideo")
        try {
            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_SET_MUTE_VIDEO
            intent.putExtra(WebRtcCallService.EXTRA_MUTE, true)
            callRtcService(intent)

            if (mCallEvent?.state == WebRtcViewModel.State.CALL_CONNECTED) {
                rtc_right_btn.setType(ChatRtcCallItem.TYPE_SPEAKER)
                rtc_right_btn.setChecked(false)

                handleSpeaker(false)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "closeLocalVideo error", ex)
        }
    }

    fun openFaceVideo() {
        ALog.d(TAG, "openFaceVideo")
        handleCameraSwitch(true)
    }

    fun openBackVideo() {
        ALog.d(TAG, "openBackVideo")
        handleCameraSwitch(false)
    }

    fun hangup() {
        handleEndCall()
    }

    private fun handleCameraSwitch(isFront: Boolean) {
        ALog.d(TAG, "handleCameraSwitch isFront: $isFront")
        try {
            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_FLIP_CAMERA
            callRtcService(intent)
        } catch (e: Exception) {
            ALog.e(TAG, "switch camera direction error", e)
        }
    }

    private fun handleCameraOpen() {
        ALog.d(TAG, "handleCameraOpen")
        try {

            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_SET_MUTE_VIDEO
            intent.putExtra(WebRtcCallService.EXTRA_MUTE, false)
            callRtcService(intent)

            if (mCallEvent?.state == WebRtcViewModel.State.CALL_CONNECTED) {
                rtc_right_btn.setType(ChatRtcCallItem.TYPE_SWITCH)
                val isSpeakerOn = !checkPluginHeadsets()
                handleSpeaker(isSpeakerOn)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "openBackVideo error", ex)
        }
    }

    private fun handleAcceptCall() {
        ALog.d(TAG, "handleAcceptCall")
        try {
            val event = mCallEvent ?: return
            setCard(event.recipient, resources.getString(R.string.common_phone_answering))

            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_ANSWER_CALL
            callRtcService(intent)

        } catch (ex: Exception) {
            ALog.e(TAG, "handleAcceptCall error", ex)
        }
    }

    private fun handleEndCall() {
        ALog.d(TAG, "handleEndCall")
        try {
            val event = mCallEvent ?: return
            if (event.state == WebRtcViewModel.State.CALL_INCOMING) {
                val intent = Intent(context, WebRtcCallService::class.java)
                intent.action = WebRtcCallService.ACTION_LOCAL_HANGUP
                callRtcService(intent)
                setCard(event.recipient, resources.getString(R.string.common_phone_ending_call))
                postDelayed({
                    mListener?.onEnd(mRecipient)
                }, WebRtcCallService.FINISH_DELAY)
            } else {
                val intent = Intent(context, WebRtcCallService::class.java)
                intent.action = WebRtcCallService.ACTION_LOCAL_HANGUP
                callRtcService(intent)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "handleEndCall error", ex)
        }
    }

    private fun handleDenyCall() {
        ALog.d(TAG, "handleDenyCall")
        try {
            val event = mCallEvent ?: return
            if (event.state == WebRtcViewModel.State.CALL_INCOMING) {
                val intent = Intent(context, WebRtcCallService::class.java)
                intent.action = WebRtcCallService.ACTION_DENY_CALL
                callRtcService(intent)
                setCard(event.recipient, resources.getString(R.string.common_phone_ending_call))
                postDelayed({
                    mListener?.onEnd(mRecipient)
                }, WebRtcCallService.FINISH_DELAY)
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "handleDenyCall error", e)
        }
    }

    private fun callRtcService(intent: Intent) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
        context.startForegroundServiceCompat(intent)
    }

    private fun handleIncomingCall(event: WebRtcViewModel) {
        if (event.isVideoCall) {
            rtc_video_btn.visibility = View.VISIBLE
        } else {
            rtc_video_btn.visibility = View.GONE
        }

        setCard(event.recipient, "")
    }

    private fun handleOutgoingCall(event: WebRtcViewModel) {
        if (event.isVideoCall) {
            rtc_video_btn.visibility = View.VISIBLE
        } else {
            rtc_video_btn.visibility = View.GONE
        }

        rtc_title.visibility = View.GONE
        rtc_left_btn.visibility = View.GONE
        rtc_right_btn.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_dialing))
    }

    private fun handleTerminate(recipient: Recipient?) {
        ALog.w(TAG, "handleTerminate called")
        rtc_video_btn.visibility = View.GONE
        rtc_active_layout.visibility = View.GONE
        rtc_passive_layout.visibility = View.GONE
        setCard(recipient, resources.getString(R.string.common_phone_ending_call))
        EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleCallRinging(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallRinging")
        if (event.isVideoCall) {
            rtc_video_btn.visibility = View.VISIBLE
        } else {
            rtc_video_btn.visibility = View.GONE
        }

        rtc_title.visibility = View.GONE
        rtc_left_btn.visibility = View.GONE
        rtc_right_btn.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_ringing))
    }

    private fun handleCallBusy(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallBusy")
        if (event.isVideoCall) {
            rtc_video_btn.visibility = View.VISIBLE
        } else {
            rtc_video_btn.visibility = View.GONE
        }
        rtc_title.visibility = View.GONE
        rtc_left_btn.visibility = View.GONE
        rtc_right_btn.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_busy))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleCallConnected(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallConnected")
        if (mMiniMode) {
            rtc_video_btn.visibility = View.GONE
        } else {
            rtc_video_btn.visibility = View.VISIBLE
        }
        rtc_left_btn.visibility = View.VISIBLE
        rtc_right_btn.visibility = View.VISIBLE

        setCard(event.recipient, resources.getString(R.string.common_phone_connected))
        setLocalAndRemoteRender(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer)

        rtc_left_btn.isEnabled = true
        rtc_video_btn.isEnabled = true
        rtc_right_btn.isEnabled = true
        updateSpeakerState()

        setCallPassTime(mPassTime)
        if (mCountRunnable == null) {
            rtc_title.visibility = View.VISIBLE
            mCountRunnable = Runnable {
                setCallPassTime(mPassTime + 1000)
                postDelayed(mCountRunnable, 1000L)
            }
            postDelayed(mCountRunnable, 1000L)
        }

        post {
            mRecipient?.let {
                mListener?.onConnect(it)
            }
        }
    }

    private fun handleRecipientUnavailable(event: WebRtcViewModel) {
        rtc_video_btn.visibility = View.GONE
        rtc_passive_layout.visibility = View.GONE
        rtc_active_layout.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_recipient_unavailable))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleServerFailure(event: WebRtcViewModel) {
        rtc_video_btn.visibility = View.GONE
        rtc_passive_layout.visibility = View.GONE
        rtc_active_layout.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_network_failed))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleNoSuchUser(event: WebRtcViewModel) {
        if (event.isVideoCall) {
            rtc_video_btn.visibility = View.VISIBLE
        } else {
            rtc_video_btn.visibility = View.GONE
        }
        rtc_passive_layout.visibility = View.GONE
        rtc_active_layout.visibility = View.GONE
        handleTerminate(event.recipient)
        post {
            mListener?.onUnknown(event.recipient)
        }
    }

    private fun handleUntrustedIdentity(event: WebRtcViewModel) {
        rtc_video_btn.visibility = View.GONE
        rtc_passive_layout.visibility = View.GONE
        rtc_active_layout.visibility = View.GONE
        val recipient = event.recipient
        post {
            mListener?.onUntrusted(recipient)
        }
    }

    private fun handleSpeaker(speakerSwitch: Boolean) {
        try {
            ALog.d(TAG, "handleSpeaker speakerSwitch: $speakerSwitch")
            if (audioManager.isSpeakerphoneOn != speakerSwitch) {
                audioManager.isSpeakerphoneOn = speakerSwitch

                if (speakerSwitch && audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
                mLastSpeaker = speakerSwitch
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "handleSpeaker error", ex)
        }
    }

    private fun setLocalAndRemoteRender(localRenderer: SurfaceViewRenderer?, remoteRenderer: SurfaceViewRenderer?) {
        ALog.d(TAG, "setLocalAndRemoteRender $localRenderer $remoteRenderer")
        local_render_layout.setSurface(localRenderer)
        remote_render_layout.setSurface(remoteRenderer)
    }

    private fun handleVideoAction(switchStatus: Boolean) {
        ALog.d(TAG, "handleVideoAction switch: $switchStatus ${mCallEvent?.state}")
        if (switchStatus) {
            handleCameraOpen()
        } else {
            closeLocalVideo()
        }
    }

    private fun handleLeftButtonAction(type: Int, switchStatus: Boolean) {
        ALog.d(TAG, "handleLeftButtonAction switch: $switchStatus")
        handleSetMuteAudio(switchStatus)
    }

    private fun handleRightButtonAction(type: Int, switchStatus: Boolean) {
        ALog.d(TAG, "handleRightButtonAction switch: $switchStatus")
        if (type == ChatRtcCallItem.TYPE_SPEAKER) {
            handleSpeaker(switchStatus)
        } else if (type == ChatRtcCallItem.TYPE_SWITCH) {
            handleCameraSwitch(switchStatus)
        }
    }

    private fun checkPluginHeadsets(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            audioDevices.forEach {
                ALog.i(TAG, "checkPluginHeadsets type: ${it.type}")
                if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    return true
                }
            }
            return false
        } else {
            return audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: WebRtcViewModel) {
        ALog.i(TAG, "Got messageRecord from service: $event")
        setCallState(event, mMiniMode)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(passTime: String) {
        ALog.i(TAG, "Got pass time from service: $passTime")
        try {
            setCallPassTime(passTime.toLong())
        } catch (ex: Exception) {
            ALog.e(TAG, "ChatRtcCallScreen got pass time error", ex)
        }
    }
}
