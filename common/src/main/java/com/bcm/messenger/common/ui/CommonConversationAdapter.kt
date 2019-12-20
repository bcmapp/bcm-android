package com.bcm.messenger.common.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.ui.StickyHeaderDecoration
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.R
import java.util.*

/**
 * 公共的会话消息适配器
 * Created by wjh on 2019/7/27
 */
class CommonConversationAdapter<T>(context: Context, private val delegate: IConversationDelegate<T>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaderDecoration.StickyHeaderAdapter<CommonConversationAdapter.HeaderViewHolder> {

    interface IConversationDelegate<T> {
        fun getViewHolderType(adapter: CommonConversationAdapter<T>, position: Int, data: T): Int
        fun createViewHolder(adapter: CommonConversationAdapter<T>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder
        fun bindViewHolder(adapter: CommonConversationAdapter<T>, viewHolder: RecyclerView.ViewHolder, position: Int, data: T)
        fun unbindViewHolder(adapter: CommonConversationAdapter<T>, viewHolder: RecyclerView.ViewHolder)
        fun getItemId(position: Int, data: T): Long
        fun getLastSeenHeaderId(position: Int, data: T): Long
        fun getReceiveTime(position: Int, data: T): Long
    }

    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    private var mSourceList = mutableListOf<T>()
    private var mLocale: Locale

    init {
        val country = SuperPreferences.getCountryString(context, Locale.getDefault().country)
        val language = SuperPreferences.getLanguageString(context, Locale.getDefault().language)
        mLocale = Locale(language, country)
    }

    fun getDataCount(): Int {
        return mSourceList.size
    }

    /**
     * 删除消息
     */
    fun removeData(data: T) {
        val contains = mSourceList.contains(data)
        if (contains) {
            val index = mSourceList.indexOf(data)
            removeData(index)
        }
    }

    /**
     * 删除指定位置消息
     */
    fun removeData(position: Int) {
        if (mSourceList.size == 0) {
            return
        }
        if (position in 0 until mSourceList.size) {
            mSourceList.removeAt(position)
            notifyItemRemoved(position)
        }

    }

    /**
     * 删除所有消息
     */
    fun removeAll() {
        mSourceList.clear()
        notifyDataSetChanged()
    }

    fun removeAllNotRefresh() {
        mSourceList.clear()
    }

    /**
     * 置换消息
     */
    fun replaceData(position: Int, data: T) {
        if (position in 0 until mSourceList.size) {
            mSourceList[position] = data
            notifyItemChanged(position)
        }
    }

    /**
     * 置换消息
     */
    fun replaceData(data: T) {
        val index = mSourceList.indexOf(data)
        if (index != -1) {
            replaceData(index, data)
        }
    }

    /**
     * 加载单条消息到末尾
     */
    fun loadData(data: T, notify: Boolean = true) {
        val index = mSourceList.size
        loadData(index, data, notify)
    }

    /**
     * 加载消息到末尾
     */
    fun loadData(dataList: List<T>, notify: Boolean = true) {
        val index = mSourceList.size
        loadData(index, dataList, notify)
    }

    /**
     * 加载单条消息到指定的位置
     */
    fun loadData(index: Int, data: T, notify: Boolean = true) {
        val count = mSourceList.size
        if (index == count) {
            mSourceList.add(data)
        } else {
            mSourceList.add(index, data)
        }
        if (notify) {
//            notifyItemRangeChanged(index, count - index + 1)
            notifyDataSetChanged()
        } else {
            notifyItemInserted(index)
        }
    }

    /**
     * 加载消息到指定的位置
     */
    fun loadData(index: Int, dataList: List<T>, notify: Boolean = true) {
        val count = mSourceList.size
        if (index == count) {
            mSourceList.addAll(dataList)
        } else {
            mSourceList.addAll(index, dataList)
        }
        if (notify) {
            //            notifyItemRangeChanged(index, count - index + dataList.size)
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(index, dataList.size)
        }
    }

    fun getData(position: Int): T {
        return mSourceList[position]
    }

    override fun getItemViewType(position: Int): Int {
        return delegate.getViewHolderType(this, position, mSourceList[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return delegate.createViewHolder(this, mInflater, parent, viewType)
    }

    override fun getItemCount(): Int {
        return mSourceList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        delegate.bindViewHolder(this, holder, position, mSourceList[position])
    }

    override fun getItemId(position: Int): Long {
        return delegate.getItemId(position, mSourceList[position])
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        delegate.unbindViewHolder(this, holder)
    }

    override fun getHeaderId(position: Int): Long {
        if (position in 0 until mSourceList.size) {
            return delegate.getLastSeenHeaderId(position, mSourceList[position])
        } else {
            return -1L
        }
    }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): HeaderViewHolder {
        val lastSeenFrame = FrameLayout(parent.context)
        lastSeenFrame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lastSeenFrame.setPadding(50.dp2Px(), 20.dp2Px(), 50.dp2Px(), 10.dp2Px())
        val lastSeenView = TextView(parent.context)
        lastSeenView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        lastSeenView.setTextColor(parent.context.getColorCompat(R.color.common_content_second_color))
        lastSeenView.textSize = 13.0f
        lastSeenView.gravity = Gravity.CENTER
        lastSeenFrame.addView(lastSeenView)
        return HeaderViewHolder(lastSeenFrame, lastSeenView)
    }

    override fun onBindHeaderViewHolder(viewHolder: HeaderViewHolder?, position: Int) {
        viewHolder?.setText(DateUtils.getConversationTimeSpan(AppContextHolder.APP_CONTEXT, getReceivedTimestamp(position), getSelectedLocale(AppContextHolder.APP_CONTEXT)))
    }

    private fun getReceivedTimestamp(position: Int): Long {
        if (position in 0 until mSourceList.size) {
            return delegate.getReceiveTime(position, mSourceList[position])
        }
        return 0
    }

    open class HeaderViewHolder(itemView: View, private val textView: TextView) : RecyclerView.ViewHolder(itemView) {

        fun setText(text: CharSequence) {
            textView.text = text
        }

        fun getText(): CharSequence {
            return textView.text
        }
    }

    /**
     * 上次阅读时间header
     */
    class LastSeenHeader(private val adapter: CommonConversationAdapter<*>, private val interval: Long) : StickyHeaderDecoration(adapter, false, false) {

        override fun hasHeader(parent: RecyclerView, stickyAdapter: StickyHeaderAdapter<*>, position: Int): Boolean {

            val currentRecordTimestamp = adapter.getReceivedTimestamp(position)
            if (currentRecordTimestamp <= 0) {
                return false
            }
            val previousRecordTimestamp = adapter.getReceivedTimestamp(position + 1)
            if (previousRecordTimestamp <= 0) {
                return true
            }
            if ((currentRecordTimestamp - previousRecordTimestamp) > interval) {
                return true
            }
            return false
        }

        override fun getHeaderTop(parent: RecyclerView, child: View, header: View, adapterPos: Int, layoutPos: Int): Int {
            return parent.layoutManager?.getDecoratedTop(child) ?: 0
        }

        override fun getHeader(parent: RecyclerView, stickyAdapter: StickyHeaderAdapter<*>, position: Int): HeaderViewHolder {
            val viewHolder = adapter.onCreateHeaderViewHolder(parent)
            adapter.onBindHeaderViewHolder(viewHolder, position)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

            val childWidth = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, viewHolder.itemView.layoutParams.width)
            val childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, viewHolder.itemView.layoutParams.height)

            viewHolder.itemView.measure(childWidth, childHeight)
            viewHolder.itemView.layout(0, 0, viewHolder.itemView.measuredWidth, viewHolder.itemView.measuredHeight)

            return viewHolder
        }
    }
}