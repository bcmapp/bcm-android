package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.utility.permission.PermissionUtil
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.chats_audio_view_new.view.*

import com.bcm.messenger.common.audio.AudioSlidePlayer
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.event.PartProgressEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.dp2Px
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import com.bcm.messenger.common.mms.AudioSlide
import kotlin.math.floor
import kotlin.math.min

/**
 */
class ChatAudioView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr), AudioSlidePlayer.Listener {

    companion object {

        private const val TAG = "ChatAudioView"
        private const val AUDIO_BACKWARD = 5
    }

    private var downloadListener: ChatComponentListener? = null
    private var audioSlidePlayer: AudioSlidePlayer? = null
    private var playingPosition: Long = 0

    private var backwardsCounter = 0

    private var mPrivateMessage: MessageRecord? = null
    private var mGroupMessage: AmeGroupMessageDetail? = null

    private var mShowPending = false
    private var mCurrentControlView: View? = null

    private var accountContext:AccountContext? = null


    private val progress: Double
        get() = if (audio_progress.progress <= 0 || audio_progress.max <= 0) {
            0.0
        } else {
            audio_progress.progress.toDouble() / audio_progress.max.toDouble()
        }

    init {
        View.inflate(context, R.layout.chats_audio_view_new, this)

        audio_play.setOnClickListener { doPlayAction(true) }
        audio_pause.setOnClickListener { doPlayAction(false) }

        setOnClickListener { v ->
            if (mPrivateMessage?.isMediaDeleted() == true || mGroupMessage?.isFileDeleted == true) {
                return@setOnClickListener
            }
            if (mPrivateMessage?.isMediaFailed() == true || mPrivateMessage?.isMediaPending() == true || mGroupMessage?.isFileDownloadFail == true) {
                if (mPrivateMessage != null) {
                    downloadListener?.onClick(v, mPrivateMessage!!)
                } else if (mGroupMessage != null) {
                    downloadListener?.onClick(v, mGroupMessage!!)
                }
                return@setOnClickListener
            }
            if (audio_play.visibility == View.VISIBLE) {
                doPlayAction(true)
            } else {
                doPlayAction(false)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }


    fun setAudio(masterSecret: MasterSecret, messageRecord: AmeGroupMessageDetail) {
        accountContext = masterSecret.accountContext
        mGroupMessage = messageRecord
        mPrivateMessage = null

        val content = messageRecord.message.content as AmeGroupMessage.AudioContent
        val slide = if (messageRecord.attachmentUri.isNullOrEmpty()) {
            AudioSlide(context, null, content.size, content.duration, false)
        } else {
            AudioSlide(context, messageRecord.getFilePartUri(accountContext), content.size, content.duration, false, true)
        }

        displayControl(audio_play)
        mShowPending = when {
            messageRecord.isSending || messageRecord.isAttachmentDownloading -> true
            messageRecord.attachmentUri.isNullOrEmpty() -> false
            else -> false
        }

        AudioSlidePlayer.stopAll()

        this.audioSlidePlayer = AudioSlidePlayer.createFor(context, masterSecret, slide, this)
        audio_progress.progress = 0
        updateMediaDuration(audioSlidePlayer, slide.duration)
        if (slide.duration <= 0) {
            audioSlidePlayer?.prepareDuration()
        }

        if (messageRecord.isFileDeleted) {
            audio_expire_text.visibility = View.VISIBLE
            audio_timestamp.visibility = View.GONE
            audio_decoration.visibility = View.GONE
        } else {
            audio_expire_text.visibility = View.GONE
            audio_timestamp.visibility = View.VISIBLE
            audio_decoration.visibility = View.VISIBLE
        }
    }

    fun setAudio(masterSecret: MasterSecret, messageRecord: MessageRecord) {
        accountContext = masterSecret.accountContext
        mPrivateMessage = messageRecord
        mGroupMessage = null

        val showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending())
        val attachment = messageRecord.getAudioAttachment() ?: return
        val slide = AudioSlide(context, attachment.getPartUri(), attachment.dataSize, attachment.duration, attachment.isVoiceNote())

        displayControl(audio_play)
        mShowPending = if (showControls && slide.isPendingDownload) {
            false
        } else {
            showControls && attachment.transferState == AttachmentDbModel.TransferState.STARTED.state
        }

        AudioSlidePlayer.stopAll()

        this.audioSlidePlayer = AudioSlidePlayer.createFor(context, masterSecret, slide, this)
        audio_progress.progress = 0
        updateMediaDuration(audioSlidePlayer, slide.duration)
        if (slide.duration <= 0) {
            audioSlidePlayer?.prepareDuration()
        }

        if (messageRecord.isMediaDeleted()) {
            audio_expire_text.visibility = View.VISIBLE
            audio_timestamp.visibility = View.GONE
            audio_decoration.visibility = View.GONE
        } else {
            audio_expire_text.visibility = View.GONE
            audio_timestamp.visibility = View.VISIBLE
            audio_decoration.visibility = View.VISIBLE
        }
    }


    fun setProgressDrawableResource(resourceId: Int) {
        setProgressDrawable(AppUtil.getDrawable(resources, resourceId))
    }


    fun setProgressDrawable(progressDrawable: Drawable) {
        audio_progress.progressDrawable = progressDrawable
    }


    fun setAudioAppearance(playRes: Int, pauseRes: Int, decorationColor: Int, textColor: Int) {
        audio_play.setImageResource(playRes)
        audio_pause.setImageResource(pauseRes)
        audio_decoration.setBackgroundColor(decorationColor)
        audio_timestamp.setTextColor(textColor)
    }

    fun cleanup() {
        if (this.audioSlidePlayer != null && audio_pause.visibility == View.VISIBLE) {
            this.audioSlidePlayer?.stop()
        }
    }

    fun setDownloadClickListener(listener: ChatComponentListener?) {
        this.downloadListener = listener
    }

    override fun onPrepare(player: AudioSlidePlayer?, totalMills: Long) {
        updateMediaDuration(player, totalMills)
    }

    override fun onStart(player: AudioSlidePlayer?, totalMills: Long) {
        updateMediaDuration(player, totalMills)
        if (this.audioSlidePlayer?.audioSlide == player?.audioSlide) {
            if (audio_pause.visibility != View.VISIBLE) {
                togglePlayToPause()
            }
        }
    }

    override fun onStop(player: AudioSlidePlayer?) {
        ALog.i("AudioView", "onStop" + audio_progress.progress)
        if (this.audioSlidePlayer?.audioSlide == player?.audioSlide) {
            togglePauseToPlay()
            backwardsCounter = AUDIO_BACKWARD
            onProgress(player, 0.0, 0)
        }
    }

    override fun onProgress(player: AudioSlidePlayer?, progress: Double, millis: Long) {
        ALog.d(TAG, "onProgress progress: $progress, mills: $millis")
        if (this.audioSlidePlayer?.audioSlide != player?.audioSlide) {
            return
        }
        val position = millis / 1000L
        if (0L == millis) {
            audio_timestamp.text = DateUtils.convertMinuteAndSecond(player?.audioSlide?.duration
                    ?: 0)
        } else if (position != playingPosition) {
            playingPosition = position
            audio_timestamp.text = DateUtils.convertMinuteAndSecond((player?.audioSlide?.duration
                    ?: 0) - millis)
        }
        val newProgress = floor(progress * audio_progress.max).toInt()
        if (newProgress > audio_progress.progress || backwardsCounter >= AUDIO_BACKWARD) {
            backwardsCounter = 0
            audio_progress.progress = newProgress

        } else {
            backwardsCounter++
        }
    }

    private fun togglePlayToPause() {
        post { displayControl(audio_pause) }
    }

    private fun togglePauseToPlay() {
        post { displayControl(audio_play) }
    }

    private fun updateMediaDuration(player: AudioSlidePlayer?, duration: Long) {
        ALog.d(TAG, "updateMediaDuration: $duration")
        val audioSlide = player?.audioSlide
        if (audioSlide?.duration != duration) {
            if (audioSlide != null) {
                audioSlide.duration = duration
                Observable.create<Boolean> {
                    mPrivateMessage?.let { record ->
                        val attachment = record.getAudioAttachment()
                        val accountContext = this.accountContext
                        if (attachment != null && accountContext != null) {
                            Repository.getAttachmentRepo(accountContext)?.updateDuration(attachment.id, attachment.uniqueId, duration)
                        } else {
                            ALog.w(TAG, "Current audio is not a DatabaseAttachment")
                        }
                    }
                    it.onNext(true)
                    it.onComplete()

                }.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                        }, {
                            ALog.e(TAG, "updateMediaDuration error", it)
                        })
            }
        }
        if (this.audioSlidePlayer?.audioSlide == audioSlide) {
            if (duration <= 0) {
                audio_timestamp.visibility = View.GONE
            } else {
                audio_timestamp.visibility = View.VISIBLE
                audio_timestamp.text = DateUtils.convertMinuteAndSecond(duration)
            }
            audio_decoration.layoutParams = audio_decoration.layoutParams.apply {
                width = min(3.dp2Px() * (duration / 1000).toInt(), 150.dp2Px())
                if (width == 0) width = 5.dp2Px()
            }
        }
    }


    private fun doPlayAction(toPlay: Boolean) {
        if (mPrivateMessage?.isMediaFailed() == true ||
                mPrivateMessage?.isMediaDeleted() == true ||
                mGroupMessage?.isFileDeleted == true ||
                mGroupMessage?.isFileDownloadFail == true) {
            return
        }
        PermissionUtil.checkStorage(context) { aBoolean ->
            if (aBoolean) {
                realDoPlayAction(toPlay)
            }
        }
    }

    private fun realDoPlayAction(toPlay: Boolean) {
        if (audioSlidePlayer == null) {
            return
        }
        try {
            if (toPlay) {
                togglePlayToPause()
                audioSlidePlayer?.play(progress)
            } else {
                audioSlidePlayer?.stop()
                onStop(audioSlidePlayer)
            }
        } catch (ex: Exception) {
            Logger.e(ex, "audio play error", ex)
        }
    }


    fun doDownloadAction() {
        PermissionUtil.checkStorage(context) { granted ->
            if (granted) {
                val data = mPrivateMessage ?: mGroupMessage ?: return@checkStorage
                if(!mShowPending) {
                    downloadListener?.onClick(this, data)
                }
            }
        }
    }


    private fun displayControl(target: View?) {
        if (mCurrentControlView != null) {
            if (mCurrentControlView === target) {
                return
            }
            mCurrentControlView?.visibility = View.GONE
            mCurrentControlView = null
        }

        if (target != null) {
            target.visibility = View.VISIBLE
        }
        mCurrentControlView = target
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (audioSlidePlayer != null && event.attachment.getPartUri() == this.audioSlidePlayer?.audioSlide?.uri) {
            Log.d(TAG, "audio progress:" + event.progress + ", " + event.total)
            mShowPending = event.progress < event.total
        }
    }
}
