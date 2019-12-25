package com.bcm.messenger.common.audio

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.providers.PersistentBlobProvider
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.disposables.Disposable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class AudioRecorder(private val context: Context, private val maxPlayTime: Long, private val masterSecret: MasterSecret?) : AudioCodec.IRecordFinished {
    companion object {
        private const val TAG = "AudioRecorder"
        private val executor = AmeDispatcher.singleScheduler.createWorker()
    }

    private val blobProvider: PersistentBlobProvider = PersistentBlobProvider.getInstance(context.applicationContext)
    private var audioCodec: AudioCodec? = null
    private var captureUri: Uri? = null
    private var listener:IRecorderListener? = null
    private var dispatcher = AmeDispatcher.mainThread
    private var recordTimer:Disposable? = null

    /**
     * 
     */
    fun setListener(listener:IRecorderListener) {
        this.listener = listener
    }

    /**
     * 
     * ，onRecorderStarted,  onRecorderFailed
     */
    fun startRecording() {
        ALog.i(TAG, "startRecording()")
        executor.schedule {
            ALog.i(TAG, "Running startRecording() + " + Thread.currentThread().id)
            try {
                stopRecordingImpl()

                val fds = ParcelFileDescriptor.createPipe()
                captureUri = blobProvider.create(masterSecret,
                        ParcelFileDescriptor.AutoCloseInputStream(fds[0]),
                        MediaUtil.AUDIO_AAC, null, null)?:throw IOException("create audio file failed")
                val audioCodec = AudioCodec(this)
                this.audioCodec = audioCodec

                dispatcher.dispatch {
                    listener?.onRecorderStarted()
                }

                audioCodec.start(ParcelFileDescriptor.AutoCloseOutputStream(fds[1]), maxPlayTime)

                startTimeCounter(audioCodec)
            } catch (e: IOException) {
                stopRecordingImpl()

                ALog.e(TAG, "startRecording", e)
                dispatcher.dispatch {
                    listener?.onRecorderFailed(e)
                }
            }
        }
    }

    /**
     * 
     * ，onRecorderFinished
     */
    fun stopRecording() {
        ALog.i(TAG, "stopRecording()")
        executor.schedule {
            ALog.i(TAG, "stopRecording " + Thread.currentThread().id)
            stopTimeCounter()
            stopRecordingImpl()
        }
    }

    /**
     * 
     * ，onRecorderCanceled
     */
    fun cancelRecording() {
        ALog.i(TAG, "cancelRecording()")
        executor.schedule {
            ALog.i(TAG, "cancelRecording " + Thread.currentThread().id)
            stopTimeCounter()

            val captureUri = this.captureUri
            val audioCodec = this.audioCodec
            this.audioCodec = null
            this.captureUri = null

            audioCodec?.cancel()

            if (null != captureUri) {
                try {
                    PersistentBlobProvider.getInstance(context).delete(captureUri)
                } catch (e:IOException) {
                    ALog.e(TAG, "cancelRecording", e)
                }
            }

            dispatcher.dispatch {
                listener?.onRecorderCanceled()
            }
        }
    }

    private fun stopRecordingImpl() {
        ALog.i(TAG, "Running stopRecording() + " + Thread.currentThread().id)

        val audioCodec = this.audioCodec
        this.audioCodec = null

        audioCodec?.stop()
    }

    override fun onRecordFinished(playTime: Long) {
        executor.schedule {
           ALog.i(TAG, "onRecordFinished ${Thread.currentThread().id}")
            stopTimeCounter()

            val captureUri = this.captureUri

            this.audioCodec?.stop()
            this.audioCodec = null
            this.captureUri = null

            if (captureUri != null) {
                dispatcher.dispatch {
                    listener?.onRecorderFinished()
                }

                try {
                    if (playTime < 1000) {
                        dispatcher.dispatch {
                            listener?.onRecorderSucceed(captureUri, 0, playTime)
                        }
                        return@schedule
                    }

                    var realPlayTime = calcAudioDurationByStream(captureUri)
                    if (realPlayTime == 0L) {
                        realPlayTime = playTime
                    }

                    val size = MediaUtil.getMediaSize(context, masterSecret, captureUri)

                    dispatcher.dispatch {
                        listener?.onRecorderSucceed(captureUri, size, realPlayTime)
                    }
                } catch (e: Exception) {
                    ALog.e(TAG, "onRecordFinished", e)
                    dispatcher.dispatch {
                        listener?.onRecorderSucceed(captureUri, 0, 0)
                    }
                }
            } else {
                dispatcher.dispatch {
                    listener?.onRecorderFailed(java.lang.Exception("record file is null"))
                }
            }
        }
    }

    private fun startTimeCounter(codec:AudioCodec) {
        stopTimeCounter()

        val timer = dispatcher.repeat({
            listener?.onRecorderProgress(codec.playTime)
        }, 1000)

        this.recordTimer = timer
    }

    private fun stopTimeCounter() {
        val timer = recordTimer
        recordTimer = null
        timer?.dispose()
    }

    private fun calcAudioDurationByStream(captureUri: Uri): Long {
        var duration = 0L
        var input:InputStream? = null
        var output:FileOutputStream? = null
        try {
            input = PartAuthority.getAttachmentStream(context, masterSecret, captureUri)
            val tmpFile = File(AMELogin.accountDir, "aac_${System.currentTimeMillis()}")
            tmpFile.createNewFile()
            output = FileOutputStream(tmpFile)
            output.write(input.readBytes())
            output.close()
            output = null

            input.close()
            input = null

            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(tmpFile.absolutePath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration.toLong()
            mediaPlayer.release()

            tmpFile.delete()
        } catch (e:Throwable) {
            ALog.e(TAG, "calcAudioDurationByStream", e)
        } finally {
          try {
              input?.close()
              output?.close()
          } catch (e:Throwable) {
              ALog.e(TAG, "calcAudioDurationByStream 1", e)
          }
        }
        return duration
    }

    interface IRecorderListener {
        fun onRecorderStarted()
        fun onRecorderProgress(millisTime:Long)
        fun onRecorderCanceled()
        fun onRecorderFinished()
        fun onRecorderSucceed(recordUri:Uri, byteSize:Long, playTime:Long)
        fun onRecorderFailed(error:Exception)
    }
}