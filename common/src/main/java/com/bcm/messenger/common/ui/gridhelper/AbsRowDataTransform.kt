package com.bcm.messenger.common.ui.gridhelper

import java.util.ArrayList

/**
 * Created by Kin on 2018/11/26
 */
abstract class AbsRowDataTransform<T>(row: Int, column: Int) {
    private val DEFAULT_ROW = 1
    private val DEFAULT_COLUMN = 1

    private val mRow: Int
    private val mColumn: Int

    init {
        if (row <= 0 || column <= 0)
            throw IllegalArgumentException("row or column must be not null")

        this.mRow = row
        this.mColumn = column
    }

    fun transform(dataList: List<T>): List<T?> {
        val destList = ArrayList<T?>()

        val pageSize = mRow * mColumn
        val size = dataList.size

        val afterTransformSize = when {
            size < pageSize -> pageSize
            size % pageSize == 0 -> size
            else -> (size / pageSize + 1) * pageSize
        }

        for (i in 0 until afterTransformSize) {
            val index = transformIndex(i, mRow, mColumn)
            if (index in 0 until size) {
                destList.add(dataList[index])
            } else {
                destList.add(null)
            }
        }

        return destList
    }

    protected abstract fun transformIndex(index: Int, row: Int, column: Int): Int
}