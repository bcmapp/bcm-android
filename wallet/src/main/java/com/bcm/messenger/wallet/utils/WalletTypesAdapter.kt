package com.bcm.messenger.wallet.utils

import android.content.Context
import android.content.res.Resources
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.sp2Px
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.BCMWalletAccountDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import java.math.BigDecimal

/**
 * Created by wjh on 2018/5/16
 */
class WalletTypesAdapter(context: Context, private var listener: WalletActionListener? = null) : LinearBaseAdapter<BCMWalletAccountDisplay>(context) {

    private var mBCMWalletList: MutableList<BCMWalletAccountDisplay>? = null

    private var mLastPosition = -1

    private var mSyncing = false//是否同步中
    private var mSecretMode: Boolean = true

    private var mTotalMoney: BigDecimal = BigDecimal.ZERO

    private var mFooterCallback: (() -> Unit)? = null
    private var mFooterText: TextView? = null
    private var mFooterType = 0

    fun initHeaderFooter(parent: RecyclerView) {
        val footer = LayoutInflater.from(parent.context).inflate(R.layout.wallet_entrance_footer, parent, false)
        mFooterText = footer.findViewById(R.id.entrance_notice_tv)
        mFooterText?.setOnClickListener {
            mFooterCallback?.invoke()
        }

        mFooterType = addFooter(footer)
    }

    fun getTotalMoney(): BigDecimal {
        return mTotalMoney
    }

    fun setSyncing(isSyncing: Boolean) {
        this.mSyncing = isSyncing
        notifyDataSetChanged()
    }

    fun showWalletEntrance(notice: String, callback: (() -> Unit)?) {
        mFooterCallback = callback
        mFooterText?.text = notice
        showFooter(mFooterType, true)
    }

    fun hideWalletEntrance() {
        showFooter(mFooterType, false)
    }

    fun notifyWalletChanged() {
        countTotalMoney()
        notifyDataSetChanged()
        listener?.onDisplayChanged()
    }

    fun addWallet(wallet: WalletDisplay) {

        if (mBCMWalletList == null) {
            mBCMWalletList = mutableListOf(BCMWalletAccountDisplay(wallet.baseWallet.coinType, mutableListOf(wallet)))
        } else {
            val typeDisplay = mBCMWalletList?.find { it.coinType == wallet.baseWallet.coinType }
            if (typeDisplay == null) {
                mBCMWalletList?.add(BCMWalletAccountDisplay(wallet.baseWallet.coinType, mutableListOf(wallet)))
            } else {
                val index = typeDisplay.coinList.indexOf(wallet)
                if (index == -1) {
                    typeDisplay.coinList.add(wallet)
                } else {
                    typeDisplay.coinList.removeAt(index)
                    typeDisplay.coinList.add(index, wallet)
                }
            }
        }
        setWalletList(mBCMWalletList ?: listOf())
    }

    fun setWalletList(BCMWalletList: List<BCMWalletAccountDisplay>) {
        mBCMWalletList = BCMWalletList.toMutableList()
        setDataList(mBCMWalletList)
        notifyWalletChanged()
    }

    fun changeSecretMode(secretMode: Boolean) {
        this.mSecretMode = secretMode
        listener?.onDisplayChanged()
        notifyDataSetChanged()
    }

    private fun countTotalMoney() {
        val list = mBCMWalletList ?: return
        mTotalMoney = list.fold(BigDecimal.ZERO) { total, next -> total + countTotalMoney(next.coinList) }
    }

    private fun countTotalMoney(walletList: List<WalletDisplay>): BigDecimal {
        return walletList.fold(BigDecimal.ZERO) { total, next -> total.add(next.getMoneyAmount()) }
    }

    fun getSecretMode(): Boolean {
        return mSecretMode
    }

    override fun onBindContentHolder(holder: ViewHolder<BCMWalletAccountDisplay>, trueData: BCMWalletAccountDisplay?) {
        if(holder is WalletHolder) {
            val pair = trueData ?: return
            holder.bind(pair.coinType, pair.coinList)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<BCMWalletAccountDisplay> {
        return WalletHolder(LayoutInflater.from(parent.context).inflate(R.layout.wallet_type_item, parent, false))
    }

    override fun onViewRecycled(holder: ViewHolder<BCMWalletAccountDisplay>) {
        if (holder is WalletHolder) {
            holder.unbind()
        }
    }

    inner class WalletHolder(itemView: View) : LinearBaseAdapter.ViewHolder<BCMWalletAccountDisplay>(itemView) {
        private val logoView: ImageView = itemView.findViewById(R.id.wallet_logo)
        private val nameView: TextView = itemView.findViewById(R.id.wallet_name)
        private val balanceView: TextView = itemView.findViewById(R.id.wallet_balance)
        private lateinit var walletList: List<WalletDisplay>
        private lateinit var coinBase: String

        init {
            itemView.setOnClickListener {
                listener?.onDetail(coinBase, walletList)
            }
        }

        fun unbind() {
            itemView.clearAnimation()
        }

        fun bind(coinBase: String, walletList: List<WalletDisplay>) {
            this.coinBase = coinBase
            this.walletList = walletList
            this.logoView.setImageDrawable(WalletSettings.formatWalletLogo(itemView.context, coinBase))
            this.nameView.text = coinBase

            displayBalance(itemView.resources, walletList)

            if (index > mLastPosition) {
                val animation = AnimationUtils.loadAnimation(itemView.context, if (index > mLastPosition) R.anim.wallet_up_from_bottom else R.anim.wallet_down_from_bottom)
                itemView.clearAnimation()
                itemView.startAnimation(animation)
                mLastPosition = index
            }
        }

        private fun displayBalance(resources: Resources, walletList: List<WalletDisplay>) {
            if (mSecretMode) {
                this.balanceView.text = resources.getString(R.string.wallet_secret_text)
            } else if (mSyncing) {
                this.balanceView.text = resources.getString(R.string.wallet_syncing_text)
            } else {
                val span = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(WalletDisplay.countCoinBalanceDisplay(coinBase, walletList), 16.sp2Px(), getColor(R.color.wallet_content_main_color)))
                span.append("\n")
                span.append(StringAppearanceUtil.applyAppearance(WalletDisplay.countMoneyBalanceDisplayWithCurrency(coinBase, walletList), 12.sp2Px(), getColor(R.color.common_content_second_color)))
                this.balanceView.text = span
            }
        }
    }

    interface WalletActionListener {

        fun onDetail(coinBase: String, walletList: List<WalletDisplay>)

        fun onDisplayChanged()
    }
}
