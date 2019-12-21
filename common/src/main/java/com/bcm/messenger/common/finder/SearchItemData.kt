package com.bcm.messenger.common.finder



/**
 * 
 * Created by wjh on 2019/4/2
 */
class SearchItemData {

    var type: BcmFinderType = BcmFinderType.ADDRESS_BOOK//

    var tag: Any? = null//tag（）

    var hasMore: Boolean? = null//，

    var isTop = false//

    var title: CharSequence = ""//

    var moreDescription: CharSequence = ""//

    var isSelected: Boolean = false//

}