package com.bcm.messenger.utility.listener

import java.util.*

class SafeWeakListeners<T> : IWeakListeners<T> {
    private val listSet = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<T, Boolean>()))
    override fun addListener(listener: T) {
        listSet.add(listener)
    }

    override fun removeListener(listener: T) {
        listSet.remove(listener)
    }

    fun forEach(iterator: (listener: T) -> Unit) {
        listSet.forEach {
            iterator(it)
        }
    }

    fun any(iterator: (listener: T) -> Boolean): Boolean {
        return listSet.any {
            iterator(it)
        }
    }
}