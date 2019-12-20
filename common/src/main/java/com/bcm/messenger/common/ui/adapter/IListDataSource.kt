package com.bcm.messenger.common.ui.adapter

/**
 * Created by bcm.social.01 on 2018/5/23.
 */
interface IListDataSource<T> {
    //更新列表
    fun updateDataSource(datalist: List<T>)

    //数据列表
    fun getDataList(): List<T>

    //列表size
    fun size(): Int

    //返回索引position 对应的数据
    fun getData(position: Int): T

    //返回索引
    fun getPosition(data:T): Int

    //数据变更通知
    fun setDataChangedNotify(listener: () -> Unit)

    //某一项数据变更通知
    fun setItemDataChangedNotify(listener: (position: Int, count: Int) -> Unit)

    //获取cell的唯一标识
    fun getItemId(position: Int): Long

    //刷新列表
    fun refresh()
}