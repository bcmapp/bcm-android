package com.bcm.messenger.chats.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.MessageListItem
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.ui.CommonSearchBar
import com.bcm.messenger.common.ui.ContentShadeView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.ui.popup.BcmPopupMenu
import com.bcm.messenger.common.utils.ConversationUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.Conversions
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.collections.HashSet

/**
 * Created by wjh on 2019/7/24
 */
class MessageListAdapterNew(context: Context,
                            val masterSecret: MasterSecret,
                            val glideRequests: GlideRequests,
                            private val local: Locale,
                            private val delegate: IThreadHolderDelete) :
        LinearBaseAdapter<ThreadRecord>(context) {

    companion object {
        private const val TAG = "MessageListAdapter"
    }

    interface IThreadHolderDelete {
        fun onViewClicked(adapter: MessageListAdapterNew, viewHolder: RecyclerView.ViewHolder)
    }

    private var mDigest = MessageDigest.getInstance("SHA1")
    private var inflater: LayoutInflater
    private var batchMode = false
    private val batchSet = Collections.synchronizedSet(HashSet<Long>())
    private var mHeaderSearch: Int = 0
    private var mHeaderShade: Int = 0
    private var mLoading = true

    init {
        try {
            setHasStableIds(true)
            this.inflater = LayoutInflater.from(context)
            mHeaderSearch = addHeader()
            mHeaderShade = addHeader()

            notifyMainChanged()

        } catch (nsae: NoSuchAlgorithmException) {
            throw AssertionError("SHA-1 missing")
        }
    }

    fun setThreadList(threadList: List<ThreadRecord>) {
        mLoading = false
        showHeader(mHeaderShade, threadList.isEmpty(), false)
        setDataList(threadList)
    }


    fun getUnreadCount(position: Int): Int {
        try {
            val data = getMainData(position)
            return data.data?.unreadCount ?: 0
        }catch (ex: Exception) {
            ALog.e(TAG, "getUnreadCount error", ex)
        }
        return 0
    }

    override fun onCreateHeaderHolder(parent: ViewGroup, viewType: Int): ViewHolder<ThreadRecord> {
        return when(viewType) {
            mHeaderSearch -> {
                ViewHolder(CommonSearchBar(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val lr = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
                    val tb = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
                    setPadding(lr, tb, lr, 0)
                    setMode(CommonSearchBar.MODE_DISPLAY)
                    setOnSearchActionListener(object : CommonSearchBar.OnSearchActionListener {
                        override fun onJump() {
                            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
                            provider.openSearch(context)
                        }

                        override fun onSearch(keyword: String) {
                        }

                        override fun onClear() {
                        }

                    })
                })
            }
            mHeaderShade -> {
                ShadeViewHolder(ContentShadeView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(0, 80.dp2Px(), 0, 50.dp2Px())
                })
            }
            else -> {
                super.onCreateHeaderHolder(parent, viewType)
            }
        }
    }

    override fun onBindContentHolder(holder: ViewHolder<ThreadRecord>, trueData: ThreadRecord?) {
        if (holder is ThreadViewHolder) {
            holder.getItem().bind(masterSecret, trueData ?: return, glideRequests, local, batchSet, batchMode)
            holder.updatePin()
        }
    }

    override fun onBindHeaderHolder(holder: ViewHolder<ThreadRecord>, position: Int) {
        if (holder is ShadeViewHolder) {
            holder.bind(mLoading)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<ThreadRecord> {
        return ThreadViewHolder(inflater.inflate(R.layout.chats_list_item_view, parent, false))
    }

    override fun onViewRecycled(holder: ViewHolder<ThreadRecord>) {
        if (holder is ThreadViewHolder) {
            holder.getItem().unbind()
        }
    }

    override fun getItemId(position: Int): Long {
        val type = getItemViewType(position)
        val idText = if(type == ITEM_TYPE_DATA) {
            getMainData(position).data?.getRecipient()?.address?.serialize() ?: return 0
        } else {
            "id_individual_contact_extra_$type"
        }
        val bytes = mDigest.digest(idText.toByteArray())
        return Conversions.byteArrayToLong(bytes)
    }


    inner class ShadeViewHolder(val shadeView: ContentShadeView) : LinearBaseAdapter.ViewHolder<ThreadRecord>(shadeView) {

        fun bind(isLoading: Boolean) {
            if (isLoading) {
                shadeView.showLoading()
            } else {
                shadeView.showContent(getString(R.string.chats_no_conversation_main), getString(R.string.chats_no_conversation_sub))
            }
        }
    }

    inner class ThreadViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<ThreadRecord>(itemView) {

        private val chatItem: MessageListItem = itemView as MessageListItem
        private var isPinned: Boolean = false
        private val pinImg: ImageView = chatItem.findViewById(R.id.chats_pin)

        private var x = 0
        private var y = 0

        init {
            chatItem.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    x = event.rawX.toInt()
                    y = event.y.toInt() - itemView.height
                }
                return@setOnTouchListener false
            }

            chatItem.setOnLongClickListener {
                val menuItems = listOf(
                        BcmPopupMenu.MenuItem(if (isPinned) getString(R.string.chats_item_cancel_pin) else getString(R.string.chats_item_pin)),
                        BcmPopupMenu.MenuItem(getString(R.string.chats_item_delete))
                )
                BcmPopupMenu.Builder(it.context)
                        .setMenuItem(menuItems)
                        .setAnchorView(it)
                        .setSelectedCallback { index ->
                            when (index) {
                                0 -> switchPin()
                                1 -> confirmDelete()
                            }
                        }
                        .show(x, y)

                return@setOnLongClickListener true
            }

            chatItem.setOnClickListener {
                delegate.onViewClicked(this@MessageListAdapterNew, this)
            }
        }

        private fun switchPin() {
            if (!isPinned) {
                ConversationUtils.setPin(chatItem.threadId, true)
            } else {
                ConversationUtils.setPin(chatItem.threadId, false)
            }
        }

        private fun confirmDelete() {
            AlertDialog.Builder(chatItem.context)
                    .setTitle(R.string.chats_item_confirm_delete_title)
                    .setMessage(R.string.chats_item_confirm_delete_message)
                    .setPositiveButton(StringAppearanceUtil.applyAppearance(getString(R.string.chats_item_delete), color = getColor(R.color.common_color_ff3737))) { _, _ ->
                        ConversationUtils.deleteConversation(chatItem.recipient, chatItem.threadId)
                    }
                    .setNegativeButton(R.string.chats_cancel, null)
                    .show()
        }

        fun updatePin() {
            val curThreadId = chatItem.threadId
            val weakThis = WeakReference(this)
            ConversationUtils.checkPin(curThreadId) {
                val t = weakThis.get()
                if (t?.chatItem?.threadId == curThreadId) {
                    isPinned = it
                    if (it) {
                        t.pinImg.visibility = View.VISIBLE
                    } else {
                        t.pinImg.visibility = View.GONE
                    }
                }
            }

        }

        fun getItem(): MessageListItem {
            return chatItem
        }
    }
}