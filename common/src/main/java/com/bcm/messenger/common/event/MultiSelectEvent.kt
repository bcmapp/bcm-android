package com.bcm.messenger.common.event

/**
 * 多选消息触发事件
 * Created by wjh on 2018/10/22
 */
data class MultiSelectEvent(val isGroup: Boolean, val dataSet: Set<Any>?) {
}