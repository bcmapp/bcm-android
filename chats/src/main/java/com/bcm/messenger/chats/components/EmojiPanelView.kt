package com.bcm.messenger.chats.components

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.adapter.EmojiPanelAdapter
import com.bcm.messenger.common.ui.gridhelper.GridPagerSnapHelper
import kotlinx.android.synthetic.main.chats_emoji_panel_view.view.*

/**
 * Emoji Keyboard and views
 *
 * Created by Kin on 2018/7/27
 */
class EmojiPanelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    private var callback: ((emoji: String) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.chats_emoji_panel_view, this)
        initView()
    }

    private fun initView() {
        emoji_recycler.adapter = EmojiPanelAdapter(context) { emoji ->
            callback?.invoke(emoji)
        }
        emoji_recycler.layoutManager = GridLayoutManager(context, 4, GridLayoutManager.HORIZONTAL, false)
        emoji_recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val index = (emoji_recycler.layoutManager as GridLayoutManager).findFirstCompletelyVisibleItemPosition()
                    emoji_indicator.setCurrentIndicator(index / 28)
                }
            }
        })
        GridPagerSnapHelper().apply {
            setRow(4)
            setColumn(7)
            attachToRecyclerView(emoji_recycler)
        }
        emoji_indicator.setIndicators((emoji_recycler.adapter as EmojiPanelAdapter).getPageSize())
    }

    /**
     * Emoji input callback
     *
     * @param callback callback to input
     */
    fun setInputCallback(callback: (emoji: String) -> Unit) {
        this.callback = callback
    }

    /**
     * Delete button callback
     *
     * @param callback delete callback
     */
    fun setDeleteCallback(callback: (view: View) -> Unit) {
        emoji_delete.setOnClickListener {
            callback.invoke(it)
        }
    }
}