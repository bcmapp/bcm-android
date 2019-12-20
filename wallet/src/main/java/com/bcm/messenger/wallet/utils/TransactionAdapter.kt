package com.bcm.messenger.wallet.utils

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.sp2Px
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by wjh on 2018/5/16
 */
class TransactionAdapter(context: Context, private val listener: TransactionActionListener?) : LinearBaseAdapter<TransactionDisplay>(context) {

    var transactionList: MutableList<TransactionDisplay>? = null
        set(value) {
            field = value
            if (value == null || value.isEmpty()) {
                showHeader(mHeaderShade, true)
                showFooter(mFooterType, false)
            } else {
                showHeader(mHeaderShade, false)
                showFooter(mFooterType, false)
            }
            setDataList(value)
        }

    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
    private var lastPosition = -1
    private val dateFormat = SimpleDateFormat("yy/MM/dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var mCoinName: TextView
    private var mCoinBalance: TextView

    private var mHeaderBalance = 0
    private var mHeaderShade = 0

    private var mFooterType = 0
    private var mLoadingFooter: TextView
    private var mIsLoadingMore = false

    init {
        val header = mLayoutInflater.inflate(R.layout.wallet_transaction_header, null)
        header.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mCoinName = header.findViewById(R.id.coin_name)
        mCoinBalance = header.findViewById(R.id.coin_balance)
        header.findViewById<ImageView>(R.id.coin_edit).setOnClickListener {
            listener?.onEdit()
        }
        mHeaderBalance = addHeader(header)

        val resources = context.resources
        val emptyView = TextView(context)
        emptyView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        emptyView.gravity = Gravity.CENTER
        emptyView.text = resources.getString(R.string.wallet_transaction_empty_description)
        emptyView.textSize = 15f
        emptyView.setPadding(resources.getDimensionPixelSize(R.dimen.common_horizontal_gap), 0, resources.getDimensionPixelSize(R.dimen.common_horizontal_gap), 0)
        emptyView.setTextColor(getColor(R.color.common_content_second_color))

        mHeaderShade = addHeader(emptyView)

        val footer = TextView(context)
        footer.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        footer.gravity = Gravity.CENTER
        footer.text = resources.getString(R.string.wallet_transaction_load_more_description)
        footer.textSize = 15f
        footer.setPadding(resources.getDimensionPixelSize(R.dimen.common_horizontal_gap),
                resources.getDimensionPixelSize(R.dimen.common_vertical_gap),
                resources.getDimensionPixelSize(R.dimen.common_horizontal_gap),
                resources.getDimensionPixelSize(R.dimen.common_vertical_gap))
        footer.setTextColor(getColor(R.color.common_content_second_color))

        mFooterType = addFooter(footer)
        mLoadingFooter = footer

        notifyMainChanged()
    }

    fun updateCoinHeader(wallet: WalletDisplay) {
        val context = getContext() ?: return

        val logo = WalletSettings.formatWalletLogoForDetail(context, wallet.baseWallet.coinType)
        val size = 40.dp2Px()
        logo.setBounds(0, 0, size, size)
        mCoinName.setCompoundDrawables(logo, null, null, null)
        mCoinName.text = wallet.displayName()

        val span = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(wallet.displayCoinAmount(), 15.sp2Px(), getColor(R.color.common_color_white)))
        span.append("\n")
        span.append(StringAppearanceUtil.applyAppearance(WalletDisplay.countMoneyBalanceDisplayWithCurrency(wallet.baseWallet.coinType, listOf(wallet)), 22.sp2Px(), getColor(R.color.common_color_white)))
        mCoinBalance.text = span

    }

    fun hasLoadingMore(): Boolean {
        return mIsLoadingMore
    }

    fun showLoadingMore() {
        mLoadingFooter.text = getContext()?.getString(R.string.wallet_transaction_load_more_description)
        if(getShowedFooterCount() <= 0) {
            showFooter(mFooterType, true, true)
        }
        mIsLoadingMore = true
    }

