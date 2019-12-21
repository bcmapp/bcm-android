package com.bcm.messenger.me.ui.login.backup

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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.QRExport
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.QREncoder
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_backup_account_key.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by zjl on 2018/10/30.
 */
class BackupAccountKeyActivity : SwipeBaseActivity() {

    private lateinit var accountId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_backup_account_key)

        accountId = intent.getStringExtra(VerifyKeyActivity.ACCOUNT_ID)

        backup_account_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                handleCopyQRCode()
            }
        })

        if (AppUtil.checkDeviceHasNavigationBar(this) && getNavigationBarHeight() > 0) {
            val lp = account_qr_detail.layoutParams as ConstraintLayout.LayoutParams
            lp.topMargin = 10.dp2Px()
            account_qr_detail.layoutParams = lp
        }

        setAccountBackedUpState()
    }

    override fun onResume() {
        super.onResume()
        initDate()
    }

    private fun initDate() {
        val genKeyTime = AmeLoginLogic.accountHistory.getGenKeyTime(AMESelfData.uid)
        val backupTime = AmeLoginLogic.accountHistory.getBackupTime(AMESelfData.uid)

        val account = AmeLoginLogic.getAccount(accountId)
        if (account != null) {
            createAccountQRCodeWithAccountData(account)
        }

        val time = SpannableStringBuilder()
        if (genKeyTime > 0) {
            time.append(getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(genKeyTime)))
        }
        if (time.isNotEmpty()) {
            time.append("\n")
        }

        if (backupTime > 0) {
            time.append(getString(R.string.me_str_backup_export_date, DateUtils.formatDayTime(backupTime)))
        } else {
            time.append(AppUtil.getString(this, R.string.me_str_backup_date_export))
            val notBackup = AppUtil.getString(this, R.string.me_not_backed_up)
            time.append(notBackup)
            val foregroundColor = ForegroundColorSpan(getColorCompat(R.color.common_color_ff3737))
            time.setSpan(foregroundColor, time.length - notBackup.length - 1, time.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text_account_time.text = time
    }

    private fun createAccountQRCodeWithAccountData(accountData: AmeAccountData) {
        val weakQr = WeakReference(account_qr)

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
            
                val tempQRCodeFile = File("${cacheDir.path}/qrcode.jpg")
                if (!tempQRCodeFile.exists()) {
                    tempQRCodeFile.createNewFile()
                }
                val bitmap = (account_qr.drawable as BitmapDrawable).bitmap
                val bos = FileOutputStream(tempQRCodeFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
                bos.flush()
                bos.close()
                val uri = FileProvider.getUriForFile(this, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", tempQRCodeFile)
                it.onNext(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                it.onComplete()
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    AmePopup.loading.dismiss()
                }
                .subscribe({
                
                    val targetIntents = filterPackages(it)
                    if (targetIntents.isEmpty()) {
                    
                        AmePopup.center.newBuilder()
                                .withTitle(getString(R.string.me_backup_notice))
                                .withContent(getString(R.string.me_backup_have_no_notes_app))
                                .withOkTitle(getString(R.string.common_popup_ok))
                                .show(this)
                        return@subscribe
                    } else {
                        
                        val chooseIntent = Intent.createChooser(targetIntents.removeAt(0), getString(R.string.me_backup_share_to))
                        chooseIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray<Array<Parcelable>>(emptyArray()))
                        startActivity(chooseIntent)
                    }
                }, {
                    it.printStackTrace()
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
            initDate()
            setAccountBackedUpState()
        })
    }

    private fun setAccountBackedUpState() {
        val backupTime = AmeLoginLogic.accountHistory.getBackupTime(AMESelfData.uid)
        if (backupTime > 0) {
            me_backup_status.setBackgroundResource(R.color.common_color_white_70)
            me_backup_status.text = AppUtil.getString(this, R.string.me_mark_as_backed_up)
            me_backup_status.setOnClickListener(null)
        } else {
            me_backup_status.setBackgroundResource(R.color.common_color_379BFF)
            me_backup_status.text = AppUtil.getString(this, R.string.me_finish_backup)
            me_backup_status.setOnClickListener {
                handleBackupKey()
            }
        }

    }
}