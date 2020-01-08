package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageModel
import com.bcm.messenger.chats.mediapreview.DragLayout
import com.bcm.messenger.chats.mediapreview.MediaMoreView
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.video.VideoPlayer
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.google.android.exoplayer2.Player
import kotlinx.android.synthetic.main.adhoc_fragment_media_preview.*
import kotlin.math.min

/**
 * adhoc preview fragment
 * Created by Kin on 2018/10/31
 */
class AdHocPreviewFragment : BaseFragment(), MediaMoreView.MoreViewActionListener {

    private val TAG = "AdHocPreviewFragment"
    private val glide = GlideApp.with(AppContextHolder.APP_CONTEXT)
    private var isUserVisible = false

    private var mContentType: String? = null
    private var mMediaUri: Uri? = null
    private var mIndexId: Long = 0
    private var mSessionId: String? = null
    private var mCurrentData: AdHocMessageDetail? = null

    private var mListener: AdHocPreviewActivity.OnPreviewListener? = null

    private var mAttachmentProgressCallback: AdHocMessageModel.DefaultOnMessageListener? = object : AdHocMessageModel.DefaultOnMessageListener(TAG) {
        override fun onProgress(message: AdHocMessageDetail, progress: Float) {
            if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                ALog.i(TAG, "onProgress session: ${message.sessionId}, index: ${message.indexId}")
                if (message.sessionId == mCurrentData?.sessionId && message.indexId == mCurrentData?.indexId) {
                    if (progress < 1.0f) {
                        preview_progress?.visibility = View.VISIBLE
                        preview_progress?.progress = progress

                        mListener?.onAttachmentComplete(false)
                    } else {
                        mCurrentData = message
                        preview_progress?.visibility = View.GONE
                        setPreviewData(glide, message.toAttachmentUri(), message.getContentType()
                                ?: "")

                        mListener?.onAttachmentComplete(true)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AdHocMessageLogic.get(accountContext).getModel()?.removeOnMessageListener(mAttachmentProgressCallback ?: return)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.adhoc_fragment_media_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initViewData()
    }

    fun setOnPreviewListener(listener: AdHocPreviewActivity.OnPreviewListener) {
        mListener = listener
    }

    private fun initView() {
        preview_root.setReleaseListener(object : DragLayout.DragLayoutListener {
            override fun onRelease() {
                val ctx = context
                if (ctx is AdHocPreviewActivity) {
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
                return preview_image?.isZooming() != true
            }
        })

        preview_player.setVideoStateChangeListener(object : VideoPlayer.VideoStateChangeListener() {
            override fun onChanged(state: Int) {
                when (state) {
                    Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_ENDED -> {
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    Player.STATE_READY -> {
                        if (preview_player?.isPlaying == true) {
                            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        })

        preview_image.setOnSingleTapListener {
            mListener?.onDismiss()
        }

        preview_player.setControllerVisibleListener {
            mListener?.onControllerVisible(it)
        }
    }

    private fun initViewData() {
        val activity = activity ?: return
        mSessionId = activity.intent.getStringExtra(AdHocPreviewActivity.SESSION_ID)
        mIndexId = activity.intent.getLongExtra(AdHocPreviewActivity.INDEX_ID, 0L)

        AdHocMessageLogic.get(accountContext).getModel()?.findMessage(mIndexId) {
            if (it == null) {
                activity.finish()
                return@findMessage
            }else {
                mCurrentData = it
                mListener?.onInit(it.indexId, it.mid, it.getMessageBodyType())
                val attachmentUri = it.toAttachmentUri()
                setPreviewData(glide, attachmentUri,
                        activity.intent.getStringExtra(AdHocPreviewActivity.DATA_TYPE) ?: "")

                AdHocMessageLogic.get(accountContext).getModel()?.addOnMessageListener(mAttachmentProgressCallback ?: return@findMessage)

                mListener?.onAttachmentComplete(attachmentUri != null)
            }
        }
    }

    private fun setPreviewData(glideRequest: GlideRequests, mediaUri: Uri?, contentType: String) {
        mMediaUri = mediaUri
        mContentType = contentType
        if (MediaUtil.isImageType(contentType)) {
            preview_image?.visibility = View.VISIBLE
            preview_player?.visibility = View.GONE
            if (mediaUri != null) {
                preview_image?.setImageUri(null, glideRequest, mediaUri, contentType)
            }
        } else {
            preview_image?.visibility = View.GONE
            preview_player?.visibility = View.VISIBLE

            preview_player?.hideVideoThumbnail()
            if (mediaUri != null) {
                preview_player?.setVideoSource(getMasterSecret(), mediaUri, false)
            }
        }
    }

    override fun onDestroyView() {
        preview_image?.cleanup()
        preview_player?.stopVideo()
        preview_player?.cleanup()
        preview_player?.removeListeners()

        super.onDestroyView()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isUserVisible && !isVisibleToUser) {
            if (preview_player?.visibility == View.VISIBLE) {
                preview_player?.stopVideo()
            }
        } else if (!isUserVisible && isVisibleToUser) {

        }
        isUserVisible = isVisibleToUser
    }

    override fun clickDownload() {
        mCurrentData?.let {
            if (it.toAttachmentUri() != null) {
                val content = it.getMessageBody()?.content as? AmeGroupMessage.AttachmentContent
                val name = if (content is AmeGroupMessage.FileContent) {
                    content.fileName
                } else {
                    null
                }
                val activity = activity as? AppCompatActivity
                if (activity != null) {
                    AttachmentSaver.saveAttachmentOnAsync(activity, it.attachmentUri, content?.mimeType, name)
                }
            }
        }
    }

//    override fun clickForward() {
//        mCurrentData?.let {
//            AdHocMessageLogic.getModel()?.forward(it)
//        }
//    }
//
//    override fun clickDelete() {
//        mCurrentData?.let {
//            AdHocMessageLogic.getModel()?.deleteMessage(listOf(it)) {success ->
//                if (success) {
//                    AmeAppLifecycle.succeed(getString(R.string.chats_delete_success), true) {
//                        mListener?.onDismiss()
//                    }
//                }else {
//                    AmeAppLifecycle.failure(getString(R.string.chats_delete_fail), true)
//                }
//            }
//        }
//    }

    override fun clickMediaBrowser() {
        ToastUtil.show(AppContextHolder.APP_CONTEXT, getString(R.string.adhoc_attachment_browse_nonsupport_description))
    }

//    override fun clickScanQRCode() {
//    }
//
//    override fun clickClose() {
//        mListener?.onDismiss()
//    }

    override fun moreOptionVisibilityChanged(isShow: Boolean) {
    }
}