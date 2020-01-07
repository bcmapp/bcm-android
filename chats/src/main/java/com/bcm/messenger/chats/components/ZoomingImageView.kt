package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.subsampling.AttachmentBitmapDecoder
import com.bcm.messenger.common.ui.subsampling.AttachmentRegionDecoder
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.CompatDecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder
import com.github.chrisbanes.photoview.PhotoView
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.pnikosis.materialishprogress.ProgressWheel
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.*
import kotlin.math.abs

/**
 *
 */
class ZoomingImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private class AttachmentBitmapDecoderFactory : DecoderFactory<AttachmentBitmapDecoder> {
        @Throws(IllegalAccessException::class, InstantiationException::class)
        override fun make(): AttachmentBitmapDecoder {
            return AttachmentBitmapDecoder(AMELogin.majorContext)
        }
    }

    private class AttachmentRegionDecoderFactory : DecoderFactory<AttachmentRegionDecoder> {
        @Throws(IllegalAccessException::class, InstantiationException::class)
        override fun make(): AttachmentRegionDecoder {
            return AttachmentRegionDecoder(AMELogin.majorContext)
        }
    }

    companion object {
        private const val TAG = "ZoomingImageView"

        private val SCAN_MAP = WeakHashMap<Any, Array<out Result>>()
    }

    private val mStandardImageView: PhotoView
    private val mHugeImageView: SubsamplingScaleImageView
    private var glideRequests: GlideRequests? = null
    private val downloadView: ProgressWheel

    private var mScanListener: ((scanResult: Array<out Result>) -> Unit)? = null

    private var mCheckDispose: Disposable? = null

    private var listener: ((v: View) -> Unit)? = null

    private var mMaxTextureSize: Int = 2048

    init {
        ALog.d(TAG, "init")
        View.inflate(context, R.layout.chats_zooming_image_view, this)

        this.mStandardImageView = findViewById(R.id.image_view)
        this.mHugeImageView = findViewById(R.id.subsampling_image_view)
//        this.mHugeImageView.setBitmapDecoderFactory(AttachmentBitmapDecoderFactory())
//        this.mHugeImageView.setRegionDecoderFactory(AttachmentRegionDecoderFactory())

        this.mHugeImageView.setOnClickListener {
            listener?.invoke(this.mHugeImageView)
        }
        this.mStandardImageView.setOnViewTapListener { _, _, _ ->
            listener?.invoke(this.mStandardImageView)
        }
        this.downloadView = findViewById(R.id.download_progress)

        this.mStandardImageView.setOnScaleChangeListener { _, _, _ ->
            //ALog.d(TAG, "OnScaleChange: ${this.mStandardImageView.scale}")
            val diff = abs(this.mStandardImageView.scale - 1.0f)
            showQRTag(diff < 0.05f)
        }

        mHugeImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
        mHugeImageView.orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
        mStandardImageView.scaleType = ImageView.ScaleType.CENTER

    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        ALog.d(TAG, "onWindowVisibilityChanged: $visibility")
        showQRTag(visibility == View.VISIBLE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val showNotice = TextSecurePreferences.getBooleanPreference(AMELogin.majorContext, TextSecurePreferences.QR_DISCERN_NOTICE, false)
        ALog.d(TAG, "onAttachedToWindow showNotice: $showNotice, isShown: $isShown")
        if (mTagList.isNotEmpty() && showNotice && this.isShown) {
            handleDefaultQrTagClick(mTagList[0], true)
        }
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        this.mStandardImageView.setOnLongClickListener(l)
        this.mHugeImageView.setOnLongClickListener(l)
    }


    fun setOnScanResultListener(callback: ((scanResult: Array<out Result>) -> Unit)?) {
        this.mScanListener = callback
    }


    fun setOnSingleTapListener(callback: (v: View) -> Unit) {
        this.listener = callback
    }


    fun setImageUri(masterSecret: MasterSecret?, glideRequests: GlideRequests,
                    uri: Uri?, contentType: String) {
        val context = context
        this.glideRequests = glideRequests
        if (uri == null) {
            return
        }

        Observable.create(ObservableOnSubscribe<Size> {
            try {
                val inputStream = if (masterSecret != null) {
                    PartAuthority.getAttachmentStream(context, masterSecret, uri)
                } else {
                    AppContextHolder.APP_CONTEXT.contentResolver.openInputStream(uri)
                }
                if (inputStream == null) {
                    it.onError(IOException("InputStream is null"))
                } else {
                    it.onNext(BitmapUtils.getImageDimensions(inputStream))
                }
            } catch (e: Throwable) {
                it.onError(e)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ size ->
                    ALog.i(TAG, "setImageUri uri: $uri, w: ${size.width}, h: ${size.height}, max: $mMaxTextureSize")
                    try {
                        if (MediaUtil.isGif(contentType)) {
                            postDelayed({
                                try {
                                    setImageScaleType(mStandardImageView, size.width, size.height)
                                    setGifUri(masterSecret, glideRequests, uri)
                                } catch (tr: Throwable) {
                                    ALog.e(TAG, "Load gif error: ${tr.message}")
                                }
                            }, 500)
                        } else if (size.width <= mMaxTextureSize && size.height <= mMaxTextureSize) {
                            setImageScaleType(mStandardImageView, size.width, size.height)
                            setStandardUri(masterSecret, glideRequests, uri, size.width, size.height)
                        } else {
                            setImageScaleType(mHugeImageView, size.width, size.height)
                            setHugeUri(masterSecret, uri)
                        }
                    } catch (ex: Exception) {
                        ALog.e(TAG, "setImageUri uri: $uri fail", ex)
                    }

                }, {
                    ALog.e(TAG, "setImageUri uri: $uri error", it)
                    stopSpinning()
                })

    }

    private fun setStandardUri(masterSecret: MasterSecret?, glideRequests: GlideRequests, uri: Uri, w: Int = Target.SIZE_ORIGINAL, h: Int = Target.SIZE_ORIGINAL) {
        mStandardImageView.visibility = View.VISIBLE
        mHugeImageView.visibility = View.GONE
        startSpinning()
        var strategy = DiskCacheStrategy.NONE
        val loadObj: Any = if (masterSecret == null) {
            uri
        } else {
            strategy = DiskCacheStrategy.NONE
            DecryptableUri(masterSecret, uri)
        }
        glideRequests.asBitmap().load(loadObj)
                .error(R.drawable.common_image_broken_img)
                .diskCacheStrategy(strategy)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        return false
                    }

                    override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        beginScanQrCode(resource, uri)
                        return false
                    }
                })
                .override(w, h)
                .into(mStandardImageView)
    }

    private fun setStandardUri(glideRequests: GlideRequests, url: String, w: Int = Target.SIZE_ORIGINAL, h: Int = Target.SIZE_ORIGINAL) {
        mStandardImageView.visibility = View.VISIBLE
        mHugeImageView.visibility = View.GONE
        startSpinning()
        glideRequests.asBitmap().load(url)
                .error(R.drawable.common_image_broken_img)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        return false
                    }

                    override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        beginScanQrCode(resource, url)
                        return false
                    }
                })
                .override(w, h)
                .into(mStandardImageView)
    }

    fun stopSpinning() {
        if (downloadView.isSpinning) {
            downloadView.stopSpinning()
        }
        downloadView.visibility = View.GONE
    }

    fun startSpinning() {
        downloadView.visibility = View.VISIBLE
        downloadView.spin()
    }

    private fun setImageScaleType(view: View, w: Int, h: Int) {
        try {
            val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            manager.defaultDisplay.getRealMetrics(metrics)
            ALog.d(TAG, "setImageScaleType w: ${metrics.widthPixels}, h: ${metrics.heightPixels}")
            if (w > metrics.widthPixels && h > metrics.heightPixels) {
                if (view is PhotoView) {
                    view.minimumScale = 0.8f
                    if (h > w) {
                        view.scaleType = ImageView.ScaleType.CENTER_CROP
                    } else {
                        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }

                } else if (view is SubsamplingScaleImageView) {
                    if (h > w) {
                        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
                    } else {
                        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                    }
                }
            } else {
                if (w < metrics.widthPixels && h < metrics.heightPixels) {
                    if (view is PhotoView) {
                        view.minimumScale = 1.0f
                        view.scaleType = ImageView.ScaleType.FIT_CENTER
                    } else if (view is SubsamplingScaleImageView) {
                        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
                    }
                } else {
                    if (view is PhotoView) {
                        view.minimumScale = 0.8f
                        if (h > w) {
                            view.scaleType = ImageView.ScaleType.CENTER
                        } else {
                            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                    } else if (view is SubsamplingScaleImageView) {
                        if (h > w) {
                            view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
                        } else {
                            view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "setStandardScaleType error", ex)
        }
    }

    private fun setHugeUri(masterSecret: MasterSecret?, uri: Uri) {
        Log.d(TAG, "setHugeUri: $uri")
        mHugeImageView.visibility = View.VISIBLE
        mStandardImageView.visibility = View.GONE
        startSpinning()
        if (masterSecret == null) {
            this.mHugeImageView.setBitmapDecoderFactory(CompatDecoderFactory(SkiaImageDecoder::class.java))
            this.mHugeImageView.setRegionDecoderFactory(CompatDecoderFactory(SkiaImageRegionDecoder::class.java))
        } else {
            this.mHugeImageView.setBitmapDecoderFactory(AttachmentBitmapDecoderFactory())
            this.mHugeImageView.setRegionDecoderFactory(AttachmentRegionDecoderFactory())
        }
        try {
            mHugeImageView.setImage(ImageSource.uri(uri))
            mHugeImageView.setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onImageLoaded() {
                    ALog.d(TAG, "onImageLoaded")
                    stopSpinning()
                    mHugeImageView.visibility = View.VISIBLE
                    mStandardImageView.visibility = View.GONE
                }

                override fun onReady() {
                    ALog.d(TAG, "onReady")
                }

                override fun onTileLoadError(p0: java.lang.Exception?) {
                    ALog.e(TAG, "onTileLoadError", p0)
                    stopSpinning()
                }

                override fun onPreviewReleased() {
                }

                override fun onImageLoadError(p0: java.lang.Exception?) {
                    ALog.e(TAG, "onImageLoadError", p0)
                    stopSpinning()
                }

                override fun onPreviewLoadError(p0: java.lang.Exception?) {
                }
            })
        } catch (e: Throwable) {
            ALog.e(TAG, "load huge uri error", e)
        }
    }

    private fun setGifUri(masterSecret: MasterSecret?, glideRequests: GlideRequests, uri: Uri) {

        mStandardImageView.visibility = View.VISIBLE
        mHugeImageView.visibility = View.GONE
        if (null != masterSecret) {
            glideRequests.asGif().load(DecryptableUri(masterSecret, uri))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(R.drawable.common_image_broken_img)
                    .listener(object : RequestListener<GifDrawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                            stopSpinning()
                            return false
                        }

                        override fun onResourceReady(resource: GifDrawable?, model: Any?, target: Target<GifDrawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            stopSpinning()
                            return false
                        }
                    })
                    .into(mStandardImageView)
        } else {
            glideRequests.asGif().load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.common_image_broken_img)
                    .listener(object : RequestListener<GifDrawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                            stopSpinning()
                            return false
                        }

                        override fun onResourceReady(resource: GifDrawable?, model: Any?, target: Target<GifDrawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            stopSpinning()
                            return false
                        }
                    })
                    .into(mStandardImageView)
        }
    }

    private fun setGifUri(glideRequests: GlideRequests, url: String) {
        mStandardImageView.visibility = View.VISIBLE
        mHugeImageView.visibility = View.GONE
        glideRequests.asGif().load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.drawable.common_image_broken_img)
                .listener(object : RequestListener<GifDrawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        return false
                    }

                    override fun onResourceReady(resource: GifDrawable?, model: Any?, target: Target<GifDrawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        stopSpinning()
                        return false
                    }
                })
                .into(mStandardImageView)
    }

    fun cleanup() {
        ALog.i(TAG, "cleanup")
        stopSpinning()
        mStandardImageView.setImageDrawable(null)
        mHugeImageView.recycle()
        clearQRTag()
        if (mCheckDispose?.isDisposed == false) {
            mCheckDispose?.dispose()
        }
    }

    private fun beginScanQrCode(bitmap: Bitmap?, loadObj: Any) {

        var showNotice = false
        if (bitmap == null) {
            mScanListener?.invoke(emptyArray())
            clearQRTag()
            return
        }

        val scanResults = SCAN_MAP[loadObj]
        if (scanResults == null) {
            mCheckDispose?.dispose()
            mCheckDispose = Observable.create(ObservableOnSubscribe<Array<Result>> {

                try {
                    showNotice = TextSecurePreferences.getBooleanPreference(AMELogin.majorContext, TextSecurePreferences.QR_DISCERN_NOTICE, false)
                    ALog.d(TAG, "beginScanQrCode")
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val hints = Hashtable<DecodeHintType, Any>()
                    hints[DecodeHintType.TRY_HARDER] = BarcodeFormat.QR_CODE
                    hints[DecodeHintType.CHARACTER_SET] = "utf-8"
                    val results = QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))), hints)
                    it.onNext(results)

                } catch (ex: Exception) {
                    it.onError(ex)
                } finally {
                    it.onComplete()
                }

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        SCAN_MAP[loadObj] = it
                        mScanListener?.invoke(it)
                        addQRTag(bitmap, it)

                        if (mTagList.isNotEmpty() && showNotice && this.isShown) {
                            handleDefaultQrTagClick(mTagList[0], true)
                        }

                    }, {
                        ALog.e(TAG, "beginCheckHasBarcode fail", it)
                        mScanListener?.invoke(emptyArray())
                        addQRTag(bitmap, null)
                    })

        } else {
            ALog.i(TAG, "already exist qr data")
            mScanListener?.invoke(scanResults)
            addQRTag(bitmap, scanResults)
        }
    }

    private var mTagClickListener: ((qrTag: QRTagBean) -> Unit)? = null
    private val mTagList = mutableListOf<QRTagBean>()

    private var mOriginWidth = 0
    private var mOriginHeight = 0

    fun setOnQrTagClickListener(listener: ((qrTag: QRTagBean) -> Unit)?) {
        mTagClickListener = listener
    }


    private fun addQRTag(bitmap: Bitmap, results: Array<out Result>?) {

        clearQRTag()
        if (results?.isNotEmpty() == true) {

            val tm = 24.dp2Px()
            val dm = context.resources.displayMetrics
            val halfTm = tm / 2.0f
            mOriginWidth = bitmap.width
            mOriginHeight = bitmap.height
            val horizontal = (dm.widthPixels - mOriginWidth) / 2.0f
            val vertical = (dm.heightPixels - mOriginHeight) / 2.0f

            for ((index, result) in results.withIndex()) {
                val rect = findRect(result.resultPoints)
                val sx = if (rect.right + horizontal + halfTm > dm.widthPixels) {
                    (dm.widthPixels - tm).toFloat()
                } else {
                    rect.right + horizontal - halfTm
                }
                val sy = if (rect.top + vertical - halfTm < 0) {
                    rect.bottom + vertical - halfTm
                } else {
                    rect.top + vertical - halfTm
                }

                val icon = QRTagView(context)
                val layoutParams = LayoutParams(tm, tm)
                layoutParams.setMargins(sx.toInt(), sy.toInt(), 0, 0)
                ALog.d(TAG, "addQRTag sx: $sx, sy: $sy")
                val tag = QRTagBean(index, sx, sy, icon, result)
                icon.setOnClickListener {
                    mTagClickListener?.invoke(tag) ?: handleDefaultQrTagClick(tag, false)
                }
                addView(icon, layoutParams)
                icon.start(0.25f, 800, 1500)
                mTagList.add(tag)
            }

        } else {
            ALog.w(TAG, "addQRTag fail, results is empty")
        }
    }

    private fun showQRTag(show: Boolean) {
        ALog.d(TAG, "showQRTag show: $show, tagList: ${mTagList.size}")
        for (tag in mTagList) {
            tag.view.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun clearQRTag() {
        ALog.d(TAG, "clearQRTag")
        for (tag in mTagList) {
            removeView(tag.view)
        }
        mTagList.clear()
    }

    private fun findRect(resultPoints: Array<out ResultPoint>): RectF {
        val result = RectF(resultPoints[1].x, resultPoints[1].y, resultPoints[2].x, resultPoints[3].y)
        result.inset(-result.width() * 0.2f, -result.height() * 0.2f)
        return result
    }

    private fun handleDefaultQrTagClick(tag: QRTagBean, popupAutoDismiss: Boolean) {
        TextSecurePreferences.setBooleanPreference(AMELogin.majorContext, TextSecurePreferences.QR_DISCERN_NOTICE, false)
        QrCodeReaderPopWindow.createPopup(context, tag.view, tag, popupAutoDismiss) {
            if (it) {
                doQrDiscern(context, tag.data)
            }
        }
    }

    private fun doQrDiscern(context: Context, result: Result) {
        ALog.d(TAG, "doQrDiscern result: ${result.text}")
        AmeModuleCenter.contact(AMELogin.majorContext)?.discernScanData(context, result.text)
    }

    fun isZooming(): Boolean {

        if (mHugeImageView.visibility == View.VISIBLE) {
            return mHugeImageView.scale > mHugeImageView.minScale
        } else if (mStandardImageView.visibility == View.VISIBLE) {
            return mStandardImageView.scale > 1.0f || (mStandardImageView.top - mStandardImageView.displayRect.top) > 1.0f
        }
        return false
    }

    class QRTagBean(var id: Int//index, from zero
                    , var sx: Float
                    , var sy: Float
                    , var view: View
                    , var data: Result
    )

}
