package com.bcm.messenger.common.finder

import com.bcm.messenger.utility.proguard.NotGuard
import java.io.Serializable

/**
 *
 * Created by wjh on 2019/4/3
 */
class SearchRecordDetail : SearchRecord {
    var tag: Any? = null//对应的数据

    constructor() {

    }

    constructor(record: SearchRecord) {
        this.times = record.times
        this.date = record.date
        this.type = record.type
    }
}


open class SearchRecord() : Serializable, NotGuard {

    var times: Long = 0//搜索次数

    var date: Long = 0//初次搜索时间

    var type: BcmFinderType = BcmFinderType.ADDRESS_BOOK//搜索类型

}