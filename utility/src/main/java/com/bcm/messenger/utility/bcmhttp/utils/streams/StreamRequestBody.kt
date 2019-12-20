package com.bcm.messenger.utility.bcmhttp.utils.streams

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

/**
 * Created by Kin on 2019/11/8
 */
class StreamRequestBody(
        private val inputStream: InputStream,
        private val mimeType: String,
        private val size: Long
) : RequestBody() {


    override fun contentType(): MediaType? {
        return MediaType.parse(mimeType)
    }

    override fun writeTo(sink: BufferedSink) {
        val outputStream = sink.outputStream()
        val buffer = ByteArray(1024)

        var read: Int
        do {
            read = inputStream.read(buffer, 0, buffer.size)
            if (read != -1) {
                outputStream.write(buffer, 0, read)
            }
        } while (read != -1)

        outputStream.flush()
    }

    override fun contentLength() = size
}