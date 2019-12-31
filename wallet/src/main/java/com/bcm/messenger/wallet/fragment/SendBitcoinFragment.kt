package com.bcm.messenger.wallet.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.FeePlan
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.ui.WalletConfirmDialog
import com.bcm.messenger.wallet.utils.BtcExchangeCalculator
import com.bcm.messenger.wallet.utils.BCMWalletManager
import com.bcm.messenger.wallet.utils.BtcWalletController
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.wallet_send_fragment.*
import kotlinx.android.synthetic.main.wallet_send_transaction_layout.*
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import java.math.BigDecimal


/**
 * 发送btc页面
 * created wjh in 2018/5/30
 **/
class SendBitcoinFragment : Fragment(), ITransferAction {

    private var mFeePlan: FeePlan? = null
    private lateinit var mWalletDisplay: WalletDisplay
    private lateinit var mFeeUpdateListener: TextWatcher
    private lateinit var mFeeChooseDialog: ChooseFeeDialogFragment
    private var mWalletModel: WalletViewModel? = null

    override fun getScanRequestCode(): Int {
        return 1000
    }

    override fun setTransferAddress(content: String) {
        to_content?.setText(content)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mWalletModel = WalletViewModel.of(activity!!)
        mWalletDisplay = arguments?.getParcelable(ARouterConstants.PARAM.WALLET.WALLET_COIN) ?: return
        mWalletDisplay.setManager(mWalletModel?.getManager())

        initViews()
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AmePopup.loading.dismiss()
        to_content.removeTextChangedListener(mFeeUpdateListener)
        amount_content.removeTextChangedListener(mFeeUpdateListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.wallet_send_fragment, container, false)
    }

