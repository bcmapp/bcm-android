package com.bcm.messenger.utility

import java.util.*

class LimitQueue<E>(val limit: Int) {

    private val queue = LinkedList<E>()

    val last: E
        get() = queue.last

    val first: E
        get() = queue.first

    fun offer(e: E) {
        if (queue.size >= limit) {
            queue.poll()
        }
        queue.offer(e)
    }

    operator fun get(position: Int): E {
        return queue[position]
    }

    fun poll(): E {
        return queue.poll()
    }

    fun clear() {
        queue.clear()
    }

    fun size(): Int {
        return queue.size
    }

}