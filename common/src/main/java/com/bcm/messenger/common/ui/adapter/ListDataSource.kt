package com.bcm.messenger.common.ui.adapter

/**
 * Created by bcm.social.01 on 2018/5/25.
 */
open class ListDataSource<D : Any> : IListDataSource<D> {

    protected var updateCallback: () -> Unit? = {}
    protected var itemUpdateCallback: (position: Int, count: Int) -> Unit? = { position, count -> }
    protected var datalist: List<D> = ArrayList()

    override fun updateDataSource(datalist: List<D>) {
        this.datalist = datalist
        updateCallback()
    }

    override fun setDataChangedNotify(listener: () -> Unit) {
        updateCallback = listener
    }

    override fun setItemDataChangedNotify(listener: (position: Int, count: Int) -> Unit) {
        itemUpdateCallback = listener
    }

    fun updateItem(position: Int) {
        itemUpdateCallback(position, 1)
    }

    override fun size(): Int {
        return datalist.size
    }

    override fun getData(position: Int): D {
        return datalist[position]
    }

    override fun getPosition(data: D): Int {
        for (i in 0 until datalist.size){
            if (datalist[i] == data){
                return i
            }
        }
        return -1
    }


    override fun getDataList(): List<D> {
        return datalist
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun refresh() {
        updateCallback()
    }
}