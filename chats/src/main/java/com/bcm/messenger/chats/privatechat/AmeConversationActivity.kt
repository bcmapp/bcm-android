package com.bcm.messenger.chats.privatechat

import android.app.Activity
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.SharedElementCallback
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.BottomPanelClickListener
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.chats.bean.SendContactEvent
import com.bcm.messenger.chats.components.ConversationInputPanel
import com.bcm.messenger.chats.components.titlebar.ChatTitleBar
import com.bcm.messenger.chats.privatechat.webrtc.CameraState
import com.bcm.messenger.chats.provider.ChatModuleImp
import com.bcm.messenger.chats.user.SendContactActivity
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.chats.util.ScreenshotManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.audio.AudioSlidePlayer
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.DraftRepo
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.*
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.imagepicker.BcmPickPhotoConstants
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.bean.SelectedModel
import com.bcm.messenger.common.mms.*
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.providers.PersistentBlobProvider
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.sms.OutgoingEncryptedMessage
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.sms.OutgoingTextMessage
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_conversation_activity.*
import kotlinx.android.synthetic.main.chats_conversation_input_panel.view.*
import me.imid.swipebacklayout.lib.SwipeBackLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Private chat
 *
 * Created by lishuangling
 */
@Route(routePath = ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
class AmeConversationActivity : SwipeBaseActivity(), RecipientModifiedListener {

    companion object {

        private const val TAG = "AmeConversationActivity"

        const val DELETE_REQUEST_CODE = 100
        const val DELETE_CONVERSATION = 101

        // The number setting here cannot conflict with the request code of BcmPickPhotoConstants
        private const val PICK_DOCUMENT = 200
        private const val PICK_LOCATION = 201

        private const val SEND_CONTACT = 202

    }

    private lateinit var glideRequests: GlideRequests

    private lateinit var mFragment: AmeConversationFragment

    private var recipientsStaleReceiver: BroadcastReceiver? = null

    private lateinit var mRecipient: Recipient

    private var isMultiSelecting = false

    private var mScreenshotManager: ScreenshotManager? = null

    private var mProfileDisposable: Disposable? = null

    private val draftsForCurrentState: DraftRepo.Drafts
        get() {
            val drafts = DraftRepo.Drafts()

            val composeText = bottom_panel?.getComposeText()
            if (!composeText.isNullOrEmpty()) {
                drafts.add(DraftRepo.Draft(DraftRepo.Draft.TEXT, composeText.toString()))
            }

            return drafts
        }

    private val isActiveGroup: Boolean
        get() {
            return false
        }

    private var mConversationModel: AmeConversationViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_conversation_activity)
        EventBus.getDefault().register(this)

        val address = intent.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (address != null) {
            mRecipient = Recipient.from(this, address, true)
            mRecipient.addListener(this)

        } else {
            finish()
            return
        }

        initializeReceivers()
        initViews()
        initData()

        AmeConfigure.checkAutoDeleteEnable { enable ->
            ALog.i(TAG, "auto_delete_enable- $enable")
        }

        window?.setStatusBarLightMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ALog.i(TAG, "onNewIntent")
        if (isFinishing) {
            ALog.i(TAG, "onNewIntent is finishing, return")
            return
        }

        val address = intent.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (address != null) {
            val recipient = Recipient.from(this, address, true)
            if (::mRecipient.isInitialized && recipient != mRecipient) {
                mRecipient.removeListener(this)
                mRecipient = recipient
                recipient.addListener(this)
                mConversationModel?.init(mConversationModel?.getThreadId() ?: 0L, recipient)
            }

        } else {
            return
        }

        markThreadAsRead(true)
        if (!bottom_panel?.getComposeText().isNullOrEmpty()) {
            ALog.i(TAG, "onNewIntent resetSnippet")
            updateThreadSnippet()
            bottom_panel?.setComposeText("")
        }

        setIntent(intent)
        initData()
        mFragment.onNewIntent()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)

        if (::mRecipient.isInitialized) {
            mRecipient.removeListener(this)
            RxBus.unSubscribe(mRecipient.address.serialize())
        }

        if (recipientsStaleReceiver != null) {
            unregisterReceiver(recipientsStaleReceiver)
        }

        mProfileDisposable?.dispose()
        super.onDestroy()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_REQUEST_CODE && resultCode == DELETE_CONVERSATION) {
            finish()
        }

        ALog.i(TAG, "onActivityResult:$requestCode")
        if (requestCode == BcmPickPhotoConstants.PICK_PHOTO_REQUEST && data != null) {
            val selectPaths = data.getSerializableExtra(BcmPickPhotoConstants.EXTRA_PATH_LIST) as ArrayList<SelectedModel>
            val imageList = mutableListOf<String>()
            val videoList = mutableListOf<Uri>()
            selectPaths.forEach {
                if (it.isVideo) {
                    videoList.add(BcmFileUtils.getFileUri(it.path))
                } else {
                    imageList.add(it.path)
                }
            }
            checkRecipientBlock {
                if (it) {
                    handleSendImage(imageList)
                    handleSendDocument(videoList)
                }
            }


        } else if (requestCode == PICK_DOCUMENT && data != null) {

            checkRecipientBlock {
                if (it) {
                    handleSendDocument(listOf(data.data))
                }
            }

        } else if (requestCode == BcmPickPhotoConstants.CAPTURE_REQUEST && resultCode == Activity.RESULT_OK) {
            val path = data?.getStringExtra(BcmPickPhotoConstants.EXTRA_CAPTURE_PATH)
            if (path != null) {
                val selectPaths = ArrayList<String>()
                selectPaths.add(path)

                checkRecipientBlock {
                    if (it) {
                        handleSendImage(selectPaths, true)
                    }
                }
            }
        } else if (requestCode == PICK_LOCATION && data != null) {
            val latitude = data.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
            val longitude = data.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
            val mapType = data.getIntExtra(ARouterConstants.PARAM.MAP.MAPE_TYPE, 0)
            val title = data.getStringExtra(ARouterConstants.PARAM.MAP.TITLE)
            val address = data.getStringExtra(ARouterConstants.PARAM.MAP.ADDRESS)
            if (mapType != 0) {

                checkRecipientBlock {
                    if (it) {
                        sendLocationMessage(AmeGroupMessage(AmeGroupMessage.LOCATION, AmeGroupMessage.LocationContent(latitude, longitude, mapType, title, address)).toString())

                    }
                }
            }

        } else if (requestCode == SEND_CONTACT && data != null) {
            try {
                val event = GsonUtils.fromJson<SendContactEvent>(data.getStringExtra(ARouterConstants.PARAM.PARAM_DATA), object : TypeToken<SendContactEvent>(){}.type)
                if (event.groupId == 0L) {
                    event.dataList.forEach { contactContent ->
                        sendContactMessage(contactContent)
                    }
                    AmeDispatcher.mainThread.dispatch({
                        if (!event.comment.isNullOrEmpty()) {
                            sendMessage(event.comment)
                        }
                    }, 1000)

                }

            }catch (ex: Exception) {
                ALog.e(TAG, "onActivityResult send_contact", ex)
            }
        }
    }

    override fun startActivity(intent: Intent) {
        if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
            intent.removeExtra(Browser.EXTRA_APPLICATION_ID)
        }
        try {
            super.startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(this, getString(R.string.chats_there_is_no_app_available_to_handle_this_link_on_your_device))
        }
    }

    override fun onStop() {
        super.onStop()
        ALog.i(TAG, "onStop")
        markThreadAsRead(true)
        updateThreadSnippet()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ALog.i(TAG, "onWindowFocusChanged hasFocus: $hasFocus")
        if (!hasFocus) {
            bottom_panel?.clearFocus()
        }
    }

    override fun onBackPressed() {
        when {
            layout_container.isInputOpen -> {
                hideInput()
            }
            isMultiSelecting -> onEvent(MultiSelectEvent(false, null))
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        ALog.i(TAG, "onPause")

        bottom_panel.voiceRecodingPanel.onPause()
        markLastSeen()
        AudioSlidePlayer.stopAll()

        mScreenshotManager?.removeScreenshotListener()
        mScreenshotManager = null

    }

    override fun onResume() {
        super.onResume()
        ALog.i(TAG, "onResume")
        initializePanelEnabled()

        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        mScreenshotManager = ScreenshotManager(this)
        mScreenshotManager?.setScreenshotListener {
            sendScreenshotMessage()
        }

    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val indexId = data.getLongExtra(ShareElements.PARAM.MEDIA_INDEX, 0L)
            setExitSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                    names?.clear()
                    sharedElements?.clear()
                    val view = mFragment.getExitView(indexId)
                    names?.add("${ShareElements.Activity.MEDIA_PREIVEW}$indexId")
                    sharedElements?.put("${ShareElements.Activity.MEDIA_PREIVEW}$indexId", view
                            ?: bottom_panel)
                    setExitSharedElementCallback(null as? SharedElementCallback)
                }
            })
        }
    }

    override fun onModified(recipient: Recipient) {
        if (isFinishing) {
            return
        }
        if (recipient == mRecipient) {
            bottom_panel?.setBurnExpireAfterRead(recipient.expireMessages)
            chat_title_bar?.setPrivateChat(mRecipient)

            mConversationModel?.checkProfileKeyUpdateToDate(this)
        }
    }

    fun hideInput() {
        bottom_panel?.hideInput()
    }

    private fun initViews() {

        val bundle = Bundle().apply {
            intent.extras?.let {
                putAll(it)
            }
            putParcelable(ARouterConstants.PARAM.PARAM_MASTER_SECRET, getMasterSecret())
            putSerializable(ARouterConstants.PARAM.PARAM_LOCALE, getSelectedLocale(baseContext))
        }
        mFragment = initFragment(R.id.fragment_content, AmeConversationFragment(), bundle)
        mFragment.setChangeReadStateListener(object : AmeConversationFragment.ChangeReadStateListener {
            override fun onOperateMarkThreadRead() {
                markThreadAsRead(false)
            }
        })

        chat_title_bar.setOnChatTitleCallback(object : ChatTitleBar.OnChatTitleCallback {

            override fun onRight(multiSelect: Boolean) {
                handleConversationSettings()
            }

            override fun onTitle(multiSelect: Boolean) {
                if (!mRecipient.isFriend) {
                    hideInput()
                    BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND).putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address).navigation(this@AmeConversationActivity)
                }
            }

            override fun onLeft(multiSelect: Boolean) {
                hideInput()
                when {
                    isMultiSelecting -> onEvent(MultiSelectEvent(false, null))
                    else -> finish()
                }
            }

        })


        layout_container.addOnKeyboardShownListener(object : KeyboardAwareLinearLayout.OnKeyboardShownListener {

            override fun onKeyboardShown() {
                mFragment.scrollToBottom(false)
            }

        })

        bottom_panel.bindInputAwareLayout(layout_container)
        bottom_panel.setOnConversationInputListener(object : ConversationInputPanel.OnConversationInputListener {

            override fun onBeforeTextOrAudioHandle(): Boolean {
                return checkRecipientBlock { }
            }

            override fun onMessageSend(message: CharSequence, replyContent: AmeGroupMessage.ReplyContent?, extContent: AmeGroupMessageDetail.ExtensionContent?) {
                sendMessage(message)
            }

            override fun onEmojiPanelShow(show: Boolean) {
                if (show) {
                    setSwipeBackEnable(false)
                    mFragment.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onMoreOptionsPanelShow(show: Boolean) {
                if (show) {
                    setSwipeBackEnable(false)
                    mFragment.scrollToBottom(false)
                } else {
                    setSwipeBackEnable(true)
                }
            }

            override fun onAudioStateChanged(state: Int, recordUri: Uri?, byteSize:Long, playTime:Long) {
                when (state) {
                    ConversationInputPanel.OnConversationInputListener.AUDIO_START -> {

                    }
                    ConversationInputPanel.OnConversationInputListener.AUDIO_COMPLETE -> {

                        mConversationModel?.sendMediaMessage(this@AmeConversationActivity, getMasterSecret(),
                                createOutgoingMessage("", SlideDeck().apply { addSlide(AudioSlide(this@AmeConversationActivity, recordUri, byteSize, playTime, MediaUtil.AUDIO_AAC, true)) },
                                (mRecipient.expireMessages * 1000).toLong(), -1, (mConversationModel?.getThreadId() ?: 0L) <= 0L)) {

                            if (it && null != recordUri) {
                                AmeDispatcher.io.dispatch {
                                    try {
                                        PersistentBlobProvider.getInstance(this@AmeConversationActivity).delete(recordUri)
                                    } catch (ex: Exception) {
                                        ALog.logForSecret(TAG, "delete audio record fail", ex)
                                    }
                                }

                            }

                        }

                    }
                    else -> {

                    }
                }
            }

        })

        bottom_panel.addOptionItem(BottomPanelItem(getString(R.string.chats_more_option_camera), R.drawable.chats_icon_camera, object : BottomPanelClickListener {
            override fun onClick(name: String, view: View) {
                checkRecipientBlock {
                    if (it) {
                        PermissionUtil.checkCamera(this@AmeConversationActivity) {
                            if (!it) {
                                return@checkCamera
                            }
                            try {
                                BcmPickPhotoView.Builder(this@AmeConversationActivity)
                                        .setCapturePhoto(true)
                                        .build().start()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

            }
        }),
                BottomPanelItem(getString(R.string.chats_more_option_album), R.drawable.chats_icon_picture, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        checkRecipientBlock {
                            if (it) {
                                PermissionUtil.checkCamera(this@AmeConversationActivity) { aBoolean ->
                                    if (!aBoolean) {
                                        return@checkCamera
                                    }
                                    BcmPickPhotoView.Builder(this@AmeConversationActivity)
                                            .setShowGif(true)
                                            .setShowVideo(true)
                                            .setPickPhotoLimit(5)
                                            .setItemSpanCount(3)
                                            .setApplyText(getString(R.string.chats_send))
                                            .build().start()
                                }
                            }
                        }

                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_file), R.drawable.chats_icon_file, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        checkRecipientBlock {
                            if (it) {
                                PermissionUtil.checkStorage(view.context) { aBoolean ->
                                    if (aBoolean) {
                                        selectMediaType(this@AmeConversationActivity, "*/*", null, PICK_DOCUMENT)
                                    }
                                }
                            }
                        }

                    }

                    private fun selectMediaType(activity: Activity, type: String, extraMimeType: Array<String>?, requestCode: Int) {
                        val intent = Intent()
                        intent.type = type
                        intent.action = Intent.ACTION_OPEN_DOCUMENT
                        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        try {
                            activity.startActivityForResult(intent, requestCode)
                            return
                        } catch (anfe: ActivityNotFoundException) {
                            Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.")
                        }

                    }

                }))

        if (!mRecipient.isGroupRecipient && !mRecipient.isSelf) {
            bottom_panel.addOptionItem(
                    BottomPanelItem(getString(R.string.chats_more_option_call), R.drawable.chats_icon_call, object : BottomPanelClickListener {
                        override fun onClick(name: String, view: View) {
                            checkRecipientBlock {
                                if (it) {
                                    if (mRecipient.isFriend || mRecipient.isAllowStranger) {
                                        val chatProvider = ChatModuleImp()
                                        chatProvider.startRtcCallService(this@AmeConversationActivity, mRecipient.address.serialize(), CameraState.Direction.NONE.ordinal)
                                    } else {
                                        ToastUtil.show(this@AmeConversationActivity, getString(R.string.common_chats_stranger_disturb_notice, mRecipient.name), Toast.LENGTH_LONG)
                                    }
                                }
                            }

                        }
                    }))
        }
        bottom_panel.addOptionItem(
                BottomPanelItem(getString(R.string.chats_more_option_location), R.drawable.chats_icon_location, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        checkRecipientBlock {
                            if (it) {
                                PermissionUtil.checkLocationPermission(this@AmeConversationActivity) { granted ->
                                    if (!granted) {
                                        return@checkLocationPermission
                                    }
                                    BcmRouter.getInstance().get(ARouterConstants.Activity.MAP).navigation(this@AmeConversationActivity, PICK_LOCATION)
                                }
                            }
                        }

                    }
                }),
                BottomPanelItem(getString(R.string.chats_more_option_namecard), R.drawable.chats_icon_contact, object : BottomPanelClickListener {
                    override fun onClick(name: String, view: View) {
                        checkRecipientBlock {
                            if (it) {
                                val intent = Intent(this@AmeConversationActivity, SendContactActivity::class.java)
                                intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address)
                                startActivityForResult(intent, SEND_CONTACT)
                            }
                        }

                    }

                }))
        if (!mRecipient.isSelf) {
            bottom_panel.addOptionItem(BottomPanelItem(getString(R.string.chats_more_option_shredder), R.drawable.chats_72_recall, object : BottomPanelClickListener {
                override fun onClick(name: String, view: View) {
                    checkRecipientBlock {
                        if (it) {
                            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
                            provider.showClearHistoryConfirm(this@AmeConversationActivity, {
                                mConversationModel?.clearConversationHistory(this@AmeConversationActivity)
                            }, {

                            })

                        }
                    }

                }
            }))
        }

        bottom_panel.setBurnAfterReadVisible(mRecipient) {
            checkRecipientBlock {
                if (it) {
                    bottom_panel.callBurnAfterRead(mRecipient, getMasterSecret())
                }
            }
        }

        swipeBackLayout?.addSwipeListener(object : SwipeBackLayout.SwipeListener {
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
        if (mConversationModel == null) {
            mConversationModel = ViewModelProviders.of(this).get(AmeConversationViewModel::class.java)
        }

        val threadId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, 0L)
        mConversationModel?.init(threadId, mRecipient)

        glideRequests = GlideApp.with(this)

        bottom_panel.setMasterSecret(getMasterSecret())
        bottom_panel.setConversationId(threadId)
        chat_title_bar.setPrivateChat(mRecipient)

        initializeProfiles()
        initializeDraft()

        mConversationModel?.checkLastDecryptFailTime(this, getMasterSecret())
        mConversationModel?.checkProfileKeyUpdateToDate(this)

    }

    private fun initializeProfiles() {
        mProfileDisposable?.dispose()
        mProfileDisposable = AmeModuleCenter.contact().fetchProfile(mRecipient) {}
    }

    private fun checkRecipientBlock(callback: (continueAction: Boolean) -> Unit): Boolean {
        if (mRecipient.isBlocked) {
            AmeCenterPopup.instance().newBuilder()
                    .withCancelable(false)
                    .withContent(getString(R.string.chats_blocked_next_cofirm_content))
                    .withCancelTitle(getString(R.string.chats_blocked_next_confirm_cancel)).withCancelListener {
                        callback.invoke(false)
                    }.withOkTitle(getString(R.string.chats_blocked_next_confirm_unblock)).withOkListener {
                        try {
                            val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
                            contactProvider.blockContact(mRecipient.address, !mRecipient.isBlocked)
                            callback.invoke(false)

                        } catch (ex: Exception) {
                            ALog.logForSecret(TAG, "checkRecipient block recipient fail", ex)
                            callback.invoke(false)
                        }
                    }.show(this)

            return false
        } else {
            callback.invoke(true)
            return true
        }
    }

    private fun handleSendImage(selectPaths: List<String>?, takenPhoto: Boolean = false) {
        if (selectPaths == null || selectPaths.isEmpty()) {
            return
        }
        ALog.i(TAG, "handleSendImage size: ${selectPaths.size}")
        Observable.fromIterable(selectPaths).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { path ->

            val uri = if (takenPhoto) Uri.fromFile(File(path)) else AppUtil.getImageContentUri(this@AmeConversationActivity, path)
            val slide = AttachmentUtils.getImageSlide(this@AmeConversationActivity, uri) ?: throw Exception("ImageSlide is null")
            createOutgoingMessage("", SlideDeck().apply { addSlide(slide) }, (mRecipient.expireMessages * 1000).toLong(), -1, (mConversationModel?.getThreadId() ?: 0L) <= 0L)

        }.delay(1500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mConversationModel?.sendMediaMessage(this, getMasterSecret(), it)

                }, {
                    ALog.logForSecret(TAG, "handleSendImage error", it)
                })

    }

    private fun handleSendDocument(selectUris: List<Uri>?) {
        if (selectUris == null || selectUris.isEmpty()) {
            return
        }
        ALog.i(TAG, "handleSendDocument size: ${selectUris.size}")
        Observable.fromIterable(selectUris).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).map { uri ->

            val slide = AttachmentUtils.getDocumentSlide(AppContextHolder.APP_CONTEXT, uri) ?: throw Exception("DocumentSlide is null")
            createOutgoingMessage("", SlideDeck().apply { addSlide(slide) }, (mRecipient.expireMessages * 1000).toLong(), -1, (mConversationModel?.getThreadId() ?: 0L) <= 0L)

        }.delay(1500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    mConversationModel?.sendMediaMessage(this, getMasterSecret(), it)
                }, {
                    ALog.logForSecret(TAG, "handleSendDocument error", it)
                })

    }

    private fun handleConversationSettings() {
        if (isActiveGroup) {
            // ignore
        } else {
            hideInput()

            layout_container?.postDelayed({
                BcmRouter.getInstance()
                        .get(ARouterConstants.Activity.CHAT_USER_PATH)
                        .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address)
                        .putLong(ARouterConstants.PARAM.PARAM_THREAD, mConversationModel?.getThreadId() ?: 0L)
                        .navigation(this, DELETE_REQUEST_CODE)
            }, 200)

        }
    }

    private fun initializeDraft() {
        val draftText = intent.getStringExtra(ARouterConstants.PARAM.PRIVATE_CHAT.TEXT_EXTRA)
        ALog.d(TAG, "initializeDraft draftText: $draftText")
        if (!draftText.isNullOrEmpty()) {
            bottom_panel?.setComposeText(draftText)
            bottom_panel?.requestComposeFocus()
        } else {
            initializeDraftFromDatabase {
                if (it.isNotEmpty()) {
                    bottom_panel?.requestComposeFocus()
                }
            }
        }
    }

    private fun initializePanelEnabled() {
        val enabled = true
        bottom_panel.panel_countdown_send_button.isEnabled = enabled
        bottom_panel.panel_audio_toggle.isEnabled = enabled

    }

    private fun initializeDraftFromDatabase(callback: (draft: String) -> Unit) {
        mConversationModel?.loadDraft {
            ALog.d(TAG, "initializeDraftFromDatabase: $it")
            bottom_panel?.setComposeText(it)
            callback(it)
        }
    }

    private fun initializeReceivers() {
        recipientsStaleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mRecipient = Recipient.from(context, mRecipient.address, true)
                mRecipient.addListener(this@AmeConversationActivity)
                onModified(mRecipient)
                mFragment.reloadList()
            }
        }

        registerReceiver(recipientsStaleReceiver, IntentFilter().apply {
            addAction(Recipient.RECIPIENT_CLEAR_ACTION)
        })

    }

    private fun updateThreadSnippet() {
        mConversationModel?.updateThreadSnippet(this, mRecipient, this.draftsForCurrentState)
    }

    private fun markThreadAsRead(lastSeen: Boolean) {
        mConversationModel?.markThreadAsRead(this, lastSeen)
    }

    private fun markLastSeen() {
        mConversationModel?.markLastSeen()
    }

    private fun createOutgoingMessage(body: String, slideDeck: SlideDeck, expiresIn: Long, subscriptionId: Int, initiating: Boolean): OutgoingMediaMessage {
        var outgoingMessage = OutgoingMediaMessage(mRecipient,
                slideDeck,
                body,
                AmeTimeUtil.getMessageSendTime(),
                subscriptionId,
                expiresIn,
                ThreadRepo.DistributionTypes.DEFAULT)

        outgoingMessage = OutgoingSecureMediaMessage(outgoingMessage)

        return outgoingMessage
    }

    private fun createOutgoingMessage(body: String): OutgoingTextMessage {

        val groupShareContent = AmeGroupMessage.GroupShareContent.fromLink(body)
       return if (groupShareContent != null) {
                OutgoingLocationMessage(mRecipient, AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_CARD, groupShareContent).toString(), (mRecipient.expireMessages * 1000).toLong())
            } else {
                OutgoingEncryptedMessage(mRecipient, body, (mRecipient.expireMessages * 1000).toLong())
            }

    }

    private fun sendMessage(messageText: CharSequence) {

        fun checkInputStringIsLegal(inputTextString: String): Boolean {
            if (TextUtils.isEmpty(inputTextString)) return false
            return true
        }

        if (!checkInputStringIsLegal(messageText.toString())) {
            ToastUtil.show(this, getString(R.string.chats_send_empty_message))
            return
        }
        mConversationModel?.sendTextMessage(this, createOutgoingMessage(messageText.toString()))

    }

    private fun sendLocationMessage(locationData: String) {
        val expireIn = mRecipient.expireMessages * 1000L
        if (mRecipient.isGroupRecipient) {
            val outgoingMessage = OutgoingComplexMediaMessage(mRecipient,
                    locationData,
                    AmeTimeUtil.getMessageSendTime(),
                    -1,
                    expireIn,
                    ThreadRepo.DistributionTypes.DEFAULT,
                    true, true)

            mConversationModel?.sendMediaMessage(this, getMasterSecret(), outgoingMessage)

        } else {
            val message = OutgoingLocationMessage(mRecipient, locationData, expireIn)
            mConversationModel?.sendTextMessage(this, message)

        }
    }

    private fun sendScreenshotMessage() {
        val screenshotMessage = AmeGroupMessage(AmeGroupMessage.SCREEN_SHOT_MESSAGE, AmeGroupMessage.ScreenshotContent("")).toString()
        mConversationModel?.sendTextMessage(this, OutgoingLocationMessage(mRecipient, screenshotMessage, (mRecipient.expireMessages * 1000).toLong()))

    }

    private fun sendContactMessage(contactContent: AmeGroupMessage.ContactContent) {

        mConversationModel?.sendTextMessage(this, OutgoingLocationMessage(mRecipient,
                AmeGroupMessage(AmeGroupMessage.CONTACT, contactContent).toString(), (mRecipient.expireMessages * 1000).toLong()))

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(multiSelectEvent: MultiSelectEvent) {
        if (multiSelectEvent.isGroup) {
            return
        }

        val dataSet = multiSelectEvent.dataSet as? Set<MessageRecord>
        isMultiSelecting = multiSelectEvent.dataSet != null
        chat_title_bar.setMultiSelectionMode(isMultiSelecting)

        mFragment.setMultiSelectedItem(dataSet)

        bottom_panel.showMultiSelectMode(isMultiSelecting, dataSet)

        bottom_panel.postDelayed({
            hideInput()
        }, 100)

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(reEditEvent: ReEditEvent) {
        if (reEditEvent.address == this.mRecipient.address) {
            bottom_panel.setComposeText(reEditEvent.content)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: UserOfflineEvent){
        if (!isFinishing && e.address == mRecipient.address) {
            ToastUtil.show(this, getString(R.string.chats_text_recipient_not_online, mRecipient.name))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: RecallFailEvent) {
        if (!isFinishing && event.uid == mRecipient.address.toString()) {
            val message = if (event.isOffline) getString(R.string.chats_recall_message_fail_offline) else getString(R.string.chats_recall_message_fail_description)
            AmePopup.result.failure(this, message, true)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: VersionTooLowEvent) {
        if (!isFinishing) {
            ToastUtil.show(this, getString(R.string.common_too_low_version_notice), Toast.LENGTH_LONG)
        }
    }

}
