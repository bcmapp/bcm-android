package com.bcm.netswitchy.proxy.support

import io.reactivex.Observable
import io.reactivex.Scheduler

interface IConnectionChecker {
    fun check(delay:Long,scheduler: Scheduler): Observable<Boolean>
}