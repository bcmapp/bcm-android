package com.bcm.messenger.common.imagepicker.widget

import android.content.Context
import android.graphics.PorterDuff
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.bcm.messenger.common.imagepicker.BcmPickHelper
import com.bcm.messenger.common.imagepicker.GRID_TAG
import com.bcm.messenger.common.imagepicker.PICK_TAG
import com.bcm.messenger.common.imagepicker.PREVIEW_TAG
import com.bcm.messenger.common.imagepicker.bean.MediaModel
import com.bcm.messenger.common.imagepicker.bean.PickPhotoChangeEvent
import com.bcm.messenger.common.imagepicker.bean.PickPhotoEvent
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.RxBus
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.common_pick_photo_preview_layout.view.*
import com.bcm.messenger.common.R

/** Created by wanbo <werbhelius@gmail.com> on 2017/10/19. */

class PreviewImage : FrameLayout {

    private val images
        get() = BcmPickHelper.selectedPhotos
    private val config = BcmPickHelper.currentPickConfig
    private var index = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        initView()
    }

    private fun initView() {
        val layout = LayoutInflater.from(context).inflate(R.layout.common_pick_photo_preview_layout, this, false) as RelativeLayout
        addView(layout)

        if (config.cropPhoto || !config.multiSelect) {
            pick_photo_preview_select_layout.visibility = View.GONE
        }
    }

    fun setImage(model: MediaModel, index: Int, full: () -> Unit) {
        this.index = index
        select(model)
        pick_photo_preview_image.setOnClickListener { full() }
        pick_photo_preview_image.isDrawingCacheEnabled = true
        pick_photo_preview_select_layout.setOnClickListener {
            if (images.contains(model.toSelectedModel())) {
                removeImage(model)
            } else {
                if (config.pickPhotoLimit > BcmPickHelper.selectedPhotos.size) {
                    addImage(model)
                } else {
                    ToastUtil.show(context, String.format(AppUtil.getString(R.string.common_pick_photo_size_limit), config.pickPhotoLimit))
                }
            }
        }
        Glide.with(context)
                .load(Uri.parse("file://" + model.path))
                .thumbnail(.1f)
                .into(pick_photo_preview_image)
    }

    fun clear() {
        Glide.with(context).clear(pick_photo_preview_image)
    }

    /** add image in list */
    private fun addImage(model: MediaModel) {
        model.isSelected = true
        images.add(model.toSelectedModel())
        select(model)
        RxBus.post(GRID_TAG, PickPhotoEvent(index, true))
    }

    /** remove image in list */
    private fun removeImage(model: MediaModel) {
        model.isSelected = false
        images.remove(model.toSelectedModel())
        select(model)
        RxBus.post(GRID_TAG, PickPhotoEvent(index, false))
    }

    private fun select(model: MediaModel) {
        if (images.contains(model.toSelectedModel())) {
            pick_photo_preview_check.visibility = View.VISIBLE
            pick_photo_preview_select_back.visibility = View.VISIBLE
            val drawable = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_select)
            val back = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_back)
            back.setColorFilter(AppUtil.getColor(resources, R.color.common_app_primary_color), PorterDuff.Mode.SRC_IN)
            pick_photo_preview_select_layout.background = drawable
            pick_photo_preview_select_back.background = back
        } else {
            pick_photo_preview_check.visibility = View.GONE
            pick_photo_preview_select_back.visibility = View.GONE
            val drawable = AppUtil.getDrawable(resources, R.drawable.common_pick_photo_svg_select_default)
            pick_photo_preview_select_layout.background = drawable
        }
        val event = PickPhotoChangeEvent()
        RxBus.post(PICK_TAG, event)
        RxBus.post(PREVIEW_TAG, event)
    }
}