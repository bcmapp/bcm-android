package com.bcm.messenger.adhoc.component

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.common.ui.CustomDataSearcher

/**
 *
 * Created by wjh on 2019-08-23
 */
class SessionDataSearcher : CustomDataSearcher<AdHocSession> {

    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0) {}
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {

    }
}