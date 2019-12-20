package com.bcm.messenger.chats.mediabrowser

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Created by zjl on 2018/10/17.
 */
class MediaHandleViewModel : ViewModel() {
    val selection: MutableLiveData<SelectionState> = MutableLiveData()
    private var isDeleteMode = false

    fun setSelecting(selecting: Boolean){
        selection.postValue(SelectionState(mutableListOf(), selecting || isDeleteMode, 0L))
    }

    fun isSelecting(): Boolean{
        return (selection.value?.selecting ?: false || isDeleteMode)
    }

    fun clearSelectionList(){
        selection.value = SelectionState(mutableListOf(), isSelecting(), 0L)
    }

    data class SelectionState(val selectionList: MutableList<MediaBrowseData>, val selecting: Boolean, var fileByteSize: Long)

    fun setDeleteMode(deleteMode: Boolean) {
        isDeleteMode = deleteMode
        selection.postValue(SelectionState(selection.value?.selectionList
                ?: mutableListOf(), isSelecting(), selection.value?.fileByteSize ?: 0L))
    }

    fun selectAll(selectionList: MutableList<MediaBrowseData>, fileByteSize: Long) {
        selection.postValue(SelectionState(selectionList, true, fileByteSize))
    }

    fun cancelSelectAll() {
        selection.postValue(SelectionState(mutableListOf(), true, 0L))
    }
}