package com.bcm.messenger.me.ui.feedback

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.me.R
import com.bumptech.glide.request.RequestOptions
import com.bcm.messenger.common.imagepicker.widget.CropRoundCornerTransform
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getDrawable

class FeedBackScreenshotAdapter(activity: AppCompatActivity, selectImage: () -> Unit) : RecyclerView.Adapter<FeedBackScreenshotAdapter.ScreenshotViewHolder>() {

    companion object {
        const val MAX_COUNT = 5
    }

    private val viewModel = ViewModelProviders.of(activity).get(FeedBackViewModel::class.java)
    private val screenshotList = ArrayList<String>()
    private val selectImage = selectImage

    fun addList(list: MutableList<String>?) {
        if (list != null && list.size > 0) {
            if (screenshotList.size + list.size <= MAX_COUNT) {
                screenshotList.addAll(list)
            } else {
                val addSize = MAX_COUNT - screenshotList.size
                for ((index, value) in list.withIndex()) {
                    if (index < addSize) {
                        screenshotList.add(value)
                    }
                }
            }
            viewModel.screenshotlist = screenshotList
            notifyDataSetChanged()
        }
    }

    fun add(item: String) {
        screenshotList.add(item)
        notifyDataSetChanged()
    }

    fun remove(position: Int) {
        screenshotList.removeAt(position)
        viewModel.screenshotlist = screenshotList
        notifyDataSetChanged()
    }

    fun remove(item: String) {
        screenshotList.remove(item)
        viewModel.screenshotlist = screenshotList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        return ScreenshotViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.me_item_feedback_screenshot, parent, false))
    }

    override fun getItemCount(): Int {
        if (screenshotList.size < 5) {
            return screenshotList.size + 1
        }
        return screenshotList.size
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        if (screenshotList.size < MAX_COUNT && position == screenshotList.size) {
            holder.item.setOnClickListener {
                if (screenshotList.size < 5 && position == screenshotList.size)
                    selectImage.invoke()
            }
            holder.bindSelect()
        } else {
            val item = screenshotList[position]
            holder.bindData(item)
            holder.closeBtn.setOnClickListener {
                remove(position)
            }
        }
        if (screenshotList.size < 5 && position == screenshotList.size) {
            holder.closeBtn.visibility = View.GONE
        } else {
            holder.closeBtn.visibility = View.VISIBLE
        }
    }


    class ScreenshotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mErrorResource = R.drawable.common_image_broken_img
        private val mPlaceHolderResource = R.drawable.common_image_place_img

        val radius = 4.dp2Px()
        val item = itemView.findViewById<ImageView>(R.id.feedback_screenshot_item)
        val closeBtn = itemView.findViewById<ImageView>(R.id.feedback_screenshot_close)

        fun bindData(address: String) {
            GlideApp.with(itemView.context).load(address)
                    .placeholder(mPlaceHolderResource)
                    .error(mErrorResource)
                    .centerCrop()
                    .apply(RequestOptions.bitmapTransform(CropRoundCornerTransform(radius, 0, CropRoundCornerTransform.CornerType.ALL)))
                    .into(item)
        }

        fun bindSelect() {
            val drawable = getDrawable(R.drawable.me_feedback_add_image_icon)
            drawable.setTint(itemView.context.getAttrColor(R.attr.common_icon_color))
            GlideApp.with(itemView.context)
                    .load(drawable)
                    .centerCrop()
                    .apply(RequestOptions.bitmapTransform(CropRoundCornerTransform(radius, 0, CropRoundCornerTransform.CornerType.ALL)))
                    .into(item)
        }

    }
}