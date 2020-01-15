package com.bcm.messenger.chats.mediabrowser.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.MediaBrowseData
import com.bcm.messenger.chats.mediabrowser.MediaHandleViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.logger.ALog

/**
 * bcm.social.01 2018/10/16.
 */
class MediaBrowserAdapter(private val accountContext: AccountContext, private val mediaHandleModel: MediaHandleViewModel,
                          private val emptyView: View, private val isDeleteMode: Boolean, context: Context,
                          private val callback: (MutableList<MediaBrowseData>) -> Unit) : RecyclerView.Adapter<MediaBrowserAdapter.ViewHolder>() {

    private val inflater = LayoutInflater.from(context)
    private var dataList: ArrayList<MutableList<MediaBrowseData>> = ArrayList()

    var beActive: Boolean = false // Current activated fragment
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var isInSelecting: Boolean = false //
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onDataClicked: (view: View, data: MediaBrowseData) -> Unit = { _, _ ->
    }

    init {
        setHasStableIds(true)
    }

    fun updateAdapterData(map: Map<String, MutableList<MediaBrowseData>>) {
        if (isDeleteMode) {
            for ((k, v) in map) {
                val arrayList = ArrayList<MediaBrowseData>()
                v.filterTo(arrayList) { it.isDownloaded() }
                if (v.size != arrayList.size) {
                    v.clear()
                    v.addAll(arrayList)
                }
            }
        }

        dataList.clear()
        for (i in map.keys) {
            val l = map[i]
            if (l?.isNotEmpty() == true) {
                dataList.add(l)
            }
        }

        callback.invoke(getDataList())
        checkEmptyViewShow()
        notifyDataSetChanged()
    }

    private fun checkEmptyViewShow() {
        if (itemCount == 0) {
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
        }
    }

    fun getDataList(): MutableList<MediaBrowseData> {
        val list = mutableListOf<MediaBrowseData>()
        dataList.forEach { list.addAll(it) }
        return list
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(this.inflater.inflate(R.layout.chats_media_image_browser_cell, parent, false))
    }

    override fun getItemCount(): Int {
        var count = 0
        for (l in dataList) {
            count += l.size
        }
        return count
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = getItem(position)
        if (null != data) {
            holder.data = data
            holder.update()

            data.observe {
                if (data.msgSource == holder.data?.msgSource) {
                    ALog.d("MediaBrowserAdapter", "onBindViewHolder observe data changed")
                    holder.update()
                }
            }
        }
    }

    private fun canShowSelection(): Boolean {
        return beActive && isInSelecting
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.data?.clearThumbnail(holder.imageView)
    }

    private fun getItem(position: Int): MediaBrowseData? {
        var index = position
        for (l in dataList) {
            if (l.size > index) {
                return l[index]
            } else {
                index -= l.size
            }
        }
        return null
    }

    inner class ViewHolder(itemView: View, var data: MediaBrowseData? = null) : RecyclerView.ViewHolder(itemView) {
        val imageView = itemView.findViewById<ImageView>(R.id.browser_image)
        val videoDurationView = itemView.findViewById<TextView>(R.id.video_duration)
        val videoDurationLayout = itemView.findViewById<View>(R.id.video_duration_layout)
        val selectionView = itemView.findViewById<CheckBox>(R.id.media_selected_check)

        init {
            itemView.setOnClickListener {
                val d = data
                if (d != null) {
                    if (canShowSelection()) {
                        selectionView.isChecked = !d.selected
                        val selection = mediaHandleModel.selection.value
                        if (null != selection) {
                            if (selectionView.isChecked) {
                                selection.fileByteSize += d.fileSize()
                                selection.selectionList.add(d)
                            } else {
                                selection.fileByteSize -= d.fileSize()
                                selection.selectionList.remove(d)
                            }
                            mediaHandleModel.selection.postValue(selection)
                        }
                    } else {
                        onDataClicked(it, d)
                    }
                }
            }
        }

        fun update() {
            val data = this.data
            if (data != null) {
                val size = 120.dp2Px()
                if (MediaUtil.isVideo(data.mediaType)) {
                    data.setThumbnail(accountContext, null, imageView, size, size, R.drawable.common_video_place_square_img)
                    videoDurationLayout.visibility = View.VISIBLE
                    videoDurationView.text = DateUtils.convertMinuteAndSecond(data.getVideoDuration() * 1000)
                } else {
                    data.setThumbnail(accountContext, null, imageView, size, size, R.drawable.common_image_place_square_img)
                    videoDurationLayout.visibility = View.GONE
                }
                selectionView.visibility = if (canShowSelection()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                if (selectionView.visibility == View.VISIBLE) {
                    selectionView.isChecked = data.selected
                }
            }
        }
    }

    fun delete(list: List<MediaBrowseData>) {
        for (i in list) {
            for (l in dataList) {
                if (l.contains(i)) {
                    l.remove(i)
                    break
                }
            }
        }
        checkEmptyViewShow()
        notifyDataSetChanged()
    }
}