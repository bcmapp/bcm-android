package com.bcm.messenger.common.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ui.Sidebar
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference

/**
 *
 * RecycleView（LinearLayoutManager， )
 * Created by wjh on 2018/3/8
 */
abstract class LinearBaseAdapter<T : Any>(context: Context? = null) :
        RecyclerView.Adapter<LinearBaseAdapter.ViewHolder<T>>(), Sidebar.FastScrollHelper {

    companion object {
        private const val TAG = "LinearBaseAdapter"
        const val ITEM_TYPE_DATA = 0//,0header，0footer
    }

    private val mContextRef: WeakReference<Context?> = WeakReference(context)

    private val mHeaderList: MutableList<BaseLinearData<T>> = mutableListOf()

    private val mFooterList: MutableList<BaseLinearData<T>> = mutableListOf()

    private var mContentList: MutableList<T> = mutableListOf()

    private val mMainList: MutableList<BaseLinearData<T>> = mutableListOf()

    protected fun getContext(): Context? {
        return mContextRef.get()
    }

    fun getTrueDataList(): List<T> {
        return mContentList
    }

    fun getHeaderDataList(): List<BaseLinearData<T>> {
        return mHeaderList
    }

    fun getFooterDataList(): List<BaseLinearData<T>> {
        return mFooterList
    }

    fun getTrueData(position: Int): T? {
        return if(position < 0 || position >= itemCount) {
            null
        }else {
            getMainData(position).data
        }
    }

    fun getMainData(position: Int): BaseLinearData<T> {
        return mMainList[position]
    }

    override fun getItemViewType(position: Int): Int {
        return mMainList[position].type
    }

    override fun getItemCount(): Int {
        return mMainList.size
    }

    /**
     * （，headerfooter）
     */
    fun notifyMainChanged() {
        mMainList.clear()
        val contentList = mContentList
        mMainList.addAll(mHeaderList.filter { it.show })
        mMainList.addAll(contentList.map { BaseLinearData(ITEM_TYPE_DATA, null, it, getLetter(it), true) })
        mMainList.addAll(mFooterList.filter { it.show })
        notifyDataSetChanged()
    }

    fun addData(content: T): Boolean {
        var index = 0
        mContentList.add(content)
        var exist: BaseLinearData<T>
        for (i in (mMainList.size - 1) downTo 0) {
            exist = mMainList[i]
            if (exist.type == ITEM_TYPE_DATA) {
                index = i + 1
                break
            }else if (exist.type > ITEM_TYPE_DATA) {
                index = i + 1
                break
            }
        }
        mMainList.add(index, BaseLinearData(ITEM_TYPE_DATA, null, content, getLetter(content), true))
        notifyItemInserted(index)
        return true
    }

    fun removeData(content: T): Boolean {
        var index = -1
        for ((i, d) in mMainList.withIndex()) {
            if (d.data == content) {
                index = i
                break
            }
        }
        ALog.d(TAG, "removeData index: $index")
        if (index != -1) {
            mContentList.remove(content)
            mMainList.removeAt(index)
            notifyItemRemoved(index)
            notifyDataSetChanged()
            return true
        }else {
            return false
        }
    }

    /**
     * 
     */
    fun setDataList(contentList: List<T>?) {
        mContentList.clear()
        mContentList.addAll(contentList ?: listOf())
        notifyMainChanged()
    }

    /**
     * item
     * @param position
     */
    fun isDataItem(position: Int): Boolean {
        return getItemViewType(position) == ITEM_TYPE_DATA
    }

    /**
     * item
     * @param position
     */
    fun isHeaderItem(position: Int): Boolean {
        return getItemViewType(position) > ITEM_TYPE_DATA
    }

    /**
     * item
     * @param position
     */
    fun isFooterItem(position: Int): Boolean {
        return getItemViewType(position) < ITEM_TYPE_DATA
    }

    /**
     * 
     * @return 
     */
    fun addHeader(v: View? = null, notify: Boolean = false): Int {
        val type = mHeaderList.size + 1
        mHeaderList.add(BaseLinearData(type, v, null, "", true))
        if(notify) {
            notifyMainChanged()
        }
        return type
    }

    /**
     * 
     * @param type 
     * @param show true，false
     */
    fun showHeader(type: Int, show: Boolean = true, notify: Boolean = true) {
        var data: BaseLinearData<T>
        for (i in 0 until mHeaderList.size) {
            data = mHeaderList[i]
            if (data.type == type && data.show != show) {
                data.show = show
                if (notify) {
                    notifyMainChanged()
                }
                break
            }
        }
    }

    /**
     * 
     * @return 
     */
    fun addFooter(v: View? = null, notify: Boolean = false): Int {
        val type = -(mFooterList.size + 1)
        mFooterList.add(BaseLinearData(type, v, null, "", true))
        if (notify) {
            notifyMainChanged()
        }
        return type
    }

    /**
     * 
     * @param type 
     * @param show 
     */
    fun showFooter(type: Int, show: Boolean, notify: Boolean = true) {
        var data: BaseLinearData<T>
        for (i in 0 until mFooterList.size) {
            data = mFooterList[i]
            if (data.type == type && data.show != show) {
                data.show = show
                if (notify) {
                    notifyMainChanged()
                }
                break
            }
        }
    }

    fun getHeaderCount(): Int {
        return mHeaderList.size
    }

    fun getFooterCount(): Int {
        return mFooterList.size
    }

    /**
     * 
     * @return 
     */
    fun getShowedHeaderCount(): Int {
        return mHeaderList.count { it.show }
    }

    /**
     * 
     * @return 
     */
    fun getShowedFooterCount(): Int {
        return mFooterList.count { it.show }
    }

    override fun showSideBar(): Boolean {
        return false
    }

    /**
     * 
     * @param letter
     * @return -1
     */
    override fun findSidePosition(letter: String): Int {
        for(i in 0 until  itemCount) {
            if(getMainData(i).letter == letter) {
                return i
            }
        }
        return -1
    }

    /**
     * 
     */
    open fun getLetter(data: T): String {
        return ""
    }

    /**
     * 
     * @param position
     * @return
     */
    override fun findSideLetter(position: Int): String? {
        return getMainData(position).letter
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        return when {
            viewType > ITEM_TYPE_DATA -> {
                onCreateHeaderHolder(parent, viewType)
            }
            viewType < ITEM_TYPE_DATA -> {
                onCreateFooterHolder(parent, viewType)
            }
            else -> onCreateContentHolder(parent)
        }
    }

    open fun onBindHeaderHolder(holder: ViewHolder<T>, position: Int) {

    }

    open fun onBindFooterHolder(holder: ViewHolder<T>, position: Int) {

    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        val data = getMainData(position)
        holder.type = data.type
        holder.index = position
        holder.data = data.data
        holder.letter = findSideLetter(position) ?: ""
        data.view = holder.itemView
        val p = position -1
        if(p < 0 || p >= itemCount) {
            holder.previousLetter = ""
            holder.previousData = null
        }else {
            holder.previousLetter = findSideLetter(p) ?: ""
            holder.previousData = getTrueData(p)
        }
        when {
            holder.type > ITEM_TYPE_DATA -> {
                onBindHeaderHolder(holder, position)
            }
            holder.type < ITEM_TYPE_DATA -> {
                onBindFooterHolder(holder, position)
            }
            else -> onBindContentHolder(holder, data.data)
        }

    }

    open fun onCreateHeaderHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val data = mHeaderList.find { it.type == viewType }
        val itemView = data?.view ?: View(parent.context)
        return ViewHolder(itemView)
    }

    open fun onCreateFooterHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val data = mFooterList.find { it.type == viewType }
        val itemView = data?.view ?: View(parent.context)
        return ViewHolder(itemView)
    }

    abstract fun onBindContentHolder(holder: ViewHolder<T>, trueData: T?)

    abstract fun onCreateContentHolder(parent: ViewGroup): ViewHolder<T>

    open class ViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var index: Int = 0
        var type: Int = 0
        var letter: String = ""
        var previousLetter: String = ""
        var data: T? = null
        var previousData: T? = null//，
    }

    /**
     * item
     */
    data class BaseLinearData<T>(val type: Int, var view: View?, val data: T?, var letter: String, var show: Boolean)

}
