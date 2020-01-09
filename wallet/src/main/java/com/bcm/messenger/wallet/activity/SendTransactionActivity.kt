package com.bcm.messenger.wallet.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.fragment.ITransferAction
import com.bcm.messenger.wallet.fragment.SendBitcoinFragment
import com.bcm.messenger.wallet.fragment.SendEtherFragment
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.model.WalletTransferEvent
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.ui.WalletConfirmDialog
import com.bcm.messenger.wallet.utils.WalletSettings
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter

/**
 * ling created in 2018/5/17
 */
@Route(routePath = ARouterConstants.Activity.WALLET_SEND_TRANSACTION)
class SendTransactionActivity : AccountSwipeBaseActivity() {

    private var mWalletDisplay: WalletDisplay? = null
    private var mTransferAction: ITransferAction? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (mTransferAction?.getScanRequestCode() == requestCode && resultCode == Activity.RESULT_OK) {
            mTransferAction?.setTransferAddress(data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT)
                    ?: return)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_send_transaction_activity)

        val walletDisplay = intent.getParcelableExtra<WalletDisplay>(ARouterConstants.PARAM.WALLET.WALLET_COIN)
        mWalletDisplay = walletDisplay

        val fragment = if (walletDisplay.baseWallet.coinType == WalletSettings.BTC) {
            SendBitcoinFragment()
        } else {
            SendEtherFragment()
        } as Fragment
        val args = Bundle()
        args.putParcelable(ARouterConstants.PARAM.WALLET.WALLET_COIN, walletDisplay)
        initFragment(R.id.container, fragment, args)
        mTransferAction = fragment as ITransferAction
        initData()
    }

    private fun initData() {
        val walletModel = WalletViewModel.of(this, accountContext)
        mWalletDisplay?.setManager(walletModel.getManager())
        walletModel.eventData.observe(this, Observer {event ->
            when(event?.id) {
                ImportantLiveData.EVENT_TRANSACTION_RESULT -> {
                    val result = event.data as? WalletTransferEvent ?: return@Observer
                    if(mWalletDisplay?.baseWallet == result.wallet) {
                        if (result.tx != null) {
                            finish()
                        }
                    }
                }
            }
        })

        //查询是否有备份账号，没有的话就提示备份
        val userProvider = AmeModuleCenter.user(accountContext)
        val noticeBackup = userProvider?.hasBackupAccount() == false

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
