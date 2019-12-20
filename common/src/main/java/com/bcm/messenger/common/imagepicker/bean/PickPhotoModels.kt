package com.bcm.messenger.common.imagepicker.bean

import com.bcm.messenger.common.R
import com.bcm.messenger.common.imagepicker.CropResultCallback
import com.bcm.messenger.common.utils.AppUtil
import java.io.Serializable

/**
 * Created by Kin on 2019/4/17
 */

data class MediaModel(val path: String, val dir: String, var duration: Long = 0L, var isSelected: Boolean = false) {
    fun toSelectedModel() = SelectedModel(path, duration > 0L, duration)
}

data class SelectedModel(val path: String, val isVideo: Boolean, val duration: Long) : Serializable

class ConfigModel {
    var pickPhotoLimit = 1
    var multiSelect = true
    var showGif = true
    var showVideo = true
    var spanCount = 3
    var cropPhoto = false
    var applyText = AppUtil.getString(R.string.common_send)
    val callbacks = mutableListOf<CropResultCallback>()
    var capturePhoto = false

    var isPicking = false
}

class PickPhotoEvent(val index: Int, val isSelected: Boolean)
class PickPhotoLoadFinishEvent
class PickPhotoListChangeEvent(val newDir: String)
class PickPhotoFinishEvent
class PickPhotoChangeEvent