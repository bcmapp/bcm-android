package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.CustomDataSearcher
import com.bcm.messenger.common.core.Address

/**
 * bcm.social.01 2018/6/11.
 */
class AddressSearchBar : CustomDataSearcher<Address> {
    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        val paddingLeftRight = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        setPadding(paddingLeftRight, 0, paddingLeftRight, 0)
        showTip(false)
    }
}