package com.bcm.messenger.common.imagepicker

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.imagepicker.bean.*
import com.bcm.messenger.common.imagepicker.ui.activity.PickPhotoDelegateActivity
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * Created by Kin on 2019/4/17
 */

const val GRID_TAG = "grid"
const val LIST_TAG = "list"
const val PICK_TAG = "pick"
const val PREVIEW_TAG = "preview"

const val REQ_INNER_PICK = 1
const val REQ_INNER_CROP = 2
const val REQ_INNER_CAPTURE = 3

object BcmPickPhotoConstants {
    const val PICK_PHOTO_REQUEST = 1
    const val CROP_PHOTO_REQUEST = 2
    const val CAPTURE_REQUEST = 3

    const val EXTRA_PATH_LIST = "path_list"
    const val EXTRA_CAPTURE_PATH = "capture_path"
}

/**
 * BCM//
 *
 * Builder，OnActivityResultIntentConstantsBcmPickPhotoConstants
 * CapturePhoto，CropPhoto，
 * PickPhotoCropPhoto，Limit1
 * CropPhoto，CropPhotoCallbackBitmap
 * ，OnActivityResult
 */
class BcmPickPhotoView private constructor(private val builder: Builder) {
    fun start() {
        BcmPickHelper.currentPickConfig = builder.config
        val intent = Intent(builder.activity, PickPhotoDelegateActivity::class.java)
        val reqCode = when {
            builder.config.cropPhoto -> BcmPickPhotoConstants.CROP_PHOTO_REQUEST
            builder.config.capturePhoto -> BcmPickPhotoConstants.CAPTURE_REQUEST
            else -> BcmPickPhotoConstants.PICK_PHOTO_REQUEST
        }
        builder.activity.startActivityForResult(intent, reqCode)
    }

    class Builder(internal val activity: Activity) {
        internal val config = ConfigModel()

        fun setPickPhotoLimit(limit: Int): Builder {
            if (limit > 0) {
                config.pickPhotoLimit = limit
            }
            return this
        }

        private fun setPickPhotoMultiSelect(isMultiSelect: Boolean): Builder {
            config.multiSelect = isMultiSelect
            return this
        }

        fun setShowGif(isShowGif: Boolean): Builder {
            config.showGif = isShowGif
            return this
        }

        fun setShowVideo(isShowVideo: Boolean): Builder {
            config.showVideo = isShowVideo
            return this
        }

        fun setCropImage(isCropImage: Boolean): Builder {
            config.cropPhoto = isCropImage
            return this
        }

        fun setItemSpanCount(span: Int): Builder {
            if (span > 0) {
                config.spanCount = span
            }
            return this
        }

        fun setApplyText(text: String): Builder {
            config.applyText = text
            return this
        }

        fun addCropCallback(callback: CropResultCallback): Builder {
            config.callbacks.add(callback)
            return this
        }

        fun setCapturePhoto(takePhoto: Boolean): Builder {
            config.capturePhoto = takePhoto
            return this
        }

        fun build(): BcmPickPhotoView {
            if (config.cropPhoto) {
                setPickPhotoLimit(1)
                setShowVideo(false)
                setPickPhotoMultiSelect(false)
                setShowGif(false)
            } else if (config.pickPhotoLimit == 1) {
                setPickPhotoMultiSelect(false)
            }
            return BcmPickPhotoView(this)
        }
    }
}

object BcmTakePhotoHelper {
    var currentPhotoPath = ""

    fun takePicture(act: Activity) {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(act.packageManager) != null) {
            val photoFile = createImageSaveFile()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
            } else {
                val imageUri = FileProvider.getUriForFile(act, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", photoFile)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            }
        }
        act.startActivityForResult(takePictureIntent, REQ_INNER_CAPTURE)
    }

    private fun createImageSaveFile(): File {
        // 6.0，DCIM/bcm，6.0
        val pic = File(if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) AmeFileUploader.DCIM_DIRECTORY else AmeFileUploader.DECRYPT_DIRECTORY)
        if (!pic.exists()) {
            pic.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val fileName = "IMG_$timeStamp"
        val tmpFile = File(pic, "$fileName.jpg")
        currentPhotoPath = tmpFile.absolutePath
        return tmpFile
    }
}

object BcmPickHelper {
    private const val TAG = "BcmPickHelper"

    val selectedPhotos: MutableList<SelectedModel> by lazy { mutableListOf<SelectedModel>() }
    val dirPhotoMap: LinkedHashMap<String, MutableList<MediaModel>> = linkedMapOf()
    val dirNames = mutableListOf<String>()
    var currentSelectableList = listOf<MediaModel>()

    var currentPickConfig = ConfigModel()

    fun startQuery(showGif: Boolean, showVideo: Boolean) {
        realQuery(showGif, showVideo)
    }

    fun changeCurrentList(dirName: String) {
        ALog.i(TAG, "Change current folder to $dirName")
        currentSelectableList = dirPhotoMap[dirName] ?: emptyList()
        val event = PickPhotoListChangeEvent(dirName)
        RxBus.post(GRID_TAG, event)
        RxBus.post(PICK_TAG, event)
    }

