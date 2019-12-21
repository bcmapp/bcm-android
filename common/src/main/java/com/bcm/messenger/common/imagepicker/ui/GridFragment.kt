package com.bcm.messenger.common.imagepicker.ui

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.imagepicker.*
import com.bcm.messenger.common.imagepicker.bean.*
import com.bcm.messenger.common.imagepicker.ui.activity.PickPhotoPreviewActivity
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.RxBus
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.common_fragment_pick_photo_grid.*
import kotlinx.android.synthetic.main.common_pick_photo_grid_item.view.*
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2019/4/17
 */
class GridFragment : Fragment() {
    private val manager: RequestManager by lazy { Glide.with(AppContextHolder.APP_CONTEXT) }
    private val config
        get() = BcmPickHelper.currentPickConfig
    private var photoList = listOf<MediaModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RxBus.subscribe<Any>(GRID_TAG) {
            when (it) {
                is PickPhotoLoadFinishEvent -> {
                    // ，UI
                    reloadData()
                }
                is PickPhotoListChangeEvent -> {
                    // ，UI
                    reloadData()
                }
                is PickPhotoEvent -> {
                    // ，UI
                    photoList[it.index].isSelected = it.isSelected
                    pick_photo_grid_list.adapter?.notifyItemChanged(it.index)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RxBus.unSubscribe(GRID_TAG)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.common_fragment_pick_photo_grid, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pick_photo_grid_list.adapter = GridAdapter()
        pick_photo_grid_list.layoutManager = GridLayoutManager(context, 3, RecyclerView.VERTICAL, false)
        pick_photo_grid_list.addOnScrollListener(scrollListener)
    }

    private inner class GridAdapter : RecyclerView.Adapter<GridViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
            val view = layoutInflater.inflate(R.layout.common_pick_photo_grid_item, parent, false)
            return GridViewHolder(view)
        }

        override fun getItemCount(): Int {
            return photoList.size
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
            holder.bindData(photoList[position])
        }
    }

    private inner class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            // 
            val screenWidth = AppUtil.getRealScreenWidth()
            val scaleSize = screenWidth / config.spanCount
            itemView.pick_photo_grid_image.layoutParams = itemView.pick_photo_grid_image.layoutParams.apply {
                width = scaleSize
                height = scaleSize
            }

            itemView.setOnClickListener {
                if (config.multiSelect) {
                    // 
                    startActivity(Intent(activity, PickPhotoPreviewActivity::class.java).apply {
                        putExtra("index", adapterPosition)
                    })
                } else {
                    // 
                    onItemSelected(adapterPosition)
                }
            }
            itemView.pick_photo_grid_select_layout.setOnClickListener {
                onItemSelected(adapterPosition)
            }
        }

        fun bindData(model: MediaModel) {
            manager.asBitmap().load(Uri.parse("file://${model.path}"))
                    .apply(imageLoadOption())
                    .into(itemView.pick_photo_grid_image)

            when {
                config.cropPhoto || !config.multiSelect -> {
                    itemView.pick_photo_grid_gif.visibility = View.GONE
                    itemView.pick_photo_grid_duration_layout.visibility = View.GONE
                    itemView.pick_photo_grid_select_layout.visibility = View.GONE
                }
                model.path.endsWith(".gif") -> {
                    itemView.pick_photo_grid_gif.visibility = View.VISIBLE
                    itemView.pick_photo_grid_duration_layout.visibility = View.GONE
                }
                model.duration > 0L -> {
                    itemView.pick_photo_grid_gif.visibility = View.GONE
                    itemView.pick_photo_grid_duration_layout.visibility = View.VISIBLE
                    itemView.pick_photo_grid_duration.text = formatTime(model.duration)
                }
                else -> {
                    itemView.pick_photo_grid_gif.visibility = View.GONE
                    itemView.pick_photo_grid_duration_layout.visibility = View.GONE
                }
            }

            select(model)
        }

        private fun select(model: MediaModel) {
            if (model.isSelected) {
                itemView.pick_photo_grid_check.visibility = View.VISIBLE
                itemView.pick_photo_grid_select_back.visibility = View.VISIBLE
                val drawable = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_select)
                val back = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_back)
                back.setColorFilter(AppUtil.getColor(resources, R.color.common_app_primary_color), PorterDuff.Mode.SRC_IN)
                itemView.pick_photo_grid_select_layout.background = drawable
                itemView.pick_photo_grid_select_back.background = back
            } else {
                val drawable = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_default)
                itemView.pick_photo_grid_check.visibility = View.GONE
                itemView.pick_photo_grid_select_back.visibility = View.GONE
                itemView.pick_photo_grid_select_layout.background = drawable
            }

            val event = PickPhotoChangeEvent()
            RxBus.post(PICK_TAG, event)
            RxBus.post(PREVIEW_TAG, event)
        }
    }

    private fun onItemSelected(position: Int) {
        val model = photoList[position]
        if (config.multiSelect) {
            // 
            if (model.isSelected) {
                // 
                model.isSelected = false
                BcmPickHelper.selectedPhotos.remove(model.toSelectedModel())
            } else {
                if (config.pickPhotoLimit > BcmPickHelper.selectedPhotos.size) {
                    // ，
                    model.isSelected = true
                    BcmPickHelper.selectedPhotos.add(model.toSelectedModel())
                } else {
                    // ，
                    activity?.let {
                        ToastUtil.show(it, getString(R.string.common_pick_photo_size_limit, config.pickPhotoLimit))
                    }
                }
            }
            pick_photo_grid_list.adapter?.notifyItemChanged(position)
        } else {
            // ，
            BcmPickHelper.selectedPhotos.add(model.toSelectedModel())
            RxBus.post(PICK_TAG, PickPhotoFinishEvent())
        }
    }

    /** image load pause when recyclerView scroll quickly */
    private var scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (Math.abs(dy) > 30) {
                manager.pauseRequests()
            } else {
                manager.resumeRequests()
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                manager.resumeRequests()
            }
        }
    }

    private fun reloadData() {
        val newList = mutableListOf<MediaModel>()
        newList.addAll(BcmPickHelper.currentSelectableList)
        photoList = newList
        pick_photo_grid_list.adapter?.notifyDataSetChanged()
    }

}