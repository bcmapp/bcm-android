package com.bcm.messenger.chats.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.util.TTSUtil
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.ui.StateButton
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.getDrawable
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_big_content_recycle_fragment.*
import me.imid.swipebacklayout.lib.app.SwipeBackActivityBase
import java.util.*

/**
 *
 * ling created in 2018/6/20
 **/
class BigContentRecycleFragment(accountContext: AccountContext) : BaseFragment(), TextToSpeech.OnInitListener {

    companion object {

        fun showBigContent(activity: AccountSwipeBaseActivity, gid: Long, indexId: Long) {
            val f = BigContentRecycleFragment(activity.accountContext)
            val arg = Bundle()
            arg.putLong(GID, gid)
            arg.putLong(INDEX_ID, indexId)
            arg.putInt(STYLE, GROUP_STYLE)
            arg.putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, activity.accountContext)
            f.arguments = arg

            activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, f)
                    .addToBackStack("big_content")
                    .commit()

            activity.hideKeyboard()
        }

        fun showBigContent(activity: AccountSwipeBaseActivity, threadId: Long, id: Long, masterSecret: MasterSecret) {
            val f = BigContentRecycleFragment(activity.accountContext)
            val arg = Bundle()
            arg.putLong(THREAD_ID, threadId)
            arg.putLong(INDEX_ID, id)
            arg.putParcelable(MASTERSECTRET, masterSecret)
            arg.putInt(STYLE, PRIVATE_STYLE)
            arg.putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, activity.accountContext)
            f.arguments = arg

            activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, f)
                    .addToBackStack("big_content")
                    .commit()

            activity.hideKeyboard()
        }

        private const val TAG = "BigContentRecycleFragment"
        private const val GET_COUNT = 10
        const val GID = "gid"
        const val INDEX_ID = "indexId"
        const val THREAD_ID = "threadId"
        const val STYLE = "style"
        const val PRIVATE_STYLE = 0
        const val GROUP_STYLE = 1
        const val MASTERSECTRET = "master_secret"
    }

    private val textItems = mutableListOf<AmeGroupMessageDetail>()
    private val textPrivateItems = mutableListOf<MessageRecord>()
    private var forwardEnd = false
    private var forwardFetching = false
    private var backwardEnd = false
    private var backwardFetching = false
    private var enterIndexId = 0L
    private var currentIndexId = 0L
    private var gid = 0L
    private var lastIndexId = 0L
    private var style: Int = 0
    private var threadId: Long = -1L
    private val chatRepo = Repository.getChatRepo(accountContext)
    private var tts: TextToSpeech? = null
    private var supportZh: Int = -2
    private var ttsPlay = false
    private var isTTSInit = false

    var holder: BigTextViewHolder? = null


    var anim: AnimationDrawable? = null

    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.chats_big_content_recycle_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enableSwipe(false)
        lastIndexId = arguments?.getLong(INDEX_ID, -1L) ?: -1L
        initView()
        initResources()
        initSpeech()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        enableSwipe(true)
        tts_speak.removeCallbacks(playRunnable)
        tts?.stop()
        tts?.shutdown()

    }

    override fun onPause() {
        super.onPause()
        big_content_recycler.removeCallbacks(speakRunnable)
    }

    private fun initSpeech() {
        tts = TextToSpeech(activity, this)
        ttsPlay = false
        setImage()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                supportZh = it.setLanguage(Locale.CHINESE)
                if (supportZh == TextToSpeech.LANG_MISSING_DATA || supportZh == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ALog.e(TAG, "not support chinese")
                    it.language = Locale.US
                }

                isTTSInit = true
                if (ttsPlay)
                    play()
                setPlay()
            }

        }
    }


    private fun changeImage() {
        if (ttsPlay) {
            ttsPlay = false
            tts?.stop()
            tts_speak.removeCallbacks(playRunnable)
            tts_speak.setImageResource(R.drawable.chats_40_tts_off)
        } else {
            ttsPlay = true
            play()
            setPlay()
        }
    }

    fun setImage() {
        if (ttsPlay) {
            tts_speak.setImageResource(R.drawable.chats_40_tts3)
        } else {
            tts_speak.setImageResource(R.drawable.chats_40_tts_off)
        }
    }

    private fun setPlay() {
        if (ttsPlay) {
            context?.let {
                tts_speak.post {
                    anim = AnimationDrawable()
                    anim?.addFrame(getDrawable(R.drawable.chats_40_tts1), 300)
                    anim?.addFrame(getDrawable(R.drawable.chats_40_tts2), 300)
                    anim?.addFrame(getDrawable(R.drawable.chats_40_tts3), 300)
                    anim?.isOneShot = false
                    tts_speak.setImageDrawable(anim)
                    anim?.start()
                }
            }

            tts_speak.post(playRunnable)
        }
    }

    private val playRunnable = object : Runnable {
        override fun run() {
            tts?.let {
                if (it.isSpeaking) {
                    tts_speak.postDelayed(this, 1000)
                } else {
                    anim?.let { animDrawable ->
                        if (animDrawable.isRunning)
                            animDrawable.stop()
                        ttsPlay = false
                        setImage()
                    }

                }
            }

        }
    }


    private fun initResources() {
        if (arguments != null) {
            lastIndexId = arguments?.getLong(INDEX_ID, -1L) ?: -1L

            style = arguments?.getInt(STYLE, 0) ?: 0
            if (style == GROUP_STYLE) {
                initGroupResources()
            } else if (style == PRIVATE_STYLE) {
                initPrivateResources()
            }
        }

    }

    @SuppressLint("CheckResult")
    private fun initGroupResources() {
        gid = arguments?.getLong(GID, -1L) ?: -1L
        enterIndexId = lastIndexId
        currentIndexId = lastIndexId
        Observable.create(ObservableOnSubscribe<AmeGroupMessageDetail> {
            val message = MessageDataManager.fetchOneMessageByGidAndIndexId(accountContext, gid, lastIndexId)
            if (message != null) {
                it.onNext(message)
                it.onComplete()
            } else {
                it.onError(Exception("Message not found"))
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    textItems.add(it)
                    big_content_recycler.adapter?.notifyDataSetChanged()
                    getBackwardMessages()
                    getForwardMessages()
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    @SuppressLint("CheckResult")
    private fun initPrivateResources() {
        threadId = arguments?.getLong(THREAD_ID, -1L) ?: -1L
        enterIndexId = lastIndexId
        currentIndexId = lastIndexId
        Observable.create(ObservableOnSubscribe<MessageRecord> {
            val message = chatRepo?.getMessage(lastIndexId)
            if (message != null) {
                it.onNext(message)
            }
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    textPrivateItems.add(it)
                    big_content_recycler.adapter?.notifyDataSetChanged()
                    getPrivateForwardMessages()
                    getPrivateBackwardMessages()
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    fun initView() {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val adapter = BigTextAdapter()
        big_content_recycler.layoutManager = layoutManager
        big_content_recycler.adapter = adapter
        big_content_recycler.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewDetachedFromWindow(p0: View) {

            }

            override fun onChildViewAttachedToWindow(p0: View) {

            }
        })
        big_content_recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE && ttsPlay) {
                    big_content_recycler?.let {
                        changeImage()
                    }
                }
            }
        })
        PagerSnapHelper().attachToRecyclerView(big_content_recycler)
        tts_speak.setOnClickListener {
            changeImage()
        }
    }

    private val speakRunnable = Runnable {
        play()
    }

    fun play() {
        val visiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (visiblePosition >= 0) {
            if (style == GROUP_STYLE) {
                val msg = textItems[visiblePosition].message
                val textContent = if(msg.isLink()) {
                    AmeGroupMessage.TextContent((msg.content as AmeGroupMessage.LinkContent).url)
                }else {
                    msg.content as AmeGroupMessage.TextContent
                }
                play(textContent.text)
            } else {
                play(textPrivateItems[visiblePosition].getDisplayBody().toString())
            }
        }
    }

    fun play(str: String) {
        synchronized(this) {
            tts?.let {
                if (it.isSpeaking) {
                    tts?.stop()
                }
            }

            if (!TextUtils.isEmpty(str) && isTTSInit) {
                if (TTSUtil.isChinese(str) && supportZh != TextToSpeech.LANG_AVAILABLE) {
                    chat_tts_notice_layout.visibility = View.VISIBLE
                    chat_tts_notice_close.setOnClickListener {
                        chat_tts_notice_layout.visibility = View.GONE
                    }
                } else {
                    chat_tts_notice_layout.visibility = View.GONE
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(1.0f)
                    tts?.speak(str, TextToSpeech.QUEUE_ADD, null, null)
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun getForwardMessages() {
        if (forwardEnd || forwardFetching) return
        forwardFetching = true
        Observable.create(ObservableOnSubscribe<List<AmeGroupMessageDetail>> {
            val list = MessageDataManager.fetchTextMessage(accountContext, gid, lastIndexId, GET_COUNT, false)
            it.onNext(list.reversed())
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.size < GET_COUNT) {
                        forwardEnd = true
                    }
                    if (it.isNotEmpty()) {
                        textItems.addAll(0, it)
                        big_content_recycler.adapter?.notifyItemRangeInserted(0, it.size)
                        big_content_recycler.adapter?.notifyItemRangeChanged(it.size, it.size + 1)
                    }
                    forwardFetching = false
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    @SuppressLint("CheckResult")
    private fun getBackwardMessages() {
        if (backwardEnd || backwardFetching) return
        backwardFetching = true
        Observable.create(ObservableOnSubscribe<List<AmeGroupMessageDetail>> {
            val list = MessageDataManager.fetchTextMessage(accountContext, gid, lastIndexId, GET_COUNT, true)
            it.onNext(list)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.size < GET_COUNT) {
                        backwardEnd = true
                    }
                    if (it.isNotEmpty()) {
                        textItems.addAll(it)
                        big_content_recycler.adapter?.notifyItemInserted(textItems.size - it.size)
                        big_content_recycler.adapter?.notifyItemRangeChanged(textItems.size - it.size - 1, textItems.size - it.size)

                    }
                    backwardFetching = false
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    @SuppressLint("CheckResult")
    private fun getPrivateForwardMessages() {
        if (forwardEnd || forwardFetching) return
        forwardFetching = true
        Observable.create(ObservableOnSubscribe<List<MessageRecord>> {
            val messages = chatRepo?.getForwardMessages(threadId, currentIndexId, GET_COUNT)?: listOf()
            if (messages.size < GET_COUNT) forwardEnd = true
            it.onNext(messages)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isNotEmpty()) {
                        textPrivateItems.addAll(0, it)
                        big_content_recycler.adapter?.notifyItemRangeInserted(0, it.size)
                        big_content_recycler.adapter?.notifyItemRangeChanged(it.size, it.size + 1)
                    }
                    forwardFetching = false
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    @SuppressLint("CheckResult")
    private fun getPrivateBackwardMessages() {
        if (backwardEnd || backwardFetching) return
        backwardFetching = true
        Observable.create(ObservableOnSubscribe<List<MessageRecord>> {
            val messages = chatRepo?.getBackwardMessages(threadId, currentIndexId, GET_COUNT)?: listOf()
            if (messages.size < GET_COUNT) backwardEnd = true
            it.onNext(messages)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isNotEmpty()) {
                        textPrivateItems.addAll(it)
                        big_content_recycler.adapter?.notifyItemInserted(textPrivateItems.size - it.size)
                        big_content_recycler.adapter?.notifyItemRangeChanged(textPrivateItems.size - it.size - 1, textPrivateItems.size - it.size)
                    }
                    backwardFetching = false
                }, {
                    ALog.e(TAG, it.toString())
                })
    }

    private fun enableSwipe(enable:Boolean) {
        val act = activity
        if (act != null && act is SwipeBackActivityBase){
            act.setSwipeBackEnable(enable)
        }
    }

    inner class BigTextAdapter : RecyclerView.Adapter<BigTextViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BigTextViewHolder {
            return BigTextViewHolder(layoutInflater.inflate(R.layout.chats_big_content_view, parent, false))
        }

        override fun onBindViewHolder(holder: BigTextViewHolder, position: Int) {
            if (style == GROUP_STYLE) {
                bindGroupData(holder, position)
            } else if (style == PRIVATE_STYLE) {
                bindPrivateData(holder, position)
            }
        }

        private fun bindGroupData(holder: BigTextViewHolder, position: Int) {
            val message = textItems[position]
            val text = when {
                message.message.isText() -> (message.message.content as AmeGroupMessage.TextContent).text
                message.message.isLink() -> (message.message.content as AmeGroupMessage.LinkContent).url
                else -> ""
            }
            if (text.isNotEmpty()) {
                holder.bindData(text, position, message.indexId)
                holder.backImg.setOnClickListener {
                    if (position > 0)
                        big_content_recycler.smoothScrollToPosition(position - 1)
                }
                holder.forwardImg.setOnClickListener {
                    if (position < textItems.size)
                        big_content_recycler.smoothScrollToPosition(position + 1)
                }
                lastIndexId = message.indexId
                if (position == 0) {
                    getForwardMessages()
                }
                if (position == textItems.lastIndex) {
                    getBackwardMessages()
                }
            }
        }

        private fun bindPrivateData(holder: BigTextViewHolder, position: Int) {
            val message = textPrivateItems[position]
            holder.bindData(message.getDisplayBody().toString(), position, message.id)
            holder.backImg.setOnClickListener {
                if (position > 0)
                    big_content_recycler.smoothScrollToPosition(position - 1)
            }
            holder.forwardImg.setOnClickListener {
                if (position < textPrivateItems.size)
                    big_content_recycler.smoothScrollToPosition(position + 1)
            }
            lastIndexId = message.id
            if (position == 0) {
                getPrivateForwardMessages()
            }
            if (position == textItems.lastIndex) {
                getPrivateBackwardMessages()
            }
        }

        override fun getItemCount(): Int {
            return when (style) {
                GROUP_STYLE -> textItems.size
                PRIVATE_STYLE -> textPrivateItems.size
                else -> 0
            }
        }

    }

    inner class BigTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val content = itemView.findViewById<EmojiTextView>(R.id.big_content)
        val backImg = itemView.findViewById<ImageView>(R.id.chats_back_img)
        val forwardImg = itemView.findViewById<ImageView>(R.id.chats_forward_img)
        val setting = itemView.findViewById<StateButton>(R.id.chat_tts_setting)
        private var indexId: Long = 0
        var anim: AnimationDrawable? = null

        fun bindData(content: String, position: Int, indexId: Long) {
            this.content.text = content
            this.indexId = indexId
            if (style == GROUP_STYLE) {
                bindGroupData(content, position, indexId)
            } else if (style == PRIVATE_STYLE) {
                bindPrivateData(content, position, indexId)
            }
            itemView.setOnClickListener {
                activity?.supportFragmentManager?.popBackStack()
            }
            setting.setOnClickListener {
                try {
                    val intent = Intent()
                    intent.action = "com.android.settings.TTS_SETTINGS"
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context?.startActivity(intent)
                }catch (ex: Exception) {
                    ALog.e(TAG, "startActivity to TTS fail", ex)
                }
            }
        }

        private fun bindGroupData(content: String, position: Int, indexId: Long) {
            if (textItems.size == 1) {
                backImg.visibility = View.GONE
                forwardImg.visibility = View.GONE
            } else if (textItems.size > 1 && position == 0) {
                backImg.visibility = View.GONE
                forwardImg.visibility = View.VISIBLE
            } else if (textItems.size >= 2 && position == textItems.size - 1) {
                backImg.visibility = View.VISIBLE
                forwardImg.visibility = View.GONE
            } else {
                backImg.visibility = View.VISIBLE
                forwardImg.visibility = View.VISIBLE
            }
        }

        private fun bindPrivateData(content: String, position: Int, indexId: Long) {
            if (textPrivateItems.size == 1) {
                backImg.visibility = View.GONE
                forwardImg.visibility = View.GONE
            } else if (textPrivateItems.size > 1 && position == 0) {
                backImg.visibility = View.GONE
                forwardImg.visibility = View.VISIBLE
            } else if (textPrivateItems.size >= 2 && position == textPrivateItems.size - 1) {
                backImg.visibility = View.VISIBLE
                forwardImg.visibility = View.GONE
            } else {
                backImg.visibility = View.VISIBLE
                forwardImg.visibility = View.VISIBLE
            }
        }

        fun getIndexId(): Long {
            return indexId
        }

    }
}
