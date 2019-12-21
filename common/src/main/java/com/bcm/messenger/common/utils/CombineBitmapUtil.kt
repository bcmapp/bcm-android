package com.bcm.messenger.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.bcm.messenger.common.R
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.reactivex.Observable

/**
 * 
 */
object CombineBitmapUtil {

    private val TAG = "CombineBitmapUtil"

    interface BitmapUnit {
        fun toBitmap():Observable<Bitmap>
    }

    class CharacterBitmapUnit(val letter: String, val url: String): BitmapUnit {
        override fun toBitmap(): Observable<Bitmap> {
            return Observable.create<Bitmap> {
                val background = try {
                    GlideApp.with(AppContextHolder.APP_CONTEXT).asBitmap().load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .error(IndividualAvatarView.getDefaultBackgroundDrawable())
                            .submit(40.dp2Px(), 40.dp2Px()).get()
                }catch (ex: Exception) {
                    ALog.e(TAG, "Glide load default url failed", ex)
                    Bitmap.createBitmap(intArrayOf(Color.parseColor("#5F768C")), 40.dp2Px(), 40.dp2Px(), Bitmap.Config.ARGB_8888)
                }
                it.onNext(IndividualAvatarView.createCoverText(AppContextHolder.APP_CONTEXT.resources, background, letter, getColor(R.color.common_color_white)).bitmap)
                it.onComplete()
            }
        }
    }

    class UrlBitmapUnit(val url: String): BitmapUnit {
        override fun toBitmap(): Observable<Bitmap> {
            return Observable.create<Bitmap> {
                val drawable = try {
                    GlideApp.with(AppContextHolder.APP_CONTEXT).asBitmap().load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .error(IndividualAvatarView.getDefaultBackgroundDrawable())
                            .submit().get()
                }catch (ex: Exception) {
                    ALog.e(TAG, "Glide load url failed", ex)
                    Bitmap.createBitmap(intArrayOf(Color.parseColor("#5F768C")), 40.dp2Px(), 40.dp2Px(), Bitmap.Config.ARGB_8888)
                }
                it.onNext(drawable)
                it.onComplete()
            }
        }
    }

    class PathBitmapUnit(val localPath: String): BitmapUnit {
        override fun toBitmap(): Observable<Bitmap> {
            return Observable.create<Bitmap> {
                val drawable = try {
                    GlideApp.with(AppContextHolder.APP_CONTEXT).asBitmap().load(localPath)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .error(IndividualAvatarView.getDefaultBackgroundDrawable())
                            .submit().get()
                }catch (ex: Exception) {
                    Bitmap.createBitmap(intArrayOf(Color.parseColor("#5F768C")), 40.dp2Px(), 40.dp2Px(), Bitmap.Config.ARGB_8888)
                }
                it.onNext(drawable)
                it.onComplete()
            }
        }
    }

    /**
     * 
     */
    class RecipientBitmapUnit(val recipient: Recipient, val name: String): BitmapUnit {

        override fun toBitmap(): Observable<Bitmap> {

            val localAvatar = recipient.localAvatar
            if (!localAvatar.isNullOrEmpty()) {
                return PathBitmapUnit(localAvatar).toBitmap()
            }
            val privacyAvatarLD = recipient.getPrivacyAvatar(false)
            if (!privacyAvatarLD.isNullOrEmpty()) {
                return PathBitmapUnit(privacyAvatarLD).toBitmap()

            }
            val privacyAvatarHD = recipient.getPrivacyAvatar(true)
            if (!privacyAvatarHD.isNullOrEmpty()) {
                return PathBitmapUnit(privacyAvatarHD).toBitmap()
            }
            val size = 40.dp2Px()
            val url = IndividualAvatarView.getAvatarThumbnailUrl(recipient, size, size)
            if (!url.isNullOrEmpty()) {
                return UrlBitmapUnit(url).toBitmap()
            }
            val defaultAvatarUrl = IndividualAvatarView.getDefaultPortraitUrl(recipient)
            val letter = StringAppearanceUtil.getFirstCharacter(name)
            return CharacterBitmapUnit(letter, defaultAvatarUrl).toBitmap()
        }

    }

    /**
     * 
     */
    fun convertBitmap(bitmapUnits: List<BitmapUnit>, width: Int, height: Int): Observable<Bitmap> {
        if (width <= 0 || height <= 0 || width%4 != 0 || height%4 != 0) {
            throw Exception("wrong size w:$width, h:$height")
        }

        if (bitmapUnits.isEmpty()) {
            throw Exception("bitmap unit list is empty")
        }

        //4
        val unitList = bitmapUnits.subList(0, Math.min(bitmapUnits.size, 4))
        return Observable.zip(unitList.map {unit-> unit.toBitmap()}) { list ->
            val bitmapList:List<Bitmap> = list as List<Bitmap>

            val combineBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val cv = Canvas(combineBitmap)
            for (i in 0 until bitmapList.size) {
                cv.drawBitmap(bitmapList[i], null, Rect(0, 0, width, height), null)
            }
            cv.save()
            cv.restore()
            return@zip  combineBitmap
        }
    }

    /**
     * 
     */
    fun combineBitmap(bitmapUnits:List<BitmapUnit>, width:Int, height:Int):Observable<Bitmap> {
        if (width <= 0 || height <= 0 || width%4 != 0 || height%4 != 0) {
            throw Exception("wrong size w:$width, h:$height")
        }

        if (bitmapUnits.isEmpty()) {
            throw Exception("bitmap unit list is empty")
        }

        //4
        val unitList = bitmapUnits.subList(0, Math.min(bitmapUnits.size, 4))
        return Observable.zip(unitList.map {unit-> unit.toBitmap()}) { list ->
            val bitmapList:List<Bitmap> = list.map { it as Bitmap }

            val combineBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val spaceH = 4
            val spaceV = 4
            val piceWidth = (width - spaceH)/2
            val piceHeight = (height - spaceV)/2

            var startX = 0
            var startY = 0

            val cv = Canvas(combineBitmap)
            for (i in 0 until bitmapList.size) {
                cv.drawBitmap(bitmapList[i], null, Rect(startX, startY, startX+piceWidth, startY+piceHeight), null)

                val next = i+1
                startX = (next%2)*(piceWidth + spaceH)
                startY = (next/2)*(piceHeight + spaceH)
            }
            cv.save()
            cv.restore()
            return@zip  combineBitmap
        }
    }


}