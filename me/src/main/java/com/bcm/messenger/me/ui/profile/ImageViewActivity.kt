package com.bcm.messenger.me.ui.profile

import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.FullTransSwipeBaseActivity
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.CropResultCallback
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.getScreenPixelSize
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.StorageUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_image_view.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Created by zjl on 2018/5/2.
 */
class ImageViewActivity : FullTransSwipeBaseActivity(), RecipientModifiedListener {

    private val TAG = "ImageViewActivity"

    private lateinit var recipient: Recipient
    private var futureBitmap: Bitmap? = null
    private var isSaving = false
    private var mForLocal = false
    private var mEditable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_image_view)

        mForLocal = intent.getBooleanExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, false)
        mEditable = intent.getBooleanExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT, true)

        if (!mEditable) {
            photo_preview_dock.setRightInvisible()
        } else {
            photo_preview_dock.setRightVisible()
        }
        photo_preview_dock.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                handleMore()
            }
        })

        try {
            recipient = Recipient.from(accountContext, accountContext.uid, true)
        } catch (ex: Exception) {
            ALog.e(TAG, "get major recipient fail", ex)
            finish()
            return
        }
        if (recipient.isResolving) {
            recipient.addListener(this)
        }
        requestPhoto(recipient)

    }

    private fun handleMore() {

        fun openChooseDialog() {
            val popBuilder = AmePopup.bottom.newBuilder()
            if (!mForLocal && !recipient.isLogin) {
                popBuilder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_save_to_album)) {
                    PermissionUtil.checkStorage(this) { granted ->
                        if (granted) {
                            Observable.create(ObservableOnSubscribe<Boolean> {
                                try {
                                    val outBitmap = avatar_container?.getPhoto()
                                    if (outBitmap == null) {
                                        it.onNext(false)
                                    } else {
                                        val outputDirectory = StorageUtil.getImageDir()
                                        if (!outputDirectory.exists()) {
                                            outputDirectory.mkdirs()
                                        }
                                        val name = recipient.bcmName ?: recipient.address.format()
                                        val outputFile = File(outputDirectory, "${name}_${System.currentTimeMillis()}.jpg")
                                        ALog.d(TAG, "doUpdate image: ${outputFile.absolutePath}")
                                        val outputStream = FileOutputStream(outputFile)
                                        it.onNext(outBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream))
                                        MediaScannerConnection.scanFile(AppContextHolder.APP_CONTEXT, arrayOf(outputFile.absolutePath),
                                                arrayOf(MediaUtil.IMAGE_JPEG), null)
                                        outputStream.close()
                                    }
                                } catch (ex: Exception) {
                                    ALog.e(TAG, "handleMore error", ex)
                                    it.onNext(false)
                                } finally {
                                    it.onComplete()
                                }
                            }).subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe { success ->
                                        if (success) {
                                            AmePopup.result.succeed(this, getString(R.string.common_save_success), true)
                                        } else {
                                            AmePopup.result.succeed(this, getString(R.string.common_save_fail), true)
                                        }
                                    }
                        } else {
                            AmePopup.result.succeed(this, getString(R.string.common_save_fail), true)
                        }
                    }
                }).withDoneTitle(getString(R.string.common_cancel))
            } else {
                popBuilder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_take_photo)) {
                    PermissionUtil.checkCamera(this) { granted ->
                        if (granted) {
                            BcmPickPhotoView.Builder(this@ImageViewActivity)
                                    .setCapturePhoto(true)
                                    .setCropImage(true)
                                    .addCropCallback(object : CropResultCallback {
                                        override fun cropResult(bmp: Bitmap) {
                                            onImageCropComplete(bmp)
                                        }
                                    })
                                    .build().start()
                        }
                    }
                }).withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_choose_photo)) {
                            BcmPickPhotoView.Builder(this@ImageViewActivity)
                                    .setCropImage(true)
                                    .addCropCallback(object : CropResultCallback {
                                        override fun cropResult(bmp: Bitmap) {
                                            onImageCropComplete(bmp)
                                        }
                                    })
                                    .build().start()
                        })
                        .withDismissListener {
                            if (isLocalAvatarNull()) {
                                finish()
                            }
                        }
                        .withDoneTitle(getString(R.string.common_cancel))

                if (mForLocal && !recipient.localAvatar.isNullOrEmpty()) {
                    popBuilder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_local_profile_reset_button), AmeBottomPopup.PopupItem.CLR_RED) {
                        AmePopup.bottom.newBuilder()
                                .withTitle(getString(R.string.me_other_profile_avatar_reset_notice, recipient.name))
                                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_local_profile_reset_button), AmeBottomPopup.PopupItem.CLR_RED) {
                                    futureBitmap = null
                                    doUpdate()
                                })
                                .withDoneTitle(getString(R.string.common_cancel))
                                .show(this)
                    })
                }
            }
            popBuilder.show(this)
        }

        openChooseDialog()
    }

    private fun isLocalAvatarNull(): Boolean {
        return mForLocal && recipient.localAvatar.isNullOrEmpty()
    }


    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
        if (futureBitmap?.isRecycled == false) {
            try {
                futureBitmap?.recycle()
            } catch (ex: Exception) {
                ALog.e(TAG, "recycle error", ex)
            }
        }
    }

    private fun onImageCropComplete(bmp: Bitmap) {
        if (BitmapUtils.getByteCount(bmp) > 10737418240) {
            ToastUtil.show(this, getString(R.string.me_avatar_size_too_big))
            return
        }
        futureBitmap = bmp
        avatar_container?.setPhoto(bmp)
        AmeDispatcher.mainThread.dispatch({
            doUpdate()
        }, 100)
    }

    private fun requestPhoto(recipient: Recipient) {
        if (isLocalAvatarNull()) {
            image_root?.setBackgroundColor(getColorCompat(R.color.common_color_transparent))
            photo_preview_dock?.visibility = View.GONE
            avatar_container?.visibility = View.GONE
            handleMore()
            return
        } else {
            image_root?.setBackgroundColor(getColorCompat(R.color.common_color_black))
            photo_preview_dock?.visibility = View.VISIBLE
            avatar_container?.visibility = View.VISIBLE
        }
        val photoType = if (!mForLocal) {
            IndividualAvatarView.PROFILE_PHOTO_TYPE
        } else {
            IndividualAvatarView.LOCAL_PHOTO_TYPE
        }

        val lp = avatar_container.layoutParams
        if (lp != null) {
            val size = getScreenPixelSize()
            val target = min(size[0], size[1])
            lp.width = target
            lp.height = target
            avatar_container.layoutParams = lp
        }
        avatar_container.setPhoto(recipient, photoType)
    }

    override fun onModified(recipient: Recipient) {
        avatar_container?.post {
            if (this.recipient == recipient) {
                recipient.removeListener(this)
                requestPhoto(recipient)
            }
        }
    }

    private fun doUpdate() {
        ALog.d(TAG, "doUpdate")
        if (isSaving) {
            return
        }
        val newBitmap = futureBitmap
        AmePopup.loading.show(this)
        isSaving = true
        UserModuleImp().updateAvatarProfile(recipient, newBitmap) { success ->
            isSaving = false
            AmePopup.loading.dismiss()
            if (success) {
                AmePopup.result.succeed(this, getString(R.string.me_edit_save_success_description)) {
                    finish()
                }
            } else {
                AmePopup.result.failure(this, getString(R.string.me_edit_save_fail_description))
            }
        }
    }
}
