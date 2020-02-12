package com.bcm.messenger.wallet.fragment

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.FeePlan
import kotlinx.android.synthetic.main.wallet_choose_fee_dialog.*

/**
 * 选择费用弹窗
 * ling created in 2018/5/17
 **/
class ChooseFeeDialogFragment : DialogFragment() {

    interface OnFeeSelectListener {
        /**
         * FEE选择结果监听
         */
        fun onFeeSelect(plan: FeePlan, pos: Int)
    }

    var feePlanList: List<FeePlan>? = null
        set(value) {
            field = value
            adapter.setDataList(value)

            if (currentSelect < 0 || currentSelect >= (value?.size ?: 0)) {
                currentSelect = 0
            }

            listener?.onFeeSelect(value?.get(currentSelect) ?: return, currentSelect)
        }

    internal lateinit var layoutInflater: LayoutInflater
    internal var adapter: FeeAdapter = FeeAdapter()

    //当前选中的位置
    var currentSelect: Int = 1
        set(value) {
            if (value < 0 || value >= (feePlanList?.size ?: 0)) {
                return
            }
            field = value
            adapter.notifyDataSetChanged()
            listener?.onFeeSelect(feePlanList?.get(value) ?: return, value)
        }

    var listener: OnFeeSelectListener? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)

        layoutInflater = LayoutInflater.from(context)
        fee_list.layoutManager = LinearLayoutManager(context)
        fee_list.adapter = this.adapter

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = context ?: return super.onCreateDialog(savedInstanceState)
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(getAttrColor(R.attr.common_activity_background)))    //设置Dialog背景透明效果
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
        return inflater.inflate(R.layout.wallet_choose_fee_dialog, container, false)
    }

    internal inner class FeeAdapter : LinearBaseAdapter<FeePlan>() {

        override fun onBindContentHolder(holder: ViewHolder<FeePlan>, trueData: FeePlan?) {
            if(holder is FeeViewHolder) {
                holder.bind(trueData ?: return)
            }
        }

        override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<FeePlan> {
            return FeeViewHolder(layoutInflater.inflate(R.layout.wallet_fee_item, parent, false))
        }

    }

    internal inner class FeeViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<FeePlan>(itemView) {
        private val nameView: TextView
        private val selectView: ImageView
        private lateinit var feePlan: FeePlan

        init {
            nameView = itemView.findViewById(R.id.fee_name)
            selectView = itemView.findViewById(R.id.fee_select)
            itemView.setOnClickListener {
                currentSelect = index
                listener?.onFeeSelect(feePlan, index)

                itemView.postDelayed({
                    dismiss()
                }, 500)

            }
        }

        fun bind(feePlan: FeePlan) {
            this.feePlan = feePlan
            nameView.text = this.feePlan.name
            if (this.index == currentSelect) {
                this.selectView.visibility = View.VISIBLE
            } else {
                this.selectView.visibility = View.GONE
            }
        }
    }
}