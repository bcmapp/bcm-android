package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.ui.CustomDataSearcher

/**
 * Created by bcm.social.01 on 2018/5/28.
 */
open class GroupSearchBar : CustomDataSearcher<AmeGroupMemberInfo> {
    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
    }
}