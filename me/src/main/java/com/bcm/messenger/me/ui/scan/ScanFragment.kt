package com.bcm.messenger.me.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.*
import android.provider.MediaStore
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.scan.CameraManager
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_scan.*
import java.util.*

/**
 * Created by wjh on 2019/7/2
 */
class ScanFragment : Fragment(), TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "ScanFragment"

        const val REQUEST_SCAN_CODE_PICK = 1000

        private const val VIBRATE_DURATION = 50L
        private const val AUTO_FOCUS_INTERVAL_MS = 2500L

    }

    private val cameraManager = CameraManager()

    @Volatile
    private var surfaceCreated = false

    private var vibrator: Vibrator? = null
    private lateinit var cameraThread: HandlerThread

    @Volatile
    private lateinit var cameraHandler: Handler

    private var mScanCharSet: String? = null

    private var mTorchOn = false

    private lateinit var previewView: TextureView

    private val openRunnable = object : Runnable {
        override fun run() {
            try {
                val camera = cameraManager.open(previewView, displayRotation())
                val framingRect = cameraManager.frame
                val framingRectInPreview = RectF(cameraManager.framePreview)
                framingRectInPreview.offsetTo(0f, 0f)
                val cameraFlip = cameraManager.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                val cameraRotation = cameraManager.orientation

                AmeDispatcher.mainThread.dispatch {
                    scan_scanner?.pauseDraw = false
                    scan_scanner?.setFraming(framingRect, framingRectInPreview, displayRotation(), cameraRotation, cameraFlip)
                }

                val focusMode = camera.parameters.focusMode
                val nonContinuousAutoFocus = Camera.Parameters.FOCUS_MODE_AUTO == focusMode || Camera.Parameters.FOCUS_MODE_MACRO == focusMode

                if (nonContinuousAutoFocus) {
                    cameraHandler.post(AutoFocusRunnable(camera))
                }

                cameraHandler.post(fetchAndDecodeRunnable)

                AmeDispatcher.mainThread.dispatch {
                    scan_flash_btn?.isEnabled = true
                }

            } catch (x: Exception) {
                ALog.e(TAG, "problem opening camera, ${x.message}")
            }

        }

        private fun displayRotation(): Int {
            val rotation = activity?.windowManager?.defaultDisplay?.rotation ?: 0
            return if (rotation == Surface.ROTATION_0) {
                0
            } else if (rotation == Surface.ROTATION_90) {
                90
            } else if (rotation == Surface.ROTATION_180) {
                180
            } else if (rotation == Surface.ROTATION_270) {
                270
            } else {
                throw IllegalStateException("rotation: $rotation")
            }
        }
    }

    private val closeRunnable = Runnable {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraManager.close()
        AmeDispatcher.mainThread.dispatch {
            scan_scanner?.pauseDraw = true
            scan_flash_btn?.isEnabled = false
        }

    }

    private val fetchAndDecodeRunnable = object : Runnable {
        private val reader = QRCodeReader()
        private val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)

        override fun run() {
            cameraManager.requestPreviewFrame { data, _ -> decode(data) }
        }

        private fun decode(data: ByteArray) {
            val source = cameraManager.buildLuminanceSource(data)

            var result = decode(source, false)
            if (result == null || result.text.isNullOrEmpty()) {
                result = decode(source, true)
            }
            if (result == null) {
                cameraHandler.post(this)
            }else {
                AmeDispatcher.mainThread.dispatch {
                    handleResult(result ?: return@dispatch)
                }
            }
        }

        private fun decode(source: PlanarYUVLuminanceSource, invert: Boolean): Result? {
            var result: Result? = null
            try {
                val newSource = if (invert) {
                    source.invert()
                }else {
                    source
                }
                val bitmap = BinaryBitmap(HybridBinarizer(newSource))
                hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = ResultPointCallback { dot -> AmeDispatcher.mainThread.dispatch {
                    scan_scanner?.addDot(dot)
                } }
                if (mScanCharSet != null) {
                    hints[DecodeHintType.CHARACTER_SET] = mScanCharSet
                }
                result = reader.decode(bitmap, hints)

            } catch (x: ReaderException) {

            } finally {
                reader.reset()
            }
            return result
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SCAN_CODE_PICK && resultCode == Activity.RESULT_OK) {
            AmeAppLifecycle.showLoading()
            Observable.create(ObservableOnSubscribe<Result> {

                try {
                    val result = decodeScanImage(BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, data?.data))
                    if (result == null) {
                        throw Exception("decodeScanImage result is null")
                    } else {
                        it.onNext(result)
                    }

                } catch (ex: Exception) {
                    ALog.e(TAG, "decodeScanImage error", ex)
                    it.onError(ex)
                } finally {
                    it.onComplete()
                }

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnTerminate {
                        AmeAppLifecycle.hideLoading()
                    }
                    .subscribe({
                        handleResult(it)
                    }, {
                        ALog.e(TAG, "scan qr error", it)
                        AmeAppLifecycle.failure(getString(R.string.common_scan_parse_qr_fail), true)
                    })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.me_fragment_scan, container, false)
        previewView = v.findViewById(R.id.scan_preview)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ALog.d(TAG, "onViewCreated")

        previewView.surfaceTextureListener = this
        scan_scanner?.drawLaser = true

        scan_flash_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            switchTorch(!mTorchOn)
        }

        scan_album_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            openAlbum(it.context)
        }

        switchTorch(mTorchOn)

        activity?.apply {
            // Stick to the orientation the activity was started with. We cannot declare this in the
            // AndroidManifest.xml, because it's not allowed in combination with the windowIsTranslucent=true
            // theme attribute.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            mScanCharSet = activity?.intent?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_CHARSET)

            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            cameraThread = HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND)
            cameraThread.start()
            cameraHandler = Handler(cameraThread.looper)

            RxBus.subscribe<NewScanActivity.ScanResumeEvent>(TAG) {
                maybeOpenCamera()
            }
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        ALog.d(TAG, "setUserVisibleHint isVisible: $isVisibleToUser")
        if (isAdded) {
            if (isVisibleToUser) {
                maybeOpenCamera()
            } else {
                cameraHandler.post(closeRunnable)
            }
        }
    }

    override fun onDestroy() {
        ALog.d(TAG, "onDestroy")
        // cancel background thread
        cameraHandler.removeCallbacksAndMessages(null)
        cameraThread.quit()

        previewView.surfaceTextureListener = null

        // We're removing the requested orientation because if we don't, somehow the requested orientation is
        // bleeding through to the calling activity, forcing it into a locked state until it is restarted.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        RxBus.unSubscribe(TAG)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ALog.d(TAG, "onResume")
        val c = context ?: return
        PermissionUtil.checkCamera(c) { granted ->
            if (granted) {
                maybeOpenCamera()
            } else {
                activity?.finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ALog.d(TAG, "onPause")
        cameraHandler.post(closeRunnable)
    }


    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        surfaceCreated = false
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        surfaceCreated = true
        maybeOpenCamera()
    }

    private fun maybeOpenCamera() {
        if (surfaceCreated && ContextCompat.checkSelfPermission(context ?: return,
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraHandler.post(openRunnable)
        }
    }

    /**
     * @param scanResult
     */
    fun handleResult(scanResult: Result) {
        vibrator?.vibrate(VIBRATE_DURATION)
        ALog.d(TAG, "scan handle result:${scanResult.text}")
//        scan_scanner?.setIsResult(true)
        RxBus.post(NewScanActivity.ScanResultEvent(scanResult))
    }

    private fun openAlbum(context: Context) {
        PermissionUtil.checkStorage(context) { granted ->
            if (granted) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                val wrapperIntent = Intent.createChooser(intent, getString(R.string.common_scan_choose_qr_title))
                startActivityForResult(wrapperIntent, REQUEST_SCAN_CODE_PICK)
            }
        }
    }

    private fun switchTorch(torch: Boolean) {
        try {
            mTorchOn = torch
            cameraManager.setTorch(torch)
            val drawable = AppUtil.getDrawable(resources, if (torch) R.drawable.common_scan_flash_on else R.drawable.common_scan_flash_off)
            scan_flash_btn?.setImageDrawable(drawable)

        } catch (ex: Exception) {
            ALog.e(TAG, "switchTorch error", ex)
        }
    }

    @Throws(Exception::class)
    private fun decodeScanImage(path: String?): Result? {
        if (path == null || path.isEmpty()) {
            return null
        }
        var localBitmap: Bitmap? = null
        try {
            // DecodeHintType and EncodeHintType
            val hints = Hashtable<DecodeHintType, Any>()
            if (mScanCharSet != null) {
                hints[DecodeHintType.CHARACTER_SET] = mScanCharSet 

            }
//            hints[DecodeHintType.TRY_HARDER] = true
            localBitmap = BitmapUtils.compressBitmap(path, CameraManager.MAX_FRAME_WIDTH, CameraManager.MAX_FRAME_HEIGHT)

            val width = localBitmap.width
            val height = localBitmap.height

            val bmpYUVBytes = getBitmapYUVBytes(localBitmap)
            return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(bmpYUVBytes, width, height, 0, 0, width, height, false))))


        } finally {
            if (localBitmap?.isRecycled == false) {
                localBitmap.recycle()
            }
        }
    }

    private fun getBitmapYUVBytes(sourceBmp: Bitmap?): ByteArray? {
        if (null != sourceBmp) {
            val inputWidth = sourceBmp.width
            val inputHeight = sourceBmp.height
            val argb = IntArray(inputWidth * inputHeight)
            sourceBmp.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            val requireWidth = if (inputWidth % 2 == 0) inputWidth else inputWidth + 1
            val requireHeight = if (inputHeight % 2 == 0) inputHeight else inputHeight + 1
            val yuv = ByteArray(requireWidth * requireHeight * 3 / 2)
            encodeYUV420SP(yuv, argb, inputWidth, inputHeight)
            sourceBmp.recycle()
            return yuv
        }
        return null
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var Y: Int
        var U: Int
        var V: Int
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var argbIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {

                // a is not used obviously
                a = argb[argbIndex] and -0x1000000 shr 24
                R = argb[argbIndex] and 0xff0000 shr 16
                G = argb[argbIndex] and 0xff00 shr 8
                B = argb[argbIndex] and 0xff
                argbIndex++

                // well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
                V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128

                Y = Math.max(0, Math.min(Y, 255))
                U = Math.max(0, Math.min(U, 255))
                V = Math.max(0, Math.min(V, 255))

                // NV21 has a plane of Y and interleaved planes of VU each
                // sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the
                // sampling is every other
                // pixel AND every other scanline.
                // ---Y---
                yuv420sp[yIndex++] = Y.toByte()
                // ---UV---
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = V.toByte()
                    yuv420sp[uvIndex++] = U.toByte()
                }
            }
        }
    }

    private inner class AutoFocusRunnable(private val camera: Camera) : Runnable {

        private val autoFocusCallback = Camera.AutoFocusCallback { success, camera ->
            // schedule again
            cameraHandler.postDelayed(this@AutoFocusRunnable, AUTO_FOCUS_INTERVAL_MS)
        }

        override fun run() {
            try {
                camera.autoFocus(autoFocusCallback)
            } catch (x: Exception) {
                ALog.e(TAG, "problem with auto-focus, will not schedule again.  $x")
            }

        }
    }
}