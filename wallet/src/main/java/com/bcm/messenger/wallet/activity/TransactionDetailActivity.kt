package com.bcm.messenger.wallet.activity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.utils.BtcWalletUtils
import com.bcm.messenger.wallet.utils.EthWalletUtils
import com.bcm.messenger.wallet.utils.WalletSettings
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.wallet_transaction_detail_activity.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.common.SwipeBaseActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by wjh on 2018/05/25
 */
class TransactionDetailActivity : SwipeBaseActivity() {

    private lateinit var mTransactionDetail: TransactionDisplay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_transaction_detail_activity)

        record_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        initView()


    }

    private fun initView() {
        mTransactionDetail = intent.getSerializableExtra(ARouterConstants.PARAM.WALLET.TRANSFER_DETAIL) as TransactionDisplay
        if (mTransactionDetail.wallet.coinType == WalletSettings.ETH) {
            record_fee_head.text = getString(R.string.wallet_transaction_detail_eth_fee_head)
        } else {
            record_fee_head.text = getString(R.string.wallet_transaction_detail_btc_fee_head)
        }

        val drawable = when (mTransactionDetail.wallet.coinType) {
            WalletSettings.BTC -> getDrawable(R.drawable.wallet_btc_icon)
            WalletSettings.ETH -> getDrawable(R.drawable.wallet_eth_icon)
            else -> getDrawable(R.drawable.wallet_eth_icon)
        }
        val size = 40.dp2Px()
        drawable?.setBounds(0, 0, size, size)
        record_amount.setCompoundDrawables(null, drawable, null, null)
        record_amount.text = mTransactionDetail.displayTransactionAmount()
        record_status.text = WalletSettings.displayTransactionStatus(mTransactionDetail)
        record_from.text = mTransactionDetail.fromAddress
        record_to.text = mTransactionDetail.toAddress

        record_fee.text = mTransactionDetail.displayTransactionFee()
        record_tx.text = mTransactionDetail.txhash
        record_block.text = mTransactionDetail.block
        val simpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        record_time.text = simpleDateFormat.format(Date(mTransactionDetail.date))

        createRecordQr()

    }

    @SuppressLint("CheckResult")
    private fun createRecordQr() {
        Observable.create(ObservableOnSubscribe<Pair<String, Bitmap>> {

            val url = createBrowserUrl(mTransactionDetail)
            ALog.d("TransactionDetailActivity", "browse url: $url")
            val qrCodeEncoder = QREncoder(url, dimension = 100.dp2Px())
            val bitmap = qrCodeEncoder.encodeAsBitmap()
            it.onNext(Pair(url, bitmap))
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    record_qr.setImageBitmap(result.second)
                    record_qr_save.setOnClickListener {
                        AmePopup.result.succeed(this, getString(R.string.wallet_transaction_qr_copy_success), true)
                        AppUtil.saveCodeToBoard(this, result.first)
                    }
                }, {
                    Logger.e(it, "TransactionDetailActivity createRecordQr fail")
                })
    }


    private fun createBrowserUrl(transaction: TransactionDisplay): String {
        return when (transaction.wallet.coinType) {
            WalletSettings.BTC -> BtcWalletUtils.getBrowserInfoUrl(transaction.txhash)
            WalletSettings.ETH -> EthWalletUtils.getBrowserInfoUrl(transaction.txhash)
            else -> ""
        }
    }

}