    fun showNoMore() {
        mLoadingFooter.text = getContext()?.getString(R.string.wallet_transaction_no_more_description)
        if(getShowedFooterCount() <= 0) {
            showFooter(mFooterType, true, true)
        }
        mIsLoadingMore = false
    }

    fun hideFooter() {
        mIsLoadingMore = false
        if(getShowedFooterCount() > 0) {
            showFooter(mFooterType, false, true)
        }
    }

    fun addTransaction(transaction: TransactionDisplay) {
        if (transactionList == null) {
            transactionList = mutableListOf(transaction)
        } else {
            val index = transactionList?.indexOf(transaction)
            if (index == null || index == -1) {
                transactionList?.add(0, transaction)
            } else {
                transactionList?.removeAt(index)
                transactionList?.add(index, transaction)
            }
        }
        setDataList(transactionList)
    }

    override fun onBindContentHolder(holder: ViewHolder<TransactionDisplay>, trueData: TransactionDisplay?) {
        if(holder is TransactionViewHolder) {
            holder.bind(trueData ?: return)
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<TransactionDisplay> {
        return TransactionViewHolder(mLayoutInflater.inflate(R.layout.wallet_transaction_item, parent, false))
    }

    override fun onViewRecycled(holder: ViewHolder<TransactionDisplay>) {
        if (holder is TransactionViewHolder) {
            holder.unbind()
        }
    }

    inner class TransactionViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<TransactionDisplay>(itemView) {

        private var dateView: TextView = itemView.findViewById(R.id.transaction_date)
        private var amountView: TextView = itemView.findViewById(R.id.transaction_amount)
        private var addressView: TextView = itemView.findViewById(R.id.transaction_address)
        private var statusView: TextView = itemView.findViewById(R.id.transaction_status)
        private lateinit var transaction: TransactionDisplay

        init {
            itemView.setOnClickListener {
                listener?.onDetail(transaction)
            }
        }

        fun unbind() {
            itemView.clearAnimation()
        }

        fun bind(transaction: TransactionDisplay) {
            val context = getContext() ?: return

            this.transaction = transaction
            val amountSpan: CharSequence
            val dateSpan = SpannableStringBuilder(StringAppearanceUtil.applyAppearance(dateFormat.format(Date(transaction.date)), 16.sp2Px(),
                    getColor(R.color.common_app_primary_color)))
            dateSpan.append("\n")
            dateSpan.append(StringAppearanceUtil.applyAppearance(timeFormat.format(Date(transaction.date)), 12.sp2Px(),
                    Color.parseColor("#616161")))
            dateView.text = dateSpan

            var addressText: String?
            if (transaction.isSent()) {
                amountSpan = StringAppearanceUtil.applyAppearance(transaction.displayTransactionAmount(), 15.sp2Px(), getColor(R.color.wallet_transaction_output_color))
                addressText = transaction.toAddress
            } else {
                amountSpan = StringAppearanceUtil.applyAppearance(transaction.displayTransactionAmount(), 15.sp2Px(), getColor(R.color.wallet_transaction_input_color))
                addressText = transaction.fromAddress
            }
            if (!transaction.memo.isNullOrEmpty()) {
                addressText += "\n" + transaction.memo
            }
            amountView.text = amountSpan
            addressView.text = addressText

            if (transaction.isError) {
                statusView.setTextColor(getColor(R.color.common_content_warning_color))

            } else if (transaction.isPending()) {
                statusView.setTextColor(Color.parseColor("#C2C2C2"))

            }
            statusView.text = WalletSettings.displayTransactionStatus(transaction)

            if (index > lastPosition) {
                val animation = AnimationUtils.loadAnimation(context, if (index > lastPosition) R.anim.wallet_up_from_bottom else R.anim.wallet_down_from_bottom)
                itemView.clearAnimation()
                itemView.startAnimation(animation)
                lastPosition = index
            }
        }

    }

    interface TransactionActionListener {
        fun onDetail(transaction: TransactionDisplay)
        fun onEdit()
    }
}