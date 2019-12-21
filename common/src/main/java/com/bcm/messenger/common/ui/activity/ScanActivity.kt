package com.bcm.messenger.common.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.R
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.IUserModule
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.scan.CameraManager
import com.bcm.messenger.common.ui.scan.ScannerView
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.api.BcmRouter
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.bcm_scan_activity.*
import java.util.*

/**
 * 
 * Created by wjh on 2018/06/06
 */
class ScanActivity : SwipeBaseActivity(), TextureView.SurfaceTextureListener, ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        private const val TAG = "ScanActivity"

        const val REQUEST_SCAN_CODE_PICK = 1000

        const val INTENT_EXTRA_RESULT = "result"
        const val INTENT_EXTRA_CHARSET = "scan_charset"
        const val INTENT_EXTRA_TIP = "scan_tip"
        const val INTENT_EXTRA_MYCODE = "scan_mycode"
        const val INTENT_EXTRA_TITLE = "scan_title"

        const val INTENT_EXTRA_BACKUP = "scan_backup"//

        private const val VIBRATE_DURATION = 50L
        private const val AUTO_FOCUS_INTERVAL_MS = 2500L

    }

    private val cameraManager = CameraManager()

    private lateinit var mRootView: View
    private lateinit var scannerView: ScannerView
    private lateinit var previewView: TextureView

    @Volatile
    private var surfaceCreated = false

    private var vibrator: Vibrator? = null
    private lateinit var cameraThread: HandlerThread

    @Volatile
    private lateinit var cameraHandler: Handler

    /**
     * 
     */
    private var mScanCharSet: String? = null

    /**
     * flash on
     */
    private var mTorchOn = false

    /**
     * my code
     */
    private var mShowMyCode = false

    /**
     * 
     */
    private val openRunnable = object : Runnable {
        override fun run() {
            try {
                val camera = cameraManager.open(previewView, displayRotation())

                val framingRect = cameraManager.frame
                val framingRectInPreview = RectF(cameraManager.framePreview)
                framingRectInPreview.offsetTo(0f, 0f)
                val cameraFlip = cameraManager.facing == CameraInfo.CAMERA_FACING_FRONT
                val cameraRotation = cameraManager.orientation

                runOnUiThread {
                    scannerView.setFraming(framingRect, framingRectInPreview, displayRotation(), cameraRotation,
                            cameraFlip)
                    val tip = intent?.getStringExtra(INTENT_EXTRA_TIP)
                    if (tip != null) {
                        scannerView.setScanTip(tip)
                    }

//                    val location = IntArray(2)
//                    scan_tool_layout.scrollTo(framingRectInPreview.left.toInt(), framingRectInPreview.top.toInt() + framingRectInPreview.height().toInt())
                    scan_tool_layout.visibility = View.VISIBLE
                }

                val focusMode = camera.parameters.focusMode
                val nonContinuousAutoFocus = Camera.Parameters.FOCUS_MODE_AUTO == focusMode || Camera.Parameters.FOCUS_MODE_MACRO == focusMode

                if (nonContinuousAutoFocus) {
                    cameraHandler.post(AutoFocusRunnable(camera))
                }

                cameraHandler.post(fetchAndDecodeRunnable)
                runOnUiThread {
                    scan_flash_btn.isEnabled = true
                }

            } catch (x: Exception) {
                ALog.e(TAG, "problem opening camera, ${x.message}")
            }

        }

        private fun displayRotation(): Int {
            val rotation = windowManager.defaultDisplay.rotation
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

    /**
     * 
     */
    private val closeRunnable = Runnable {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraManager.close()
        runOnUiThread {
            scan_flash_btn.isEnabled = false
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
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = ResultPointCallback { dot -> runOnUiThread { scannerView.addDot(dot) } }
                if (mScanCharSet != null) {
                    hints[DecodeHintType.CHARACTER_SET] = mScanCharSet
                }
                val scanResult = reader.decode(bitmap, hints)

                runOnUiThread { handleResult(scanResult) }
            } catch (x: ReaderException) {
                // retry
                cameraHandler.post(this)
            } finally {
                reader.reset()
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SCAN_CODE_PICK && resultCode == Activity.RESULT_OK) {
            AmePopup.loading.show(this)
            Observable.create(ObservableOnSubscribe<Result> {

                try {
                    val result = decodeScanImage(BcmFileUtils.getFileAbsolutePath(this, data?.data))
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
                        AmePopup.loading.dismiss()
                    }
                    .subscribe({
                        handleResult(it)
                    }, {
                        AmePopup.result.failure(this, getString(R.string.common_scan_parse_qr_fail))
                    })
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setBackgroundDrawable(AppUtil.getDrawable(resources, R.color.common_color_black))

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Stick to the orientation the activity was started with. We cannot declare this in the
        // AndroidManifest.xml, because it's not allowed in combination with the windowIsTranslucent=true
        // theme attribute.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        setContentView(R.layout.bcm_scan_activity)
        scan_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                openAlbum()
            }
        })

        mRootView = findViewById(android.R.id.content)
        scannerView = findViewById(R.id.scan_activity_mask)
        previewView = findViewById(R.id.scan_activity_preview)
        previewView.surfaceTextureListener = this

        cameraThread = HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND)
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)

        if (intent.hasExtra(INTENT_EXTRA_CHARSET)) {
            mScanCharSet = intent.getStringExtra(INTENT_EXTRA_CHARSET)
        }
        if (intent.hasExtra(INTENT_EXTRA_MYCODE)) {
            mShowMyCode = intent.getBooleanExtra(INTENT_EXTRA_MYCODE, false)
        }

        if (intent.hasExtra(INTENT_EXTRA_TITLE)) {
            scan_title_bar.setCenterText(intent.getStringExtra(INTENT_EXTRA_TITLE))
        }
        if (intent.getBooleanExtra(INTENT_EXTRA_BACKUP, false)) {
            val imageView = ImageView(this)
            imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.common_info_icon)
            val padding = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
            imageView.setPadding(padding, 0, padding, 0)
            scan_title_bar.setRightIcon(R.drawable.common_info_icon)
            imageView.setOnClickListener {
                openBackupInfo()
            }
        } else {
            val textView = TextView(this)
            textView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val padding = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
            textView.setPadding(padding, 0, padding, 0)
            textView.textSize = 15f
            textView.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            textView.setText(R.string.common_scan_album)
            textView.gravity = Gravity.CENTER
            scan_title_bar.setRightText(getString(R.string.common_scan_album))
            textView.setOnClickListener {
                openAlbum()
            }
        }

        PermissionUtil.checkCamera(this) { granted ->
            if (granted) {
                maybeOpenCamera()
            } else {
                finish()
            }
        }

        initView()
    }

    private fun initView() {

        scan_flash_btn.setOnClickListener {
            switchTorch(!mTorchOn)
        }
        scan_qr_btn.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.ME_QR).navigation(this)
        }
        switchTorch(mTorchOn)

        val codeDrawable = AppUtil.getDrawable(resources, R.drawable.common_scan_my_qr)
        codeDrawable.setBounds(0, 0, AppUtil.dp2Px(resources, 36), AppUtil.dp2Px(resources, 36))
        scan_qr_btn.setCompoundDrawables(null, codeDrawable, null, null)

        scan_qr_btn.visibility = if (mShowMyCode) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        maybeOpenCamera()
    }

    override fun onPause() {
        super.onPause()
        cameraHandler.post(closeRunnable)
    }

    override fun onDestroy() {
        // cancel background thread
        cameraHandler.removeCallbacksAndMessages(null)
        cameraThread.quit()

        previewView.surfaceTextureListener = null

        // We're removing the requested orientation because if we don't, somehow the requested orientation is
        // bleeding through to the calling activity, forcing it into a locked state until it is restarted.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroy()
    }

    private fun maybeOpenCamera() {
        if (surfaceCreated && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraHandler.post(openRunnable)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceCreated = true
        maybeOpenCamera()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceCreated = false
        return true
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setShowWhenLocked(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setShowWhenLocked(false)
    }

    override fun onBackPressed() {
        scannerView.visibility = View.GONE
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA ->
                // don't launch camera app
                return true
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                cameraHandler.post { cameraManager.setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP) }
                return true
            }
            else -> {
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 
     * @param scanResult
     */
    fun handleResult(scanResult: Result) {
        vibrator?.vibrate(VIBRATE_DURATION)

        scannerView.setIsResult(true)

        val result = Intent()
        ALog.d(TAG, "scan handle result:${scanResult.text}")
        result.putExtra(INTENT_EXTRA_RESULT, scanResult.text)
        setResult(Activity.RESULT_OK, result)
        postFinish()
    }

    private fun postFinish() {
        Handler().postDelayed({ finish() }, 50)
    }

    /**
     * 
     */
    private fun openBackupInfo() {
        val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
        userProvider.gotoBackupTutorial()
    }

    /**
     * 
     */
    private fun openAlbum() {

        PermissionUtil.checkStorage(this) { granted ->
            if (granted) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                val wrapperIntent = Intent.createChooser(intent, getString(R.string.common_scan_choose_qr_title))
                startActivityForResult(wrapperIntent, REQUEST_SCAN_CODE_PICK)
            }
        }
    }

    /**
     * 
     */
    private fun switchTorch(torch: Boolean) {
        try {

            mTorchOn = torch
            cameraManager.setTorch(torch)
            val drawable = AppUtil.getDrawable(resources, if (torch) R.drawable.common_scan_flash_on else R.drawable.common_scan_flash_off)
            val size = AppUtil.dp2Px(resources, 36)
            drawable.setBounds(0, 0, size, size)
            scan_flash_btn.setCompoundDrawables(null, drawable, null, null)

        } catch (ex: Exception) {
            ALog.e(TAG, "switchTorch error", ex)
        }
    }

    /**
     * 
     * @param path 
     * @return
     */
    @Throws(Exception::class)
    private fun decodeScanImage(path: String?): Result? {
        if (path == null || path.isEmpty()) {
            return null
        }
        var localBitmap: Bitmap? = null
        try {
            //，
            // DecodeHintType EncodeHintType
            val hints = Hashtable<DecodeHintType, Any>()
            if (mScanCharSet != null) {
                hints[DecodeHintType.CHARACTER_SET] = mScanCharSet // 

            }
//            hints[DecodeHintType.TRY_HARDER] = true
            localBitmap = BitmapUtils.compressBitmap(path, CameraManager.MAX_FRAME_WIDTH, CameraManager.MAX_FRAME_HEIGHT)

            val width = localBitmap.width
            val height = localBitmap.height

            // YUVRGB，
            val bmpYUVBytes = getBitmapYUVBytes(localBitmap)
            return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(bmpYUVBytes, width, height, 0, 0, width, height, false))))


        } finally {
            //bitmap
            if (localBitmap?.isRecycled == false) {
                localBitmap.recycle()
            }
        }
    }

    /**
     * bitmapYUV
     *
     * @param sourceBmp bitmap
     * @return yuv
     */
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

    /**
     * bitmapargbyuv420sp
     * yuv420spMediaCodec, AvcEncoder
     *
     * @param yuv420sp yuv429sp
     * @param argb     argb
     * @param width    bmpWidth
     * @param height   bmpHeight
     */
    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        // 
        val frameSize = width * height
        // Yindex0
        var yIndex = 0
        // UVindexframeSize
        var uvIndex = frameSize
        // YUV, ARGB
        var Y: Int
        var U: Int
        var V: Int
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var argbIndex = 0
        // ---，RGBYUV---
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
