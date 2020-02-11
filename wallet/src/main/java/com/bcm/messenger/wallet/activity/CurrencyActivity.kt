package com.bcm.messenger.wallet.activity

import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.utils.CurrencyAdapter
import kotlinx.android.synthetic.main.wallet_currency_activity.*

/**
 * Created by wjh on 2018/6/2
 */
class CurrencyActivity : AccountSwipeBaseActivity() {
    private val TAG = "CurrencyActivity"

    private var mWalletModel: WalletViewModel? = null
    private var mCurrencyCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_currency_activity)

        mWalletModel = WalletViewModel.of(this, accountContext)
        mCurrencyCode = mWalletModel?.getManager()?.getCurrentCurrency() ?: ""

        currency_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                AmePopup.loading.show(this@CurrencyActivity)
                changeCurrencyCode(mCurrencyCode) { result ->
                    AmePopup.loading.dismiss()
                    if (result) {
                        mWalletModel?.getManager()?.saveCurrencyCode(mCurrencyCode)
                        AmePopup.result.succeed(this@CurrencyActivity, getString(R.string.wallet_currency_change_success_text), true)
                    } else {
                        AmePopup.result.failure(this@CurrencyActivity, getString(R.string.wallet_currency_change_fail_text), true)
                    }
                }
            }
        })

        val adapter = CurrencyAdapter(this, mWalletModel?.getManager() ?: return, object : CurrencyAdapter.CurrencySelectionListener {
            override fun onSelect(currencyCode: String) {
                mCurrencyCode = currencyCode
            }

        })
        currency_list.layoutManager = LinearLayoutManager(this)
        currency_list.adapter = adapter

        mWalletModel?.eventData?.observe(this, Observer {
            ALog.d(TAG, "CurrencyActivity observe event: ${it?.id}")
        })

    }

    fun changeCurrencyCode(currency: String, callback: (success: Boolean) -> Unit) {
        WalletViewModel.of(this, accountContext).queryAllExchangeRate(currency) {result->
            if (result != null) {
                callback.invoke(result > 0)
            }else {
                callback.invoke(false)
            }
        }
    }
}