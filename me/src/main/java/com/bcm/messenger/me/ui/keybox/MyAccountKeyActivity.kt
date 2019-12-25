package com.bcm.messenger.me.ui.keybox

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.content.FileProvider
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.QRExport
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_my_account_key.*
import kotlinx.android.synthetic.main.me_item_keybox_account.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by zjl on 2018/10/30.
 */
class MyAccountKeyActivity : SwipeBaseActivity() {

    private val TAG = "MyAccountKeyActivity"
    private lateinit var accountId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_my_account_key)

        accountId = intent.getStringExtra(VerifyKeyActivity.ACCOUNT_ID)

        account_my_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                handleCopyQRCode()
            }
        })

        account_my_backup.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleBackupKey()
        }

        account_my_notice.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            UserModuleImp().gotoBackupTutorial()
        }

        fetchProfile(accountId)

        window?.setStatusBarLightMode()
    }

    override fun onResume() {
        super.onResume()
        initData()
    }

    private fun initData() {

        keybox_account_entrance?.visibility = View.GONE
        keybox_account_divider?.visibility = View.GONE
        keybox_account_qr?.visibility = View.VISIBLE

        val genKeyTime = AmeLoginLogic.accountHistory.getGenKeyTime(AMELogin.uid)
        val backupTime = AmeLoginLogic.accountHistory.getBackupTime(AMELogin.uid)

        // 新老帐号兼容
        val account = AmeLoginLogic.getAccount(accountId)
        if (account != null) {
            createAccountQRCodeWithAccountData(account)
        }

        if (genKeyTime > 0) {
            account_generate_date?.text = getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(genKeyTime))
        }

        if (backupTime > 0) {
            account_backup_date?.text = getString(R.string.me_str_backup_export_date, DateUtils.formatDayTime(backupTime))
            account_my_backup?.background = getDrawable(R.drawable.common_grey_bg)
            account_my_backup?.text = AppUtil.getString(this, R.string.me_mark_as_backed_up)
            account_my_backup?.setTextColor(getColorCompat(R.color.common_color_black_70))
            account_my_backup?.isEnabled = false
            account_my_notice?.visibility = View.INVISIBLE

        } else {
            val backupBuilder = SpannableStringBuilder()
            backupBuilder.append(AppUtil.getString(this, R.string.me_str_backup_date_export))
            val notBackup = AppUtil.getString(this, R.string.me_not_backed_up)
            backupBuilder.append(notBackup)
            val foregroundColor = ForegroundColorSpan(getColorCompat(R.color.common_color_ff3737))
            backupBuilder.setSpan(foregroundColor, backupBuilder.length - notBackup.length - 1, backupBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            account_backup_date?.text = backupBuilder
            account_my_backup?.background = getDrawable(R.drawable.common_blue_bg)
            account_my_backup?.text = AppUtil.getString(this, R.string.me_finish_backup)
            account_my_backup?.setTextColor(getColorCompat(R.color.common_color_white))
            account_my_backup?.isEnabled = true
            account_my_notice?.visibility = View.VISIBLE

            val noticeBuilder = SpannableStringBuilder()
            noticeBuilder.append(getString(R.string.me_account_not_backup_warning))
            noticeBuilder.append(StringAppearanceUtil.applyAppearance(getString(R.string.me_account_backup_help_description), color = getColorCompat(R.color.common_app_primary_color)))
            account_my_notice?.text = noticeBuilder
        }
    }

    private fun fetchProfile(accountId: String) {
        if (accountId.isNotEmpty()) {
            val account = AmeLoginLogic.accountHistory.getAccount(accountId)
            val realUid: String? = account?.uid
            val name: String? = account?.name
            val avatar: String? = account?.avatar

            if (!realUid.isNullOrEmpty()) {

                val weakThis = WeakReference(this)
                Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                    try {
                        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(realUid), false)
                        val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                            avatar
                        }else {
                            null
                        }
                        recipient.setProfile(recipient.profileKey, name, finalAvatar)
                        emitter.onNext(recipient)
                    } finally {
                        emitter.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ recipient ->
                            weakThis.get()?.keybox_account_openid?.text = "${getString(R.string.me_id_title)}: $realUid"
                            weakThis.get()?.keybox_account_name?.text = recipient.name
                            weakThis.get()?.keybox_account_img?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)

                        }, { _ ->

                        })

            }
        }
    }

    private fun createAccountQRCodeWithAccountData(accountData: AmeAccountData) {
        val weakQr = WeakReference(keybox_account_qr)

        AmePopup.loading.show(this, false)
        Observable.create(ObservableOnSubscribe<Bitmap> {
            val qrEncoder = QREncoder(QRExport.accountDataToAccountJson(accountData), dimension = 225.dp2Px(), charset = "utf-8")
            val bitmap = qrEncoder.encodeAsBitmap()
            it.onNext(bitmap)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    weakQr.get()?.setImageBitmap(it)
                    AmePopup.loading.dismiss()
                }, {
                    AmeAppLifecycle.failure(getString(R.string.me_switch_device_qr_fail_description), true)
                    Logger.e(it, "SwitchDeviceActivity create qr fail")
                    AmePopup.loading.dismiss()
                })
    }

    private fun handleCopyQRCode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Android 7.0以下不允许复制二维码
            MeConfirmDialog.showCopyBackup(this, {}, {})
        } else {
            MeConfirmDialog.showCopyBackup(this, {}, {
                copyQRCode()
            })
        }
    }

    private fun copyQRCode() {
        AmePopup.loading.show(this)
        Observable.create<Uri> {
            try {
                // 把二维码写入到文件
                val tempQRCodeFile = File("${cacheDir.path}/qrcode.jpg")
                if (!tempQRCodeFile.exists()) {
                    tempQRCodeFile.createNewFile()
                }
                val bitmap = (keybox_account_qr?.drawable as? BitmapDrawable)?.bitmap ?: throw Exception("qr drawable is null")
                val bos = FileOutputStream(tempQRCodeFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
                bos.flush()
                bos.close()
                val uri = FileProvider.getUriForFile(this, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", tempQRCodeFile)
                it.onNext(uri)
            } catch (e: Exception) {
                it.onError(e)
            } finally {
                it.onComplete()
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    AmePopup.loading.dismiss()
                }
                .subscribe({
                    // 找到便签应用
                    val targetIntents = filterPackages(it)
                    if (targetIntents.isEmpty()) {
                        // 没有便签应用，弹窗
                        AmePopup.center.newBuilder()
                                .withTitle(getString(R.string.me_backup_notice))
                                .withContent(getString(R.string.me_backup_have_no_notes_app))
                                .withOkTitle(getString(R.string.common_popup_ok))
                                .show(this)
                        return@subscribe
                    } else {
                        // 跳转或选择便签应用
                        val chooseIntent = Intent.createChooser(targetIntents.removeAt(0), getString(R.string.me_backup_share_to))
                        chooseIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray<Array<Parcelable>>(emptyArray()))
                        startActivity(chooseIntent)
                    }
                }, {
                    ALog.e(TAG, "copyQRCode error", it)
                    AmePopup.loading.dismiss()
                })
    }

    private fun filterPackages(uri: Uri): ArrayList<Intent> {
        val targetIntents = ArrayList<Intent>()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
        }
        val packageList = packageManager.queryIntentActivities(intent, 0)
        packageList.forEach {
            if (it.activityInfo.packageName.contains("note") || it.activityInfo.packageName.contains("memo")) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    type = "image/*"
                }
                targetIntents.add(shareIntent)
            }
        }

        return targetIntents
    }

    private fun handleBackupKey() {
        MeConfirmDialog.showBackupComplete(this, {}, {

            AmeLoginLogic.saveCurrentBackup(AmeTimeUtil.localTimeSecond())
            initData()
        })
    }

}