package com.bcm.messenger.chats.components.recyclerview

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView


/**
 * bcm.social.01 2018/5/24.
 */
class WrapContentGridLayoutManager(context: Context, spanCount: Int) : GridLayoutManager(context, spanCount) {
    override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
        super.onMeasure(recycler, state, widthSpec, heightSpec)
    }
}