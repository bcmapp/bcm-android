package com.bcm.messenger.common.mms

import android.content.ContentUris
import android.net.Uri

/**
 * Created by Kin on 2019/11/6
 */
class GroupUriParser(private val uri: Uri) {
    fun getGid() = uri.pathSegments[2].toLong()

    fun getIndexId() = ContentUris.parseId(uri)
}