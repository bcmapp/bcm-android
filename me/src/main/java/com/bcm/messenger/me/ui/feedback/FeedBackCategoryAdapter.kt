package com.bcm.messenger.me.ui.feedback

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.me.R

class FeedBackCategoryAdapter(activity: AppCompatActivity, list: MutableList<String>) : RecyclerView.Adapter<FeedBackCategoryAdapter.CategoryViewHolder>() {
    private var categoryList: MutableList<String> = list
    val viewModel: FeedBackViewModel = ViewModelProviders.of(activity).get(FeedBackViewModel::class.java)
    var selectItem: String? = viewModel.categoryText
    var selectedPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        return CategoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.me_item_feedback_category, parent, false))
    }

    override fun getItemCount(): Int {
        return categoryList.size
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = categoryList[position]
        holder.bindData(item, selectItem)
        holder.item.setOnClickListener {
            viewModel.categoryText = item
            selectItem = item
//            if (selectedPosition != -1)  //刷新旧位置
//                notifyItemChanged(selectedPosition)
//            notifyItemChanged(position) //刷新选中位置
            notifyDataSetChanged()
            selectedPosition = position  //更换选中位置
        }
    }


    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val item = itemView.findViewById<TextView>(R.id.feedback_category_item)

        fun bindData(text: String, selectItem: String?) {
            item.text = text
            if (selectItem != null && text == selectItem) {
                item.setBackgroundResource(R.drawable.common_blue_bg)
                item.setTextColor(getColor(R.color.common_color_white))
            } else {
                item.setBackgroundResource(R.drawable.common_grey_bg)
                item.setTextColor(getColor(R.color.common_color_black))
            }
        }
    }
}