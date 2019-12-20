package com.bcm.messenger.me.ui.base

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.widget.TextView
import com.bcm.messenger.me.R

class BcmResultView : RelativeLayout {
    private var resultText: TextView?=null

    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}
    constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style) {
        LayoutInflater.from(context).inflate(R.layout.me_layout_result_view, this)
        resultText = findViewById(R.id.text_result)
    }

    fun setResult(result: String) {
        resultText?.text = result
    }
}