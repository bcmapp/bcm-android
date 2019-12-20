package com.bcm.messenger.common.video.exo

import android.media.MediaDataSource
import android.os.Build
import androidx.annotation.RequiresApi
import com.bcm.messenger.common.crypto.CtrStreamUtil
import com.bcm.messenger.common.crypto.MasterSecret
import java.io.File
import java.io.FileInputStream

/**
 * Created by Kin on 2019/11/14
 */
@RequiresApi(Build.VERSION_CODES.M)
class CtrDataSource(
        private val masterSecret: MasterSecret?,
        private val file: File,
        private val random: ByteArray?,
        private val length: Long
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        val inputStream = if (masterSecret == null || random == null) {
            FileInputStream(file)
        } else {
            CtrStreamUtil.createForDecryptingInputStream(masterSecret, random, file, position)
        }

        var totalRead = 0
        var localSize = size
        var localOffset = offset

        inputStream.use {
            while (localSize > 0) {
                val read = it.read(buffer, localOffset, localSize)

                if (read == -1) {
                    return if (totalRead == 0) {
                        -1
                    } else {
                        totalRead
                    }
                }

                localSize -= read
                localOffset += read
                totalRead += read
            }
        }

        return totalRead
    }

    override fun getSize() = length

    override fun close() {

    }
}