package com.bcm.messenger.common.utils
import kotlin.math.min

fun <T> List<T>.split(splitSize:Int):List<List<T>> {
    val listArray = ArrayList<List<T>>()

    var start = 0
    do {
        val end = min(start+splitSize, size)
        listArray.add(subList(start, end))
        start = end
    } while (start < size)

    return listArray
}
