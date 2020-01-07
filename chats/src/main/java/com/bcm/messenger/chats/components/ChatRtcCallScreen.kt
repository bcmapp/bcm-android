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
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.webrtc.SurfaceViewRenderer
import org.whispersystems.libsignal.IdentityKey
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

    private lateinit var mLocalRenderLayout: ChatRenderLayout
    private lateinit var mRemoteRenderLayout: ChatRenderLayout

    private lateinit var mActiveLayout: View
    private lateinit var mPassiveLayout: View

    private var mRecipient: Recipient? = null

    private lateinit var mPhotoView: IndividualAvatarView
    private lateinit var mCallNameView: TextView
    private lateinit var mPhotoBackground: ImageView
    private lateinit var mEndView: ChatRtcCallItem
    private lateinit var mVideoView: ChatRtcCallItem
    private lateinit var mActionLeftView: ChatRtcCallItem
    private lateinit var mActionRightView: ChatRtcCallItem
    private lateinit var mAcceptView: ChatRtcCallItem
    private lateinit var mDeclineView: ChatRtcCallItem
    private lateinit var mSmallStatusItem: TextView
    private lateinit var mMinimizeView: ImageView
    private lateinit var mPassTimeView: TextView

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

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
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
                if (mVideoView.visibility == View.VISIBLE) {
                    mVideoView.visibility = View.GONE
                    mActionLeftView.visibility = View.GONE
                    mActionRightView.visibility = View.GONE
                    mEndView.visibility = View.GONE
                    mMinimizeView.visibility = View.GONE
                    mPassTimeView.visibility = View.GONE
                } else {
                    mVideoView.visibility = View.VISIBLE
                    mActionLeftView.visibility = View.VISIBLE
                    mActionRightView.visibility = View.VISIBLE
                    mEndView.visibility = View.VISIBLE
                    mMinimizeView.visibility = View.VISIBLE
                    mPassTimeView.visibility = View.VISIBLE

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

        this.mLocalRenderLayout = findViewById(R.id.local_render_layout)
        this.mLocalRenderLayout.setRadius(6f.dp2Px())
        this.mRemoteRenderLayout = findViewById(R.id.remote_render_layout)

        this.mMinimizeView = findViewById(R.id.rtc_screen_minimize_iv)
        this.mPhotoView = findViewById(R.id.rtc_photo)
        this.mCallNameView = findViewById(R.id.rtc_name)
        this.mPhotoBackground = findViewById(R.id.rtc_photo_background)

        this.mPassiveLayout = findViewById(R.id.rtc_passive_layout)
        this.mActiveLayout = findViewById(R.id.rtc_active_layout)

        this.mMinimizeView.setOnClickListener {
            mListener?.onMinimize()
        }

        this.mVideoView = findViewById(R.id.rtc_video_btn)
        this.mVideoView.setType(ChatRtcCallItem.TYPE_VIDEO, false)
        this.mVideoView.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {
            override fun onChecked(type: Int, isChecked: Boolean): Boolean {
                val granted = PermissionUtil.checkCamera(context)
                if (!granted) {
                    PermissionUtil.checkCamera(context) { granted ->
                        if (granted) {
                            mVideoView.setChecked(isChecked)
                        }
                    }

                }else {
                    handleVideoAction(isChecked)
                }
                return granted

            }

        })

        this.mActionLeftView = findViewById(R.id.rtc_left_btn)
        this.mActionLeftView.setType(ChatRtcCallItem.TYPE_MUTE)
        this.mActionLeftView.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {

            override fun onChecked(type: Int, isChecked: Boolean): Boolean {

                val granted = PermissionUtil.checkAudio(context)
                if (!granted) {
                    PermissionUtil.checkAudio(context) { granted ->
                        if (granted) {
                            mActionLeftView.setChecked(isChecked)
                        }
                    }
                }else {
                    handleLeftButtonAction(type, isChecked)
                }

                return granted
            }

        })

        this.mActionRightView = findViewById(R.id.rtc_right_btn)
        this.mActionRightView.setOnControlActionListener(object : ChatRtcCallItem.OnControlActionListener {
            override fun onChecked(type: Int, isChecked: Boolean): Boolean {

                val granted = if (type == ChatRtcCallItem.TYPE_SPEAKER) {
                    PermissionUtil.checkAudio(context)
                }else {
                    PermissionUtil.checkCamera(context)
                }

                if (!granted) {
                    if (type == ChatRtcCallItem.TYPE_SPEAKER) {
                        PermissionUtil.checkAudio(context) {
                            if (it) {
                                mActionRightView.setChecked(isChecked)
                            }
                        }
                    }else {
                        PermissionUtil.checkCamera(context) {
                            if (it) {
                                mActionRightView.setChecked(isChecked)
                            }
                        }
                    }

                }else {
                    handleRightButtonAction(type, isChecked)
                }

                return granted

            }

        })

        this.mEndView = findViewById(R.id.rtc_end_btn)
        this.mEndView.setType(ChatRtcCallItem.TYPE_END)
        this.mEndView.setOnClickListener {
            handleEndCall()
        }

        this.mAcceptView = findViewById(R.id.rtc_accept_btn)
        this.mAcceptView.setType(ChatRtcCallItem.TYPE_ACCEPT)
        this.mAcceptView.setOnClickListener {
            handleAcceptCall()
        }

        this.mDeclineView = findViewById(R.id.rtc_decline_btn)
        this.mDeclineView.setType(ChatRtcCallItem.TYPE_DECLINE)
        this.mDeclineView.setOnClickListener {
            handleDenyCall()
        }

        this.mSmallStatusItem = findViewById(R.id.rtc_small_control_btn)
        this.mSmallStatusItem.setOnClickListener {
            handleEndCall()
        }

        this.mPassTimeView = findViewById(R.id.rtc_time_tv)

        this.mActionLeftView.isEnabled = false
        this.mActionRightView.isEnabled = false
        this.mVideoView.isEnabled = false

        this.mLocalRenderLayout.isHidden = true
        this.mRemoteRenderLayout.isHidden = true

        this.mRemoteRenderLayout.isClickable = false
        this.mLocalRenderLayout.isClickable = false

        this.mVideoView.setIconSize(40.dp2Px())

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

        mActionLeftView.setChecked(!event.isMicrophoneEnabled)

        ALog.d(TAG, "setCallState, mini: $miniMode, hasLocalVideo: $mLocalVideoStatus")
        if (mMiniMode) {
            mMinimizeView.visibility = View.GONE
            mVideoView.visibility = View.GONE
            mPhotoView.visibility = View.GONE
            mCallNameView.visibility = View.GONE
            mPassTimeView.visibility = View.GONE
            mActiveLayout.visibility = View.GONE
            mPassiveLayout.visibility = View.GONE

            mSmallStatusItem.visibility = View.VISIBLE
            val drawable: Drawable = if (mLocalVideoStatus != CameraState.UNKNOWN) {
                getDrawable(R.drawable.chats_message_call_video_sent)
            } else {
                getDrawable(R.drawable.chats_message_call_audio_sent)
            }
            drawable.setBounds(0, 0, 13.dp2Px(), 13.dp2Px())
            this.mSmallStatusItem.setCompoundDrawables(drawable, null, null, null)

            setLocalVideoShow(false)
            setRemoteVideoShow(mHasRemoteVideo)

        } else {
            mMinimizeView.visibility = View.VISIBLE
            mVideoView.visibility = View.VISIBLE
            mSmallStatusItem.visibility = View.GONE

            if (mHasRemoteVideo) {
                mPhotoView.visibility = View.GONE
                mCallNameView.visibility = View.GONE
            } else {
                mPhotoView.visibility = View.VISIBLE
                mCallNameView.visibility = View.VISIBLE
            }

            if (event.state == WebRtcViewModel.State.CALL_INCOMING) {
                mPassiveLayout.visibility = View.VISIBLE
                mActiveLayout.visibility = View.GONE
            } else {
                mPassiveLayout.visibility = View.GONE
                mActiveLayout.visibility = View.VISIBLE
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
    }

    fun setOnChatRtcCallActionListener(listener: OnChatRtcCallActionListener?) {
        mListener = listener
    }

    fun updateSpeakerState() {

        if (mActionRightView.getType() == ChatRtcCallItem.TYPE_SPEAKER) {
            mActionRightView.isEnabled = !checkPluginHeadsets()
            ALog.i(TAG, "updateSpeakerState, isEnable: ${mActionRightView.isEnabled}")
        }else {
            mActionRightView.isEnabled = true
        }
    }

    private fun setUntrustedIdentity(personInfo: Recipient?, untrustedIdentity: IdentityKey?) {

    }

    private fun setLocalVideoShow(show: Boolean) {
        ALog.d(TAG, "setLocalVideoShow: $show")
        if (show && this.mLocalRenderLayout.isHidden) {
            this.mLocalRenderLayout.isHidden = false
            this.mLocalRenderLayout.getSurface()?.setMirror(true)
            this.mLocalRenderLayout.getSurface()?.setZOrderMediaOverlay(true)
        } else if (!show && !this.mLocalRenderLayout.isHidden) {
            this.mLocalRenderLayout.isHidden = true

        }
        checkUserInfoShow()
        this.mPassTimeView.visibility = if (show && !mMiniMode) View.VISIBLE else View.GONE
        this.mVideoView.setChecked(show)
    }

    private fun setRemoteVideoShow(show: Boolean) {
        ALog.d(TAG, "setRemoteVideoShow: $show")
        mHasRemoteVideo = show
        if (show && this.mRemoteRenderLayout.isHidden) {
            this.mRemoteRenderLayout.isHidden = false

        } else if (!show && !this.mRemoteRenderLayout.isHidden) {
            this.mRemoteRenderLayout.isHidden = true
        }

        if (mMiniMode) {
            this.mRemoteRenderLayout.getSurface()?.setMirror(true)
        } else {
            this.mRemoteRenderLayout.getSurface()?.setMirror(false)
        }

        checkUserInfoShow()
        this.mPassTimeView.visibility = if (show && !mMiniMode) View.VISIBLE else View.GONE
    }

    private fun checkUserInfoShow() {
        if (!mRemoteRenderLayout.isHidden || !mLocalRenderLayout.isHidden || mMiniMode) {
            this.mPhotoView.visibility = View.INVISIBLE
            this.mCallNameView.visibility = View.INVISIBLE

        }else {
            this.mPhotoView.visibility = View.VISIBLE
            this.mCallNameView.visibility = View.VISIBLE
        }
    }

    private fun setCallPassTime(passTime: Long) {
        mPassTime = passTime
        val timeString = DateUtils.convertTimingMinuteAndSecond(passTime)
        this.mPassTimeView.text = timeString
        this.mSmallStatusItem.text = timeString
        this.mCallNameView.text = SpannableStringBuilder(mRecipient?.name ?: "")
                .append("\n")
                .append(StringAppearanceUtil.applyAppearance(timeString, 14.dp2Px()))
    }

    private fun doUpdateCallCard(accountContext: AccountContext, recipient: Recipient, status: String?) {
        try {
            mPhotoView.setPhoto(accountContext, recipient)
            val request = GlideApp.with(context.applicationContext).asBitmap()
            val w = context.getScreenWidth()
            val h = context.getScreenHeight()
            val loadObj = if (!recipient.localAvatar.isNullOrEmpty()) {
                recipient.localAvatar
            }
            else if (!recipient.bcmAvatar.isNullOrEmpty()) {
                recipient.bcmAvatar
            }
            else {
                IndividualAvatarView.getDefaultPortraitUrl(recipient)
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
                            mPhotoBackground.setImageDrawable(null)
                        }else {
                            mPhotoBackground.setImageBitmap(newBitmap.blurBitmap(context, 25f))
                            mPhotoBackground.setColorFilter(context.getColorCompat(R.color.chats_rct_call_background_filter), PorterDuff.Mode.SRC_OVER)
                        }
                    }catch (ex: Exception) {
                        ALog.e(TAG, "doUpdateCallCard error", ex)
                    }
                }

            })

        } catch (ex: Exception) {
            ALog.e(TAG, "ChatRtcCallScreen show image error", ex)
        }
        mCallNameView.text = recipient.name
    }


    private fun setCard(accountContext: AccountContext, recipient: Recipient?, status: String? = null) {
        this.mStatus = status
        if (recipient == null) {
            return
        }
        if (mRecipient != recipient) {
            mRecipient?.removeListener(this)
            mRecipient = recipient
            mRecipient?.addListener(this)
            doUpdateCallCard(accountContext, recipient, status)

            if (recipient.getPrivacyAvatar(true).isNullOrEmpty()) {
                AmeModuleCenter.contact(accountContext)?.checkNeedDownloadAvatar(true, recipient)
            }
        }
    }

    override fun onModified(recipient: Recipient) {
        if (mRecipient == recipient) {
            doUpdateCallCard(recipient.address.context(), recipient, mStatus)
        }
    }

    private fun handleSetMuteAudio(isMute: Boolean) {
        ALog.d(TAG, "handleSetMuteAudio: $isMute")
        try {
            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_SET_MUTE_AUDIO
            intent.putExtra(WebRtcCallService.EXTRA_MUTE, isMute)
            context.startForegroundServiceCompat(intent)

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
            context.startForegroundServiceCompat(intent)

            mActionRightView.setType(ChatRtcCallItem.TYPE_SPEAKER)
            mActionRightView.setChecked(false)

            handleSpeaker(false)
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
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            ALog.e(TAG, "switch camera direction error", e)
        }
    }

    private fun handleCameraOpen() {
        ALog.d(TAG, "handleCameraOpen")
        try {
            val isSpeakerOn = !checkPluginHeadsets()

            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_SET_MUTE_VIDEO
            intent.putExtra(WebRtcCallService.EXTRA_MUTE, false)
            context.startForegroundServiceCompat(intent)
            mActionRightView.setType(ChatRtcCallItem.TYPE_SWITCH)

            handleSpeaker(isSpeakerOn)
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
            context.startForegroundServiceCompat(intent)

        }catch (ex: Exception) {
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
                context.startForegroundServiceCompat(intent)
                setCard(event.recipient, resources.getString(R.string.common_phone_ending_call))
                postDelayed({
                    mListener?.onEnd(mRecipient)
                }, WebRtcCallService.FINISH_DELAY)

            } else {
                val intent = Intent(context, WebRtcCallService::class.java)
                intent.action = WebRtcCallService.ACTION_LOCAL_HANGUP
                context.startForegroundServiceCompat(intent)
            }

        }catch (ex: Exception) {
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
                context.startForegroundServiceCompat(intent)
                setCard(event.recipient, resources.getString(R.string.common_phone_ending_call))
                postDelayed({
                    mListener?.onEnd(mRecipient)
                }, WebRtcCallService.FINISH_DELAY)
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "handleDenyCall error", e)
        }
    }

    private fun handleIncomingCall(event: WebRtcViewModel) {
        mVideoView.visibility = View.GONE
        setCard(event.recipient, "")
    }

    private fun handleOutgoingCall(event: WebRtcViewModel) {
        mVideoView.visibility = View.GONE
        mPassTimeView.visibility = View.GONE
        mActionLeftView.visibility = View.GONE
        mActionRightView.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_dialing))
    }

    private fun handleTerminate(recipient: Recipient?) {
        ALog.w(TAG, "handleTerminate called")
        mVideoView.visibility = View.GONE
        mActiveLayout.visibility = View.GONE
        mPassiveLayout.visibility = View.GONE
        setCard(recipient, resources.getString(R.string.common_phone_ending_call))
        EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleCallRinging(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallRinging")
        mVideoView.visibility = View.GONE
        mPassTimeView.visibility = View.GONE
        mActionLeftView.visibility = View.GONE
        mActionRightView.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_ringing))
    }

    private fun handleCallBusy(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallBusy")
        mVideoView.visibility = View.GONE
        mPassTimeView.visibility = View.GONE
        mActionLeftView.visibility = View.GONE
        mActionRightView.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_busy))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleCallConnected(event: WebRtcViewModel) {
        ALog.d(TAG, "handleCallConnected")
        if (mMiniMode) {
            mVideoView.visibility = View.GONE
        }else {
            mVideoView.visibility = View.VISIBLE
        }
        mActionLeftView.visibility = View.VISIBLE
        mActionRightView.visibility = View.VISIBLE

        setCard(event.recipient, resources.getString(R.string.common_phone_connected))
        setLocalAndRemoteRender(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer)

        this.mActionLeftView.isEnabled = true
        this.mVideoView.isEnabled = true
        this.mActionRightView.isEnabled = true
        updateSpeakerState()

        setCallPassTime(mPassTime)
        if (mCountRunnable == null) {
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
        mVideoView.visibility = View.GONE
        mPassiveLayout.visibility = View.GONE
        mActiveLayout.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_recipient_unavailable))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleServerFailure(event: WebRtcViewModel) {
        mVideoView.visibility = View.GONE
        mPassiveLayout.visibility = View.GONE
        mActiveLayout.visibility = View.GONE
        setCard(event.recipient, resources.getString(R.string.common_phone_network_failed))
        post {
            mListener?.onEnd(mRecipient)
        }
    }

    private fun handleNoSuchUser(event: WebRtcViewModel) {
        mVideoView.visibility = View.GONE
        mPassiveLayout.visibility = View.GONE
        mActiveLayout.visibility = View.GONE
        handleTerminate(event.recipient)
        post {
            mListener?.onUnknown(event.recipient)
        }
    }

    private fun handleUntrustedIdentity(event: WebRtcViewModel) {
        mVideoView.visibility = View.GONE
        mPassiveLayout.visibility = View.GONE
        mActiveLayout.visibility = View.GONE
        val theirIdentity = event.identityKey
        val recipient = event.recipient
        setUntrustedIdentity(recipient, theirIdentity)
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
        ALog.d(TAG, "setLocalAndRemoteRender")
        mLocalRenderLayout.setSurface(localRenderer)
        mRemoteRenderLayout.setSurface(remoteRenderer)

    }

    private fun handleVideoAction(switchStatus: Boolean) {
        ALog.d(TAG, "handleVideoAction switch: $switchStatus")
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
        } else if(type == ChatRtcCallItem.TYPE_SWITCH) {
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
