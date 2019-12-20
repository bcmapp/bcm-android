package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.common.core.LocationItem
import com.bcm.messenger.common.ui.CustomDataSearcher

/**
 *
 * Created by wjh on 2019-09-29
 */
class LocationItemSearcher : CustomDataSearcher<LocationItem> {

    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

    }
}