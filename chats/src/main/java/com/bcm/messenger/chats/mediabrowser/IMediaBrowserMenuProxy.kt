package com.bcm.messenger.chats.mediabrowser

/**
 * Created by zjl on 2018/10/17.
 */
interface IMediaBrowserMenuProxy {
    fun forward()
    fun save()
    fun delete()
    fun active(beActive:Boolean)
}