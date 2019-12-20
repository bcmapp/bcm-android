package com.bcm.messenger.chats.adapter

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.transformEmojiList
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.ui.emoji.EmojiTextView

/**
 * Emoji panel Adapter and ViewHolder
 *
 * Created by Kin on 2018/7/25
 */

/**
 * Emoji panel Adapter
 *
 * @param context Context, for inflating views.
 * @param onClick click emoji callback
 */
class EmojiPanelAdapter(private val context: Context, private val onClick: (emoji: String) -> Unit) : RecyclerView.Adapter<EmojiPanelAdapter.EmojiViewHolder>() {
    private val width = AppUtil.getScreenWidth(context) / 7

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.chats_emoji_panel_item, parent, false)
        return EmojiViewHolder(view, onClick)
    }

    override fun getItemCount() = transformEmojiList.size

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.bindData(transformEmojiList[position] ?: "")
    }

    fun getPageSize() = if (transformEmojiList.size % 28 == 0) transformEmojiList.size / 28 else transformEmojiList.size / 28 + 1

    /**
     * Emoji ViewHolder
     * @param itemView emoji view
     * @param onClick click emoji callback
     */
    inner class EmojiViewHolder(itemView: View, onClick: (emoji: String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val emojiText = itemView.findViewById<EmojiTextView>(R.id.chats_emoji_text)

        init {
            itemView.layoutParams = itemView.layoutParams.apply {
                width = this@EmojiPanelAdapter.width
            }
        }

        init {
            emojiText.setOnClickListener {
                onClick(emojiText.text.toString())
            }
        }

        fun bindData(emoji: String) {
            emojiText.text = emoji
        }
    }
}
