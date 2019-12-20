package com.bcm.messenger.common.ui.popup

import android.app.Activity
import android.app.Dialog
import android.view.*
import android.widget.NumberPicker
import android.widget.TextView
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.DataPicker
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2018/9/21
 */
class DataPickerPopupWindow(activity: Activity) : Dialog(activity, R.style.CommonPickerPopupWindow) {

    private var title = ""
    private var dataList = listOf<String>()
    private var doneButtonText = ""
    private var callback: ((index: Int) -> Unit)? = null
    private var currentIndex = 0
    private var weakActivity: WeakReference<Activity>? = null

    private lateinit var titleView: TextView
    private lateinit var pickerView: DataPicker
    private lateinit var doneView: TextView

    init {
        weakActivity = WeakReference(activity)

        this.requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.setContentView(R.layout.common_data_picker_view)
        this.setCanceledOnTouchOutside(true)

        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setWindowAnimations(R.style.CommonBottomPopupWindow)
    }

    private fun initView() {
        titleView = findViewById(R.id.data_picker_title)
        pickerView = findViewById(R.id.data_picker)
        doneView = findViewById(R.id.data_picker_done)

        titleView.text = title
        pickerView.displayedValues = dataList.toTypedArray()
        pickerView.minValue = 0
        pickerView.maxValue = dataList.size - 1
        pickerView.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        pickerView.wrapSelectorWheel = false
        pickerView.value = currentIndex
        pickerView.setOnValueChangedListener { _, _, newVal ->
            currentIndex = newVal
        }
        doneView.setOnClickListener {
            dismiss()
            callback?.invoke(currentIndex)
        }

        window?.attributes = window?.attributes?.apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.5f
        }
    }

    fun setTitle(title: String): DataPickerPopupWindow {
        this.title = title
        return this
    }

    fun setDataList(list: List<String>): DataPickerPopupWindow {
        this.dataList = list
        return this
    }

    fun setButtonText(text: String): DataPickerPopupWindow {
        this.doneButtonText = text
        return this
    }

    fun setCallback(callback: (Int) -> Unit): DataPickerPopupWindow {
        this.callback = callback
        return this
    }

    fun setCurrentIndex(index:Int):DataPickerPopupWindow{
        if (index >= 0&&index<dataList.size){
            currentIndex = index
        }
        return this
    }

    override fun dismiss() {
        super.dismiss()

        window.attributes = window.attributes.apply {
            dimAmount = 1f
        }
    }

    override fun show() {
        initView()
        super.show()
    }
}