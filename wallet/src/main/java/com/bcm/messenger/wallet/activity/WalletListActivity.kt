package com.bcm.messenger.wallet.activity

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.BCMWalletAccountDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.utils.WalletListAdapter
import com.bcm.messenger.wallet.utils.WalletSettings
import kotlinx.android.synthetic.main.wallet_list_activity.*

/**
 * 某种数字货币的列表页面
 * Created by wjh on 2018/5/28
 */
class WalletListActivity : SwipeBaseActivity() {

    private lateinit var mAdapter: WalletListAdapter
    private lateinit var mCoinType: String
    private lateinit var mWalletList: ArrayList<WalletDisplay>

    private var mWalletModel: WalletViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_list_activity)

        list_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                AmePopup.bottom.newBuilder()
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.wallet_popup_create_description)){
                            mWalletModel?.getManager()?.goForCreateWallet(this@WalletListActivity, mCoinType)
                        }).withDoneTitle(getString(R.string.common_cancel))
                        .show(this@WalletListActivity)
            }
        })

        initData()
    }

    private fun initData() {
        mWalletModel = WalletViewModel.of(this).apply {
            setAccountContext(accountContext)
        }
        mCoinType = intent.getStringExtra(ARouterConstants.PARAM.WALLET.COIN_TYPE)
        when (mCoinType) {
            WalletSettings.BTC -> list_title_bar.setCenterText(getString(R.string.wallet_list_btc_title))
            else -> list_title_bar.setCenterText(getString(R.string.wallet_list_eth_title))
        }

        mWalletList = intent.getParcelableArrayListExtra(ARouterConstants.PARAM.WALLET.WALLET_LIST)

        mAdapter = WalletListAdapter(this, object : WalletListAdapter.WalletActionListener {
            override fun onDetail(wallet: WalletDisplay) {
                val intent = Intent(this@WalletListActivity, WalletDetailActivity::class.java)
                intent.putExtra(ARouterConstants.PARAM.WALLET.WALLET_COIN, wallet)
                startBcmActivity(intent)
            }
        })

        wallet_list.layoutManager = LinearLayoutManager(this)
        wallet_list.adapter = mAdapter

        mAdapter.walletList = mWalletList

        //监听重要事件（如名字变更，汇率变更）
        mWalletModel?.eventData?.observe(this, Observer { event ->
            ALog.d("WalletListActivity", "observe event: ${event?.id}")
            when (event?.id) {
                ImportantLiveData.EVENT_NAME_CHANGED -> {
                    if (event.data != null) {
                        val newWallet = event.data as WalletDisplay
                        if (mCoinType == newWallet.baseWallet.coinType) {
                            mAdapter.addWallet(newWallet)
                        }
                    }
                }
                ImportantLiveData.EVENT_RATE_UPDATE -> mAdapter.notifyDataSetChanged()
                ImportantLiveData.EVENT_DELETE -> {
                    val w = mWalletList.find { it.baseWallet == event.data }
                    if (w != null) {
                        mWalletList.remove(w)
                        mAdapter.walletList = mWalletList
                    }
                }
                //监听余额变更
                ImportantLiveData.EVENT_BALANCE -> {
                    if (event.data != null) {
                        val newWallet = event.data as WalletDisplay
                        if (mCoinType == newWallet.baseWallet.coinType) {
                            mAdapter.addWallet(newWallet)
                        }
                    }
                }
                //列表的更新
                ImportantLiveData.EVENT_BALANCE_LIST -> {
                    if (event.data != null) {
                        val newList = event.data as? MutableList<BCMWalletAccountDisplay>
                        if (newList != null) {
                            for (walletTypeDisplay in newList) {
                                if (walletTypeDisplay.coinType == mCoinType) {
                                    mAdapter.walletList = walletTypeDisplay.coinList
                                    break
                                }
                            }
                        }
                    }
                }

                ImportantLiveData.EVENT_NEW -> {
                    if(event.data != null) {
                        val wallet = event.data as BCMWallet
                        if (mCoinType == wallet.coinType) {
                            mAdapter.addWallet(wallet.toEmptyDisplayWallet())
                        }
                    }
                }
            }
        })
    }

}