package com.bcm.messenger.chats.util

import com.bcm.messenger.common.ui.gridhelper.AbsRowDataTransform

/**
 * Created by Kin on 2018/7/26
 */
class FourRowDataTransform<T>(row: Int, column: Int) : AbsRowDataTransform<T>(row, column) {
    override fun transformIndex(index: Int, row: Int, column: Int): Int {
        val pageCount = row * column

        val curPageIndex = index / pageCount
        val divisor = index % pageCount

        val i = divisor % row
        var transformIndex = (column * i) + divisor / row

        transformIndex += curPageIndex * pageCount

        return transformIndex
    }
}