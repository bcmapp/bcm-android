package com.bcm.messenger.common.imagepicker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.imagepicker.BcmPickHelper
import com.bcm.messenger.common.imagepicker.LIST_TAG
import com.bcm.messenger.common.imagepicker.bean.MediaModel
import com.bcm.messenger.common.imagepicker.bean.PickPhotoLoadFinishEvent
import com.bcm.messenger.common.imagepicker.imageLoadOption
import com.bcm.messenger.common.imagepicker.ui.activity.PickPhotoActivity
import com.bcm.messenger.common.utils.RxBus
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.common_fragment_pick_photo_grid.*
import kotlinx.android.synthetic.main.common_pick_photo_list_item.view.*
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2019/4/17
 */
class ListFragment : Fragment() {
    private val dirMap = BcmPickHelper.dirPhotoMap
    private val dirList = BcmPickHelper.dirNames
    private val glide: RequestManager by lazy { Glide.with(AppContextHolder.APP_CONTEXT) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RxBus.subscribe<PickPhotoLoadFinishEvent>(LIST_TAG) {
            pick_photo_grid_list.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(LIST_TAG)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.common_fragment_pick_photo_grid, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {
        pick_photo_grid_list.adapter = ListAdapter()
        pick_photo_grid_list.layoutManager = LinearLayoutManager(context)
    }

    private inner class ListAdapter : RecyclerView.Adapter<ListViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            val view = layoutInflater.inflate(R.layout.common_pick_photo_list_item, parent, false)
            return ListViewHolder(view)
        }

        override fun getItemCount(): Int {
            return dirList.size
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            val dirName = dirList[position]
            holder.bindData(dirName, dirMap[dirName] ?: emptyList())
        }
    }

    private inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindData(dirName: String, photoList: List<MediaModel>) {
            if (photoList.isNotEmpty()) {
                itemView.pick_photo_list_dir_name.text = dirName
                itemView.pick_photo_list_photo_size.text = photoList.size.toString()

                glide.asBitmap().load("file://${photoList[0].path}")
                        .apply(imageLoadOption())
                        .into(itemView.pick_photo_list_cover)

                itemView.setOnClickListener {
                    (activity as? PickPhotoActivity)?.changeDir(dirName)
                }
            }
        }
    }
}