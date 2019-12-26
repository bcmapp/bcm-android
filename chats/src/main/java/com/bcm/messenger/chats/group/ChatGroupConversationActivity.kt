package com.bcm.messenger.chats.group

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.SharedElementCallback
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.BottomPanelClickListener
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.chats.bean.SendContactEvent
import com.bcm.messenger.chats.components.ConversationInputPanel
import com.bcm.messenger.chats.components.titlebar.ChatTitleBar
import com.bcm.messenger.chats.group.live.ChatLiveSettingActivity
import com.bcm.messenger.chats.group.live.LiveFlowController
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.chats.group.setting.ChatGroupSettingActivity
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.chats.user.SendContactActivity
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.audio.AudioSlidePlayer
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.repositories.DraftRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.GroupListChangedEvent
import com.bcm.messenger.common.event.GroupNameOrAvatarChanged
import com.bcm.messenger.common.event.MultiSelectEvent
import com.bcm.messenger.common.event.ReEditEvent
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo
import com.bcm.messenger.common.imagepicker.BcmPickPhotoConstants
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.bean.SelectedModel
import com.bcm.messenger.common.providers.PersistentBlobProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_tt_conversation_activity.*
import me.imid.swipebacklayout.lib.SwipeBackLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Group chat activity
 *
 * Created by zjl on 2018/5/11.
 */
@Route(routePath = ARouterConstants.Activity.CHAT_GROUP_CONVERSATION)
class ChatGroupConversationActivity : SwipeBaseActivity(), RecipientModifiedListener {

    companion object {
        const val TAG = "TTConversationActivity"
        const val DELETE_REQUEST_CODE = 100
        const val PICK_DOCUMENT = 101
        const val PICK_LOCATION = 102
        const val SEND_CONTACT = 103
    }

    private var groupModel: GroupViewModel? = null
    private var fragment: ChatGroupConversationFragment? = null

    private var groupRecipient: Recipient? = null
    private var groupId = -1L
    private var threadId = 0L
    private var liveController: LiveFlowController? = null
    private var isMultiSelecting = false
    private var isShowingNotice = false

