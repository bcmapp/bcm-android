package com.bcm.messenger.chats.mediapreview.viewmodel

import androidx.lifecycle.ViewModel
import com.bcm.messenger.chats.mediapreview.bean.MediaViewData
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2018/10/31
 */
abstract class BaseMediaViewModel(protected val accountContext: AccountContext) : ViewModel() {

    protected val masterSecret = accountContext.masterSecret

    abstract fun getCurrentData(threadId: Long, indexId: Long, result: (data: MediaViewData) -> Unit)

    abstract fun getAllMediaData(threadId: Long, indexId: Long, reverse: Boolean = false, result: (dataList: List<MediaViewData>) -> Unit)

    abstract fun deleteData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?)

    abstract fun saveData(data: MediaViewData?, result: ((success: Boolean, path: String) -> Unit)?)
}