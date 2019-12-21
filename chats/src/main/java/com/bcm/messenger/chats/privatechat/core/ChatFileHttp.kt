package com.bcm.messenger.chats.privatechat.core

import com.bcm.messenger.common.bcmhttp.BcmBaseHttp
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.FileMetricsInterceptor
import com.bcm.messenger.utility.bcmhttp.facade.SyncHttpWrapper
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.interceptor.ProgressInterceptor
import com.bcm.messenger.utility.proguard.NotGuard
import okhttp3.OkHttpClient
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.internal.push.http.DigestingRequestBody
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Exception

object ChatFileHttp : SyncHttpWrapper(BcmBaseHttp()) {
    private const val TAG = "ChatFileHttp"
    private val bigFileHttp = SyncHttpWrapper(BcmBaseHttp())

    private const val BIG_FILE_SIZE = 6*1024*1024 

    init {
        val client = OkHttpClient.Builder()
                .addInterceptor(FileMetricsInterceptor())
                .addInterceptor(ProgressInterceptor())
                .build()
        setClient(client)

        val bigFileClient = OkHttpClient.Builder()
                .addInterceptor(FileMetricsInterceptor())
                .addInterceptor(ProgressInterceptor())
                .build()
        bigFileHttp.setClient(bigFileClient)
    }


    @Throws(NoContentException::class, BaseHttp.HttpErrorException::class)
    fun uploadAttachmentToAws(uploadUrl: String, fields: Array<AttachmentUploadField>?, data: InputStream, dataSize: Long,
                                      fileName: String?, contentType: String, outputStreamFactory: OutputStreamFactory): ByteArray {
        val body = DigestingRequestBody(data, outputStreamFactory, contentType, dataSize)

        val http = if (dataSize > BIG_FILE_SIZE) {
            bigFileHttp
        } else {
            this
        }

        http.postForm<AmeEmpty>(uploadUrl, "file", fileName, body, AmeEmpty::class.java){ builder ->
            fields?.forEach {
                builder.addFormData(it.key, it.value)
            }
        }
        return body.transmittedDigest
    }

    @Throws(IOException::class)
    fun downloadAttachment(url: String, localDestination: File, fileSize:Long, maxSizeBytes: Int, listener: SignalServiceAttachment.ProgressListener?) {
        val http = if (fileSize > BIG_FILE_SIZE) {
            bigFileHttp
        } else {
            this
        }
        try {
            val input = http.getFile(url, null)
            val output = FileOutputStream(localDestination)
            val buffer = ByteArray(4096)
            val contentLength = input.contentLength
            var totalRead = 0
            if (contentLength > maxSizeBytes.toLong()) {
                throw NonSuccessfulResponseCodeException("File exceeds maximum size.")
            }

            var read = input.stream.read(buffer)
            while (read != -1) {
                output.write(buffer, 0, read)
                totalRead += read
                if (totalRead > maxSizeBytes) {
                    localDestination.delete()
                    throw NonSuccessfulResponseCodeException("File exceeds maximum size.")
                }

                listener?.onAttachmentProgress(contentLength, totalRead.toLong())
                read = input.stream.read(buffer)
            }

            output.close()
            Log.w(TAG, "Downloaded: " + url + " to: " + localDestination.absolutePath)
        } catch (e: Exception) {
            throw PushNetworkException(e)
        }
    }

    data class AttachmentUploadField(val key: String, val value: String) : NotGuard
}