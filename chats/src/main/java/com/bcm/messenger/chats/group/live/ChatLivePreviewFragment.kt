package com.bcm.messenger.chats.group.live

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_fragment_live_preview.*
import org.greenrobot.eventbus.EventBus

class ChatLivePreviewFragment : Fragment() {

    companion object {
        const val TAG = "ChatLivePreviewFragment"
        const val LIVE_URL = "live_url"
        const val LIVE_PREVIEW_IMG = "live_preview_img"
        const val LIVE_PREVIEW_WIDTH = "live_preview_width"
        const val LIVE_PREVIEW_HEIGHT = "live_preview_height"
        const val LIVE_DURATION = "live_duration"
        const val LIVE_PLAY_URL = "play_url"
    }

    private lateinit var url: String
    private lateinit var playUrl: String
    private var duration: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_fragment_live_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
    }

    fun initView() {
        url = arguments?.getString(LIVE_URL) ?: ""
        playUrl = arguments?.getString(LIVE_PLAY_URL) ?: ""
        duration = arguments?.getLong(LIVE_DURATION, 0) ?: 0L
        if (url.isEmpty()) {
            ALog.e(TAG, "url is null")
            return
        }

        if (playUrl.isBlank()) {
            ALog.e(TAG, "play url is null")
            return
        }

        if (duration == 0L) {
            ALog.e(TAG, "duration is 0")
            return
        }
        chats_live_preview_view.setStreamingVideoSource(playUrl, false)
    }

    override fun onPause() {
        super.onPause()
        chats_live_preview_view.stopVideo()
    }

    override fun onDestroyView() {
        chats_live_preview_view.stopVideo()
        chats_live_preview_view.cleanup()
        super.onDestroyView()
    }

    fun publish(gid: Long) {
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.chats_live_flow_start_title))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_live_flow_start_start), AmeBottomPopup.PopupItem.CLR_BLUE) {
                    doPublish(gid)
                })
                .withDoneTitle(getString(R.string.chats_cancel))
                .show(activity)
    }

    private fun doPublish(gid: Long) {
        context?.let {
            val serviceTime = AmeTimeUtil.serverTimeMillis()
            val uri = Uri.parse(url)
            val isYouTubeLink = uri.host == "www.youtube.com" || uri.host == "m.youtube.com" || uri.host == "youtu.be"
            Observable.create(ObservableOnSubscribe<Long> { emitter ->
                try {
                    val liveIndexId = GroupLiveInfoManager.get(AMELogin.majorContext).stashLiveInfo(gid, serviceTime,
                            if (isYouTubeLink) GroupLiveInfo.LiveSourceType.Youtube else GroupLiveInfo.LiveSourceType.Original, url, serviceTime, duration)
                    emitter.onNext(liveIndexId)
                } finally {
                    emitter.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ liveIndexId ->
                        if (liveIndexId >= 0L) {
                            GroupMessageLogic.get(AMELogin.majorContext).messageSender.sendStartLiveMessage(gid, serviceTime, playUrl,
                                    AmeGroupMessage.LiveContent.PlaySource(if (isYouTubeLink) GroupLiveInfo.LiveSourceType.Youtube.value else GroupLiveInfo.LiveSourceType.Original.value, url),
                                    duration, object : MessageSender.SenderCallback {
                                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                                    AmeDispatcher.io.dispatch {
                                        if (messageDetail != null && messageDetail.message.isLiveMessage()) {
                                            GroupLiveInfoManager.get(AMELogin.majorContext).updateWhenSendLiveMessage(liveIndexId, messageDetail.message.content as AmeGroupMessage.LiveContent)
                                            AmeDispatcher.mainThread.dispatch {
                                                if (isSuccess) {
                                                    val format = chats_live_preview_view.videoFormat
                                                    if (format != null) {
                                                        EventBus.getDefault().post(LiveFlowController.VideoSizeChangeEvent(serviceTime, format.width, format.height))
                                                    }
                                                    activity?.finish()
                                                } else {
                                                    GroupLiveInfoManager.get(AMELogin.majorContext).clearStashLiveInfo(liveIndexId)
                                                    ToastUtil.show(it, "send error")
                                                    ALog.e(TAG, "publish messageSender error,url= $url,duration =$duration")
                                                }
                                            }
                                        }
                                    }
                                }
                            })
                        } else {
                            ToastUtil.show(it, "database error")
                            ALog.e(TAG, "publish datebase error,url= $url,duration =$duration")
                        }
                    }, {
                        ALog.e(TAG, "database error", it)
                    })
        }
    }
}
