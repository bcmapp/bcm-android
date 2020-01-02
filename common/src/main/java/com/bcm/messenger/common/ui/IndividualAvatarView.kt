package com.bcm.messenger.common.ui

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.util.*
import kotlin.math.min

/**
 *
 * 
 *
 * Created by wjh on 2018/3/12
 */
class IndividualAvatarView : CardView {

    companion object {

        const val TAG = "IndividualAvatarView"

        const val DEFAULT_PHOTO_TYPE = 0//
        const val LOCAL_PHOTO_TYPE = 2
        const val PROFILE_PHOTO_TYPE = 3
        const val KEYBOX_PHOTO_TYPE = 4//
        const val NAME_CARD_TYPE = 5//

        //
        private val portraitMap by lazy {
            val map = hashMapOf<String, String>()
            for ((i, char) in ('A'..'Z').withIndex()) {
                val index = if (i < 20) i + 21 else i + 1
                map[char.toString()] = String.format(Locale.US, "${BuildConfig.DOWNLOAD_URL}/default/portrait_%03d.png", index)
            }
            for ((i, char) in ('a'..'z').withIndex()) {
                val index = if (i < 20) i + 21 else i + 1
                map[char.toString()] = String.format(Locale.US, "${BuildConfig.DOWNLOAD_URL}/default/portrait_%03d.png", index)
            }
            for (i in 0 until 10) {
                map[i.toString()] = String.format(Locale.US, "${BuildConfig.DOWNLOAD_URL}/default/portrait_%03d.png", i + 21)
            }
            map["#"] = "${BuildConfig.DOWNLOAD_URL}/default/portrait_001.png"
            return@lazy map
        }

        /**
         * 
         */
        fun getLocalAvatarObj(recipient: Recipient?): Any? {
            val avatar = recipient?.localAvatar
            return if (avatar == null || avatar.isEmpty()) {
                null
            } else {
                Uri.parse(avatar)
            }
        }

        /**
         * 
         */
        fun getAvatarThumbnailUrl(recipient: Recipient?, width: Int? = null, height: Int? = null): String? {
            //
            val fullAvatar = if (recipient?.isGroupRecipient == true) {
                return recipient.profileAvatar
            } else if (recipient == null) {
                null
            } else {
                val avatar = recipient.profileAvatar
                if (avatar.isNullOrEmpty()) {
                    null
                } else {
                    if (avatar.startsWith(ContentResolver.SCHEME_FILE)) {
                        return avatar
                    } else {
                        BcmHttpApiHelper.getDownloadApi("/avatar/" + recipient.profileAvatar)
                    }
                }
            }
            return getAvatarThumbnailUrl(fullAvatar, width, height)
        }

        /**
         * 
         */
        fun getAvatarThumbnailUrl(avatar: String?, width: Int? = null, height: Int? = null): String? {
            try {
                if (avatar.isNullOrEmpty()) {
                    return null
                }

                return if (width == null && height == null) {
                    avatar
                } else {
                    avatar + "?ips_thumbnail/3/w/" + (width ?: 0) + "/h/" + (height ?: 0)
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "getAvatarThumbnailUrl error", ex)
            }
            return null
        }

        /**
         * 
         */
        fun getDefaultBackgroundDrawable(): Drawable {
            return ColorDrawable(Color.parseColor("#5F768C"))
        }

        /**
         * 
         */
        fun getDefaultPortraitUrl(recipient: Recipient?): String {
            val address = recipient?.address?.serialize()
            val char = address?.substring(address.length - 1, address.length) ?: "#"
            return portraitMap[char] ?: portraitMap["#"]!!
        }

        /**
         * 
         * @param letter 
         * @param size 
         * @param color 
         * @param background 
         */
        fun createConvertText(resources: Resources, letter: String, size: Int, fontSize: Int, color: Int, background: Int): BitmapDrawable {

            val newBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(newBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = fontSize.toFloat()
            paint.color = color
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawColor(background)
            canvas.drawText(letter, size / 2f, size / 2f - ((paint.descent() + paint.ascent()) / 2), paint)
            canvas.save()
            canvas.restore()
            return BitmapDrawable(resources, newBitmap)
        }

        /**
         * 
         */
        fun createCoverText(resources: Resources, bitmap: Bitmap, letter: String, textColor: Int): BitmapDrawable {
            val width = bitmap.width
            val height = bitmap.height
            val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(newBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textSize = min(width / 3f, height / 3f)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = textSize
            paint.color = textColor
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            canvas.drawText(letter, width / 2f, height / 2f - ((paint.descent() + paint.ascent()) / 2), paint)
            canvas.save()
            canvas.restore()
            return BitmapDrawable(resources, newBitmap)
        }
    }

    interface RecipientPhotoCallback {
        fun onLoaded(recipient: Recipient?, bitmap: Bitmap?, success: Boolean)
    }

    /**
     */
    private var mTextAppearance: Pair<Int, Int>

    private var mCurrentRecipient: Recipient? = null//
    private var mCurrentRequestObj: Any? = null//
    private var mCurrentText: String? = null //

    /**
     * 
     */
    private var mRequestManager: GlideRequests? = null

    /**
     * 
     */
    private var mWaitingRunnable: Runnable? = null

    private var mOval: Boolean = false
    private var mRadius: Float = 0.0F
    private var needLetter = false

    private var mCallback: RecipientPhotoCallback? = null

    private var mImageView: ImageView? = null
    private var mTextView: TextView? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonRecipientPhotoViewStyle)
        val photoNameSize = typedArray.getDimensionPixelSize(R.styleable.CommonRecipientPhotoViewStyle_recipient_photo_name_size, 0)
        val photoNameColor = typedArray.getColor(R.styleable.CommonRecipientPhotoViewStyle_recipient_photo_name_color, Color.WHITE)
        val photoRadius = typedArray.getDimensionPixelSize(R.styleable.CommonRecipientPhotoViewStyle_recipient_photo_radius, 0)
        val isOval = typedArray.getBoolean(R.styleable.CommonRecipientPhotoViewStyle_recipient_photo_oval, true)
        val photoResource = typedArray.getResourceId(R.styleable.CommonRecipientPhotoViewStyle_recipient_photo_custom_img, 0)

        typedArray.recycle()

        mTextAppearance = Pair(photoNameSize, photoNameColor)

        mImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(photoResource)
            addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            })
        }
        mTextView = EmojiTextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(photoNameColor)
            textSize = photoNameSize.toFloat()
            typeface = Typeface.DEFAULT_BOLD
            addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            })
        }
        cardElevation = 0.0F

        var lp = layoutParams
        if (lp == null) {
            lp = LayoutParams(50.dp2Px(), 50.dp2Px())
            layoutParams = lp
        }

        if (photoRadius > 0) {
            radius = photoRadius.toFloat()
        } else if (isOval) {
            mOval = isOval
            updateStyle()
        }
    }


    /**
     * 
     */
    fun setCallback(callback: RecipientPhotoCallback?) {
        mCallback = callback
    }

    /**
     * 
     * @param size 
     * @param color 
     */
    fun setNameAppearance(size: Int, color: Int) {
        mTextAppearance = Pair(size, color)
    }

    fun setOval(oval: Boolean) {
        if (mOval != oval) {
            mOval = oval
            updateStyle()
        }
    }

    /**
     * 
     */
    private fun clearArgument() {
        mCurrentRequestObj = null
        mCurrentRecipient = null
        mCurrentText = null
        clearText()
    }

    override fun setRadius(radius: Float) {
        if (mRadius != radius) {
            mOval = false
            mRadius = radius
            super.setRadius(radius)
        }
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        super.setLayoutParams(params)
        if (mOval) {
            updateStyle()
        }
    }

    override fun setBackground(background: Drawable?) {
        mImageView?.background = background
        clearArgument()
    }

    fun getPhoto(): Bitmap? {
        try {
            val drawable = mImageView?.drawable as? BitmapDrawable
            return drawable?.bitmap

        } catch (ex: Exception) {
            ALog.e(TAG, "getPhoto error", ex)
        }
        return null
    }

    fun setPhoto(drawable: Drawable) {
        mImageView?.setImageDrawable(drawable)
        clearArgument()
    }

    fun setPhoto(bitmap: Bitmap) {
        mImageView?.setImageBitmap(bitmap)
        clearArgument()
    }

    fun setPhoto(@DrawableRes id: Int) {
        mImageView?.setImageResource(id)
        clearArgument()
    }

    /**
     * 
     */
    fun setPhoto(accountContext: AccountContext, recipient: Recipient?, alterName: String?, photoType: Int) {
        try {
            if (mRequestManager == null) {
                mRequestManager = GlideApp.with(AppContextHolder.APP_CONTEXT)
            }
            requestPhoto(accountContext,mRequestManager ?: return, recipient, alterName, photoType)

        } catch (ex: Exception) {
            ALog.e(TAG, "setPhoto error", ex)
        }
    }

    /**
     * 
     * @param recipient
     */
    fun setPhoto(accountContext: AccountContext, recipient: Recipient?) {
        try {
            if (mRequestManager == null) {
                mRequestManager = GlideApp.with(AppContextHolder.APP_CONTEXT)
            }
            requestPhoto(accountContext,mRequestManager ?: return, recipient, null)

        } catch (ex: Exception) {
            ALog.e(TAG, "setPhoto error", ex)
        }
    }

    /**
     * 
     * @param recipient
     * @param photoType
     */
    fun setPhoto(accountContext: AccountContext, recipient: Recipient?, photoType: Int) {
        try {
            if (mRequestManager == null) {
                mRequestManager = GlideApp.with(AppContextHolder.APP_CONTEXT)
            }
            requestPhoto(accountContext, mRequestManager ?: return, recipient, null, photoType)

        } catch (ex: Exception) {
            ALog.e(TAG, "setPhoto error", ex)
        }
    }

    /**
     * 
     */
    fun requestPhoto(photoObj: Any?, placeHolderResource: Int, errorHolderResource: Int) {
        if (mCurrentRequestObj == photoObj) {
            return
        }
        if (mRequestManager == null) {
            mRequestManager = GlideApp.with(AppContextHolder.APP_CONTEXT)
        }
        val requestManager = mRequestManager ?: return
        clearArgument()
        mCurrentRequestObj = photoObj
        val diskCacheStrategy = if (photoObj is String && BcmFileUtils.isExist(photoObj)) {
            DiskCacheStrategy.NONE
        } else {
            DiskCacheStrategy.ALL
        }
        mImageView?.let {
            it.background = null
            requestManager
                    .load(photoObj)
                    .placeholder(placeHolderResource)
                    .error(errorHolderResource)
                    .diskCacheStrategy(diskCacheStrategy)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            if (mCurrentRequestObj == photoObj) {
                                isDrawingCacheEnabled = true
                                buildDrawingCache()
                                mCallback?.onLoaded(null, drawingCache, false)
                            }
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            if (mCurrentRequestObj == photoObj) {
                                mCallback?.onLoaded(null, (resource as? BitmapDrawable)?.bitmap, true)
                            }
                            return false
                        }

                    })
                    .into(it)
        }
    }

    private fun updateStyle() {
        if (layoutParams.width > 0 && layoutParams.height > 0) {
            updateStyle(getCurrentSize())
        } else {
            post {
                updateStyle(getCurrentSize())
            }
        }
    }

    private fun updateStyle(size: Int) {
        super.setRadius(if (mOval) {
            size / 2.0F
        } else {
            mRadius
        })
    }

    private fun getTextSize(size: Int): Float {
        return if (mTextAppearance.first > 0) {
            mTextAppearance.first.toFloat()
        } else {
            size - size * 3.0f / 5.0f
        }
    }

    /**
     * 
     */
    private fun clearText() {
        mTextView?.visibility = View.GONE
    }

    private fun updateText(text: CharSequence?, textSizePx: Float) {
        mTextView?.visibility = View.VISIBLE
        mTextView?.text = text
        mTextView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    }

    private fun getCurrentSize(): Int {
        return if (layoutParams.height > 0 && layoutParams.width > 0) {
            min(layoutParams.height, layoutParams.width)
        } else {
            min(height, width)
        }
    }

    /**
     * 
     */
    private fun requestPhoto(accountContext: AccountContext, requestManager: GlideRequests, recipient: Recipient?, alterName: String?, photoType: Int = DEFAULT_PHOTO_TYPE) {
        val size = getCurrentSize()
        //
        if (size <= 0) {
            if (mWaitingRunnable != null) {
                removeCallbacks(mWaitingRunnable)
            }
            mWaitingRunnable = Runnable {
                try {
                    requestPhoto(accountContext, requestManager, recipient, alterName, photoType)
                } catch (ex: Exception) {
                    ALog.e(TAG, "requestPhoto error", ex)
                }
            }
            post(mWaitingRunnable)
            return
        }

        var needLetter = false //
        val desireHD = size > PrivacyProfile.getMaxLDSize()
        val photoObj = when (photoType) {

            KEYBOX_PHOTO_TYPE -> {
                if (!recipient?.getPrivacyAvatar(desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(desireHD)
                } else if (!recipient?.getPrivacyAvatar(!desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(!desireHD)
                } else if (!recipient?.profileAvatar.isNullOrEmpty()) {
                    getAvatarThumbnailUrl(recipient, size, size)
                } else {
                    needLetter = true
                    getDefaultPortraitUrl(recipient)
                }
            }
            LOCAL_PHOTO_TYPE -> {
                if (!recipient?.localAvatar.isNullOrEmpty()) {
                    recipient?.localAvatar
                } else {
                    needLetter = true
                    getDefaultPortraitUrl(recipient)
                }
            }
            PROFILE_PHOTO_TYPE -> {
                if (!recipient?.getPrivacyAvatar(desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(desireHD)
                } else if (!recipient?.getPrivacyAvatar(!desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(!desireHD)
                } else if (!recipient?.profileAvatar.isNullOrEmpty()) {
                    getAvatarThumbnailUrl(recipient, size, size)
                } else {
                    needLetter = true
                    getDefaultPortraitUrl(recipient)
                }
            }
            else -> {
                if (!recipient?.localAvatar.isNullOrEmpty()) {
                    recipient?.localAvatar
                } else if (!recipient?.getPrivacyAvatar(desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(desireHD)
                } else if (!recipient?.getPrivacyAvatar(!desireHD).isNullOrEmpty()) {
                    recipient?.getPrivacyAvatar(!desireHD)
                } else if (!recipient?.profileAvatar.isNullOrEmpty()) {
                    getAvatarThumbnailUrl(recipient, size, size)
                } else {
                    needLetter = true
                    getDefaultPortraitUrl(recipient)
                }
            }
        } ?: ""

        val name = if (!alterName.isNullOrEmpty()) {
            StringAppearanceUtil.getFirstCharacter(alterName)
        } else {
            val n = when (photoType) {
                LOCAL_PHOTO_TYPE -> recipient?.localName
                PROFILE_PHOTO_TYPE -> recipient?.bcmName
                KEYBOX_PHOTO_TYPE -> recipient?.bcmName
                else -> recipient?.name
            }
            StringAppearanceUtil.getFirstCharacter(n ?: recipient?.address?.format()
            ?: Recipient.UNKNOWN_LETTER)
        }
        if (mCurrentRecipient == recipient && mCurrentText == name && photoObj == mCurrentRequestObj) { //
            ALog.d(TAG, "recipient: ${recipient?.address?.serialize()} same, not update avatar, photoObj: $photoObj")
            return
        }
        mCurrentRecipient = recipient
        mCurrentText = name
        mCurrentRequestObj = photoObj

        ALog.d(TAG, "requestPhoto begin: ${recipient?.address?.serialize()}, photoObj: $photoObj, photoType: $photoType")
        updateText(name, getTextSize(size))
        updateStyle(size)
        val strategy = if (photoObj.startsWith(ContentResolver.SCHEME_FILE)) {
            DiskCacheStrategy.NONE
        } else {
            DiskCacheStrategy.ALL
        }
        this.needLetter = needLetter

        mImageView?.let {
            it.background = null
            requestManager
                    .load(photoObj)
                    .diskCacheStrategy(strategy)
                    .placeholder(ColorDrawable(Color.parseColor("#5F768C")))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            ALog.d(TAG, "requestPhoto fail recipient: ${recipient?.address}, current: ${mCurrentRecipient?.address}, photoObj: $photoObj, photoType: $photoType")
                            if (mCurrentRecipient == recipient) {
                                isDrawingCacheEnabled = true
                                buildDrawingCache()
                                mCallback?.onLoaded(recipient, drawingCache, false)
                            }
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            ALog.d(TAG, "requestPhoto success recipient: ${recipient?.address}, current: ${mCurrentRecipient?.address}, photoObj: $photoObj, photoType: $photoType")
                            if (mCurrentRecipient == recipient) {
                                if (needLetter) {
                                    updateText(name, getTextSize(size))
                                } else {
                                    clearText()
                                }
                                mCallback?.onLoaded(recipient, (resource as? BitmapDrawable)?.bitmap, true)
                            }
                            return false
                        }
                    })
                    .into(it)
        }

        val contactModule = AmeModuleCenter.contact(accountContext)
        if (recipient?.needRefreshProfile() == true) {
            contactModule?.checkNeedFetchProfile(recipient, callback = object : IContactModule.IProfileCallback {
                override fun onDone(recipient: Recipient, viaJob: Boolean) {
                    val avatar = recipient.getPrivacyAvatar(desireHD)
                    if (avatar.isNullOrEmpty()) {
                        ALog.i(TAG, "requestPhoto check need download Avatar desireHD: $desireHD, size: $size")
                        contactModule.checkNeedDownloadAvatar(desireHD, recipient)
                    }
                }
            })
        } else if (recipient != null) {
            val avatar = recipient.getPrivacyAvatar(desireHD)
            if (avatar.isNullOrEmpty()) {
                ALog.i(TAG, "requestPhoto check need download Avatar desireHD: $desireHD, size: $size")
                contactModule?.checkNeedDownloadAvatar(desireHD, recipient)
            }
        }
    }

    fun showCoverText() {
        if (needLetter) {
            mTextView?.visibility = View.VISIBLE
            mTextView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize(getCurrentSize()))
        }
    }

    fun hideCoverText() {
        mTextView?.visibility = View.GONE
    }
}