    private val messageCallback = object : MessageSender.SenderCallback {
        override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
        }
    }

    override fun onDestroy() {
        ALog.d(TAG, "onDestroy")
        liveController?.onDestroy()
        EventBus.getDefault().unregister(this)
        groupModel?.destroy()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        setContentView(R.layout.chats_tt_conversation_activity)
        initView()
        initData()

        window?.setStatusBarLightMode()

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ALog.w(TAG, "onNewIntent()")
        if (isFinishing) {
            ALog.w(TAG, "Activity is finishing...")
            return
        }
        setIntent(intent)
        initData()
    }

    private fun initView() {
        this.fragment = initFragment(R.id.fragment_content, ChatGroupConversationFragment(), null)

        chat_title_bar.setOnChatTitleCallback(object : ChatTitleBar.OnChatTitleCallback {
            override fun onLeft(multiSelect: Boolean) {
                hideInput()
                when {
                    isMultiSelecting -> onEvent(MultiSelectEvent(true, null))
                    liveController?.isLiveInFullScreen() == true -> liveController?.switchToSmallSize()
                    else -> finish()
                }
            }

            override fun onRight(multiSelect: Boolean) {
                handleGroupSetting()
            }

            override fun onTitle(multiSelect: Boolean) {
            }
        })

        layout_container.addOnKeyboardShownListener(object : KeyboardAwareLinearLayout.OnKeyboardShownListener {
            override fun onKeyboardShown() {
                fragment?.scrollToBottom(false)
            }
        })

        bottom_panel.removeAllOptionItems()
        bottom_panel.addOptionItem(BottomPanelItem(getString(R.string.chats_more_option_camera), R.drawable.chats_icon_camera, object : BottomPanelClickListener {
            override fun onClick(name: String, view: View) {
                PermissionUtil.checkCamera(this@ChatGroupConversationActivity) {
                    if (it) {
                        try {
                            BcmPickPhotoView.Builder(this@ChatGroupConversationActivity)
                                    .setCapturePhoto(true)
                                    .build().start()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }),
                BottomPanelItem(getString(R.string.chats_more_option_album), R.drawable.chats_icon_picture, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        PermissionUtil.checkCamera(this@ChatGroupConversationActivity) {
                            if (it) {
                                BcmPickPhotoView.Builder(this@ChatGroupConversationActivity)
                                        .setPickPhotoLimit(5)
                                        .setShowGif(true)
                                        .setShowVideo(true)
                                        .setApplyText(getString(R.string.chats_send))
                                        .build()
                                        .start()
                            }
                        }
                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_file), R.drawable.chats_icon_file, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        PermissionUtil.checkStorage(this@ChatGroupConversationActivity) {
                            if (it) {
                                selectMediaType(this@ChatGroupConversationActivity, "*/*", null, PICK_DOCUMENT)
                            }
                        }
                    }

                    private fun selectMediaType(activity: Activity, type: String, extraMimeType: Array<String>?, requestCode: Int) {
                        val intent = Intent()
                        intent.type = type
                        if (extraMimeType != null) {
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType)
                        }
                        intent.action = Intent.ACTION_OPEN_DOCUMENT
                        try {
                            activity.startActivityForResult(intent, requestCode)
                            return
                        } catch (e: Throwable) {
                            ALog.e(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.", e)
                        }
                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_location), R.drawable.chats_icon_location, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        PermissionUtil.checkLocationPermission(this@ChatGroupConversationActivity) {
                            if (it) {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.MAP).navigation(this@ChatGroupConversationActivity, PICK_LOCATION)
                            }
                        }
                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_namecard), R.drawable.chats_icon_contact, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        val intent = Intent(this@ChatGroupConversationActivity, SendContactActivity::class.java)
                        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, GroupUtil.addressFromGid(groupId))
                        startActivityForResult(intent, SEND_CONTACT)
                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_tv), R.drawable.chats_72_tv, object : BottomPanelClickListener {

                    @SuppressLint("CheckResult")
                    override fun onClick(name: String, view: View) {
                        if (groupModel?.myRole() == AmeGroupMemberInfo.OWNER) {
                            Observable.create(ObservableOnSubscribe<Boolean> {
                                val liveInfo = GroupLiveInfoManager.getInstance().getCurrentLiveInfo(groupId)
                                if (liveInfo != null)
                                    it.onNext(liveInfo.liveStatus == GroupLiveInfo.LiveStatus.LIVING.value || liveInfo.liveStatus == GroupLiveInfo.LiveStatus.PAUSE.value)
                                else {
                                    it.onNext(false)
                                }
                                it.onComplete()
                            }).subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        if (it) { // Cannot enter if playing
                                            AlertDialog.Builder(this@ChatGroupConversationActivity)
                                                    .setMessage(R.string.chats_live_is_living_tip)
                                                    .setPositiveButton(R.string.common_understood, null)
                                                    .show()
                                        } else {
                                            val intent = Intent(this@ChatGroupConversationActivity, ChatLiveSettingActivity::class.java)
                                            intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
                                            startActivity(intent)
                                        }
                                    }, {
                                        ALog.e(TAG, "live entry error", it)
                                    })
                        } else {
                            AlertDialog.Builder(this@ChatGroupConversationActivity)
                                    .setTitle(R.string.chats_forward_notice)
                                    .setMessage(R.string.chats_live_member_tip)
                                    .setPositiveButton(R.string.common_understood, null)
                                    .show()
                        }
                    }
                }))

        bottom_panel.setOnConversationInputListener(object : ConversationInputPanel.OnConversationInputListener {

            override fun onBeforeTextOrAudioHandle(): Boolean {
                return true
            }

            override fun onMessageSend(message: CharSequence, replyContent: AmeGroupMessage.ReplyContent?, extContent: AmeGroupMessageDetail.ExtensionContent?) {
                handleSendText(message, replyContent, extContent)
            }

            override fun onEmojiPanelShow(show: Boolean) {
                if (show) {
                    setSwipeBackEnable(false)
                    fragment?.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onMoreOptionsPanelShow(show: Boolean) {
                if (show) {
                    setSwipeBackEnable(false)
                    fragment?.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onAudioStateChanged(state: Int, recordUri: Uri?, byteSize: Long, playTime: Long) {
                when (state) {
                    ConversationInputPanel.OnConversationInputListener.AUDIO_START -> {
                        liveController?.muteVideo()
                    }
                    ConversationInputPanel.OnConversationInputListener.AUDIO_COMPLETE -> {

                        handleSendAudio(recordUri, byteSize, playTime)

                        liveController?.unMuteVideo()
                    }
                    else -> {
                        liveController?.unMuteVideo()
                    }
                }
            }
        })
        bottom_panel.bindInputAwareLayout(layout_container)

        illegal_leave_group.setOnClickListener {
            var newOwner: String? = null
            if (groupModel?.myRole() == AmeGroupMemberInfo.OWNER) {
                newOwner = groupModel?.randomGetGroupMember()?.uid?.serialize()
            }

            AmePopup.loading.show(this)
            groupModel?.leaveGroup(newOwner) { succeed, error ->
                if (succeed) {
                    illegal_leave_group.postDelayed({
                        AmePopup.loading.dismiss()
                        finish()
                    }, 2000)
                } else {
                    if (!TextUtils.isEmpty(error)) {
                        AmePopup.result.failure(this, error)
                    }
                    AmePopup.loading.dismiss()
                }
            }
        }

        swipeBackLayout.addSwipeListener(object : SwipeBackLayout.SwipeListener {
            override fun onScrollStateChange(state: Int, scrollPercent: Float) {
            }

            override fun onEdgeTouch(edgeFlag: Int) {
                bottom_panel?.clearFocus()
                AmeDispatcher.mainThread.dispatch({
                    hideKeyboard()
                }, 300)
            }

            override fun onScrollOverThreshold() {

            }
        })
    }

    private fun initData() {
        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        val newGroupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        if (newGroupId != groupId) {
            //
            //If a new group ID is received in the current window, the related data of the previous group needs to be saved and recycled.
            val groupInfo = groupModel?.getGroupInfo()
            if (groupInfo != null && groupInfo.role != AmeGroupMemberInfo.VISITOR) {
                ALog.i(TAG, "finish, readAllMessage")
                groupModel?.readAllMessage()
                saveDraft()
            }
            bottom_panel?.setComposeText("")
            liveController?.onDestroy()
            groupModel?.destroy()
        }
        groupId = newGroupId
        bottom_panel.setConversationId(groupId)
        groupModel = GroupLogic.newModel(groupId)
        val groupInfo = groupModel?.getGroupInfo()
        if (groupInfo == null) {
            finish()
            return
        }
        groupRecipient = Recipient.recipientFromNewGroup(this, groupInfo)
        val newThreadId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, -1L)
        if (newThreadId <= 0) {
            ThreadListViewModel.getThreadId(groupRecipient ?: return) {
                intent.putExtra(ARouterConstants.PARAM.PARAM_THREAD, it)
                initConversation(it)
            }
        } else {
            initConversation(newThreadId)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            bottom_panel?.clearFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        ALog.d(TAG, "onPause begin")
        bottom_panel.voiceRecodingPanel.onPause()
        AudioSlidePlayer.stopAll()
        liveController?.onPause()
        hideKeyboard()
        ALog.d(TAG, "onPause end")
    }

    override fun onStop() {
        super.onStop()
        ALog.d(TAG, "onStop")
        liveController?.onStop()
        val groupInfo = groupModel?.getGroupInfo()
        if (groupInfo != null && groupInfo.role != AmeGroupMemberInfo.VISITOR) {
            ALog.i(TAG, "finish, readAllMessage")
            groupModel?.readAllMessage()
            saveDraft()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                finish()
            }
        }
        ALog.d(TAG, "onActivityResult requestCode: $requestCode resultCode: $resultCode")
        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        when (requestCode) {
            BcmPickPhotoConstants.PICK_PHOTO_REQUEST -> {
                val selectPaths = data.getSerializableExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST) as ArrayList<SelectedModel>
                val videoList = mutableListOf<String>()
                val imageList = mutableListOf<String>()
                selectPaths.forEach {
                    if (it.isVideo) {
                        videoList.add(it.path)
                    } else {
                        imageList.add(it.path)
                    }
                }
                handleSendImage(imageList)
                handleSendVideo(videoList)
            }
            PICK_DOCUMENT -> handleSendDocument(data.data)
            BcmPickPhotoConstants.CAPTURE_REQUEST -> {
                val path = data.getStringExtra(BcmPickPhotoConstants.EXTRA_CAPTURE_PATH)
                if (path != null) {
                    val selectPaths = ArrayList<String>()
                    selectPaths.add(path)
                    handleSendImage(selectPaths, true)
                }
            }
            PICK_LOCATION -> {
                val latitude = data.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
                val longitude = data.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
                val mapType = data.getIntExtra(ARouterConstants.PARAM.MAP.MAPE_TYPE, 0)
                val title = data.getStringExtra(ARouterConstants.PARAM.MAP.TITLE)
                val address = data.getStringExtra(ARouterConstants.PARAM.MAP.ADDRESS)
                if (mapType != 0) {
                    GroupMessageLogic.messageSender.sendLocationMessage(groupId, latitude, longitude, mapType, title, address, messageCallback)
                } else {
                    ALog.w(TAG, "sendLocationMessage fail, mapType is zero")
                }
            }
            SEND_CONTACT -> {
                try {
                    val event = GsonUtils.fromJson<SendContactEvent>(data.getStringExtra(ARouterConstants.PARAM.PARAM_DATA), object : TypeToken<SendContactEvent>() {}.type)
                    if (event.groupId > 0) {
                        event.dataList.forEach {
                            GroupMessageLogic.messageSender.sendContactMessage(event.groupId, it, null)
                        }
                        AmeDispatcher.mainThread.dispatch({
                            if (!event.comment.isNullOrEmpty()) {
                                GroupMessageLogic.messageSender.sendTextMessage(event.groupId, event.comment.toString())
                            }
                        }, 1000)

                    }
                } catch (ex: Exception) {
                    ALog.e(TAG, "onActivityResult send_contact", ex)
                }
            }
        }
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val indexId = data.getLongExtra(ShareElements.PARAM.MEDIA_INDEX, 0L)
            setExitSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                    names?.clear()
                    sharedElements?.clear()
                    val transitionTag = "${ShareElements.Activity.MEDIA_PREIVEW}$indexId"
                    val view = fragment?.getExitView(indexId)
                    names?.add(transitionTag)
                    sharedElements?.put(transitionTag, view ?: bottom_panel)
                    setExitSharedElementCallback(null as? SharedElementCallback)
                }
            })

            fragment?.notifyDataSetChanged()
        }
    }

    override fun onBackPressed() {
        when {
            layout_container.isInputOpen -> {
                hideInput()
            }
            isMultiSelecting -> onEvent(MultiSelectEvent(true, null))
            liveController?.isLiveInFullScreen() == true -> liveController?.switchToSmallSize()
            else -> super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()

        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancelAll()

        liveController?.onResume()
    }

    private fun initConversation(newThreadId: Long) {

        this.groupModel?.setThreadId(newThreadId)
        if (this.threadId != newThreadId) {
            this.threadId = newThreadId
            fragment?.onNewIntent()
        }

        this.groupModel?.checkSync()
        updateGroupInfo()
        updateMemberAtList()
        initLiveFlowController()

        initializeDraft()
    }

    @SuppressLint("CheckResult")
    private fun initializeDraft() {

        Observable.create(ObservableOnSubscribe<String> {
            val draftRepo = Repository.getDraftRepo()
            val drafts = draftRepo.getDrafts(threadId)
            val draftText = drafts.getSnippet(AppContextHolder.APP_CONTEXT)
            if (!draftText.isNullOrEmpty()) {
                it.onNext(draftText)
            }
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    bottom_panel?.setComposeText(it)
                    if (it.isNotEmpty()) {
                        bottom_panel?.requestComposeFocus()
                    }
                }, {
                    ALog.e(TAG, "initializeDraft fail", it)
                })
    }

    @SuppressLint("CheckResult")
    private fun saveDraft() {
        val drafts = DraftRepo.Drafts()
        if (bottom_panel.getComposeText().isNotEmpty()) {
            drafts.add(DraftRepo.Draft(DraftRepo.Draft.TEXT, bottom_panel.getComposeText().toString()))
        }
        groupModel?.saveDrafts(this, drafts) {
            ALog.d(TAG, "saveDrafts result: $it")
        }
    }

    private fun updateMemberAtList() {
        val memberList = groupModel?.getGroupMemberList()
        if (memberList == null || memberList.isEmpty()) {
            ALog.d(TAG, "memberList is empty")

            return
        }
        bottom_panel.setAllAtList(memberList)
    }

    fun hideInput() {
        bottom_panel?.hideInput()
    }

    private fun handleSendText(text: CharSequence, replyContent: AmeGroupMessage.ReplyContent?, extContent: AmeGroupMessageDetail.ExtensionContent?) {
        if (replyContent == null) {
            GroupMessageLogic.messageSender.sendTextMessage(groupId, text.toString(), messageCallback, extContent)
        } else {
            GroupMessageLogic.messageSender.sendReplyMessage(groupId, text, replyContent, messageCallback, extContent)
        }
    }

    @SuppressLint("CheckResult")
    private fun handleSendAudio(data: Uri?, fileSize: Long, duration: Long) {
        if (data == null) {
            return
        }
        Observable.create(ObservableOnSubscribe<Boolean> {
            val file = PersistentBlobProvider.getInstance(this).getFile(ContentUris.parseId(data))
            GroupMessageLogic.messageSender.sendAudioMessage(getMasterSecret(), groupId,
                    AttachmentUtils.getAudioContent(AppContextHolder.APP_CONTEXT, data, fileSize, duration, "")
                            ?: throw Exception("AudioContent is null"), file.absolutePath, messageCallback)

            it.onNext(true)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnError {
                    PersistentBlobProvider.getInstance(this).delete(data)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {
                    ALog.e(TAG, "handleSendAudio error", it)
                })
    }

    @SuppressLint("CheckResult")
    private fun handleSendImage(selectPaths: List<String>?, takenPhoto: Boolean = false) {
        if (selectPaths == null || selectPaths.isEmpty()) {
            return
        }
        /**
         * Send images order with 1.5s interval
         */
        Observable.fromIterable(selectPaths).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { selectPath ->
            try {
                if (!BcmFileUtils.isExist(selectPath)) {
                    throw Exception("select image is not exist")
                }
                val uri = if (takenPhoto) Uri.fromFile(File(selectPath)) else AppUtil.getImageContentUri(this@ChatGroupConversationActivity, selectPath)
                val imageContent = AttachmentUtils.getAttachmentContent(this@ChatGroupConversationActivity, uri, selectPath)
                        as? AmeGroupMessage.ImageContent ?: throw Exception("ImageContent is null")
                val size = BitmapUtils.getActualImageSize(selectPath)
                imageContent.width = size.width
                imageContent.height = size.height
                GroupMessageLogic.messageSender.sendImageMessage(getMasterSecret(), groupId, imageContent, uri, selectPath, messageCallback)
            } catch (ex: Exception) {
                ALog.e(TAG, "sendImageMessage fail", ex)
            }
        }.delay(1500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }


    @SuppressLint("CheckResult")
    private fun handleSendDocument(data: Uri?) {
        if (data == null) {
            return
        }

        Observable.create(ObservableOnSubscribe<Boolean> {

            val filePath = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, data)
                    ?: throw Exception("file path is null")

            val attachmentContent = AttachmentUtils.getAttachmentContent(AppContextHolder.APP_CONTEXT, data, filePath)

            when (attachmentContent) {
                is AmeGroupMessage.ImageContent -> {
                    GroupMessageLogic.messageSender.sendImageMessage(getMasterSecret(), groupId, attachmentContent, data, filePath, messageCallback)
                }
                is AmeGroupMessage.VideoContent -> {
                    GroupMessageLogic.messageSender.sendVideoMessage(getMasterSecret(), groupId, data, attachmentContent, filePath, messageCallback)
                }
                is AmeGroupMessage.FileContent -> {
                    GroupMessageLogic.messageSender.sendDocumentMessage(getMasterSecret(), groupId, attachmentContent, filePath, messageCallback)
                }
            }
            it.onNext(true)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }) { throwable ->
                    ALog.e(TAG, "handleSendDocument error", throwable)
                }
    }

    @SuppressLint("CheckResult")
    private fun handleSendVideo(selectPaths: List<String>?) {
        if (selectPaths == null || selectPaths.isEmpty()) {
            return
        }

        Observable.fromIterable(selectPaths).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { selectPath ->
            try {
                if (!BcmFileUtils.isExist(selectPath)) {
                    throw Exception("select video is not exist")
                }
                val file = File(selectPath)
                val fileUri = Uri.fromFile(file)
                val content = AttachmentUtils.getAttachmentContent(AppContextHolder.APP_CONTEXT, fileUri, selectPath)
                        as? AmeGroupMessage.VideoContent ?: throw Exception("videoContent is null")
                GroupMessageLogic.messageSender.sendVideoMessage(getMasterSecret(), groupId, fileUri, content, selectPath, messageCallback)

            } catch (ex: Exception) {
                ALog.e(TAG, "sendVideoMessage error", ex)
            }
        }.delay(1500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {

                })
    }

    private fun handleGroupSetting() {
        ALog.i(TAG, "handleGroupSetting")
        if (groupModel?.getGroupInfo()?.legitimateState == AmeGroupInfo.LegitimateState.ILLEGAL) {
            return
        }

        if (groupModel?.myRole() != AmeGroupMemberInfo.VISITOR) {
            hideInput()

            layout_container?.postDelayed({
                startActivityForResult(Intent(this, ChatGroupSettingActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
                    putExtra(ARouterConstants.PARAM.PARAM_THREAD, threadId)
                }, DELETE_REQUEST_CODE)
            }, 200)
        }
    }

    override fun onModified(recipient: Recipient) {
        if (isDestroyed || isFinishing) {
            return
        }
        AmeDispatcher.mainThread.dispatch {
            invalidateOptionsMenu()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupViewModel.GroupChatAtEvent) {
        bottom_panel.insertAtRecipient(e.recipient)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupViewModel.GroupInfoChangedEvent) {
        chat_title_bar?.post {
            updateGroupInfo()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: GroupViewModel.MemberListChangedEvent) {
        chat_title_bar?.post {
            updateGroupInfo()
            updateMemberAtList()
        }
    }

    @Subscribe
    fun onEvent(e: GroupViewModel.MyRoleChangedEvent) {
        ALog.d(TAG, "receive MyRoleChangedEvent")
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(reEditEvent: ReEditEvent) {
        ALog.d(TAG, "receive recall event")
        if (GroupUtil.gidFromAddress(reEditEvent.address) == this.groupId) {
            bottom_panel.setComposeText(reEditEvent.content)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(multiSelectEvent: MultiSelectEvent) {
        if (!multiSelectEvent.isGroup) {
            return
        }

        val dataSet = multiSelectEvent.dataSet as? MutableSet<AmeGroupMessageDetail>

        isMultiSelecting = dataSet != null
        chat_title_bar.setMultiSelectionMode(isMultiSelecting)
        fragment?.setMultiSelectedItem(dataSet)

        bottom_panel.showMultiSelectMode(isMultiSelecting, dataSet)

        bottom_panel.postDelayed({
            hideKeyboard()
        }, 100)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupListChangedEvent) {
        if (event.gid == groupId) {
            if (groupModel?.myRole() == AmeGroupMemberInfo.VISITOR) {
                liveController?.onDestroy()

                if (!isFinishing && !isDestroyed) {
                    AmePopup.center.newBuilder().withContent(getString(R.string.chats_group_removed_notice_title, groupModel?.groupName()))
                            .withOkTitle(getString(R.string.common_understood)).show(this)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupViewModel.JoinRequestListChanged) {
        if (groupModel?.groupId() == event.groupId) {
            ALog.d(TAG, "receive JoinRequestListChanged event")
            chat_title_bar.showDot(groupModel?.getJoinRequestUnreadCount() ?: 0 > 0)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupNameOrAvatarChanged) {
        if (event.gid == groupId) {
            ALog.d(TAG, "receive GroupNameOrAvatarChanged event")
            if (event.name.isNotBlank()) {
                val memberCount = max((groupModel?.memberCount()
                        ?: 0), (groupModel?.getGroupInfo()?.memberCount ?: 0))
                chat_title_bar.updateGroupName(event.name, memberCount)
            }
            if (event.avatarPath.isNotBlank()) {
                chat_title_bar.updateGroupAvatar(groupId, event.avatarPath)
            }
        }
    }

    private fun updateGroupInfo() {
        val title = groupModel?.getGroupInfo()?.displayName ?: groupId.toString()
        val memberCount = max((groupModel?.memberCount()
                ?: 0), (groupModel?.getGroupInfo()?.memberCount ?: 0))
        chat_title_bar.setGroupChat(groupId, title, memberCount)
        chat_title_bar.showDot(groupModel?.getJoinRequestUnreadCount() ?: 0 > 0)

        if (groupModel?.getGroupInfo()?.legitimateState == AmeGroupInfo.LegitimateState.ILLEGAL) {
            illegal_group_layout.visibility = View.VISIBLE
        } else {
            illegal_group_layout.visibility = View.GONE
        }

        when {
            groupModel?.myRole() == AmeGroupMemberInfo.VISITOR -> {
                bottom_panel?.setRestrictionMode(true, getString(R.string.chats_group_visitor_ban))
                chat_title_bar.showRight(false)
            }
            groupModel?.getGroupInfo()?.key.isNullOrEmpty() -> {
                val text = if (groupModel?.getGroupInfo()?.newGroup == false) {
                    getString(R.string.chats_group_member_null_key_restriction_description)
                } else {
                    getString(R.string.chats_group_key_refreshing_description)
                }
                bottom_panel?.setRestrictionMode(true, text)
                chat_title_bar.showRight(true)
            }
            else -> {
                bottom_panel?.setRestrictionMode(false)
                chat_title_bar.showRight(true)
            }
        }

        checkPopState()
    }

    private fun checkPopState() {
        checkShowGroupNotice()
    }

    private fun checkShowGroupNotice() {
        val showNotice = groupModel?.getGroupInfo()?.isShowNotice ?: false
        val notice = groupModel?.getGroupInfo()?.noticeContent ?: ""
        if (showNotice && !TextUtils.isEmpty(notice)
                && groupModel?.myRole() == AmeGroupMemberInfo.MEMBER
                && !isShowingNotice) {
            isShowingNotice = true

            val wSelf = WeakReference(this)
            AmeDispatcher.mainThread.dispatch({
                if (wSelf.get()?.isFinishing == true) {
                    return@dispatch
                }
                try {
                    val dialog = ChatGroupNoticeDialog()
                    dialog.create(groupModel?.getGroupInfo()?.noticeContent
                            ?: "", object : ChatGroupNoticeDialog.ConfirmListener {
                        override fun onConfirm() {
                            groupModel?.updateNoticeShowState(false)
                        }
                    })
                    dialog.show(supportFragmentManager, "GroupNotice")
                } catch (e: Throwable) {
                    ALog.e(TAG, "", e)
                }
            }, 800)
        }
    }

    private fun initLiveFlowController() {
        liveController = LiveFlowController(this, groupId, groupModel?.myRole() == AmeGroupMemberInfo.OWNER)
        fragment?.setFlowWindowController(liveController)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        liveController?.onConfigurationChanged(newConfig)
    }
}