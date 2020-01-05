package com.bcm.messenger.chats.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Vibrator
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.RotateAnimation
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.adapter.BottomPanelAdapter
import com.bcm.messenger.chats.adapter.ChatAtListAdapter
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.privatechat.AmeConversationViewModel
import com.bcm.messenger.chats.provider.ChatModuleImp
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.audio.AudioRecorder
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.MultiSelectEvent
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.OutgoingExpirationUpdateMessage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.InputAwareLayout
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout
import com.bcm.messenger.common.ui.popup.DataPickerPopupWindow
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_conversation_input_panel.view.*
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * Created by wjh on 2018/8/24
 */
class ConversationInputPanel : androidx.constraintlayout.widget.ConstraintLayout,
        VoiceRecodingPanel.Listener,
        KeyboardAwareLinearLayout.OnKeyboardShownListener,
        KeyboardAwareLinearLayout.OnKeyboardHiddenListener,
        AudioRecorder.IRecorderListener {

    interface OnConversationInputListener {

        companion object {
            const val AUDIO_START = 0
            const val AUDIO_COMPLETE = 1
            const val AUDIO_CANCEL = 2
            const val AUDIO_ERROR = 3
        }

        //check before send text or audio，true: pass，false: cancel
        fun onBeforeTextOrAudioHandle(): Boolean
        fun onMessageSend(message: CharSequence, replyContent: AmeGroupMessage.ReplyContent? = null, extContent: AmeGroupMessageDetail.ExtensionContent? = null)
        fun onEmojiPanelShow(show: Boolean)
        fun onMoreOptionsPanelShow(show: Boolean)
        fun onAudioStateChanged(state: Int, recordUri: Uri?, byteSize: Long, playTime: Long)
    }

    inner class ExtraContainer(val view: View, val isEmojiPanel: Boolean, override var isShowing: Boolean) : InputAwareLayout.InputView {

        private var optionOpenAnim = RotateAnimation(0f, 45f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

        private var optionCloseAnim = RotateAnimation(45f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

        override fun show(height: Int, immediate: Boolean) {
            isShowing = true
            lockBottomExtraContainer(height)
            if (isEmojiPanel) {
                showEmojiPanel(true)
            } else {
                showMoreOptionPanel(true)
            }
        }

        override fun hide(immediate: Boolean) {
            isShowing = false
            if (isEmojiPanel) {
                showEmojiPanel(false)
            } else {
                showMoreOptionPanel(false)
            }
            if (mInputAwareLayout?.isKeyboardOpen == false && mEmojiContainer?.isShowing == false && mOptionContainer?.isShowing == false) {
                lockBottomExtraContainer(0)
            }
        }

        private fun showMoreOptionPanel(show: Boolean) {
            if (show) {
                if ((!optionOpenAnim.hasStarted() || optionOpenAnim.hasEnded()) && panel_option_container.visibility != View.VISIBLE) {

                    panel_more_options_iv.clearAnimation()
                    optionOpenAnim.duration = 100
                    optionOpenAnim.fillAfter = true
                    optionOpenAnim.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationRepeat(animation: Animation?) {
                        }

                        override fun onAnimationEnd(animation: Animation?) {

                        }

                        override fun onAnimationStart(animation: Animation?) {
                            panel_option_container.visibility = View.VISIBLE
                            mListener?.onMoreOptionsPanelShow(show)
                        }

                    })
                    panel_more_options_iv.startAnimation(optionOpenAnim)
                }

            } else {

                if ((optionOpenAnim.hasStarted() && !optionOpenAnim.hasEnded()) || panel_option_container.visibility == View.VISIBLE) {
                    panel_more_options_iv.clearAnimation()
                    optionCloseAnim.duration = 100
                    optionCloseAnim.fillAfter = true
                    panel_more_options_iv.startAnimation(optionCloseAnim)
                    panel_option_container.visibility = View.GONE
                    mListener?.onMoreOptionsPanelShow(show)
                }
            }
        }

        /**
         * @param show
         */
        private fun showEmojiPanel(show: Boolean) {
            if (show) {
                if (panel_emoji_container.visibility != View.VISIBLE) {
                    panel_emoji_container.visibility = View.VISIBLE
                    panel_emoji_toggle.setImageDrawable(AppUtil.getDrawable(resources, R.drawable.chats_keyboard))
                    mListener?.onEmojiPanelShow(show)
                }
            } else {
                if (panel_emoji_container.visibility == View.VISIBLE) {
                    panel_emoji_container.visibility = View.GONE
                    panel_emoji_toggle.setImageDrawable(AppUtil.getDrawable(resources, R.drawable.chats_emoji))
                    mListener?.onEmojiPanelShow(show)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ConversationInputPanel"

        const val OPTION_ITEM_CAMERA = "camera"
        const val OPTION_ITEM_IMAGE = "image"
        const val OPTION_ITEM_DOCUMENT = "document"
        const val OPTION_ITEM_LOCATION = "location"
        const val OPTION_ITEM_CALL = "call"
        const val OPTION_ITEM_CONTACT = "contact"
        const val OPTION_ITEM_RECALL = "recall"

        const val OPTION_ITEM_MAX = 4 // item number
        const val INPUT_NUM_MAX = 4000 // max char number

        const val AUDIO_RECORD_MAX_TIME = 59800L //max 59s
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

        LayoutInflater.from(context).inflate(R.layout.chats_conversation_input_panel, this)
        initResources(attributeSet)
        init(context)
    }

    private var isGroup = false
    private var mCurrentConversationId: Long = 0//group id or threadId
    private var mAtDisposable: Disposable? = null

    private var mListener: OnConversationInputListener? = null
    private lateinit var mOptionAdapter: BottomPanelAdapter

    private val mAllAtList = mutableListOf<Recipient>()

    private lateinit var mChatAtAdapter: ChatAtListAdapter

    private lateinit var audioRecorder: AudioRecorder
    lateinit var voiceRecodingPanel: VoiceRecodingPanel

    private var mInputAwareLayout: InputAwareLayout? = null

    private var pickView: DataPickerPopupWindow? = null
    private var expireValue = 1

    private var mEmojiContainer: ExtraContainer? = null
    private var mOptionContainer: ExtraContainer? = null

    private var mBatchSelected: Set<Any>? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mInputAwareLayout?.addOnKeyboardShownListener(this)
        mInputAwareLayout?.addOnKeyboardHiddenListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ALog.d(TAG, "onDetachedFromWindow")
        mInputAwareLayout?.removeOnKeyboardHiddenListener(this)
        mInputAwareLayout?.removeOnKeyboardShownListener(this)
    }

    override fun onStartClicked() {
        ALog.i(TAG, "onStartClicked")

        val pass = mListener?.onBeforeTextOrAudioHandle() ?: true
        if (pass) {
            if (checkHasAudioPermission()) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(10)
                setWindowScreenLocked(true)
                ALog.i(TAG, "onStartClicked")
                audioRecorder.startRecording()
            } else {
                PermissionUtil.checkAudio(context) {}
            }
        }
    }

    override fun onFinishClicked() {
        ALog.i(TAG, "onFinishClicked")

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(10)
        setWindowScreenLocked(false)
        audioRecorder.stopRecording()
        panel_compose_text.requestFocus()
    }

    @SuppressLint("CheckResult")
    override fun onCancelClicked() {
        ALog.i(TAG, "onCancelClicked")

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(30)
        setWindowScreenLocked(false)
        audioRecorder.cancelRecording()
        panel_compose_text.requestFocus()
    }

    override fun onRecorderStarted() {
        ALog.i(TAG, "onRecorderStarted")
        voiceRecodingPanel.showRecordState()
        mListener?.onAudioStateChanged(OnConversationInputListener.AUDIO_START, null, 0, 0)
    }

    override fun onRecorderProgress(millisTime: Long) {
        voiceRecodingPanel.showPlayTime(millisTime)
    }

    override fun onRecorderCanceled() {
        ALog.i(TAG, "onRecorderCanceled ")
        voiceRecodingPanel.hideRecordState()
    }

    override fun onRecorderFinished() {
        voiceRecodingPanel.hideRecordState()
    }

    override fun onRecorderSucceed(recordUri: Uri, byteSize: Long, playTime: Long) {
        ALog.i(TAG, "onRecordFinished $byteSize, time:$playTime")

        if (byteSize > 0 && playTime >= 1000) {
            mListener?.onAudioStateChanged(OnConversationInputListener.AUDIO_COMPLETE, recordUri, byteSize, playTime)
        } else {
            if (playTime < 1000) {
                ToastUtil.show(context, AppUtil.getString(AppContextHolder.APP_CONTEXT, R.string.chats_record_time_too_short))
            }
            mListener?.onAudioStateChanged(OnConversationInputListener.AUDIO_CANCEL, recordUri, byteSize, playTime)
        }
    }

    override fun onRecorderFailed(error: Exception) {
        voiceRecodingPanel.hideRecordState()
        ALog.e(TAG, "onRecorderFailed ", error)
        ToastUtil.show(context, resources.getString(R.string.chats_unable_to_record_audio_warning))
        mListener?.onAudioStateChanged(OnConversationInputListener.AUDIO_ERROR, null, 0, 0)
    }

    override fun onKeyboardHidden() {
        if (mEmojiContainer?.isShowing == false && mOptionContainer?.isShowing == false) {
            lockBottomExtraContainer(0)
        }
    }

    override fun onKeyboardShown() {
        ALog.d(TAG, "onKeyboardShown")
        voiceRecodingPanel.onKeyboardShown()
        mInputAwareLayout?.let {
            lockBottomExtraContainer(it.keyboardHeight)
        }
    }

    private fun initResources(attributeSet: AttributeSet?) {
        val array = context.obtainStyledAttributes(attributeSet, R.styleable.chats_conversationInputPanelStyle)
        isGroup = array.getBoolean(R.styleable.chats_conversationInputPanelStyle_chats_isGroup, false)
        array.recycle()
        panel_compose_text.background?.setColorFilter(context.getColorCompat(R.color.common_color_white), PorterDuff.Mode.MULTIPLY)

        clipChildren = false
        clipToPadding = false

    }

    private fun init(context: Context) {
        mEmojiContainer = ExtraContainer(panel_emoji_container, true, false)
        mOptionContainer = ExtraContainer(panel_option_container, false, false)
        panel_compose_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val text = s.toString().trim()
                updateToggleButtonState(text.isNotEmpty())
                if (text.length > INPUT_NUM_MAX) {
                    panel_compose_text.setText(panel_compose_text.text?.subSequence(0, INPUT_NUM_MAX) ?: "")
                    panel_compose_text.setSelection(INPUT_NUM_MAX)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onAtTextChanged(panel_compose_text, start, before, count)
            }

        })
        panel_compose_text.setOnClickListener {
            panel_compose_text.isFocusableInTouchMode = true
            mInputAwareLayout?.showSoftkey(panel_compose_text)
            panel_compose_text.postDelayed({ panel_compose_text.requestFocus() }, 200)
        }

        panel_countdown_send_button.setOnClickListener {
            val pass = mListener?.onBeforeTextOrAudioHandle() ?: true
            if (pass) {
                if (panel_compose_text.textTrimmed.isNotEmpty()) {
                    val replyContent = panel_reply_content_tv.tag as? AmeGroupMessage.ReplyContent
                    mListener?.onMessageSend(panel_compose_text.text.toString(), replyContent, getTargetExtensionContent())
                    panel_compose_text.text?.clear()
                    setReply(null, null)
                    panel_countdown_send_button.startCountDown()
                }
            }
        }
        panel_countdown_send_button.isEnabled = true

        panel_more_options_iv.setOnClickListener {
            val showOption = panel_option_container.visibility != View.VISIBLE
            mOptionContainer?.let {
                if (showOption) {
                    mInputAwareLayout?.show(panel_compose_text, it)
                } else {
                    mInputAwareLayout?.hideAttachedInput(true)
                }
            }
        }

        mOptionAdapter = BottomPanelAdapter(context)
        panel_option_container.layoutManager = GridLayoutManager(context, OPTION_ITEM_MAX)
        panel_option_container.adapter = mOptionAdapter

        panel_emoji_toggle.setOnClickListener {
            val showEmoji = panel_emoji_container.visibility != View.VISIBLE
            mEmojiContainer?.let {
                if (showEmoji) {
                    mInputAwareLayout?.show(panel_compose_text, it)
                } else {
                    mInputAwareLayout?.showSoftkey(panel_compose_text)
                }
            }

        }
        panel_emoji_container.setInputCallback { emoji ->
            val posStart = panel_compose_text.selectionStart
            val posEnd = panel_compose_text.selectionEnd
            if (posStart != posEnd) {
                panel_compose_text.text?.delete(posStart, posEnd)
            }
            if (panel_compose_text.textTrimmed.length + emoji.length <= INPUT_NUM_MAX) {
                panel_compose_text.text?.insert(posStart, emoji)
            }
        }
        panel_emoji_container.setDeleteCallback {
            panel_compose_text.onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            panel_compose_text.onKeyUp(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }

        voiceRecodingPanel = VoiceRecodingPanel()
        voiceRecodingPanel.onFinishInflate(this)
        voiceRecodingPanel.setListener(this)

        audioRecorder = AudioRecorder(context, AUDIO_RECORD_MAX_TIME, null)
        audioRecorder.setListener(this)

        initAtList()

        initMultiSelectAction()

    }

    override fun clearFocus() {
        panel_compose_text.clearFocus()
        super.clearFocus()
    }

    fun hideInput() {
        mInputAwareLayout?.hideCurrentInput(panel_compose_text)
    }

    fun setBurnAfterReadVisible(recipient: Recipient, callback: ((v: View) -> Unit)?) {

        panel_burn_toggle.visibility = if (!recipient.isSelf && callback != null) View.VISIBLE else View.GONE
        panel_burn_delay_tv.visibility = panel_burn_toggle.visibility
        if (callback != null) {
            setBurnExpireAfterRead(recipient.expireMessages)
            panel_burn_toggle.setOnClickListener {
                mInputAwareLayout?.hideCurrentInput(panel_compose_text)
                callback.invoke(it)
            }
        } else {
            panel_burn_toggle.setOnClickListener(null)
        }
    }

    fun callBurnAfterRead(recipient: Recipient, masterSecret: MasterSecret) {
        fun getBurnExpireFromIndex(index: Int): Int {
            return when (index) {
                0 -> 0
                1 -> 30
                2 -> 120
                3 -> 300
                4 -> 3600
                5 -> 86400
                6 -> 604800
                7 -> 1209600
                else -> 0
            }
        }

        val c = context
        if (c is FragmentActivity) {
            pickView = DataPickerPopupWindow(c)
                    .setTitle(c.getString(R.string.chats_destroy_message_popup_title))
                    .setDataList(listOf(
                            c.getString(R.string.chats_auto_clear_off),
                            c.getString(R.string.chats_destroy_message_30_sec),
                            c.getString(R.string.chats_destroy_message_2_min),
                            c.getString(R.string.chats_destroy_message_5_min),
                            c.getString(R.string.chats_destroy_message_1_hour),
                            c.getString(R.string.chats_destroy_message_1_day),
                            c.getString(R.string.chats_destroy_message_1_week),
                            c.getString(R.string.chats_destroy_message_2_weeks)
                    ))
                    .setCurrentIndex(expireValue)
                    .setCallback { index ->
                        val expire = getBurnExpireFromIndex(index)
                        if (recipient.expireMessages == expire) {
                            post {
                                ToastUtil.show(context, resources.getString(R.string.chats_read_burn_choose_same))
                            }
                            return@setCallback
                        }

                        Observable.create(ObservableOnSubscribe<Boolean> {
                            val goon = if (recipient.expireMessages != expire) {

                                Repository.getRecipientRepo()?.setExpireTime(recipient, expire.toLong())

                                true

                            } else {
                                false
                            }
                            it.onNext(goon)
                            it.onComplete()
                        }).subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ result ->
                                    if (result) {

                                        ViewModelProviders.of(c).get(AmeConversationViewModel::class.java).sendMediaMessage(c,
                                                masterSecret,
                                                OutgoingExpirationUpdateMessage(recipient, AmeTimeUtil.getMessageSendTime(), expire * 1000L)) { success ->

                                            if (success) {
                                                setBurnExpireAfterRead(expire)

                                            }
                                        }

                                    }
                                }, {
                                    ALog.e(TAG, "callBurnAfterRead error", it)
                                })

                    }

            pickView?.show()
        }

    }

    fun setBurnExpireAfterRead(expire: Int) {

        fun getIndexFromBurnExpire(expire: Int): Int {
            return when (expire) {
                0 -> 0
                30 -> 1
                120 -> 2
                300 -> 3
                3600 -> 4
                86400 -> 5
                604800 -> 6
                1209600 -> 7
                else -> 0
            }
        }

        expireValue = getIndexFromBurnExpire(expire)
        when (expireValue) {
            1 -> {
                panel_burn_delay_tv.text = "30s"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            2 -> {
                panel_burn_delay_tv.text = "2m"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            3 -> {
                panel_burn_delay_tv.text = "5m"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            4 -> {
                panel_burn_delay_tv.text = "1h"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            5 -> {
                panel_burn_delay_tv.text = "1d"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            6 -> {
                panel_burn_delay_tv.text = "1w"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            7 -> {
                panel_burn_delay_tv.text = "2w"
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_enabled_icon))
            }
            else -> {
                panel_burn_delay_tv.text = ""
                panel_burn_toggle.setImageDrawable(context.getDrawable(R.drawable.chats_destroy_msg_icon))
            }
        }
    }

    fun setOnConversationInputListener(listener: OnConversationInputListener) {
        mListener = listener
    }

    fun setConversationId(conversationId: Long) {
        mCurrentConversationId = conversationId
    }

    fun bindInputAwareLayout(inputAwareLayout: InputAwareLayout) {
        mInputAwareLayout = inputAwareLayout
        inputAwareLayout.addOnKeyboardShownListener(this)
        inputAwareLayout.addOnKeyboardHiddenListener(this)
    }

    @SuppressLint("CheckResult")
    fun setAllAtList(allAtList: List<AmeGroupMemberInfo>) {
        ALog.d(TAG, "setAllAtList size: ${allAtList.size}")
        Observable.create(ObservableOnSubscribe<List<Recipient>> {
            try {
                val self = Recipient.major()
                val resultList = mutableListOf<Recipient>()
                allAtList.forEach {
                    val r = Recipient.from(context, it.uid, true)
                    if (r.address != self.address) {
                        resultList.add(r)
                    }
                }
                it.onNext(resultList)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mAllAtList.clear()
                    mAllAtList.addAll(it)
                    mChatAtAdapter.setAtList(mAllAtList, "")
                    if (isGroup) {
                        mChatAtAdapter.setGroupId(mCurrentConversationId)
                    }
                }, {
                    ALog.e(TAG, "onAtTextChanged error", it)
                })
    }

    fun setComposeText(text: CharSequence) {
        panel_compose_text?.setText(text)
        panel_compose_text?.post {
            panel_compose_text?.setSelection(panel_compose_text.text?.length ?: 0)
        }

    }

    fun getComposeText(): CharSequence {
        return panel_compose_text.text ?: ""
    }


    fun requestComposeFocus() {
        postDelayed({
            ALog.d(TAG, "requestComposeFocus")
            panel_compose_text?.requestFocus()
            mInputAwareLayout?.showSoftkey(panel_compose_text ?: return@postDelayed)
        }, 300)

    }

    fun setMasterSecret(masterSecret: MasterSecret?) {
        if (masterSecret != null) {
            audioRecorder = AudioRecorder(context, AUDIO_RECORD_MAX_TIME, masterSecret)
            audioRecorder.setListener(this)
        }
    }

    fun setRestrictionMode(isRestricted: Boolean, content: CharSequence? = null) {
        if (isRestricted) {
            panel_restriction_ban.visibility = View.VISIBLE
            panel_audio_toggle.isEnabled = false
            panel_countdown_send_button.isEnabled = false
            panel_compose_text.isEnabled = false
            panel_more_options_iv.isEnabled = false
            panel_emoji_toggle.isEnabled = false
            restriction_ban_txt.text = content

        } else {
            panel_restriction_ban.visibility = View.GONE
            panel_audio_toggle.isEnabled = true
            panel_countdown_send_button.isEnabled = true
            panel_compose_text.isEnabled = true
            panel_more_options_iv.isEnabled = true
            panel_emoji_toggle.isEnabled = true
            restriction_ban_txt.text = content
        }
    }

    fun addOptionItem(vararg items: BottomPanelItem) {
        mOptionAdapter.addItems(items.toMutableList())
    }

    fun addOptionItem(index: Int, item: BottomPanelItem) {
        mOptionAdapter.addItem(index, item)
    }

    fun getOptionSize(): Int {
        return mOptionAdapter.getSize()
    }

    fun removeAllOptionItems() {
        mOptionAdapter.clearItems()
    }

    fun removeOptionItem(name: String) {
        mOptionAdapter.removeItem(name)
    }

    fun updateOptionItem(item: BottomPanelItem) {
        mOptionAdapter.updateItem(item)
    }

    fun isMoreOptionPanelShowed(): Boolean {
        return panel_option_container.visibility == View.VISIBLE
    }

    fun isEmojiPanelShowed(): Boolean {
        return panel_emoji_container.visibility == View.VISIBLE
    }

    fun insertAtRecipient(recipient: Recipient) {
        addAtData(TempAtData(recipient, panel_compose_text.selectionStart))
    }

    fun showMultiSelectMode(show: Boolean, batchSelected: Set<Any>?) {
        if(show) {
            if(panel_other_action_layout.visibility != View.VISIBLE) {
                mBatchSelected = batchSelected
                mInputAwareLayout?.hideCurrentInput(panel_compose_text)
                panel_other_action_layout.clearAnimation()
                panel_other_action_layout.visibility = View.VISIBLE
                panel_other_action_layout.startAnimation(AnimationUtils.loadAnimation(context, R.anim.common_slide_from_bottom_fast))
                panel_main_ban.visibility = View.GONE
            }
        }else {
            if(panel_other_action_layout.visibility == View.VISIBLE) {
                mBatchSelected = batchSelected
                panel_other_action_layout.clearAnimation()
                panel_other_action_layout.startAnimation(AnimationUtils.loadAnimation(context, R.anim.common_slide_to_bottom_fast).apply {
                    setAnimationListener(object : Animation.AnimationListener{
                        override fun onAnimationRepeat(animation: Animation?) {
                        }

                        override fun onAnimationEnd(animation: Animation?) {
                            panel_other_action_layout.visibility = View.GONE
                        }

                        override fun onAnimationStart(animation: Animation?) {
                        }

                    })
                })
                panel_main_ban.visibility = View.VISIBLE
            }
        }
    }

    fun showAtList(show: Boolean) {
        ALog.d(TAG, "showAtList show: $show")
        if (mAllAtList.isEmpty()) {
            return
        }

        val visibility = if (show) View.VISIBLE else View.GONE
        if (panel_at_list.visibility != visibility) {
            mChatAtAdapter.setAtList(mAllAtList, "")
            panel_at_list.visibility = visibility
            if (visibility == View.VISIBLE) {
                val animation = AnimationUtils.loadAnimation(context, R.anim.common_slide_from_bottom)
                panel_at_list.clearAnimation()
                panel_at_list.startAnimation(animation)
            }
        }

    }

    fun setReply(messageDetail: AmeGroupMessageDetail?, locateCallback: ((v: View) -> Unit)?) {

        if(messageDetail != null) {

            postDelayed({
                panel_compose_text.requestFocus()
                mInputAwareLayout?.showSoftkey(panel_compose_text)

                panel_reply_layout.visibility = View.VISIBLE
                val recipient = messageDetail.sender
                val groupModel = GroupLogic.getModel(messageDetail.gid)
                panel_reply_to_tv.text = if (recipient == null) {
                    null
                }else {
                    BcmGroupNameUtil.getGroupMemberName(recipient, groupModel?.getGroupMember(recipient.address.serialize()))
                }

                val replyContent = if (messageDetail.message.type == AmeGroupMessage.CHAT_REPLY) {//If the reply message type is also reply, it is treated as a normal text message, and only the text is returned
                    val rc = messageDetail.message.content as AmeGroupMessage.ReplyContent
                    AmeGroupMessage.ReplyContent(messageDetail.serverIndex, messageDetail.senderId, AmeGroupMessage<AmeGroupMessage.TextContent>(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(rc.text)).toString(), "")
                }
                else {
                    AmeGroupMessage.ReplyContent(messageDetail.serverIndex, messageDetail.senderId, messageDetail.message.toString(), "")
                }

                panel_reply_content_tv.tag = replyContent
                panel_reply_content_tv.visibility = View.VISIBLE
                panel_reply_content_tv.text = replyContent.getReplyDescribe(messageDetail.gid, false)
                panel_reply_text_tv.visibility = View.GONE

                panel_reply_close_iv.setOnClickListener {
                    setReply(null, null)
                }
                panel_reply_layout.setOnClickListener {
                    locateCallback?.invoke(it)
                }

            }, 150)

        }else {

            panel_reply_layout.visibility = View.GONE
            panel_reply_content_tv.tag = null
        }

    }

    private fun checkHasAudioPermission(): Boolean {
        return PackageManager.PERMISSION_GRANTED == context.packageManager.checkPermission("android.permission.RECORD_AUDIO", context.packageName)
    }

    /**
     * set bottom container height
     */
    private fun lockBottomExtraContainer(height: Int) {
        ALog.d(TAG, "lockBottomExtraContainer height: $height")
        panel_extra_container.layoutParams = panel_extra_container.layoutParams.apply {
            this.height = height
        }
    }

    private fun setWindowScreenLocked(lock: Boolean) {
        val context = getContext()
        if (context is Activity) {
            val window = context.window ?: return
            if (lock) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun initMultiSelectAction() {
        panel_forward_btn.setOnClickListener {

            val weakThis = WeakReference(this)
            ChatModuleImp().forwardMessage(context, isGroup, mCurrentConversationId, mBatchSelected ?: return@setOnClickListener) {
                if (it.isEmpty()){
                    weakThis.get()?.postDelayed({
                        EventBus.getDefault().post(MultiSelectEvent(isGroup, null))
                    },500)

                }
            }
        }
        panel_delete_btn.setOnClickListener {
            if(mCurrentConversationId == 0L) {
                ALog.i(TAG, "message delete fail, conversationId is 0")
                return@setOnClickListener
            }
            if(mBatchSelected?.isNotEmpty() == true) {
                ChatModuleImp().deleteMessage(context, isGroup, mCurrentConversationId, mBatchSelected ?: return@setOnClickListener) {
                    if(it.isEmpty()) {
                        EventBus.getDefault().post(MultiSelectEvent(isGroup, null))
                    }
                }
            }else {
                ToastUtil.show(context, context.getString(R.string.chats_select_mode_delete_empty))
            }
        }
    }

    private fun initAtList() {
        ALog.d(TAG, "initAtList")
        mChatAtAdapter = ChatAtListAdapter(context, object : ChatAtListAdapter.AtActionListener {
            override fun onSelect(recipient: Recipient) {

                val lastIndex = getAtingIndex()
                remoteNearestAtData(panel_compose_text.text?.length ?: 0)
                addAtData(TempAtData(recipient, lastIndex))

            }

        })
        panel_at_list.adapter = mChatAtAdapter
        panel_at_list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private val mCurrentAtDataList = mutableListOf<TempAtData>()

    private fun addAtData(data: TempAtData) {
        if (data.index < 0) {
            ALog.d(TAG, "addAtData fail, index is smaller zero")
            return
        }

        try {

            ALog.d(TAG, "addAtData index: ${data.index}")
            val recipient = data.recipient
            if (recipient != null) {
                val atPart = createAtPart(recipient, mCurrentConversationId)
                val sourceText = panel_compose_text.text
                val spanBuilder = SpannableStringBuilder()
                spanBuilder.append(atPart)
                spanBuilder.append(" ")
                sourceText?.insert(data.index, spanBuilder)
            }

            var insertIndex = 0
            for (d in mCurrentAtDataList) {
                //Determine the index of the at data inserted, and ensure that the larger the index, the later
                if (data.index > d.index) {
                    insertIndex++
                } else {
                    break
                }
            }
            mCurrentAtDataList.add(insertIndex, data)

        } catch (ex: Exception) {
            ALog.e(TAG, "addAtData error", ex)

        } finally {
            showAtList(getAtingIndex() >= 0)
        }
    }

    private fun remoteNearestAtData(fromIndex: Int) {
        try {
            var target: TempAtData? = null
            for (d in mCurrentAtDataList) {
                //Find at data whose index is less than fromIndex and delete it directly
                if (d.index <= fromIndex) {
                    target = d
                } else {
                    break
                }
            }

            if (target != null) {
                mCurrentAtDataList.remove(target)

                val removeIndex = target.index
                ALog.d(TAG, "remoteNearestAtData index: $removeIndex, from: $fromIndex")
                val recipient = target.recipient
                if (recipient == null) {
                    if (fromIndex > removeIndex) {
                        val sourceText = panel_compose_text.text
                        sourceText?.delete(removeIndex, fromIndex)
                    }
                } else {

                    val replaceText = panel_compose_text.text?.subSequence(removeIndex, fromIndex)
                    val atPart = createAtPart(recipient, mCurrentConversationId)
                    if (replaceText?.endsWith(atPart) == true) {
                        panel_compose_text.text?.delete(removeIndex, fromIndex)
                    }
                }

            }

        } catch (ex: Exception) {
            ALog.e(TAG, "remoteNearestAtData error", ex)
        } finally {
            showAtList(getAtingIndex() >= 0)
        }
    }

    private fun updateAtDataIndex(fromIndex: Int, count: Int) {
        if (count == 0) {
            return
        }
        val iterator = mCurrentAtDataList.iterator()
        while (iterator.hasNext()) {
            val d = iterator.next()
            // Find all at data whose index is greater than or equal to this fromIndex, increase the index by count, indicating that the input box inserts or deletes text at the fromIndex position,
            // and the indexes of all at data after that must be updated
            ALog.d(TAG, "updateAtDataIndex i: ${d.index}, from: $fromIndex, count: $count")
            if (d.index >= fromIndex) {
                d.index += count
                if (d.index < 0) {
                    iterator.remove()
                }
            }
        }
    }


    private fun getAtingIndex(): Int {
        for (d in mCurrentAtDataList) {
            if (d.recipient == null) {
                return d.index
            }
        }
        return -1
    }

    private var mLastInputText: CharSequence = ""

    @SuppressLint("CheckResult")
    private fun onAtTextChanged(inputText: EditText, start: Int, before: Int, count: Int) {
        if (mAllAtList.isEmpty()) {
            return
        }

        val add = count - before // If count is greater than before, the total behavior of the current operation is to add characters, otherwise it is remove character
        val atingIndex = getAtingIndex()

        ALog.d(TAG, "onAtTextChanged sourceText: ${inputText.text}, mLastAtIndex: $atingIndex, start: $start, add: $add")
        if (atingIndex >= 0) {

            if (start <= atingIndex) {
                if (add < 0) {

                    if (start + before > atingIndex) {
                        remoteNearestAtData(atingIndex)
                    }
                }

            } else {

                val search = inputText.text.substring(atingIndex + 1)
                ALog.d(TAG, "onAtTextChanged search: $search")

                mAtDisposable?.dispose()
                mAtDisposable = Observable.create(ObservableOnSubscribe<List<Recipient>> {
                    try {
                        if (search.isNullOrEmpty()) {
                            it.onNext(mAllAtList)
                        } else {
                            val model = GroupLogic.getModel(mCurrentConversationId)
                            it.onNext(mAllAtList.filter {
                                val name = BcmGroupNameUtil.getGroupMemberName(it, model?.getGroupMember(it.address.serialize()))
                                StringAppearanceUtil.containIgnore(name, search)
                            })
                        }
                    } finally {
                        it.onComplete()
                    }
                }).delaySubscription(500, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            mChatAtAdapter.setAtList(it, search)
                        }, {
                            ALog.e(TAG, "onAtTextChanged error", it)
                        })

            }

            updateAtDataIndex(start, add)

        } else {

            updateAtDataIndex(start, add)

            val num = Math.abs(add)
            if (num == 1) {

                if (add > 0) {
                    if (inputText.text.endsWith(ARouterConstants.CHAT_AT_CHAR)) {

                        addAtData(TempAtData(null, start))
                    }

                } else {

                    if (start < mLastInputText.length && start + 1 <= mLastInputText.length && mLastInputText.subSequence(start, start + 1) == " ") {
                        remoteNearestAtData(start)
                    }
                }
            }
        }

        mLastInputText = inputText.text.toString()
        ALog.d(TAG, "onAtTextChanged lastInputText: $mLastInputText")

    }

    private fun getTargetExtensionContent(): AmeGroupMessageDetail.ExtensionContent {
        var atPart: CharSequence
        val targetList = mutableListOf<String>()
        for (temp in mCurrentAtDataList) {
            val at = temp.recipient ?: continue
            atPart = createAtPart(at, mCurrentConversationId)
            if (panel_compose_text.text?.contains(atPart) == true) {
                targetList.add(at.address.serialize())
                ALog.d(TAG, "getTargetExtensionContent part: ${at.address.serialize()}")
            }
        }
        mCurrentAtDataList.clear()
        showAtList(false)
        val ext = AmeGroupMessageDetail.ExtensionContent()
        ext.atList = targetList
        return ext
    }

    private fun updateToggleButtonState(hasText: Boolean) {
        if (!hasText) {
            panel_countdown_send_button.visibility = View.GONE
            panel_audio_toggle.visibility = View.VISIBLE
        } else {
            panel_audio_toggle.visibility = View.GONE
            panel_countdown_send_button.visibility = View.VISIBLE
        }
    }

    private fun createAtPart(recipient: Recipient, groupId: Long): CharSequence {
        val model = GroupLogic.getModel(groupId)
        return ARouterConstants.CHAT_AT_CHAR + BcmGroupNameUtil.getGroupMemberAtName(recipient, model?.getGroupMember(recipient.address.serialize()))
    }

    private class TempAtData(var recipient: Recipient?, var index: Int = -1) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TempAtData

            if (recipient != other.recipient) return false
            if (index != other.index) return false

            return true
        }

        override fun hashCode(): Int {
            var result = recipient?.hashCode() ?: 0
            result = 31 * result + index
            return result
        }
    }


}