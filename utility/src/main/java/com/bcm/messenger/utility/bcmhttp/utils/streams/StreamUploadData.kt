package com.bcm.messenger.utility.bcmhttp.utils.streams

import java.io.InputStream

/**
 * Created by Kin on 2019/11/8
 */
data class StreamUploadData(val inputStream: InputStream, val name: String, val mimeType: String, val dataSize: Long)