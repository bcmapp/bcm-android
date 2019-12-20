package com.bcm.messenger.chats.map

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.LocationItem
import com.bcm.messenger.utility.InputLengthFilter

/**
 * Location list
 *
 * Created by zjl on 2018/6/19.
 */
class LocationAdapter(context: Context, private val listener: ItemMarkerListener) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

    val dataList = mutableListOf<LocationItem>()
    val inflater: LayoutInflater = LayoutInflater.from(context)

    fun addDataList(dataList: List<LocationItem>) {
        this.dataList.addAll(dataList)
        notifyDataSetChanged()
    }

    fun addData(data: LocationItem) {
        this.dataList.add(data)
        notifyDataSetChanged()
    }

    fun resetDataList(dataList: List<LocationItem>) {
        this.dataList.clear()
        this.dataList.addAll(dataList)
        notifyDataSetChanged()
    }

    private fun clearFlag() {
        dataList.forEach {
            it.markFlag = false
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val it = inflater.inflate(R.layout.chats_location_map_item, parent, false)
        return ViewHolder(it)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = dataList[position]
        holder.bind(data, position)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var nameView: TextView
        var addressView: TextView
        var markIconView: ImageView
        var confirmImgView: ImageView

        private var mData: LocationItem? = null

        init {
            nameView = itemView.findViewById(R.id.locate_name)
            addressView = itemView.findViewById(R.id.locate_address)
            markIconView = itemView.findViewById(R.id.mark_flag)
            confirmImgView = itemView.findViewById(R.id.location_confirm_img)

            itemView.setOnClickListener { v ->
                clearFlag()
                mData?.markFlag = true
                markIconView.visibility = View.VISIBLE
                confirmImgView.visibility = View.VISIBLE
                listener.onClick(v, mData ?: return@setOnClickListener)
            }
        }

        fun bind(data: LocationItem, position: Int) {
            mData = data
            nameView.text = InputLengthFilter.filterString(data.title, 40)
            addressView.text = data.address

            if (position == 0) {
                markIconView.visibility = View.VISIBLE
            } else {
                markIconView.visibility = View.GONE
            }
            if (data.markFlag) {
                confirmImgView.visibility = View.VISIBLE
            } else {
                confirmImgView.visibility = View.GONE
            }
        }
    }

    interface ItemMarkerListener {
        fun onClick(view: View, item: LocationItem)
    }
}