package com.bcm.messenger.wallet.utils

import android.content.Context
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.sp2Px
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.WalletDisplay

/**
 * Created by wjh on 2018/5/28
 */
class WalletListAdapter(context: Context, private var listener: WalletActionListener? = null) : LinearBaseAdapter<WalletDisplay>(context) {

    private val layoutInflater = LayoutInflater.from(context)

    var walletList: MutableList<WalletDisplay>? = null
        set(value) {
            field = value
            setDataList(value)
        }

    fun addWallet(wallet: WalletDisplay) {

        if (walletList == null) {
            walletList = mutableListOf(wallet)
        } else {
            val index = walletList?.indexOf(wallet)
            if (index == null || index == -1) {
                walletList?.add(wallet)
            } else {
                walletList?.removeAt(index)
                walletList?.add(index, wallet)
            }
        }
        setDataList(walletList)
    }

    override fun onBindContentHolder(holder: ViewHolder<WalletDisplay>, trueData: WalletDisplay?) {
        if(holder is WalletHolder) {
            holder.bind(trueData ?: return)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<WalletDisplay> {
        return WalletHolder(layoutInflater.inflate(R.layout.wallet_list_item, parent, false))
    }

    override fun onViewRecycled(holder: ViewHolder<WalletDisplay>) {
        if(holder is WalletHolder) {
            holder.unbind()
        }
    }

    private var lastPosition = -1

    inner class WalletHolder(itemView: View) : LinearBaseAdapter.ViewHolder<WalletDisplay>(itemView) {

        private val nameView: TextView = itemView.findViewById(R.id.wallet_name)
        private val balanceView: TextView = itemView.findViewById(R.id.wallet_balance)
        private lateinit var wallet: WalletDisplay

        init {
            itemView.setOnClickListener {
                listener?.onDetail(wallet)
            }
        }

        fun unbind() {
            itemView.clearAnimation()
        }

        /**
         * 绑定数据
         */
        fun bind(wallet: WalletDisplay) {
            val context = getContext() ?: return

            this.wallet = wallet
            val logo = WalletSettings.formatWalletLogo(itemView.context, wallet.baseWallet.coinType)
            logo.setBounds(0, 0, 20.dp2Px(), 20.dp2Px())
            this.nameView.text = wallet.displayName()
            this.nameView.compoundDrawablePadding = 5.dp2Px()
            this.nameView.setCompoundDrawables(logo, null, null, null)

            val span = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(wallet.displayCoinAmount(), 12.sp2Px(), getColor(R.color.common_content_second_color)))
            span.append("\n")
            span.append(StringAppearanceUtil.applyAppearance("≈" + wallet.displayMoneyAmount(), 16.sp2Px(), getColor(R.color.wallet_content_main_color)))
            this.balanceView.text = span

            if (index > lastPosition) {
                val animation = AnimationUtils.loadAnimation(context, if (index > lastPosition) R.anim.wallet_up_from_bottom else R.anim.wallet_down_from_bottom)
                itemView.clearAnimation()
                itemView.startAnimation(animation)
                lastPosition = index
            }
        }
    }

    /**
     * 钱包子列表回调
     */
    interface WalletActionListener {
        fun onDetail(wallet: WalletDisplay)
    }
}