package com.bcm.messenger.common.finder



/**
 * 搜索结果数据
 * Created by wjh on 2019/4/2
 */
class SearchItemData {

    var type: BcmFinderType = BcmFinderType.ADDRESS_BOOK//搜索结果类型

    var tag: Any? = null//根据类型决定对应tag的数据类型（可能为空）

    var hasMore: Boolean? = null//是否显示更多选项，是否有更多

    var isTop = false//是否首个

    var title: CharSequence = ""//标题

    var moreDescription: CharSequence = ""//更多的描述

    var isSelected: Boolean = false//是否选中

}