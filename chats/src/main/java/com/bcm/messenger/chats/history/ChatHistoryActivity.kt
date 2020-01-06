package com.bcm.messenger.chats.history

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.SharedElementCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.viewholder.ChatViewHolder
import com.bcm.messenger.chats.group.viewholder.IncomeHistoryViewHolder
import com.bcm.messenger.chats.group.viewholder.OutgoingHistoryViewHolder
import com.bcm.messenger.chats.group.viewholder.SystemTipsViewHolder
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.CommonConversationAdapter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_activity_chat_history.*
import java.io.File
import java.util.*

/**
 * Created by Kin on 2018/10/24
 */
class ChatHistoryActivity : SwipeBaseActivity() {

    private val TAG = "ChatHistoryActivity"

    companion object {
        const val CHAT_HISTORY_GID = "gid"
        const val MESSAGE_INDEX_ID = "id"
    }

    private lateinit var adapter: CommonConversationAdapter<AmeGroupMessageDetail>
    private var gid = 0L
    private var indexId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_chat_history)

        gid = intent.getLongExtra(CHAT_HISTORY_GID, 0L)
        indexId = intent.getLongExtra(MESSAGE_INDEX_ID, 0L)

        initView()
        initResources()
    }

    private fun initView() {
        chat_history_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        val glideRequest = GlideApp.with(this)
        adapter = CommonConversationAdapter(this, object : CommonConversationAdapter.IConversationDelegate<AmeGroupMessageDetail> {

            private val calendar = Calendar.getInstance()
            override fun getViewHolderType(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, position: Int, data: AmeGroupMessageDetail): Int {
                return when {
                    data.message.type == AmeGroupMessage.SYSTEM_INFO -> R.layout.chats_tip_message_item
                    data.isSendByMe -> R.layout.chats_group_conversation_sent_item
                    else -> R.layout.chats_group_conversation_received_item
                }
            }

            override fun createViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return when (viewType) {
                    R.layout.chats_tip_message_item -> SystemTipsViewHolder(inflater.inflate(viewType, parent, false))
                    R.layout.chats_group_conversation_sent_item -> OutgoingHistoryViewHolder(inflater.inflate(viewType, parent, false)).apply {
                        setCanLongClick(true)
                    }
                    else -> IncomeHistoryViewHolder(inflater.inflate(viewType, parent, false)).apply {
                        setCanLongClick(true)
                    }
                }

            }

            override fun bindViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, viewHolder: RecyclerView.ViewHolder, position: Int, data: AmeGroupMessageDetail) {
                if (viewHolder is ChatViewHolder) {
                    viewHolder.bind(data, glideRequest, null)

                    if (position == 0) { // Distance to bottom for last message
                        val lp = viewHolder.itemView.layoutParams as RecyclerView.LayoutParams
                        lp.bottomMargin = 25.dp2Px()
                        viewHolder.itemView.layoutParams = lp
                    } else {  // Set bottom distance to 0
                        val lp = viewHolder.itemView.layoutParams as RecyclerView.LayoutParams
                        if (lp.bottomMargin > 0) {
                            lp.bottomMargin = 0
                            viewHolder.itemView.layoutParams = lp
                        }
                    }

                } else if (viewHolder is SystemTipsViewHolder) {
                    viewHolder.bindData(data)
                }
            }

            override fun unbindViewHolder(adapter: CommonConversationAdapter<AmeGroupMessageDetail>, viewHolder: RecyclerView.ViewHolder) {
                if (viewHolder is ChatViewHolder) {
                    viewHolder.unBindData()
                } else if (viewHolder is SystemTipsViewHolder) {
                    viewHolder.unBindData()
                }
            }

            override fun getItemId(position: Int, data: AmeGroupMessageDetail): Long {
                return data.indexId
            }

            override fun getLastSeenHeaderId(position: Int, data: AmeGroupMessageDetail): Long {
                calendar.time = Date(data.sendTime)
                return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR)).toLong()
            }

            override fun getReceiveTime(position: Int, data: AmeGroupMessageDetail): Long {
                return data.sendTime
            }

        })

        chat_history_recycler_view.adapter = adapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, true)
        layoutManager.stackFromEnd = true
        chat_history_recycler_view.layoutManager = layoutManager
        chat_history_recycler_view.setHasFixedSize(false)
        chat_history_recycler_view.addItemDecoration(CommonConversationAdapter.LastSeenHeader(adapter, 3 * 60 * 1000))

    }

    private fun initResources() {
        if (gid == ARouterConstants.PRIVATE_TEXT_CHAT) {
            getPrivateMessage()
        } else {
            getGroupMessage()
        }
    }

    private fun getPrivateMessage() {
        Observable.create<List<AmeHistoryMessageDetail>> {
            val message = Repository.getChatRepo(accountContext)?.getMessage(indexId)
            if (message != null) {
                val groupMessage = AmeGroupMessage.messageFromJson(message.body)
                val outerContent = groupMessage.content as AmeGroupMessage.HistoryContent

                var index = 0L
                var mediaIndex = 0
                val list = outerContent.messageList.map { msg ->
                    AmeHistoryMessageDetail().apply {
                        gid = ARouterConstants.PRIVATE_TEXT_CHAT
                        indexId = message.id
                        serverIndex = index
                        senderId = msg.sender
                        sendTime = msg.sendTime
                        isSendByMe = msg.sender == accountContext.uid
                        sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
                        this.message = AmeGroupMessage.messageFromJson(msg.messagePayload ?: "")
                        thumbPsw = msg.thumbPsw
                        attachmentPsw = msg.attachmentPsw
                        val content = this.message.content
                        when (content) {
                            is AmeGroupMessage.FileContent -> {
                                if (content.isExist()) {
                                    attachmentUri = Uri.fromFile(File(content.getPath().second + File.separator + content.getExtension())).toString()
                                }
                            }
                            is AmeGroupMessage.ImageContent, is AmeGroupMessage.VideoContent -> {
                                this.mediaIndex = mediaIndex
                                mediaIndex++
                            }
                        }
                        ++index
                    }
                }
                it.onNext(list.reversed())
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "getPrivateMessage: ${it.size}")
                    adapter.loadData(it)
                }, {
                    ALog.e(TAG, "getPrivateMessage error", it)
                })
    }

    private fun getGroupMessage() {
        Observable.create<List<AmeHistoryMessageDetail>> {
            val message = MessageDataManager.fetchOneMessageByGidAndIndexId(accountContext, gid, indexId)
            if (message != null) {
                val outerContent = message.message.content as AmeGroupMessage.HistoryContent

                var index = 0L
                var mediaIndex = 0
                val list = outerContent.messageList.map { msg ->
                    AmeHistoryMessageDetail().apply {
                        gid = message.gid
                        indexId = message.indexId
                        serverIndex = index
                        senderId = msg.sender
                        sendTime = msg.sendTime
                        isSendByMe = msg.sender == accountContext.uid
                        sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
                        this.message = AmeGroupMessage.messageFromJson(msg.messagePayload ?: "")
                        thumbPsw = msg.thumbPsw
                        attachmentPsw = msg.attachmentPsw

                        val content = this.message.content
                        when (content) {
                            is AmeGroupMessage.FileContent -> {
                                if (content.isExist()) {
                                    attachmentUri = Uri.fromFile(File(content.getPath().second + File.separator + content.getExtension())).toString()
                                }
                            }
                            is AmeGroupMessage.ImageContent, is AmeGroupMessage.VideoContent -> {
                                this.mediaIndex = mediaIndex
                                mediaIndex++
                            }
                        }
                        index++
                    }
                }
                it.onNext(list.reversed())
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "getGroupMessage: ${it.size}")
                    adapter.loadData(it)
                }, {
                    ALog.e(TAG, "getGroupMessage error", it)
                })
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val indexId = data.getLongExtra(ShareElements.PARAM.MEDIA_INDEX, 0L)
            setExitSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                    names?.clear()
                    sharedElements?.clear()
                    names?.add("${ShareElements.Activity.MEDIA_PREIVEW}$indexId")
                    sharedElements?.put("${ShareElements.Activity.MEDIA_PREIVEW}$indexId", getExitView(indexId)
                            ?: chat_history_hidden_view)
                    setExitSharedElementCallback(null as? SharedElementCallback)
                }
            })
        }
    }


    fun getExitView(indexId: Long): View? {
        val layoutManager = chat_history_recycler_view?.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val lastPos = layoutManager.findLastVisibleItemPosition()
            for (i in firstPos..lastPos) {
                val view = layoutManager.findViewByPosition(i) ?: continue
                val holder = chat_history_recycler_view?.getChildViewHolder(view)
                if (holder is ChatViewHolder) {
                    val bodyView = holder.getView(indexId)
                    if (bodyView != null) return bodyView
                }
            }
        }
        return null
    }
}