    private fun initViews() {

        mFeeChooseDialog = ChooseFeeDialogFragment()
        mFeeChooseDialog.listener = object : ChooseFeeDialogFragment.OnFeeSelectListener {
            override fun onFeeSelect(plan: FeePlan, pos: Int) {
                updateFeePlan(plan)
            }
        }
        mFeeChooseDialog.feePlanList = FeePlan.getBtcDefault()

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.finish()
            }
        })

        advance_switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                advance_layout_btc.visibility = View.VISIBLE
                simple_fee_layout.visibility = View.GONE
            } else {
                advance_layout_btc.visibility = View.GONE
                simple_fee_layout.visibility = View.VISIBLE
            }
        }

        fee_suggest_type.setOnClickListener {
            mFeeChooseDialog.show(childFragmentManager, "choose")
        }
        paste_btn.setOnClickListener {
            val address = AppUtil.getCodeFromBoard(activity)
            if (!TextUtils.isEmpty(address)) {
                to_content.setText(address)
            }
        }
        qr_scan_btn.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                    .navigation(activity, getScanRequestCode())
        }
        transfer_btn.setOnClickListener {
            AmeAppLifecycle.showLoading()
            checkTransferAllow { pass, description ->
                AmeAppLifecycle.hideLoading()
                if (!pass) {
                    AmeAppLifecycle.failure(description, true)
                } else {
                    val fm = fragmentManager ?: return@checkTransferAllow
                    val walletDisplay = mWalletDisplay
                    //弹窗确认是否真的交易
                    val confirmDialog = TransferConfirmDialogFragment()
                    confirmDialog.mName = mWalletDisplay.displayName()
                    confirmDialog.mAmount = amount_content.text.toString()
                    confirmDialog.mType = getString(R.string.wallet_transfer_type_transfer)
                    confirmDialog.mFromAddress = walletDisplay.baseWallet.address
                    confirmDialog.mToAddress = to_content.text.toString()
                    if (advance_switch.isChecked) {
                        confirmDialog.mFeeCost = btc_fee_price.text.toString()
                    } else {
                        confirmDialog.mFeeCost = BtcExchangeCalculator.convertAmount(mFeePlan?.fee
                                ?: "0").toString()
                    }
                    confirmDialog.listener = object : TransferConfirmDialogFragment.OnTransferConfirmListener {
                        override fun onConfirm() {
                            checkInputAndSendBitcoin(confirmDialog)
                        }

                    }
                    confirmDialog.show(fm, "TransferConfirm")
                }
            }

        }
        amount_all_btn.setOnClickListener {
            amount_content.setText(mWalletDisplay.getCoinAmount().toString())
        }

        mFeeUpdateListener = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                requestFeePlan()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTransferButtonStatus()
            }

        }
        to_content.addTextChangedListener(mFeeUpdateListener)
        amount_content.addTextChangedListener(mFeeUpdateListener)
    }

    private fun initData() {

        amount_unit.text = mWalletDisplay.baseWallet.coinType
        requestAccountBalance()
    }

    /**
     * 更新按钮的状态
     */
    private fun updateTransferButtonStatus() {
        transfer_btn.isEnabled = !(amount_content.text.isEmpty() && to_content.text.isEmpty())
    }

    /**
     * 更新当前小费计划
     */
    private fun updateFeePlan(feePlan: FeePlan) {
        this.mFeePlan = feePlan
        fee_suggest_type?.text = feePlan.name
        val feeCost = mFeePlan?.fee ?: "0"
        val span = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(BtcExchangeCalculator.convertAmountDisplay(coin = *Array(1, { feeCost })).toString(),
                15.dp2Px(), getColorCompat(R.color.wallet_content_main_color)))
        span.append("\n")
        span.append(StringAppearanceUtil.applyAppearance("≈" +
                BtcExchangeCalculator.convertMoneyDisplay(coin = *Array(1, { feeCost })), 12.dp2Px(), Color.parseColor("#C2C2C2")))
        fee_suggest_count?.text = span
    }

    /**
     * 更新当前环境的小费标准
     */
    private fun requestFeePlan() {

        mWalletModel?.querySuggestFee(mWalletDisplay.baseWallet) {result ->
            if (result != null) {
                mFeeChooseDialog.feePlanList = result
            }
        }

    }

    /**
     * 请求可用的比特币
     */
    private fun requestAccountBalance() {

        assets_count.text = mWalletDisplay.displayCoinAmount()
        mWalletModel?.queryBalance(mWalletDisplay.baseWallet) {result ->
            if (result != null) {
                mWalletDisplay = result
                assets_count.text = mWalletDisplay.displayCoinAmount()
            }
        }
    }

    /**
     * 确认比特币交易弹窗
     */
    private fun checkInputAndSendBitcoin(confirmDialogFragment: TransferConfirmDialogFragment) {

        WalletConfirmDialog.showForPassword(activity, getString(R.string.wallet_confirm_password_title),
                confirmListener = { password ->
                    sendBitcoin(password, confirmDialogFragment)
                }, passwordChecker = {
            //密码验证通过才能进行交易
            mWalletModel?.getManager()?.verifyPassword(it) ?: false
        })
    }

    /**
     * 发送比特币
     */
    private fun sendBitcoin(password: String, confirmDialogFragment: TransferConfirmDialogFragment) {

        AmeAppLifecycle.showLoading()
        val extra = Bundle()
        extra.putString(BtcWalletController.EXTRA_FEE, confirmDialogFragment.mFeeCost.toString())
        extra.putString(BtcWalletController.EXTRA_MEMO, data_content.text.toString())
        mWalletModel?.getManager()?.startTransferService(AppContextHolder.APP_CONTEXT, mWalletDisplay, confirmDialogFragment.mToAddress.toString(),
                confirmDialogFragment.mAmount.toString(), extra)
    }

    /**
     * 检测地址是否正确
     */
    @SuppressLint("CheckResult")
    private fun checkTransferAllow(callback: (pass: Boolean, description: String) -> Unit) {
        Observable.create(ObservableOnSubscribe<Pair<Boolean, String>> {

            var result = false
            var description = ""
            if (to_content.text.toString().isEmpty()) {
                description = getString(R.string.wallet_transfer_to_address_warning)
            } else if (mWalletDisplay.baseWallet.isMine(to_content.text.toString())) {
                description = getString(R.string.wallet_transfer_to_same_address_warning)
            } else if (amount_content.text.toString().isEmpty()) {
                description = getString(R.string.wallet_transfer_amount_waning)
            } else {
                val balance = mWalletDisplay.getCoinAmount().toDouble()
                val amount = BigDecimal(amount_content.text.toString()).toDouble()
                if (balance < amount) {
                    description = getString(R.string.wallet_transfer_amount_not_enough_warning)
                } else {
                    result = true
                }
            }
            it.onNext(Pair(result, description))
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    callback.invoke(it.first, it.second)
                }, {
                    callback.invoke(false, it.message ?: "")
                })
    }


}