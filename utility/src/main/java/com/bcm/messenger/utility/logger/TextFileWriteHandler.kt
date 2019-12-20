package com.bcm.messenger.utility.logger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.orhanobut.logger.DiskLogStrategy
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * ling created in 2018/6/13
 **/

class TextFileWriteHandler(private val context:Context, looper: Looper, folder: String, private val maxFileSize: Int, var logFileCount: Int, val callback: () -> Unit) : Handler(looper) {
    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024 // 4M per file

        fun buildDiskLogStrategy(context: Context, logDir: String, logFileCount: Int, callback: () -> Unit): DiskLogStrategy {
            if (TextUtils.isEmpty(logDir)) {
                throw IllegalArgumentException("please select log root folder")
            }

            val folderFile = File(logDir)
            if (!folderFile.exists()) {
                folderFile.mkdirs()
            }

            val ht = HandlerThread("AndroidTextFileLogger.$logDir")
            ht.start()
            val handler = TextFileWriteHandler(context, ht.looper, logDir, MAX_BYTES, logFileCount, callback)
            return DiskLogStrategy(handler)
        }
    }

    private var logFile: File
    private val logFolder: String = folder
    private var fileWriter: FileWriter? = null
    private var logSize:Int = 0

    init {
        if (logFileCount <= 0) {
            logFileCount = 2
        }
        logFile = getLogFile()
    }

    override fun handleMessage(msg: Message) {
        val content = msg.obj as String


        try {
            var fileWriter = this.fileWriter
            if (null == fileWriter) {
                this.fileWriter = FileWriter(logFile, true)
                fileWriter = this.fileWriter
            }

            if (null != fileWriter) {
                writeLog(fileWriter, content)
                fileWriter.flush()
            }

            logSize += content.length
            if (logSize >= maxFileSize) {
                this.fileWriter?.close()

                logFile = getLogFile()
                this.fileWriter = FileWriter(logFile, true)
            }
        } catch (e: IOException) {
            val fileWriter = this.fileWriter
            if (fileWriter != null) {
                try {
                    fileWriter.flush()
                    fileWriter.close()
                } catch (e1: IOException) { /* fail silently */
                }
            }
        }
    }


    private fun trimLogFile() {
        val list = listFileSortByModifyTime(logFolder)

        if (null != list) {
            if (list.size < logFileCount) {
                return
            }

            try {
                var deleteCount = list.size - logFileCount
                for (f in list) {
                    f.deleteRecursively()

                    --deleteCount
                    if (deleteCount < 0) {
                        break
                    }
                }
            } catch (e: SecurityException) {
                Log.e("trimLogFile", "failed", e)
            }
        }
    }


    private fun listFileSortByModifyTime(folder: String): List<File>? {
        val list = File(folder).listFiles()?.map { it -> it }?.toMutableList()
        list?.sortWith(Comparator { file, newFile ->
            when {
                file.lastModified() < newFile.lastModified() -> -1
                file.lastModified() == newFile.lastModified() -> 0
                else -> 1
            }
        })
        return list
    }

    /**
     * This is always called on a single background thread.
     * Implementing classes must ONLY write to the fileWriter and nothing more.
     * The abstract class takes care of everything else including close the stream and catching IOException
     *
     * @param fileWriter an instance of FileWriter already initialised to the correct file
     */
    @Throws(IOException::class)
    private fun writeLog(fileWriter: FileWriter, content: String) {
        fileWriter.append(content)
    }

    @SuppressLint("CheckResult")
    private fun getLogFile(): File {
        Observable.create(ObservableOnSubscribe<Void> {
            trimLogFile()
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({}, {})
        logSize = 0

        val dateFormat = SimpleDateFormat("MM_dd_HH_mm_ss")
        val logFileName = String.format(Locale.US, "log-%s-%d.log", dateFormat.format(Date(System.currentTimeMillis())), android.os.Process.myPid())

        val file = File(logFolder + logFileName)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            try {
                file.createNewFile()
            } catch (e: Exception) {
                Log.e("Logger", "", e)
            }
        }
        callback.invoke()
        return file
    }
}