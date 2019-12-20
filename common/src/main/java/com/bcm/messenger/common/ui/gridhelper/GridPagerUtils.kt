package com.bcm.messenger.common.ui.gridhelper

/**
 * Created by Kin on 2018/11/26
 */


/**
 * transform and fill empty data
 *
 * @param srcList the source of data
 * @param row     the row of grid
 * @param column  the column of grid
 * @param <T>
 * @return
</T> */
fun <T> transformAndFillEmptyData(srcList: List<T>, row: Int, column: Int): List<T?> {
    if (row == 0 || column == 0)
        throw IllegalArgumentException("row or column must be not null")

    //1. new a empty ArrayList
    val destList = ArrayList<T?>()

    val size = srcList.size
    val pageCount = row * column

    //2. get the traverseCount
    val traverseCount = when {
        size < pageCount -> pageCount
        size % pageCount == 0 -> size
        else -> (size / pageCount + 1) * pageCount
    }

    //3. travrse the list
    for (i in 0 until traverseCount) {
        val pre = i / pageCount
        val divisor = i % pageCount

        var index = if (divisor % row == 0) {//even
            divisor / 2
        } else {//odd
            column + divisor / 2
        }

        //this is very important
        index += pre * pageCount

        if (index in 0 until size) {
            destList.add(srcList[index])
        } else {
            destList.add(null)
        }
    }

    //4. back
    return destList
}

/**
 * transform and fill empty data
 *
 * @param orderTransform order transform
 * @param dataList
 * @param <T>
 * @return
</T> */
fun <T> transformAndFillEmptyData(orderTransform: AbsRowDataTransform<T>, dataList: List<T>): List<T?> {
    return orderTransform.transform(dataList)
}