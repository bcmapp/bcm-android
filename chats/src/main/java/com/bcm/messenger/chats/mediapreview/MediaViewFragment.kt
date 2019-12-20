package com.bcm.messenger.chats.mediapreview

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.MediaVideoDurationEvent
import com.bcm.messenger.chats.mediapreview.bean.MSG_TYPE_PRIVATE
import com.bcm.messenger.chats.mediapreview.bean.MediaViewData
import com.bcm.messenger.chats.util.GroupAttachmentProgressEvent
import com.bcm.messenger.chats.util.HistoryGroupAttachmentProgressEvent
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.PartProgressEvent
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.video.VideoPlayer
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.google.android.exoplayer2.Player
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_fragment_media_view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.min

/**
 * media preview fragment
 * Created by Kin on 2018/10/31
 */
class MediaViewFragment : Fragment() {

    private val TAG = "MediaViewFragment"
    private var mData: MediaViewData? = null
    private var mMasterSecret: MasterSecret? = null
    private val glide = GlideApp.with(AppContextHolder.APP_CONTEXT)
    private var isUserVisible = false
    private var mListener: MediaViewFragmentActionListener? = null

    interface MediaViewFragmentActionListener {
        fun videoPlaying()
        fun clickImage()
        fun longClickImage(): Boolean
        fun controllerVisible(isVisible: Boolean)
        fun dataIsVideo(isVideo: Boolean)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_media_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initViewData()
        EventBus.getDefault().register(this)
    }

