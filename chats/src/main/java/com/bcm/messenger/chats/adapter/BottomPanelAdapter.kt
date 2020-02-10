package com.bcm.messenger.chats.adapter

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.bean.BottomPanelItem
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.utility.QuickOpCheck


/**
 * Created by zjl on 2018/6/27.
 */
class BottomPanelAdapter(private val context: Context) : RecyclerView.Adapter<BottomPanelAdapter.BottomViewHolder>() {

    private var inflater: LayoutInflater
    private var list: MutableList<BottomPanelItem> = mutableListOf()

    init {
        this.inflater = LayoutInflater.from(context)
    }

    fun addItem(item: BottomPanelItem) {
        list.add(item)
        notifyDataSetChanged()
    }

    fun addItem(index: Int, item: BottomPanelItem) {
        list.add(index, item)
        notifyDataSetChanged()
    }

    fun getSize(): Int {
        return list.size
    }

    fun removeItem(item: BottomPanelItem) {
        list.remove(item)
        notifyDataSetChanged()
    }

    fun removeItem(name: String) {
        list.forEach {
            if (it.name == name) {
                list.remove(it)
                notifyDataSetChanged()
            }
        }
    }

    fun updateItem(updateItem: BottomPanelItem) {
        for ((index, item) in list.withIndex()) {
            if (item.name == updateItem.name) {
                list[index] = updateItem
                notifyDataSetChanged()
            }
        }
    }

    fun clearItems() {
        list.clear()
        notifyDataSetChanged()
    }

    fun addItems(list: MutableList<BottomPanelItem>) {
        this.list.addAll(list)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: BottomViewHolder, position: Int) {
        val item = list[position]
        holder.panelImg.setImageResource(item.imgId)
        holder.panelImg.drawable.setTint(context.getAttrColor(R.attr.common_icon_color))
        holder.panelImg.setOnClickListener { v ->
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            item.listener.onClick(item.name, v)
        }
        holder.panelName.text = item.name
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BottomViewHolder {
        return BottomViewHolder(inflater.inflate(R.layout.chats_bottom_pannel_item, parent, false))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class BottomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val panelImg: ImageView = itemView.findViewById(R.id.bottom_pannel_item)
        val panelName = itemView.findViewById<TextView>(R.id.bottom_panel_name)
    }
}