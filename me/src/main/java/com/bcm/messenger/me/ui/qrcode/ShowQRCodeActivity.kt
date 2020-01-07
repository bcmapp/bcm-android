package com.bcm.messenger.me.ui.qrcode

import android.graphics.Bitmap
import android.os.Bundle
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.QRExport
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.utility.QREncoder
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_show_qr_code.*
import java.lang.ref.WeakReference

/**
 * Created by bcm.social.01 on 2018/9/20.
 */
class ShowQRCodeActivity : SwipeBaseActivity()  {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.me_activity_show_qr_code)
        about_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                AmeModuleCenter.user(accountContext)?.gotoBackupTutorial()
            }
        })

        val uid = intent.getStringExtra(VerifyKeyActivity.ACCOUNT_ID)

        var genKeyTime = 0L
        var backupTime = 0L

        val account = AmeLoginLogic.getAccount(uid)
        if (account != null){
            genKeyTime = account.genKeyTime
            backupTime = account.backupTime
            createAccountQRCodeWithAccountData(account)

        }

        var time  =""
        if (genKeyTime > 0){
            time += getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(genKeyTime))
        }
        if (backupTime > 0){
            if (time.isNotEmpty()){
                time += "\n"
            }
            time += getString(R.string.me_str_backup_date, DateUtils.formatDayTime(backupTime))
        }
        text_account_time.text = time
    }

    private fun createAccountQRCodeWithAccountData(profile: AmeAccountData) {
        val weakQr = WeakReference(account_qr)

        AmePopup.loading.show(this, false)
        Observable.create(ObservableOnSubscribe<Bitmap> {
            val qrEncoder = QREncoder(QRExport.accountDataToAccountJson(profile), dimension = 225.dp2Px(), charset = "utf-8")
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
}