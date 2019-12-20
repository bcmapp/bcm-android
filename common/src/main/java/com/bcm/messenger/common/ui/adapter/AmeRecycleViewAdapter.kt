package com.bcm.messenger.common.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference


/**
 * bcm.social.01 2018/5/22.
 */
open class AmeRecycleViewAdapter<T : Any>(context: Context, private var dataModel: IListDataSource<T>) : RecyclerView.Adapter<AmeRecycleViewAdapter.ViewHolder<T>>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var viewHolderDelegate: IViewHolderDelegate<T>? = null

    init {

        val wSelf = WeakReference(this)
        this.dataModel.setDataChangedNotify {
            wSelf.get()?.notifyDataSetChanged()
        }

        this.dataModel.setItemDataChangedNotify { position, count ->
            wSelf.get()?.notifyItemRangeChanged(position, count)
        }
    }


    fun setViewHolderDelegate(viewHolderDelegate: IViewHolderDelegate<T>?) {
        this.viewHolderDelegate = viewHolderDelegate
    }

    override fun onViewRecycled(holder: ViewHolder<T>) {
        viewHolderDelegate?.unbindViewHolder(this, holder)
    }

    override fun getItemViewType(position: Int): Int {
        return viewHolderDelegate?.getViewHolderType(this, position, dataModel.getData(position))
                ?:return super.getItemViewType(position)
    }

    override fun getItemId(position: Int): Long {
        if (hasStableIds()) {
            return dataModel.getItemId(position);
        }
        return super.getItemId(position)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        holder.index = position
        holder.setData(dataModel.getData(position))
        viewHolderDelegate?.bindViewHolder(this, holder)
    }

    override fun getItemCount(): Int {
        return dataModel.size()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val holder = viewHolderDelegate?.createViewHolder(this, inflater, parent, viewType)
                ?: ViewHolder(View(inflater.context, null))
        holder.itemView.setOnClickListener {
            viewHolderDelegate?.onViewClicked(this, holder)
        }

        holder.itemView.setOnLongClickListener {
            return@setOnLongClickListener viewHolderDelegate?.onViewLongClicked(this, holder)?:false
        }
        return holder
    }

    open class ViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var index:Int = 0
        private var data:T? = null

        open fun setData(data:T) {
            this.data = data
        }

        open fun getData():T? {
            return data
        }
    }

    interface IViewHolderDelegate<T:Any> {
        fun getViewHolderType(adapter: AmeRecycleViewAdapter<T>, position: Int, data:T):Int {return 0}
        fun bindViewHolder(adapter: AmeRecycleViewAdapter<T>, viewHolder: ViewHolder<T>) {}
        fun unbindViewHolder(adapter: AmeRecycleViewAdapter<T>, viewHolder: ViewHolder<T>) {}
        fun createViewHolder(adapter: AmeRecycleViewAdapter<T>, inflater: LayoutInflater, parent:ViewGroup, viewType: Int): ViewHolder<T>
        fun onViewClicked(adapter: AmeRecycleViewAdapter<T>, viewHolder: ViewHolder<T>) {}
        fun onViewLongClicked(adapter: AmeRecycleViewAdapter<T>, viewHolder: ViewHolder<T>):Boolean {return false}
    }
}