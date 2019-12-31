package com.bcm.messenger.wallet.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.Observer
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.ui.WalletConfirmDialog
import com.bcm.messenger.wallet.utils.WalletSettings
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.wallet_receive_activity.*
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.api.BcmRouter
import com.bcm.messenger.utility.StorageUtil
import java.io.File
import java.io.FileOutputStream

/**
 * Created by wjh on 1018/05/19
 */
class AssetsReceiveActivity : SwipeBaseActivity() {

    private lateinit var mWalletDisplay: WalletDisplay
    private var mQrContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_receive_activity)

        receive_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        receive_qr_save.setOnClickListener {
            val content = mQrContent
            if (content == null) {
                AmePopup.result.failure(this, getString(R.string.wallet_receive_qr_save_fail), true)
            } else {
                exportQRFile(mWalletDisplay.displayName().toString() + "_receive_qr.jpg")
            }
        }

        receive_address.setOnClickListener {
            saveTextToBoard(receive_address.text.toString())
            AmePopup.result.succeed(this, getString(R.string.wallet_receive_address_save_success), true)
        }

        initData()
    }

    private fun initData() {
        val model = WalletViewModel.of(this)
        mWalletDisplay = intent.getParcelableExtra(ARouterConstants.PARAM.WALLET.WALLET_COIN)
        mWalletDisplay.setManager(model.getManager())
        createWalletAddressQRCode()
        model.eventData.notice(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_TRANSACTION_NEW, null))
        model.eventData.observe(this, Observer {
            if (it?.id == ImportantLiveData.EVENT_TRANSACTION_NEW) {//当收到交易记录并且当前没有备份过账号的时候，需要弹窗提示
                val transaction = it.data as? TransactionDisplay
                if (mWalletDisplay.baseWallet == transaction?.wallet) {

                    //查询是否有备份账号，没有的话就提示备份
                    val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
                    val noticeBackup = !userProvider.hasBackupAccount()

                    if (noticeBackup) {
                        //提示账号备份
                        WalletConfirmDialog.showForNotice(this, getString(R.string.wallet_account_backup_title),
                                getString(R.string.wallet_account_backup_notice),
                                getString(R.string.wallet_account_backup_cancel),
                                getString(R.string.wallet_account_backup_confirm),
                                {

                                }, {
                            //点击备份
                            BcmRouter.getInstance().get(ARouterConstants.Activity.ME_ACCOUNT).navigation(this)
                        })
                    }
                }
            }
        })
    }

    private fun createWalletAddressQRCode() {
        val content = when (mWalletDisplay.baseWallet.coinType) {
            WalletSettings.BTC -> {
                qrForBTC()
            }
            WalletSettings.ETH -> {
                qrForETH()
            }
            else -> ""
        }
        receive_address.text = content
        ALog.d("AssetsReceiveActivity", "createWalletAddress: $content")

        Observable.create(ObservableOnSubscribe<Pair<String, Bitmap>> {

            val qrEncoder = QREncoder(content, dimension = 225.dp2Px())
            val bitmap = qrEncoder.encodeAsBitmap()
            it.onNext(Pair(qrEncoder.data, bitmap))
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mQrContent = it.first
                    receive_qr.setImageBitmap(it.second)

                }, {

                    Logger.e(it, "AssetsReceiveActivity update qr fail")
                })

    }

    private fun qrForBTC(): String {
        return mWalletDisplay.freshAddress()
    }

    private fun qrForETH(): String {
        return mWalletDisplay.baseWallet.getStandardAddress()
    }

    private fun exportQRFile(targetName: String) {
        PermissionUtil.checkStorage(this) { granted ->
            if(granted) {
                try {
                    //val currentTime = System.currentTimeMillis()

                    val bitmap = receive_qr.drawable as BitmapDrawable
                    Observable.create(ObservableOnSubscribe<Boolean> {
                        val file = File(StorageUtil.getImageDir(), targetName)
                        val fos = FileOutputStream(file)
                        val result = bitmap.bitmap.compress(Bitmap.CompressFormat.JPEG, 60, fos)
                        fos.flush()
                        fos.close()
                        val uri = Uri.fromFile(file)
                        MediaStore.Images.Media.insertImage(contentResolver, file.absolutePath, targetName, null)

                        AppContextHolder.APP_CONTEXT.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))

                        it.onNext(result)
                        it.onComplete()

                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                if(it) {
                                    AmePopup.result.succeed(this, getString(R.string.wallet_receive_qr_save_success), true)
                                }else {
                                    AmePopup.result.failure(this, getString(R.string.wallet_receive_qr_save_fail), true)
                                }
                            }, {
                                ALog.e("AssertsReceiveActivity", "exportQRFile fail", it)
                            })

                } catch (ex: Exception) {
                    ALog.e("AssetsReceiveActivity", "exportQRFile fail", ex)
                }
            }
        }

    }
}