    private fun initView() {
        mediaview_root.setReleaseListener(object : DragLayout.DragLayoutListener {
            override fun onRelease() {
                val ctx = context
                if (ctx is MediaViewActivity) {
                    ctx.dismissActivity()
                }
            }

            override fun onPositionChanged(percentage: Float) {
                val ctx = context
                if (ctx is Activity) {
                    var alpha = min((255 * percentage).toInt(), 255)
                    if (alpha < 50) alpha = 0
                    ctx.window.decorView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
                }
            }

            override fun canDrag(): Boolean {
                return !mediaview_imageview.isZooming()
            }
        })

        mediaview_play.setOnClickListener {
            downloadVideo(mediaview_videoplayer ?: return@setOnClickListener)
        }

        mediaview_videoplayer.setVideoStateChangeListener(object : VideoPlayer.VideoStateChangeListener() {
            override fun onChanged(state: Int) {
                when (state) {
                    Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_ENDED -> {
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    Player.STATE_READY -> {
                        updateVideoDuration(mediaview_videoplayer.duration)
                        if (mediaview_videoplayer.isPlaying) {
                            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            mListener?.videoPlaying()
                        } else {
                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        })

        mediaview_imageview.setOnSingleTapListener {
            mListener?.clickImage()
        }
        mediaview_imageview.setOnLongClickListener {
            return@setOnLongClickListener mListener?.longClickImage() ?: false
        }
        mediaview_videoplayer.setControllerVisibleListener {
            mListener?.controllerVisible(it)
        }
    }

    private fun initViewData() {
        val data = mData
        val masterSecret = mMasterSecret
        if (data != null && masterSecret != null) {
            mediaview_root?.setDataType(data.mediaType)
            if (data.mediaType == com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE) {
                mediaview_imageview?.visibility = View.VISIBLE
                mediaview_videoplayer?.visibility = View.GONE
                mediaview_play?.visibility = View.GONE
                data.setImage(mediaview_imageview ?: return, glide, masterSecret)
            } else {
                mediaview_imageview?.visibility = View.GONE
                mediaview_videoplayer?.visibility = View.VISIBLE
                if (data.mediaUri != null) {
                    mediaview_play?.visibility = View.GONE
                    data.setVideo(mediaview_videoplayer ?: return, masterSecret)
                } else {
                    mediaview_play?.visibility = View.VISIBLE
                    data.setVideoThumbnail(mediaview_videoplayer ?: return, glide)
                }
            }
        }
    }

    fun setData(data: MediaViewData?) {
        this.mData = data
        initViewData()
    }

    fun setListener(listener: MediaViewFragmentActionListener) {
        this.mListener = listener
    }

    fun setMasterSecret(masterSecret: MasterSecret) {
        this.mMasterSecret = masterSecret
    }

    fun hideController() {
        if (mData?.mediaUri != null) {
            mediaview_videoplayer?.hideControllerWithoutCallback()
        }
    }

    fun showController() {
        mediaview_videoplayer?.showControllerWithoutCallback()
    }

    fun getData(): MediaViewData? = mData

    override fun onDestroyView() {
        mediaview_imageview?.cleanup()
        mediaview_videoplayer?.stopVideo()
        mediaview_videoplayer?.cleanup()
        mediaview_videoplayer?.removeListeners()
        mListener = null
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        val data = mData
        if (data != null) {
            if (isUserVisible && !isVisibleToUser) {
                if (data.mediaType == com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_VIDEO) {
                    mediaview_videoplayer?.stopVideo()
                }
            } else if (!isUserVisible && isVisibleToUser) {
                mListener?.dataIsVideo(data.mediaType == com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_VIDEO)
            }
            isUserVisible = isVisibleToUser
        }
    }

    fun getTransitionView(): View? {
        return if (mData?.mediaType == com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE) {
            mediaview_imageview
        } else {
            mediaview_videoplayer
        }
    }


    private fun downloadVideo(videoPlayer: VideoPlayer) {
        val data = mData
        val masterSecret = mMasterSecret
        if (data != null && masterSecret != null) {
            if (data.mediaUri == null) {
                mediaview_progress.spin()
                mediaview_progress.visibility = View.VISIBLE
                mediaview_play.visibility = View.GONE

                data.downloadVideo(videoPlayer, masterSecret) {
                    mediaview_progress?.stopSpinning()
                    mediaview_progress?.visibility = View.GONE

                    if (data.msgType != MSG_TYPE_PRIVATE) {
                        if (data.mediaUri != null) {
                            mediaview_play?.visibility = View.GONE
                            if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                                mediaview_videoplayer?.playVideo()
                            }

                        } else {
                            mediaview_play?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun updateVideoDuration(duration: Long) {
        if (duration == 0L) {
            return
        }
        ALog.i(TAG, "updateVideoDuration : $duration")
        val attachmentId = mData?.getPrivateAttachmentId() ?: return
        val durationInSections = duration / 1000
        EventBus.getDefault().post(MediaVideoDurationEvent(attachmentId, durationInSections))
        Observable.create<Boolean> {
            try {
                Repository.getAttachmentRepo().updateDuration(attachmentId.rowId, attachmentId.uniqueId, durationInSections)
                it.onNext(true)
            } catch (ex: Exception) {
                ALog.e(TAG, "updateVideoDuration", ex)
                it.onNext(false)
            } finally {
                it.onComplete()
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, {})
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: PartProgressEvent) {
        try {
            val data = mData
            val masterSecret = mMasterSecret
            if (data != null && masterSecret != null) {
                val messageRecord = data.sourceMsg as MessageRecord
                val attachment = messageRecord.getVideoAttachment() ?: return
                if (attachment == event.attachment) {
                    val progress = event.progress.toFloat() / event.total
                    if (progress >= 1.0f) {
                        mediaview_progress?.visibility = View.GONE
                        mediaview_progress?.stopSpinning()

                        data.mediaUri = attachment.getPartUri()

                        if (data.mediaType == com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE) {
                            data.setImage(mediaview_imageview, glide, masterSecret)
                        } else {
                            if (data.mediaUri != null) {
                                mediaview_play?.visibility = View.GONE

                                if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                                    mediaview_videoplayer?.playVideo()
                                }

                            } else {
                                mediaview_play?.visibility = View.VISIBLE
                            }

                            data.refreshSlide(attachment)
                            data.setVideo(mediaview_videoplayer ?: return, masterSecret)
                        }

                    } else {
                        mediaview_progress?.visibility = View.VISIBLE
                        mediaview_progress?.spin()

                        if (data.mediaType != com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE) {
                            mediaview_play?.visibility = View.GONE
                        }
                    }
                }
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "receive private video download progress error", ex)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupAttachmentProgressEvent) {
        try {
            val data = mData
            if (data != null) {
                if (data.msgType != MSG_TYPE_PRIVATE) {
                    ALog.i(TAG, "on GroupAttachmentProgressEvent progress: ${event.progress}")
                    val it = data.sourceMsg as AmeGroupMessageDetail
                    if (it is AmeHistoryMessageDetail) {
                        val tc = it.message.content as AmeGroupMessage.ThumbnailContent
                        if (event is HistoryGroupAttachmentProgressEvent &&
                                (event.url == tc.url || event.url == tc.thumbnail_url)) {
                            if (event.action == GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING) {
                                it.isThumbnailDownloading = event.progress < 1.0
                            } else if (event.action == GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING) {
                                it.isAttachmentDownloading = event.progress < 1.0
                            }
                        }
                    } else {
                        if (it.gid == event.gid && it.indexId == event.indexId) {
                            if (event.action == GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING) {
                                it.isThumbnailDownloading = event.progress < 1.0
                            } else if (event.action == GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING) {
                                it.isAttachmentDownloading = event.progress < 1.0
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "onEvent GroupAttachmentProgressEvent fail", ex)
        }
    }

}