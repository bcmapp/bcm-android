package com.bcm.messenger.wallet.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.accountmodule.IUserModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.utils.startBcmActivityForResult
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.model.WalletTransferEvent
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.ui.WalletConfirmDialog
import com.bcm.messenger.wallet.utils.TransactionAdapter
import com.bcm.messenger.wallet.utils.WalletSettings
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.wallet_detail_activity.*

/**
 * 对应币种钱包的详细页面
 * Created by wjh on 2018/5/18
 */
class WalletDetailActivity : SwipeBaseActivity() {

    private val TAG = "WalletDetailActivity"

    private val REQUEST_TRANSFER = 1//交易请求
    private val REQUEST_RECEIVE = 2//收款页面返回

    private lateinit var mAdapter: TransactionAdapter
    private lateinit var mWalletDisplay: WalletDisplay
    private var mWalletModel: WalletViewModel? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TRANSFER && resultCode == Activity.RESULT_OK) {
            loadTransactionList()
        } else if (requestCode == REQUEST_RECEIVE) {
            loadTransactionList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.wallet_detail_activity)

        initData()
        initView()
    }

    private fun initView() {
        detail_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                val popBuilder = AmePopup.bottom.newBuilder()
                popBuilder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.wallet_popup_create_description)){
                    mWalletModel?.getManager()?.goForCreateWallet(this@WalletDetailActivity, mWalletDisplay.baseWallet.coinType)
                })
                if (!WalletSettings.isBCMDefault(mWalletDisplay.baseWallet.accountIndex)){
                    popBuilder.withPopItem(AmeBottomPopup.PopupItem(getString(R.string.wallet_popup_delete_description), AmeBottomPopup.PopupItem.CLR_RED){
                        mWalletModel?.getManager()?.goForDeleteWallet(this@WalletDetailActivity, mWalletDisplay.baseWallet)
                    })
                }

                popBuilder.withDoneTitle(getString(R.string.common_cancel)).show(this@WalletDetailActivity)
            }
        })

        detail_refresh_layout.setOnRefreshListener {
            loadTransactionList()
        }

        //跳转到收款页面
        detail_receive.setOnClickListener {
            AmePopup.loading.show(this@WalletDetailActivity, false)
            AmeDispatcher.io.dispatch{
                checkPasswordBeforeHandle {
                    AmeDispatcher.mainThread.dispatch {
                        AmePopup.loading.dismiss()
                        if (it) {
                            val intent = Intent(this, AssetsReceiveActivity::class.java)
                            intent.putExtra(ARouterConstants.PARAM.WALLET.WALLET_COIN, mWalletDisplay)
                            startBcmActivityForResult(intent, REQUEST_RECEIVE)
                        }
                    }
                }
            }


        }

        //跳转到转账页面
        detail_transfer.setOnClickListener {
            AmePopup.loading.show(this@WalletDetailActivity, false)
            AmeDispatcher.io.dispatch{
                checkPasswordBeforeHandle {
                    AmeDispatcher.mainThread.dispatch {
                        AmePopup.loading.dismiss()
                        if (it) {
                            val intent = Intent(this, SendTransactionActivity::class.java)
                            intent.putExtra(ARouterConstants.PARAM.WALLET.WALLET_COIN, mWalletDisplay)
                            startBcmActivityForResult(intent, REQUEST_TRANSFER)
                        }
                    }
                }
            }
        }
    }

    private fun initData() {

        mWalletModel = WalletViewModel.of(this, accountContext)
        mWalletDisplay = intent.getParcelableExtra(ARouterConstants.PARAM.WALLET.WALLET_COIN)
        mWalletDisplay.setManager(mWalletModel?.getManager())
        mWalletModel?.eventData?.observe(this, Observer { event ->
            ALog.d(TAG, "observe event: ${event?.id}")
            when (event?.id) {
                ImportantLiveData.EVENT_NAME_CHANGED -> mAdapter.updateCoinHeader(mWalletDisplay)
                ImportantLiveData.EVENT_RATE_UPDATE -> mAdapter.updateCoinHeader(mWalletDisplay)
                ImportantLiveData.EVENT_DELETE -> {
                    finish()
                }
                ImportantLiveData.EVENT_BALANCE -> {
                    if (event.data == null) return@Observer
                    val newWalletDisplay = event.data as WalletDisplay
                    if (newWalletDisplay == mWalletDisplay) {
                        mWalletDisplay = newWalletDisplay
                        mAdapter.updateCoinHeader(mWalletDisplay)
                    }
                }
                ImportantLiveData.EVENT_TRANSACTION_NEW -> {
                    if (event.data == null) return@Observer
                    val newTransaction = event.data as TransactionDisplay
                    if (mWalletDisplay.baseWallet == newTransaction.wallet) {
                        mAdapter.addTransaction(newTransaction)
                    }
                }
                ImportantLiveData.EVENT_ACCOUNT_BACKUP -> {
                    val notice = event.data as? Boolean
                    if (notice == true) {
                        //提示账号备份
                        WalletConfirmDialog.showForNotice(this, getString(R.string.wallet_account_backup_title),
                                getString(R.string.wallet_account_backup_notice),
                                getString(R.string.wallet_account_backup_cancel),
                                getString(R.string.wallet_account_backup_confirm),
                                {
                                    //点击later
                                }, {
                            //点击备份
                            BcmRouter.getInstance().get(ARouterConstants.Activity.ME_ACCOUNT).navigation(this)
                        })
                    }
                }
                ImportantLiveData.EVENT_TRANSACTION_RESULT -> {
                    val result = event.data as? WalletTransferEvent ?: return@Observer
                    if (mWalletDisplay.baseWallet == result.wallet) {
                        loadTransactionList()
                    }
                }
            }
        })

        mAdapter = TransactionAdapter(this, object : TransactionAdapter.TransactionActionListener {
            override fun onEdit() {

                changeWalletName()
            }

            override fun onDetail(transaction: TransactionDisplay) {
                // 添加交易详情页
                val intent = Intent(this@WalletDetailActivity, TransactionDetailActivity::class.java)
                intent.putExtra(ARouterConstants.PARAM.WALLET.TRANSFER_DETAIL, transaction)
                startBcmActivity(intent)
            }

        })
        transaction_list.layoutManager = LinearLayoutManager(this)
        transaction_list.adapter = mAdapter
        mAdapter.updateCoinHeader(mWalletDisplay)

        loadTransactionList()
    }

    /**
     * 加载交易记录
     */
    private fun loadTransactionList() {
        detail_refresh_layout.isRefreshing = true
        mWalletModel?.queryBalance(mWalletDisplay.baseWallet) {result -> }
        mWalletModel?.queryTransactions(mWalletDisplay.baseWallet) {result ->
            detail_refresh_layout.isRefreshing = false
            if (result != null) {
                mAdapter.transactionList = result.toMutableList()
            }
        }
    }

    /**
     * 修改钱包名
     */
    private fun changeWalletName() {
        WalletConfirmDialog.showForEdit(this@WalletDetailActivity,
                getString(R.string.wallet_name_edit_confirm_title), previous = mWalletDisplay.displayName(), hint = getString(R.string.wallet_name_hint),
                confirmListener = { name ->
                    AmePopup.loading.show(this@WalletDetailActivity)
                    Observable.create(ObservableOnSubscribe<Boolean> {
                        if (mWalletDisplay.baseWallet.name != name) {
                            mWalletDisplay.baseWallet.name = name
                            mWalletModel?.getManager()?.changeWalletName(mWalletDisplay.baseWallet.address, name)
                            mWalletModel?.eventData?.noticeDelay(ImportantLiveData.ImportantEvent(ImportantLiveData.EVENT_NAME_CHANGED, mWalletDisplay))

                        }
                        it.onNext(true)
                        it.onComplete()
                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                AmePopup.loading.dismiss()
                            }, {
                                AmePopup.loading.dismiss()
                            })

                })

    }

    /**
     * 处理之前先检测用户密码
     * @param callback pass: true表示可以继续操作，false表示不可以
     */
    @SuppressLint("CheckResult")
    private fun checkPasswordBeforeHandle(callback: (pass: Boolean) -> Unit) {
        val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()

        //回调结果表示是否需要修改密码
        userProvider.checkUseDefaultPin { useDefault, defaultPin ->
            if (useDefault) {
                WalletConfirmDialog.showForPasswordChange(this, getString(R.string.wallet_change_password_title),
                        getString(R.string.wallet_change_password_notice), cancel = getString(R.string.wallet_change_password_cancel),
                        confirm = getString(R.string.wallet_change_password_confirm), cancelListener = {
                    callback.invoke(false)
                }, confirmListener = {
                    callback.invoke(true)
                }, passwordChecker = { newPassword ->
                    userProvider.changePinPassword(defaultPin
                            ?: throw Exception("default pin is null"), newPassword)
                })

            } else {
                callback.invoke(true)
            }
        }

    }

}