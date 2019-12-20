package com.bcm.messenger.adhoc.ui.channel

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.app.SharedElementCallback
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocChatTitleBar
import com.bcm.messenger.adhoc.component.AdHocConversationPanel
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.chats.bean.BottomPanelClickListener
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.imagepicker.BcmPickPhotoConstants
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.bean.SelectedModel
import com.bcm.messenger.common.providers.PersistentBlobProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.adhoc_activity_conversation.*
import kotlinx.android.synthetic.main.adhoc_conversation_input_panel.view.*
import java.io.File

/**
 * adhoc chat activity
 * Created by wjh on 2019/7/27
 */
@Route(routePath = ARouterConstants.Activity.ADHOC_CONVERSATION)
class AdHocConversationActivity : SwipeBaseActivity() {

    companion object {
        private const val TAG = "AdHocConversationActivity"
        const val REQUEST_SETTING = 100

        const val PICK_DOCUMENT = 101
        const val PICK_LOCATION = 102
        const val PICK_CONTACT = 103
    }

    private var mSession: String = ""
    private var mFragment: AdHocConversationFragment? = null
    private var mSelf: Recipient? = null

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val indexId = data.getLongExtra(ShareElements.PARAM.MEDIA_INDEX, 0L)
            setExitSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                    names?.clear()
                    sharedElements?.clear()
                    val view = mFragment?.getMessageView(indexId)
                    names?.add("${ShareElements.Activity.MEDIA_PREIVEW}$indexId")
                    sharedElements?.put("${ShareElements.Activity.MEDIA_PREIVEW}$indexId", view
                            ?: bottom_panel)
                    setExitSharedElementCallback(null as? SharedElementCallback)
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ALog.i(TAG, "onActivityResult requestCode: $requestCode, resultCode: $resultCode")
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        when(requestCode) {
            REQUEST_SETTING -> {
                finish()
            }
            BcmPickPhotoConstants.PICK_PHOTO_REQUEST -> {
                val selectPaths = data?.getSerializableExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST) as? ArrayList<SelectedModel>
                val videoList = mutableListOf<String>()
                val imageList = mutableListOf<String>()
                selectPaths?.forEach {
                    if (it.isVideo) {
                        videoList.add(it.path)
                    } else {
                        imageList.add(it.path)
                    }
                }
                handleSendImage(imageList)
                handleSendVideo(videoList)
            }
            PICK_DOCUMENT -> {
                handleSendDocument(data?.data)
            }
            BcmPickPhotoConstants.CAPTURE_REQUEST -> {
                val path = data?.getStringExtra(BcmPickPhotoConstants.EXTRA_CAPTURE_PATH)
                if (path != null) {
                    val selectPaths = ArrayList<String>()
                    selectPaths.add(path)
                    handleSendImage(selectPaths, true)
                }
            }
            PICK_CONTACT -> {

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_activity_conversation)
        initViewAndResource()
        window?.setStatusBarLightMode()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        hideInput()
        setIntent(intent)
        markAllRead {
            mSession = intent?.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION) ?: ""
            ALog.i(TAG, "onNewIntent session: $mSession")
            AdHocMessageLogic.initModel(this, mSession)
            initSessionInfo()
            mFragment?.onNewIntent()
        }
    }

    override fun finish() {
        ALog.i(TAG, "finish")
        markAllRead()
        val draft = bottom_panel?.getComposeText()
        if (draft != null) {
            AdHocMessageLogic.getModel()?.saveDraft(draft) {
                ALog.i(TAG, "saveDraft result: $it")
            }
        }
        super.finish()
    }

    override fun onBackPressed() {
        when {
            layout_container.isInputOpen -> {
                hideInput()
            }
            else -> super.onBackPressed()
        }
    }

    private fun initViewAndResource() {
        try {
            mSelf = Recipient.fromSelf(this, true)
        }catch (ex: Exception) {
            finish()
            return
        }
        mSession = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION) ?: ""
        AdHocMessageLogic.initModel(this, mSession)
        mFragment = initFragment(R.id.fragment_content, AdHocConversationFragment(), null)

        chat_title_bar.setOnChatTitleCallback(object : AdHocChatTitleBar.OnChatTitleCallback {
            override fun onLeft() {
                hideInput()
                finish()
            }

            override fun onRight() {
                hideInput()
                layout_container?.postDelayed( {
                    startActivityForResult(Intent(this@AdHocConversationActivity, AdHocChannelSettingActivity::class.java).apply {
                        putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, mSession)
                    }, REQUEST_SETTING)

                }, 300)

            }

            override fun onTitle() {
            }

        })

        layout_container.addOnKeyboardShownListener(object : KeyboardAwareLinearLayout.OnKeyboardShownListener {
            override fun onKeyboardShown() {
                mFragment?.scrollToBottom(false)
            }

        })

        bottom_panel.bindInputAwareLayout(layout_container)
        bottom_panel.setOnConversationListener(object : AdHocConversationPanel.OnConversationInputListener {
            override fun onBeforeConversationHandle(): Boolean {
                return true
            }

            override fun onMessageSend(message: CharSequence, atList: Set<String>) {
                AdHocMessageLogic.send(mSession, mSelf?.name ?: "", message.toString(), atList)
            }

            override fun onEmojiPanelShow(show: Boolean) {
                ALog.i(TAG, "onEmojiPanelShow show: $show")
                if (show) {
                    setSwipeBackEnable(false)
                    mFragment?.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onMoreOptionsPanelShow(show: Boolean) {
                ALog.i(TAG, "onMoreOptionsPanelShow show: $show")
                if (show) {
                    setSwipeBackEnable(false)
                    mFragment?.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onAudioStateChanged(state: Int, recordUri: Uri?, byteSize: Long, playTime: Long) {
                when (state) {
                    AdHocConversationPanel.OnConversationInputListener.AUDIO_START -> {

                    }
                    AdHocConversationPanel.OnConversationInputListener.AUDIO_COMPLETE -> {
                        handleSendAudio(recordUri, byteSize, playTime)
                    }
                    else -> {

                    }
                }
            }
        })

        bottom_panel.removeAllOptionItems()
        bottom_panel.addOptionItem(BottomPanelItem(getString(R.string.chats_more_option_camera), R.drawable.chats_icon_camera, object : BottomPanelClickListener {
            override fun onClick(name: String, view: View) {
                PermissionUtil.checkCamera(this@AdHocConversationActivity) {
                    if (it) {
                        try {
                            BcmPickPhotoView.Builder(this@AdHocConversationActivity)
                                    .setCapturePhoto(true)
                                    .build().start()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }),
                BottomPanelItem(getString(R.string.chats_more_option_album), R.drawable.chats_icon_picture, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        PermissionUtil.checkCamera(this@AdHocConversationActivity) {
                            if (it) {
                                BcmPickPhotoView.Builder(this@AdHocConversationActivity)
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
                        PermissionUtil.checkStorage(this@AdHocConversationActivity) {
                            if (it) {
                                selectMediaType(this@AdHocConversationActivity, "*/*", null, PICK_DOCUMENT)
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
                }))

        initSessionInfo()
    }

    private fun initSessionInfo() {
        val sessionInfo = AdHocSessionLogic.getSession(mSession)
        sessionInfo?.let {
            chat_title_bar.setSession(sessionInfo)
            bottom_panel.setComposeText(it.draft)

            if (sessionInfo.isChat()) {
                AdHocSessionLogic.addChatSession(it.uid) {
                    ALog.i(TAG, "initSessionInfo addChat result: $it")
                }
            }
        }
    }


    private fun markAllRead(callback: ((result: Boolean) -> Unit)? = null) {
        val model = AdHocMessageLogic.getModel()
        if (model != null) {
            model.readAll {
                ALog.i(TAG, "markAllRead session: $mSession, result: $it")
                if (it) {
                    AdHocMessageLogic.updateSessionUnread(mSession, 0, true)
                    AdHocSessionLogic.updateAtMeStatus(mSession, false)
                }
                callback?.invoke(it)
            }
        }else {
            callback?.invoke(false)
        }
    }


    fun hideInput() {
        layout_container?.hideCurrentInput(bottom_panel?.panel_compose_text ?: return)
    }


    private fun handleSendAudio(data: Uri?, fileSize: Long, duration: Long) {
        if (data == null) {
            return
        }
        Observable.create(ObservableOnSubscribe<AmeGroupMessage.AttachmentContent> {
            val file = PersistentBlobProvider.getInstance(this).getFile(ContentUris.parseId(data))
            val content = AttachmentUtils.getAudioContent(AppContextHolder.APP_CONTEXT, data, fileSize, duration, Uri.fromFile(file).toString())
                    ?: throw Exception("getAudioContent fail")
            it.onNext(content)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnError {
                    PersistentBlobProvider.getInstance(this).delete(data)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    AdHocMessageLogic.send(mSession, mSelf?.name ?: "", it)
                }, {
                    ALog.e(TAG, "handleSendAudio error", it)
                })
    }


    private fun handleSendImage(selectPaths: List<String>?, takenPhoto: Boolean = false) {
        if (selectPaths == null || selectPaths.isEmpty()) {
            return
        }
        val overSizeList = mutableListOf<String>()
        var hasNoticeTooLarge = false
        var index = 0

        Observable.fromIterable(selectPaths).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { selectPath ->

            if (!BcmFileUtils.isExist(selectPath)) {
                throw Exception("select image is not exist")
            }
            val uri = if (takenPhoto) Uri.fromFile(File(selectPath)) else AppUtil.getImageContentUri(this@AdHocConversationActivity, selectPath)
            val imageContent = AttachmentUtils.getAttachmentContent(this@AdHocConversationActivity, uri, selectPath)
                    as? AmeGroupMessage.ImageContent ?: throw Exception("ImageContent is null")
            if (imageContent.size > AmeGroupMessage.AttachmentContent.SIZE_MAX) {
                overSizeList.add(selectPath)
                throw Exception("select image size is too large")
            }
            val size = BitmapUtils.getActualImageSize(selectPath)
            imageContent.width = size.width
            imageContent.height = size.height
            imageContent.url = uri.toString()
            imageContent

        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val delay = if (index == 0) 0L else 1000L
                    index++
                    AmeDispatcher.mainThread.dispatch({
                        AdHocMessageLogic.send(mSession, mSelf?.name ?: "", it)
                    }, delay)

                }, {
                    ALog.e(TAG, "handleSendImage error", it)
                    if (overSizeList.isNotEmpty() && !hasNoticeTooLarge) {
                        hasNoticeTooLarge = true
                        ToastUtil.show(this, getString(R.string.adhoc_attachment_too_large_upload_fail), Toast.LENGTH_LONG)
                    }
                })

    }

    private fun handleSendDocument(data: Uri?) {
        if (data == null) {
            return
        }
        val overSizeList = mutableListOf<String>()
        var hasNoticeTooLarge = false
        Observable.create(ObservableOnSubscribe<AmeGroupMessage.AttachmentContent> {

            val filePath = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, data)
                    ?: throw Exception("file path is null")
            val attachmentContent = AttachmentUtils.getAttachmentContent(AppContextHolder.APP_CONTEXT, data, filePath)
                    ?: throw Exception("attachmentContent is null")

            if (attachmentContent.size > AmeGroupMessage.AttachmentContent.SIZE_MAX) {
                overSizeList.add(filePath)
                throw Exception("select document size is too large")
            }

            if (attachmentContent is AmeGroupMessage.ImageContent) {
                val size = BitmapUtils.getActualImageSize(filePath)
                attachmentContent.width = size.width
                attachmentContent.height = size.height
            }
            attachmentContent.url = Uri.fromFile(File(filePath)).toString()
            it.onNext(attachmentContent)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    AdHocMessageLogic.send(mSession, mSelf?.name ?: "", it)

                }) { throwable ->
                    ALog.e(TAG, "handleSendDocument error", throwable)
                    if (overSizeList.isNotEmpty() && !hasNoticeTooLarge) {
                        hasNoticeTooLarge = true
                        ToastUtil.show(this, getString(R.string.adhoc_attachment_too_large_upload_fail), Toast.LENGTH_LONG)
                    }
                }
    }


    private fun handleSendVideo(selectPaths: List<String>?) {
        if (selectPaths == null || selectPaths.isEmpty()) {
            return
        }
        val overSizeList = mutableListOf<String>()
        var hasNoticeTooLarge = false
        var index = 0
        Observable.fromIterable(selectPaths).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { selectPath ->

            if (!BcmFileUtils.isExist(selectPath)) {
                throw Exception("select video is not exist")
            }
            val file = File(selectPath)
            val fileUri = Uri.fromFile(file)
            val content = AttachmentUtils.getAttachmentContent(AppContextHolder.APP_CONTEXT, fileUri, selectPath)
                    as? AmeGroupMessage.VideoContent ?: throw Exception("videoContent is null")
            if (content.size > AmeGroupMessage.AttachmentContent.SIZE_MAX) {
                overSizeList.add(selectPath)
                throw Exception("select video size is too large")
            }
            content.url = fileUri.toString()
            content

        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val delay = if (index > 0) 1000L else 0L
                    index++
                    AmeDispatcher.mainThread.dispatch({
                        AdHocMessageLogic.send(mSession, mSelf?.name ?: "", it)

                    }, delay)

                }, {
                    ALog.e(TAG, "handleSendVideo error", it)
                    if (overSizeList.isNotEmpty() && !hasNoticeTooLarge) {
                        hasNoticeTooLarge = true
                        ToastUtil.show(this, getString(R.string.adhoc_attachment_too_large_upload_fail), Toast.LENGTH_LONG)
                    }
                })
    }


}