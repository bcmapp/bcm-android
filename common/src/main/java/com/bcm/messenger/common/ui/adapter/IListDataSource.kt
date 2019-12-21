package com.bcm.messenger.common.ui.adapter

/**
 * Created by bcm.social.01 on 2018/5/23.
 */
interface IListDataSource<T> {
    //
    fun updateDataSource(datalist: List<T>)

    //
    fun getDataList(): List<T>

    //size
    fun size(): Int

    //position 
    fun getData(position: Int): T

    //
    fun getPosition(data:T): Int

    //
    fun setDataChangedNotify(listener: () -> Unit)

    //
    fun setItemDataChangedNotify(listener: (position: Int, count: Int) -> Unit)

    //cell
    fun getItemId(position: Int): Long

    //
    fun refresh()
}