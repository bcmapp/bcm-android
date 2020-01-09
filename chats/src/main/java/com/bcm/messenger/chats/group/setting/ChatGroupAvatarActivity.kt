package com.bcm.messenger.chats.group.setting

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Bundle
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.imagepicker.BcmPickPhotoView
import com.bcm.messenger.common.imagepicker.CropResultCallback
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.createScreenShot
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_activity_group_avatar.*
import java.io.File

/**
 * Created by Kin on 2019/7/4
 */
class ChatGroupAvatarActivity : AccountSwipeBaseActivity() {
    private val TAG = "ChatGroupAvatarActivity"

    private lateinit var groupModel: GroupViewModel
    private var gid = 0L
    private var role = AmeGroupMemberInfo.VISITOR
    private var futureBitmap: Bitmap? = null

    private var mHandling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_activity_group_avatar)

        gid = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        role = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ROLE, AmeGroupMemberInfo.VISITOR)
        val groupModel = GroupLogic.get(accountContext).getModel(gid)
        if (null == groupModel) {
            finish()
            return
        }
        this.groupModel = groupModel

        group_avatar_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                openChooseDialog()
            }
        })

        updateAvatar()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (futureBitmap?.isRecycled == false) {
            try {
                futureBitmap?.recycle()
            }catch (ex: Exception) {
                ALog.e(TAG, "recycle error", ex)
            }
        }
    }

    private fun updateAvatar() {
        group_avatar.showGroupAvatar(accountContext, gid, false)
    }

    private fun openChooseDialog() {
        val builder = AmePopup.bottom.newBuilder()
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_group_setting_save_to_album)) {
                    saveAvatarToLocal()
                })
        if (role == AmeGroupMemberInfo.OWNER) {
            builder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_change)) {
                openChangeDialog()
            })
        }
        builder.withDoneTitle(getString(R.string.chats_cancel))
                .show(this)
    }

    private fun openChangeDialog() {
        if (mHandling) {
            ToastUtil.show(this, "photo is handling")
            return
        }
        AmePopup.bottom.newBuilder()
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_take_photo)) {
                    BcmPickPhotoView.Builder(this@ChatGroupAvatarActivity)
                            .setCapturePhoto(true)
                            .setCropImage(true)
                            .addCropCallback(object : CropResultCallback {
                                override fun cropResult(bmp: Bitmap) {
                                    onImageCropComplete(bmp)
                                }
                            })
                            .build().start()
                })
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_choose_photo)) {
                    BcmPickPhotoView.Builder(this@ChatGroupAvatarActivity)
                            .setCropImage(true)
                            .addCropCallback(object : CropResultCallback {
                                override fun cropResult(bmp: Bitmap) {
                                    onImageCropComplete(bmp)
                                }
                            })
                            .build().start()
                })
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.common_delete_photo), AmeBottomPopup.PopupItem.CLR_RED) {
                    group_avatar.clearBitmap()
                    updateAvatar(null)
                })
                .withDoneTitle(getString(R.string.chats_cancel))
                .show(this)
    }

    private fun onImageCropComplete(bmp: Bitmap) {
        ALog.i(TAG, "onImageCropComplete finish")
        futureBitmap = bmp
        AmeDispatcher.mainThread.dispatch({
            updateAvatar(bmp)
        }, 100)
    }

    private fun updateAvatar(updateBitmap: Bitmap?) {
        AmePopup.loading.show(this)
        if (updateBitmap != null) {
            if (mHandling) {
                return
            }
            mHandling = true
            Observable.create<Pair<String, String>> {
                val avatarPath = BcmFileUtils.saveBitmap2File(updateBitmap)
                if (!avatarPath.isNullOrBlank()) {
                    // TODO: Encrypt group avatar
//                    val groupInfo = groupModel.getGroupInfo()
//                    if (groupInfo == null) {
//                        it.onError(AssertionError("GroupInfo is null."))
//                        return@create
//                    }
//
//                    val tempKey = BCMPrivateKeyUtils.generateKeyPair()
//                    val groupPublicKey = groupInfo.groupPublicKey
//                    if (groupPublicKey == null || groupPublicKey.isEmpty()) {
//                        it.onError(AssertionError("GroupPublicKey is null or empty."))
//                        return@create
//                    }
//
//                    val aesKey = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(groupPublicKey, tempKey.privateKey.serialize())
//                    val destFilePath = "${AmeFileUploader.ENCRYPT_DIRECTORY}${File.separator}avatar-group-${groupInfo.gid}"
//                    BCMEncryptUtils.encryptFileByAES256(avatarPath, destFilePath, aesKey)

//                    if (BcmFileUtils.getFileSize(File(avatarPath)) >= 10737418240) {
//                        it.onError(Exception(resources.getString(R.string.chats_group_info_edit_portrait_size_exceeded)))
//                    } else {
//                        File(avatarPath).delete()
//                        it.onNext(Pair(destFilePath, Base64.encodeBytes(tempKey.publicKey.serialize())))
//                    }

                    if (BcmFileUtils.getFileSize(File(avatarPath)) >= 10737418240) {
                        it.onError(Exception(resources.getString(R.string.chats_group_info_edit_portrait_size_exceeded)))
                    } else {
                        it.onNext(Pair(avatarPath, ""))
                    }
                } else {
                    it.onError(Exception(getString(R.string.chats_group_info_edit_save_failed)))
                }
                it.onComplete()

            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.d(TAG, "begin uploadGroupAvatar")
                        AmeFileUploader.uploadGroupAvatar(accountContext, AppContextHolder.APP_CONTEXT, File(it.first), object : AmeFileUploader.FileUploadCallback() {
                            override fun onUploadFailed(filePath: String, msg: String?) {
                                failed(msg)
                            }

                            override fun onUploadSuccess(url: String?, id: String?) {
                                doUpdateAvatar(url, it.second)
                            }

                            override fun onProgressChange(currentProgress: Float) {}
                        })

                    }, {
                        failed(it.message)
                    })

        }else {
            doUpdateAvatar("", null)
        }

    }

    @SuppressLint("CheckResult")
    private fun doUpdateAvatar(url: String?, key: String?) {
        ALog.d(TAG, "doUpdateAvatar url: $url")
        if (url.isNullOrEmpty()) {
            failed("url is empty")
            return
        }

        Observable.create<Pair<Boolean, String?>> {
            val emitter = it
            val callback: (succeed: Boolean, error: String?) -> Unit = { succeed, error ->
                emitter.onNext(Pair(succeed, error))
                emitter.onComplete()
            }

            val urlString = /*if (!url.isNullOrBlank() && key != null) {
                val bean = GroupInfoEntity.EncryptedProfileBean().apply {
                    content = url
                    this.key = key
                }
                GsonUtils.toJson(bean)
            } else {*/
                url
            /*}*/

            if (!groupModel.updateGroupAvatar( urlString, callback)) {
                it.onNext(Pair(false, getString(R.string.chats_group_info_edit_save_failed)))
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.first) {
                        group_avatar?.setLoadCallback {
                            mHandling = false
                            AmePopup.loading.dismiss()
                            AmePopup.result.succeed(this, getString(R.string.chats_group_info_edit_save_success)) {
                                finish()
                            }
                        }
                        group_avatar?.showGroupAvatar(accountContext, gid, false)

                    } else {
                        failed(it.second)
                    }
                }, {
                    ALog.e(TAG, "doSave url: $url error", it)
                    failed(it.message)
                })
    }

    private fun failed(error: String?) {
        mHandling = false
        AmePopup.loading.dismiss()
        AmePopup.result.failure(this, error
                ?: getString(R.string.chats_group_info_edit_save_failed))
    }

    private fun saveAvatarToLocal() {
        val bitmap = group_avatar.createScreenShot()
        Observable.create<String> {
            val path = BcmFileUtils.saveBitmap2File(bitmap, "BCM-GROUP-${groupModel.groupId()}.jpg", AmeFileUploader.DCIM_DIRECTORY)
            if (path == null) {
                it.onError(Exception("Save QR code error"))
                return@create
            }
            it.onNext(path)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    AmePopup.result.failure(this, getString(R.string.chats_group_info_edit_save_failed), true)
                }
                .subscribe {
                    MediaScannerConnection.scanFile(this, arrayOf(it), arrayOf(BcmFileUtils.IMAGE_PNG), null)
                    AmePopup.result.succeed(this, getString(R.string.chats_group_info_edit_save_success), true)
                }
    }

}