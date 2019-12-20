package com.bcm.messenger.chats.components.recyclerview

import com.bcm.messenger.common.ui.adapter.ListDataSource

/**
 * Created by bcm.social.01 on 2018/5/25.
 */
open class SelectionDataSource<D : Any> : ListDataSource<D>() {
    private val selectionList = ArrayList<D>()

    fun selectList():ArrayList<D>{
        return this.selectionList
    }

    fun select(data:D) {
        selectionList.add(data)
        refreshItem(data)
    }

    fun unSelect(data: D) {
        selectionList.remove(data)
        refreshItem(data)
    }

    fun isSelected(data: D): Boolean {
        return selectionList.contains(data)
    }

    fun clearSelectList() {
        selectionList.clear()
        updateCallback()
    }

    private fun refreshItem(data: D){
        val position = getPosition(data)
        if (position >= 0){
            updateItem(position)
        }
    }
}