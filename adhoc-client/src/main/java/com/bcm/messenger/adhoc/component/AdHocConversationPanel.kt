package com.bcm.messenger.adhoc.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import android.view.animation.RotateAnimation
import androidx.recyclerview.widget.GridLayoutManager
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.chats.adapter.BottomPanelAdapter
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.chats.components.ConversationInputPanel
import com.bcm.messenger.chats.components.VoiceRecodingPanel
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.ui.InputAwareLayout
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.permission.PermissionUtil
import kotlinx.android.synthetic.main.adhoc_conversation_input_panel.view.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.audio.AudioRecorder

/**
 * adhoc input panel
 * Created by wjh on 2019/7/29
 */
class AdHocConversationPanel : androidx.constraintlayout.widget.ConstraintLayout,
        KeyboardAwareLinearLayout.OnKeyboardShownListener,
        KeyboardAwareLinearLayout.OnKeyboardHiddenListener,
        VoiceRecodingPanel.Listener, AudioRecorder.IRecorderListener {

    /**
     * callback
     */
    interface OnConversationInputListener {

        companion object {
            const val AUDIO_START = 0
            const val AUDIO_COMPLETE = 1
            const val AUDIO_CANCEL = 2
            const val AUDIO_ERROR = 3
        }

        //check before send text or audio
        fun onBeforeConversationHandle(): Boolean
        fun onMessageSend(message: CharSequence, atList: Set<String>)
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
            if (mInputAwareLayout?.isKeyboardOpen == false && mEmojiExtraContainer?.isShowing == false && mOptionExtraContainer?.isShowing == false) {
                lockBottomExtraContainer(0)
            }
        }

        /**
         * show more option
         * @param show
         */
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
         * show emoji
         * @param show
         */
        private fun showEmojiPanel(show: Boolean) {
            if (show) {
                if (panel_emoji_container.visibility != View.VISIBLE) {
                    panel_emoji_container.visibility = View.VISIBLE
                    panel_emoji_toggle.setImageDrawable(AppUtil.getDrawable(resources, R.drawable.chats_conversation_keyboard_icon))
                    mListener?.onEmojiPanelShow(show)
                }
            } else {
                if (panel_emoji_container.visibility == View.VISIBLE) {
                    panel_emoji_container.visibility = View.GONE
                    panel_emoji_toggle.setImageDrawable(AppUtil.getDrawable(resources, R.drawable.chats_conversation_emoji_icon))
                    mListener?.onEmojiPanelShow(show)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AdHocConversationPanel"
        const val INPUT_NUM_MAX = 4000//max char num
    }

    private var mListener: OnConversationInputListener? = null
    private var mInputAwareLayout: InputAwareLayout? = null
    private var mAtMap = mutableMapOf<String, String>()
    private var mEmojiExtraContainer: ExtraContainer? = null
    private var mOptionExtraContainer: ExtraContainer? = null
    private var mOptionAdapter: BottomPanelAdapter? = null

    private var voiceRecodingPanel: VoiceRecodingPanel? = null
    private var audioRecorder: AudioRecorder? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.adhoc_conversation_input_panel, this)
        init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mInputAwareLayout?.addOnKeyboardHiddenListener(this)
        mInputAwareLayout?.addOnKeyboardShownListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mInputAwareLayout?.removeOnKeyboardHiddenListener(this)
        mInputAwareLayout?.removeOnKeyboardShownListener(this)
    }

    private fun init(context: Context) {
        mEmojiExtraContainer = ExtraContainer(panel_emoji_container, true, false)
        mOptionExtraContainer = ExtraContainer(panel_option_container, false, false)
        panel_countdown_send_button.setOnClickListener {
            if (mListener?.onBeforeConversationHandle() == true) {
                val sendText = panel_compose_text.textTrimmed
                if (sendText.isNotEmpty()) {
                    val atList = mutableSetOf<String>()
                    for ((atNick, atId) in mAtMap) {
                        if (sendText.contains(atNick)) {
                            atList.add(atId)
                        }
                    }
                    mListener?.onMessageSend(panel_compose_text.text ?: "", atList)
                    panel_compose_text.text?.clear()
                }
            }
        }
        panel_compose_text.setOnClickListener {
            panel_compose_text.isFocusableInTouchMode = true
            panel_compose_text.postDelayed({ mInputAwareLayout?.showSoftkey(panel_compose_text) }, 200)

        }
        panel_compose_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (panel_compose_text.textTrimmed.length > INPUT_NUM_MAX) {
                    panel_compose_text.setText(panel_compose_text.text?.subSequence(0, INPUT_NUM_MAX))
                    panel_compose_text.setSelection(INPUT_NUM_MAX)
                }
                updateToggleButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (panel_compose_text.text?.isEmpty() == true) {
                    clearAt()
                }
            }

        })
        panel_emoji_toggle.setOnClickListener {
            ALog.i(TAG, "emoji panel onClick, visibility: ${panel_emoji_container.visibility}")
            val show = panel_emoji_container.visibility != View.VISIBLE
            if (show) {
                mEmojiExtraContainer?.let {
                    mInputAwareLayout?.show(panel_compose_text, it)
                }
            }else {
                mInputAwareLayout?.showSoftkey(panel_compose_text)
            }

        }
        panel_more_options_iv.setOnClickListener {
            val show = panel_option_container.visibility != View.VISIBLE
            mOptionExtraContainer?.let {
                if (show) {
                    mInputAwareLayout?.show(panel_compose_text, it)
                }else {
                    mInputAwareLayout?.hideAttachedInput(true)
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

        mOptionAdapter = BottomPanelAdapter(context)
        panel_option_container.layoutManager = GridLayoutManager(context, 4)
        panel_option_container.adapter = mOptionAdapter

        voiceRecodingPanel = VoiceRecodingPanel()
        voiceRecodingPanel?.onFinishInflate(this)
        voiceRecodingPanel?.setListener(this)

        audioRecorder = AudioRecorder(context, ConversationInputPanel.AUDIO_RECORD_MAX_TIME, null)
        audioRecorder?.setListener(this)

    }

    fun bindInputAwareLayout(inputAwareLayout: InputAwareLayout) {
        mInputAwareLayout = inputAwareLayout
        inputAwareLayout.addOnKeyboardHiddenListener(this)
        inputAwareLayout.addOnKeyboardShownListener(this)
    }

    fun setOnConversationListener(listener: OnConversationInputListener) {
        mListener = listener
    }

    override fun onStartClicked() {
        ALog.i(TAG, "onStartClicked")

        val pass = mListener?.onBeforeConversationHandle() ?: true
        if (pass) {
            if (checkHasAudioPermission()) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(10)
                setWindowScreenLocked(true)
                ALog.i(TAG, "onStartClicked")
                audioRecorder?.startRecording()
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
        audioRecorder?.stopRecording()
        panel_compose_text.requestFocus()
    }

    @SuppressLint("CheckResult")
    override fun onCancelClicked() {
        ALog.i(TAG, "onCancelClicked")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(30)
        setWindowScreenLocked(false)
        audioRecorder?.cancelRecording()
        panel_compose_text.requestFocus()
    }

    override fun onRecorderStarted() {
        ALog.i(TAG, "onRecorderStarted")
        voiceRecodingPanel?.showRecordState()
        mListener?.onAudioStateChanged(ConversationInputPanel.OnConversationInputListener.AUDIO_START, null, 0, 0)
    }

    override fun onRecorderProgress(millisTime: Long) {
        voiceRecodingPanel?.showPlayTime(millisTime)
    }

    override fun onRecorderCanceled() {
        ALog.i(TAG, "onRecorderCanceled ")
        voiceRecodingPanel?.hideRecordState()
    }

    override fun onRecorderFinished() {
        voiceRecodingPanel?.hideRecordState()
    }

    override fun onRecorderSucceed(recordUri: Uri, byteSize: Long, playTime: Long) {
        ALog.i(TAG, "onRecordFinished $byteSize, time:$playTime")

        if (byteSize > 0 && playTime >= 1000) {
            mListener?.onAudioStateChanged(ConversationInputPanel.OnConversationInputListener.AUDIO_COMPLETE, recordUri, byteSize, playTime)
        } else {
            if (playTime < 1000) {
                ToastUtil.show(context, AppUtil.getString(AppContextHolder.APP_CONTEXT, com.bcm.messenger.chats.R.string.chats_record_time_too_short))
            }
            mListener?.onAudioStateChanged(ConversationInputPanel.OnConversationInputListener.AUDIO_CANCEL, recordUri, byteSize, playTime)
        }
    }

    override fun onRecorderFailed(error: Exception) {
        voiceRecodingPanel?.hideRecordState()
        ALog.e(TAG, "onRecorderFailed ", error)
        ToastUtil.show(context, resources.getString(com.bcm.messenger.chats.R.string.chats_unable_to_record_audio_warning))
        mListener?.onAudioStateChanged(ConversationInputPanel.OnConversationInputListener.AUDIO_ERROR, null, 0, 0)
    }

    override fun onKeyboardHidden() {
        ALog.i(TAG, "onKeyboardHidden, emoji: ${mEmojiExtraContainer?.isShowing}, option: ${mOptionExtraContainer?.isShowing}")
        if (mEmojiExtraContainer?.isShowing != true && mOptionExtraContainer?.isShowing != true) {
            lockBottomExtraContainer(0)
        }
    }

    override fun onKeyboardShown() {
        ALog.i(TAG, "onKeyboardShown")
        voiceRecodingPanel?.onKeyboardShown()
        lockBottomExtraContainer(mInputAwareLayout?.keyboardHeight ?: 0)
    }

    fun getComposeText(): CharSequence {
        return panel_compose_text?.text ?: ""
    }

    /**
     * set wanted send text
     */
    fun setComposeText(text: CharSequence) {
        panel_compose_text?.setText(text)
        panel_compose_text?.setSelection(panel_compose_text.text?.length ?: 0)
    }

    /**
     * add at
     */
    fun addAt(atId: String, atNick: String, showSoft: Boolean = true) {
        if (!mAtMap.containsKey(atNick)) {
            mAtMap[atNick] = atId
            val text = SpannableStringBuilder(panel_compose_text?.text ?: "")
            text.append("@$atNick ")
            panel_compose_text?.text = text
            if (showSoft) {
                mInputAwareLayout?.showSoftkey(panel_compose_text)
                panel_compose_text?.post {
                    panel_compose_text?.setSelection(panel_compose_text.text?.length ?: 0)
                }
            }
        }
    }

    fun clearAt() {
        mAtMap.clear()
    }


    fun addOptionItem(vararg items: BottomPanelItem) {
        mOptionAdapter?.addItems(items.toMutableList())
    }

    fun addOptionItem(index: Int, item: BottomPanelItem) {
        mOptionAdapter?.addItem(index, item)
    }

    fun getOptionSize(): Int {
        return mOptionAdapter?.getSize() ?: 0
    }

    fun removeAllOptionItems() {
        mOptionAdapter?.clearItems()
    }

    fun removeOptionItem(name: String) {
        mOptionAdapter?.removeItem(name)
    }

    fun updateOptionItem(item: BottomPanelItem) {
        mOptionAdapter?.updateItem(item)
    }

    fun isMoreOptionPanelShowed(): Boolean {
        return panel_option_container.visibility == View.VISIBLE
    }

    fun isEmojiPanelShowed(): Boolean {
        return panel_emoji_container.visibility == View.VISIBLE
    }

    /**
     * update button state
     */
    private fun updateToggleButtonState() {
        if (panel_compose_text.textTrimmed.isEmpty()) {
            panel_countdown_send_button.visibility = View.GONE
            panel_audio_toggle.visibility = View.VISIBLE
        } else {
            panel_audio_toggle.visibility = View.GONE
            panel_countdown_send_button.visibility = View.VISIBLE
        }
    }

    /**
     * handle bottom container height
     */
    private fun lockBottomExtraContainer(height: Int) {
        panel_extra_container.layoutParams = panel_extra_container.layoutParams.apply {
            this.height = height
            ALog.d(TAG, "lockBottomExtraContainer height: $height")
        }
    }

    /**
     * check audio permission
     */
    private fun checkHasAudioPermission(): Boolean {
        return PackageManager.PERMISSION_GRANTED == context.packageManager.checkPermission("android.permission.RECORD_AUDIO", context.packageName)
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
}