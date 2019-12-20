package com.bcm.messenger.common.grouprepository.events

class GroupRefreshKeyEvent(val gid:Long, val uid:String, val groupKey:String, val groupInfoSecret:String) {
}