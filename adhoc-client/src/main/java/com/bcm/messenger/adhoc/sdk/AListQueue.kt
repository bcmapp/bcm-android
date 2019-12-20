package com.bcm.messenger.adhoc.sdk

class  AListQueue<T> (private val maxCount:Int) {
    private inner class Item <T> {
        var v:T? = null
    }
    private val arrayList = ArrayList<Item<T>>()

    private var head = -1
    private var tail = -1
    private var queueSize = 0

    fun push(i:T) {
        if (arrayList.size < maxCount) {
            arrayList.add(Item())
        }

        var next = ++head
        if (next >= maxCount) {
            next %= maxCount
        }

        if (next == tail) {
            ++tail
            if (tail >= maxCount) {
                tail %= maxCount
            }
        }

        arrayList[next].v = i
        head = next
        if (tail < 0) {
            tail = head
        }

        queueSize = Math.min(++ queueSize, maxCount)

    }

    fun pop() {
        if (tail >= 0) {
            arrayList[tail].v = null
            if (tail == head) {
                tail = -1
                head = -1
            } else {
                ++tail
                if (tail >= maxCount) {
                    tail %= maxCount
                }
            }
        }

        queueSize = Math.max(-- queueSize, 0)
    }

    fun clear() {
        arrayList.clear()
        head = -1
        tail = -1
        queueSize = 0
    }

    fun isEmpty(): Boolean {
        return tail == head && tail == -1
    }

    fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    fun size(): Int {
        return queueSize
    }

    fun get(index:Int): T? {
        val pos = (tail + index)%maxCount
        if (pos > head) {
            return null
        }
        return arrayList[(tail + index)%maxCount].v!!
    }

    fun toList():List<T> {
        return when {
            isEmpty() -> listOf()
            tail == head -> listOf(arrayList[tail].v!!)
            tail < head -> arrayList.subList(tail, head+1).map { it.v!! }
            else -> {
                val array = ArrayList<T>()
                array.addAll(arrayList.subList(tail, maxCount).map { it.v!! })
                array.addAll(arrayList.subList(0, head+1).map { it.v!! })
                array
            }
        }
    }
}