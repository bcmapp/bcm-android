package com.bcm.messenger.wallet.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.wallet.R

/**
 * 法币单位列表适配器
 * Created by wjh on 2018/6/2
 */
class CurrencyAdapter(context: Context, private val manager: BCMWalletManager, private var listener: CurrencySelectionListener? = null) : LinearBaseAdapter<String>(context) {

    private var mLayoutInflater = LayoutInflater.from(context)
    private var selectedCurrency: String = manager.getCurrentCurrency()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    init {
        setDataList(WalletSettings.getCurrencyList())
    }

    override fun onBindContentHolder(holder: ViewHolder<String>, trueData: String?) {
        if(holder is CurrencyViewHolder) {
            holder.bind(trueData ?: return)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<String> {
        return CurrencyViewHolder(mLayoutInflater.inflate(R.layout.wallet_currency_item, parent, false))
    }

    inner class CurrencyViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<String>(itemView) {

        val nameView: TextView
        val selectView: ImageView
        lateinit var currency: String

        init {
            nameView = itemView.findViewById(R.id.currency_name)
            selectView = itemView.findViewById(R.id.currency_select)
            itemView.setOnClickListener {
                selectedCurrency = currency
                listener?.onSelect(currency)
            }
        }

        fun bind(currency: String) {
            this.currency = currency
            nameView.text = currency
            if (selectedCurrency == currency) {
                selectView.visibility = View.VISIBLE
            } else {
                selectView.visibility = View.GONE
            }
        }
    }

    interface CurrencySelectionListener {
        fun onSelect(currencyCode: String)
    }

}