package com.bcm.messenger.wallet.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.*
import androidx.fragment.app.DialogFragment
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.getDrawable
import com.bcm.messenger.wallet.R
import kotlinx.android.synthetic.main.wallet_transaction_confirm_dialog.*

/**
 * 支付交易确认窗口
 * created by wjh in 2018/5/17
 **/
class TransferConfirmDialogFragment : DialogFragment() {

    interface OnTransferConfirmListener {
        fun onConfirm()
    }

    var listener: OnTransferConfirmListener? = null

    var mName: CharSequence? = null
    //交易数量
    var mAmount: CharSequence? = null
    var mToAddress: CharSequence? = null
    var mFromAddress: CharSequence? = null
    //交易类型
    var mType: CharSequence? = null
    //支付小费(最终结算的）
    var mFeeCost: CharSequence? = null
    //用于eth的gas
    var mGasPrice: CharSequence? = null
    //用于eth的gas limit
    var mGasLimit: CharSequence? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)

        transfer_confirm_amount.text = mAmount
        transfer_confirm_to.text = mToAddress

        val name = mName ?: ""
        val span = SpannableString(name)
        span.setSpan(AbsoluteSizeSpan(16, true), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(getAttrColor(R.attr.common_text_secondary_color)), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val builder = SpannableStringBuilder(span)
        transfer_confirm_from.text = builder
        transfer_confirm_fee.text = mFeeCost

        transfer_confirm_btn.setOnClickListener {
            listener?.onConfirm()
            dismiss()
        }
        transfer_confirm_cancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(R.color.common_background_color)    //设置Dialog背景透明效果
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.setCanceledOnTouchOutside(true)

        val windowParams = dialog.window?.attributes?.apply {
            dimAmount = 0.0f
            gravity = Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        dialog.window?.attributes = windowParams

        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.wallet_transaction_confirm_dialog, container, false)
    }

}