    fun clear() {
        ALog.i(TAG, "Clear picker data")
        selectedPhotos.clear()
        dirPhotoMap.clear()
        dirNames.clear()
        currentSelectableList = emptyList()
        currentPickConfig = ConfigModel()
    }

    private fun realQuery(showGif: Boolean, showVideo: Boolean) {
        AmeDispatcher.io.dispatch {
            val allPhotoTitle = AppUtil.getString(R.string.common_all_photos)
            val uri = MediaStore.Files.getContentUri("external")
            val cursor = when {
                showGif && showVideo -> {
                    ALog.d(TAG, "Start query all media, include gif and video")
                    AppContextHolder.APP_CONTEXT.contentResolver.query(uri, null,
                            """
                            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? or
                            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?
                        """.trimIndent(),
                            arrayOf("${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"),
                            "${MediaStore.Files.FileColumns.DATE_MODIFIED} desc")
                }
                showGif && !showVideo -> {
                    ALog.d(TAG, "Start query media, include gif but not include video")
                    AppContextHolder.APP_CONTEXT.contentResolver.query(uri, null,
                            """
                            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?
                        """.trimIndent(),
                            arrayOf("${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"),
                            "${MediaStore.Files.FileColumns.DATE_MODIFIED} desc")
                }
                !showGif && showVideo -> {
                    ALog.d(TAG, "Start query media, include video but not include gif")
                    AppContextHolder.APP_CONTEXT.contentResolver.query(uri, null,
                            """
                            (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? or
                            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) and
                            ${MediaStore.Files.FileColumns.MIME_TYPE} != ?
                        """.trimIndent(),
                            arrayOf("${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}", "image/gif"),
                            "${MediaStore.Files.FileColumns.DATE_MODIFIED} desc")
                }
                else -> {
                    ALog.d(TAG, "Start query media, exclude gif and video")
                    AppContextHolder.APP_CONTEXT.contentResolver.query(uri, null,
                            """
                            ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? and
                            ${MediaStore.Files.FileColumns.MIME_TYPE} != ?
                        """.trimIndent(),
                            arrayOf("${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", "image/gif"),
                            "${MediaStore.Files.FileColumns.DATE_MODIFIED} desc")
                }
            }
            while (cursor?.moveToNext() == true) {
                val path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION))

                val file = File(path)
                if (!file.exists()) {
                    continue
                }

                val folderName = file.parentFile.name
                val model = MediaModel(path, folderName, duration, false)

                var allList = dirPhotoMap[allPhotoTitle]
                if (allList == null) {
                    dirNames.add(allPhotoTitle)
                    allList = mutableListOf()
                    dirPhotoMap[allPhotoTitle] = allList
                }
                allList.add(model)

                var dirList = dirPhotoMap[folderName]
                if (dirList == null) {
                    dirNames.add(folderName)
                    dirList = mutableListOf()
                    dirPhotoMap[folderName] = dirList
                }
                dirList.add(model)
            }
            cursor?.close()

            AmeDispatcher.mainThread.dispatch {
                currentSelectableList = dirPhotoMap[allPhotoTitle] ?: mutableListOf()
                ALog.d(TAG, "Query finish, notify UI")
                val event = PickPhotoLoadFinishEvent()
                RxBus.post(GRID_TAG, event)
                RxBus.post(LIST_TAG, event)
            }
        }
    }
}

interface CropResultCallback {
    fun cropResult(bmp: Bitmap)
}

fun imageLoadOption(): RequestOptions {
    return RequestOptions()
            .centerCrop()
            .placeholder(R.color.common_color_white)
            .error(R.drawable.common_image_broken_img)
            .priority(Priority.LOW)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(true)
}

fun formatTime(time: Long): String {
    return when {
        time == 0L -> "0:00"
        time < 59999L -> String.format("0:%02d", time / 1000)
        else -> {
            val min = time / 60000L
            val sec = (time - min * 60000) / 1000L
            String.format("%d:%02d", min, sec)
        }
    }
}

object BcmPickPhotoCropHelper {
    private const val TAG = "BcmPickPhotoCropHelper"

    const val KEY_PIC_PATH = "key_pic_path"

    var cropSize = min(AppContextHolder.APP_CONTEXT.getScreenWidth(), 1080)
//            60 * 2
    /**
     * listeners of image crop complete
     */
    private var mImageCropCompleteListeners = CopyOnWriteArrayList<OnImageCropCompleteListener>()

    fun addOnImageCropCompleteListener(l: OnImageCropCompleteListener) {
        this.mImageCropCompleteListeners.add(l)
        ALog.i(TAG, "=====addOnImageCropCompleteListener:" + l.javaClass.toString())
    }

    fun removeOnImageCropCompleteListener(l: OnImageCropCompleteListener) {
        this.mImageCropCompleteListeners.remove(l)
        ALog.i(TAG, "=====remove mImageCropCompleteListeners:" + l.javaClass.toString())
    }

    fun notifyImageCropComplete(bmp: Bitmap, ratio: Int) {
        for (l in mImageCropCompleteListeners) {
            l.onImageCropComplete(bmp, ratio.toFloat())
        }
    }

    interface OnImageCropCompleteListener {
        fun onImageCropComplete(bmp: Bitmap?, ratio: Float)
    }
}