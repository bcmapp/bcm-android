package com.bcm.messenger.utility.listener

interface IWeakListeners<T> {
    fun addListener(listener:T)
    fun removeListener(listener: T)
}