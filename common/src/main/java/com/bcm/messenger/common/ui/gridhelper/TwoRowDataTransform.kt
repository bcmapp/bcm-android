package com.bcm.messenger.common.ui.gridhelper

/**
 * Created by Kin on 2019/9/23
 */
class TwoRowDataTransform<T>(row: Int, column: Int) : AbsRowDataTransform<T>(row, column) {
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