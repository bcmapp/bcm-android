package com.bcm.messenger.common.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.annotation.ArrayRes
import androidx.annotation.AttrRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.R
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.NetworkUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.orhanobut.logger.Logger
import org.whispersystems.libsignal.util.guava.Optional
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


/**
 * 
 * Created by wjh on 2018/3/8
 */
object AppUtil {

    private const val TAG = "AppUtil"

    fun getAudioManager(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getPowerManager(context: Context): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    /**
     * intent（，）
     *
     * @return
     */
    fun getAppDetailSettingIntent(context: Context): Intent {
        val localIntent = Intent()
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        localIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        localIntent.data = Uri.fromParts("package", context.packageName, null)
        return localIntent
    }

    /**
     * intent
     */
    fun getLaunchAppIntent(context: Context, packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * apk
     */
    fun isApkFile(context: Context, path: String): Boolean {
        return try {
            val manager = context.packageManager
            val info = manager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
            !info?.applicationInfo?.packageName.isNullOrEmpty()
        }catch (ex: Exception) {
            ALog.e(TAG, "isApkFile error", ex)
            false
        }
    }

    /**
     * 
     */
    fun checkInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true
        }
        return context.packageManager.canRequestPackageInstalls()
    }

    fun requestInstallPermission(context: Context) {
        try {
            val packageUri = Uri.parse("package:${context.packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            ALog.e(TAG, "requestInstallPermission error", ex)
        }
    }

    fun requestInstallPermission(activity: Activity, requestCode: Int) {
        try {
            val packageUri = Uri.parse("package:${activity.packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            activity.startActivityForResult(intent, requestCode)
        } catch (ex: Exception) {
            ALog.e(TAG, "requestInstallPermission error", ex)
        }
    }

    /**
     * 
     */
    @Deprecated(message = "Use Context.startServiceCompat() instead", replaceWith = ReplaceWith("Context.startServiceCompat"))
    fun startServiceIfBackground(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "startServiceIfBackground error", ex)
        }
    }

    /**
     * copy from shared perferences A to B
     */
    fun copy(prefA: SharedPreferences, prefB: SharedPreferences) {
        val setA = prefA.all
        if (setA.isNotEmpty()){
            val editorB = prefB.edit()
            for (pair in setA.entries){
                when {
                    pair.value is Boolean -> editorB.putBoolean(pair.key, pair.value as Boolean)
                    pair.value is Float -> editorB.putFloat(pair.key, pair.value as Float)
                    pair.value is Int -> editorB.putInt(pair.key, pair.value as Int)
                    pair.value is Long -> editorB.putLong(pair.key, pair.value as Long)
                    pair.value is String -> editorB.putString(pair.key, pair.value as String)
                    pair.value is Set<*> -> {
                        val valueSet = pair.value as Set<String>
                        editorB.putStringSet(pair.key, valueSet)
                    }
                }
            }
            editorB.apply()
        }
    }

    @Throws(IOException::class)
    fun zipRealCompress(outputZipFile: String, compressFileList: List<String>) {
        val fout = File(outputZipFile)
        fout.createNewFile()

        val outputStream = FileOutputStream(fout)
        //
        val fileChannel = outputStream.channel
        fileChannel.truncate(0)
        fileChannel.close()

        val zipOutputStream = ZipOutputStream(FileOutputStream(fout))

        val bufferSize = 1024 * 1024
        val buffer = ByteArray(bufferSize)
        var fileInputStream: FileInputStream? = null
        try {
            for (sf in compressFileList) {
                val file = File(sf)
                if (!file.exists()) {
                    continue
                }
                val filename = file.name
                val ze = ZipEntry(filename)
                zipOutputStream.putNextEntry(ze)

                fileInputStream = FileInputStream(file)
                var byteRead = fileInputStream.read(buffer, 0, bufferSize)
                while (byteRead > 0) {
                    zipOutputStream.write(buffer, 0, byteRead)
                    byteRead = fileInputStream.read(buffer, 0, bufferSize)
                }

                zipOutputStream.closeEntry()
                fileInputStream.close()
                fileInputStream = null
            }
        } finally {
            fileInputStream?.close()
            outputStream.close()
            zipOutputStream.close()
        }
    }

    /**
     * contextactivity
     */
    private fun context2Activity(context: Context): Activity? {
        try {
            return context as? Activity ?: if (context is ContextWrapper) {
                context2Activity(context.baseContext)
            } else {
                null
            }
        }catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun activityFinished(context: Context): Boolean {
        return context2Activity(context)?.isFinishing?:true
    }

    fun activityDestroyed(context: Context): Boolean {
        return context2Activity(context)?.isDestroyed?:true
    }

    fun activityFinished(view: View?): Boolean {
        view?:return true
        return context2Activity(view.context)?.isFinishing?:true
    }

    fun activityDestroyed(view: View?): Boolean {
        view?:return true
        return context2Activity(view.context)?.isDestroyed?:true
    }

    fun getSignature(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val mdg = MessageDigest.getInstance("SHA-1")
        mdg.update(info.signatures[0].toByteArray())
        return Base64.encodeBytes(mdg.digest(), 0)
    }

    /**
     * 
     * @param context
     */
    fun getCodeFromBoard(context: Context?): CharSequence {
        try {
            context ?: return ""
            val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = c.primaryClip ?: return ""
            return if (clipData.itemCount == 0) {
                ""
            } else {
                clipData.getItemAt(0).text
            }

        } catch (ex: Exception) {
            Logger.e(ex, "getCodeFromBoard error", ex)
        }
        return ""
    }

    /**
     * 
     * @param context
     * @param srcText
     */
    fun saveCodeToBoard(context: Context?, srcText: String) {
        try {
            context ?: return
            val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            c.primaryClip = ClipData.newPlainText("text", srcText)

        } catch (ex: Exception) {
            Logger.e(ex, "saveCodeToBoard error", ex)
        }

    }

    /**
     * uri
     *
     * @param uri uri
     */
    fun saveUriToBoard(context: Context?, uri: Uri) {
        try {
            context ?: return
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip = ClipData.newUri(context.contentResolver, "uri", uri)
        } catch (ex: Exception) {
            ALog.e(TAG, "saveUriToBoard error", ex)
        }
    }

    /**
     * uri
     *
     * @return uri
     */
    fun getUriFromBoard(context: Context?): Uri? {
        try {
            context ?: return null
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            return if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).uri
            } else null
        } catch (ex: Exception) {
            ALog.e(TAG, "getUri error", ex)
        }
        return null
    }

    /**
     * 
     *
     * @param intent 
     */
    fun saveIntentToBoard(context: Context?, intent: Intent) {
        try {
            context ?: return
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip = ClipData.newIntent("intent", intent)
        } catch (ex: Exception) {
            ALog.e(TAG, "saveIntentToBoard error", ex)
        }
    }

    /**
     * 
     *
     * @return 
     */
    fun getIntentFromBoard(context: Context?): Intent? {
        try {
            context ?: return null
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            return if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).intent
            } else null
        } catch (ex: Exception) {
            ALog.e(TAG, "getIntentFromBoard error", ex)
        }
        return null
    }

    /**
     * 
     */
    fun measureText(content: String): Int {
        val paint = Paint()
        val rect = Rect()
        paint.getTextBounds(content, 0, content.length, rect)
        return rect.width()
    }

    fun clamp(value: Int, min: Int, max: Int): Int {
        return Math.min(Math.max(value, min), max)
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return Math.min(Math.max(value, min), max)
    }

    /**
     * ：dppx
     * @param res
     * @param dp
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Float.dp2Px()"), message = "Use Float.dp2Px() instead")
    fun dp2Px(res: Resources, dp: Float): Float {
        return dp * res.displayMetrics.density + 0.5f
    }

    /**
     * ：dppx
     * @param res
     * @param dp
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Int.dp2Px()"), message = "Use Int.dp2Px() instead")
    fun dp2Px(res: Resources, dp: Int): Int {
        return (dp * res.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * ：sppx
     * @param res
     * @param sp
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Int.sp2Px()"), message = "Use Int.sp2Px() instead")
    fun sp2Px(res: Resources, sp: Int): Int {
        return (sp * res.displayMetrics.scaledDensity + 0.5f).toInt()
    }

    /**
     * ：pxdp
     * @param res
     * @param px
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Int.px2Dp()"), message = "Use Int.px2Dp() instead")
    fun px2Dp(res: Resources, px: Int): Int {
        val scale = res.displayMetrics.density
        return (px / scale + 0.5f).toInt()
    }

    /**
     * ：pxsp
     * @param res
     * @param px
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Int.px2Sp()"), message = "Use Int.px2Sp() instead")
    fun px2Sp(res: Resources, px: Int): Int {
        val fontScale = res.displayMetrics.scaledDensity
        return (px / fontScale + 0.5f).toInt()
    }

    fun getColor(context: Context, @AttrRes attr: Int): Int {
        val styledAttributes = context.obtainStyledAttributes(intArrayOf(attr))
        val result = styledAttributes.getColor(0, -1)
        styledAttributes.recycle()
        return result
    }

    fun getDrawableRes(c: Context, @AttrRes attr: Int): Int {
        return getDrawableRes(c.theme, attr)
    }

    fun getDrawableRes(theme: Resources.Theme, @AttrRes attr: Int): Int {
        val out = TypedValue()
        theme.resolveAttribute(attr, out, true)
        return out.resourceId
    }

    fun getDrawable(c: Context, @AttrRes attr: Int): Drawable? {
        return ContextCompat.getDrawable(c, getDrawableRes(c, attr))
    }

    fun getResourceIds(c: Context, @ArrayRes array: Int): IntArray {
        val typedArray = c.resources.obtainTypedArray(array)
        val resourceIds = IntArray(typedArray.length())
        for (i in 0 until typedArray.length()) {
            resourceIds[i] = typedArray.getResourceId(i, 0)
        }
        typedArray.recycle()
        return resourceIds
    }

    /**
     * 
     * @param res
     * @param resId
     */
    @Deprecated(replaceWith = ReplaceWith("Context.getColorCompat(resId: Int)"), message = "Use Context.getColorCompat(resId: Int) instead")
    fun getColor(res: Resources, resId: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            res.getColor(resId, null)
        } else {
            res.getColor(resId)
        }
    }

    /**
     * 
     */
    fun getDrawable(res: Resources, resId: Int): Drawable {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            res.getDrawable(resId, null)
        } else {
            res.getDrawable(resId)
        }
    }

    fun getCircleDrawable(fillColor: Int, size: Int): Drawable {
        val gd = GradientDrawable()//drawable
        gd.setColor(fillColor)
        gd.shape = GradientDrawable.OVAL
        gd.setSize(size, size)
        return gd
    }

    fun getString(context: Context, resId: Int): String {
        return context.getString(resId)
    }

    fun getString(resId: Int): String {
        return AppContextHolder.APP_CONTEXT.getString(resId)
    }

    /**
     * ，，，
     * @param activity
     * @param event 
     * @param focusView view
     */
    fun hideKeyboard(activity: Activity?, event: MotionEvent?, focusView: View?) {
        try {
            if (activity == null || event == null) {
                return
            }
            if (focusView != null) {

                val location = intArrayOf(0, 0)
                focusView.getLocationInWindow(location)

                val left = location[0]
                val top = location[1]
                val right = left + focusView.width
                val bottom = top + focusView.height

                // ，，
                if (event.rawX < left || event.rawX > right || event.rawY < top || event.rawY > bottom) {
                    // 
                    val token = focusView.windowToken
                    val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    inputMethodManager?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
                }
            }

        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     * 
     *
     * @param activity activity
     */
    @Deprecated(replaceWith = ReplaceWith("Activity.hideKeyboard()"), message = "Use Activity.hideKeyboard() instead")
    fun hideKeyboard(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }

    @Deprecated(replaceWith = ReplaceWith("View.hideKeyboard()"), message = "Use View.hideKeyboard() instead")
    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * 
     *
     * @param view view
     */
    @Deprecated(replaceWith = ReplaceWith("View.showKeyboard()"), message = "Use View.showKeyboard() instead")
    fun showKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    /**
     * Gets the content:// URI from the given corresponding path to a file
     * uri
     *
     * @param context
     * @param filePath
     * @return content Uri
     */
    fun getImageContentUri(context: Context, filePath: String): Uri {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID), MediaStore.Images.Media.DATA + "=? ",
                    arrayOf(filePath), null)
            return if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            } else {
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DATA, filePath)
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        } finally {
            cursor?.close()
        }
    }

    /**
     * uri
     */
    fun getImagePathFromURI(context: Context, contentURI: Uri): String {
        val result: String
        val cursor = if (contentURI.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
            context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.ImageColumns.DATA),
                    null, null,
                    MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
            )
        } else {
            // 
            context.contentResolver.query(
                    MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.ImageColumns.DATA), null, null,
                    MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
            )
        }
        if (cursor == null)
            result = contentURI.path ?: ""
        else {
            cursor.moveToFirst()
            val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            result = cursor.getString(index) ?: ""
            cursor.close()
        }
        return result
    }


    /**
     * 
     * @param context
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Context.checkOverlayPermission()"), message = "Use Context.checkOverlayPermission() instead")
    fun checkOverlaysPermission(context: Context): Boolean {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED

            }
        } catch (ex: Exception) {
            Logger.e(ex, "checkOverlaysPermission error")
        }

        return false
    }

    /**
     * 
     * @param fragment
     * @param requestCode
     * @return true，false
     */
    fun requestOverlaysPermission(fragment: Fragment, requestCode: Int): Boolean {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //，
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + fragment.context?.packageName))
                fragment.startActivityForResult(intent, requestCode)
                true

            } else {
                if (ContextCompat.checkSelfPermission(fragment.context!!, Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                    fragment.requestPermissions(arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW), requestCode)
                }
                true
            }
        } catch (ex: Exception) {
            Logger.e(ex, "requestOverlaysPermission error")
        }

        return false
    }

    /**
     * 
     * @param activity
     * @param requestCode
     * @return true，false
     */
    @Deprecated(replaceWith = ReplaceWith("Activity.requestOverlaysPermission(requestCode: Int)"), message = "Use Activity.requestOverlaysPermission(requestCode: Int) instead")
    fun requestOverlaysPermission(activity: Activity, requestCode: Int): Boolean {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //，
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                activity.startActivityForResult(intent, requestCode)
                true

            } else {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW), requestCode)
                }
                true
            }
        } catch (ex: Exception) {
            Logger.e(ex, "requestOverlaysPermission error")
        }

        return false
    }

    @Deprecated(replaceWith = ReplaceWith("Context.getPackageInfo()"), message = "Use Context.getPackageInfo() instead")
    fun getPackageInfo(ctx: Context): PackageInfo {
        var info: PackageInfo? = null
        try {
            info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace(System.err)
        }

        if (info == null) info = PackageInfo()
        return info
    }

    /**
     * whether this process is named with processName
     *
     * @param context
     * @param processName
     * @return
     * return whether this process is named with processName
     *  * if context is null, return false
     *  * if [ActivityManager.getRunningAppProcesses] is null, return false
     *  * if one process of [ActivityManager.getRunningAppProcesses] is equal to processName, return
     * true, otherwise return false
     *
     */
    fun isNamedProcess(context: Context?, processName: String): Boolean {
        if (context == null) {
            return false
        }

        val pid = android.os.Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processInfoList = manager.runningAppProcesses ?: return true

        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid && processInfo.processName == processName) {
                return true
            }
        }
        return false
    }

    /**
     * whether application is in background
     *
     *  * need use permission android.permission.GET_TASKS in Manifest.xml
     *
     *
     * @param context
     * @return if application is in background return true, otherwise return false
     */
    fun isApplicationInBackground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskList = am.getRunningTasks(1)
        if (taskList != null && !taskList.isEmpty()) {
            val topActivity = taskList[0].topActivity
            if (topActivity != null && topActivity.packageName != context.packageName) {
                return true
            }
        }
        return false
    }

    /**
     * 
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setTranslucentStatus()"), message = "Use Window.setTranslucentStatus() instead")
    private fun setTranslucentStatus(window: Window) {
        // 5.0
        when {
            RomUtil.isMiui() || RomUtil.isFlyme() -> {
                window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            }
            else -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.TRANSPARENT
            }
        }
    }

    /**
     * 
     * @param window
     * @param dark 
     */
    @Deprecated(replaceWith = ReplaceWith("Window.transparencyBar(dark: Boolean)"), message = "Use Window.transparencyBar(dark: Boolean) instead")
    fun transparencyBar(window: Window, dark: Boolean = true) {

        if (dark) {
            setStatusBarLightMode(window)
        } else {
            setStatusBarDarkMode(window)
        }
    }

    /**
     * ，
     * 4.4MIUIV、Flyme6.0Android
     *
     * @param window
     * @return 1:MIUUI 2:Flyme 3:android6.0
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarLightMode()"), message = "Use Window.setStatusBarLightMode() instead")
    fun setStatusBarLightMode(window: Window): Int {
        setTranslucentStatus(window)
        var result = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            when {
                setStatusBarLightModeForMIUI(window, true) -> result = 1
                setStatusBarLightModeForFlyme(window, true) -> result = 2
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    result = 3
                }
            }
        }
        return result
    }

    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarDarkMode()"), message = "Use Window.setStatusBarDarkMode() instead")
    fun setStatusBarDarkMode(window: Window): Int {
        setTranslucentStatus(window)
        var result = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            when {
                setStatusBarLightModeForMIUI(window, false) -> result = 1
                setStatusBarLightModeForFlyme(window, false) -> result = 2
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
                    result = 3
                }
            }
        }
        return result
    }

    /**
     * ，。
     * 4.4MIUIV、Flyme6.0Android
     *
     * @param window
     * @param type   1:MIUUI 2:Flyme 3:android6.0
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarLightMode(type: Int)"), message = "Use Window.setStatusBarLightMode(type: Int) instead")
    fun setStatusBarLightMode(window: Window, type: Int) {
        setTranslucentStatus(window)
        when (type) {
            1 -> setStatusBarLightModeForMIUI(window, true)
            2 -> setStatusBarLightModeForFlyme(window, true)
            3 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }

    }

    /**
     * MIUIflyme6.0
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarDarkMode(type: Int)"), message = "Use Window.setStatusBarDarkMode(type: Int) instead")
    fun setStatusBarDarkMode(window: Window, type: Int) {
        setTranslucentStatus(window)
        when (type) {
            1 -> setStatusBarLightModeForMIUI(window, false)
            2 -> setStatusBarLightModeForFlyme(window, false)
            3 -> window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
        }

    }


    /**
     * 
     * Flyme
     *
     * @param window 
     * @param dark   
     * @return boolean true
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarLightModeForFlyme(dark: Boolean)"), message = "Use Window.setStatusBarLightModeForFlyme(dark: Boolean) instead")
    private fun setStatusBarLightModeForFlyme(window: Window?, dark: Boolean): Boolean {
        var result = false
        if (window != null) {
            try {
                val lp = window.attributes
                val darkFlag = WindowManager.LayoutParams::class.java
                        .getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
                val meizuFlags = WindowManager.LayoutParams::class.java
                        .getDeclaredField("meizuFlags")
                darkFlag.isAccessible = true
                meizuFlags.isAccessible = true
                val bit = darkFlag.getInt(null)
                var value = meizuFlags.getInt(lp)
                if (dark) {
                    value = value or bit
                } else {
                    value = value and bit.inv()
                }
                meizuFlags.setInt(lp, value)
                window.attributes = lp
                result = true
            } catch (e: Exception) {
            }

        }
        return result
    }

    /**
     * ，MIUIV6
     *
     * @param window 
     * @param dark   
     * @return boolean true
     */
    @Deprecated(replaceWith = ReplaceWith("Window.setStatusBarLightModeForMIUI(dark: Boolean)"), message = "Use Window.setStatusBarLightModeForMIUI(dark: Boolean) instead")
    private fun setStatusBarLightModeForMIUI(window: Window?, dark: Boolean): Boolean {
        var result = false
        if (window != null) {
            val clazz = window.javaClass
            try {
                var darkModeFlag = 0
                val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
                val field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
                darkModeFlag = field.getInt(layoutParams)
                val extraFlagField = clazz.getMethod("setExtraFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                if (dark) {
                    extraFlagField.invoke(window, darkModeFlag, darkModeFlag)//
                } else {
                    extraFlagField.invoke(window, 0, darkModeFlag)//
                }
                result = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // 7.7.13 API，，
                    if (dark) {
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
            } catch (e: Exception) {
            }

        }
        return result
    }


    /**
     * 
     *
     * @param context
     * @return
     */
    @Deprecated(replaceWith = ReplaceWith("Context.getScreenPixelSize()"), message = "Use Context.getScreenPixelSize() instead")
    fun getScreenPixelSize(context: Context): IntArray {
        val metrics = context.resources.displayMetrics
        return intArrayOf(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 
     * @param context Context
     * @return Screen width pixels
     */
    @Deprecated(replaceWith = ReplaceWith("Context.getScreenWidth()"), message = "Use Context.getScreenWidth() instead")
    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * ，
     * @param context Context
     * @return Screen height pixels, exclude status bar and navigation bar
     */
    @Deprecated(replaceWith = ReplaceWith("Context.getScreenHeight()"), message = "Use Context.getScreenHeight() instead")
    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * 
     * @return Screen width pixels
     */
    fun getRealScreenWidth(): Int {
        val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels
    }

    /**
     * ，
     * @return Screen height pixels, include status bar and navigation bar
     */
    fun getRealScreenHeight(): Int {
        val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        return metrics.heightPixels
    }

    @Deprecated(replaceWith = ReplaceWith("Context.getStatusBarHeight()"), message = "Use Context.getStatusBarHeight() instead")
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return context.resources.getDimensionPixelSize(resourceId)
    }

    @Deprecated(replaceWith = ReplaceWith("Context.getNavigationBarHeight()"), message = "Use Context.getNavigationBarHeight() instead")
    fun getNavigationBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return context.resources.getDimensionPixelSize(resourceId)
    }

    fun checkDeviceHasNavigationBar(context: Context): Boolean {
        var hasNavigationBar = false
        val rs = context.resources
        val id = rs.getIdentifier("config_showNavigationBar", "bool", "android")
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id)
        }
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val m = systemPropertiesClass.getMethod("get", String::class.java)
            val navBarOverride = m.invoke(systemPropertiesClass, "qemu.hw.mainkeys") as String
            if ("1" == navBarOverride) {
                hasNavigationBar = false
            } else if ("0" == navBarOverride) {
                hasNavigationBar = true
            }
        } catch (e: Exception) {

        }

        return hasNavigationBar
    }

    /**
     * activity， 5.x
     *
     * @param context
     * @return
     */
    fun getCurrentPkgName(context: Context): String? {
        val START_TASK_TO_FRONT = 2
        var field: Field? = null
        try {
            field = ActivityManager.RunningAppProcessInfo::class.java.getDeclaredField("processState")//.
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = manager.runningAppProcesses
        var currentInfo: ActivityManager.RunningAppProcessInfo? = null
        if (runningAppProcesses != null) {
            for (i in runningAppProcesses.indices) {
                val process = runningAppProcesses[i]
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    // 
                    var state: Int? = null
                    try {
                        state = field!!.getInt(process)//,.
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (state != null && state == START_TASK_TO_FRONT) {
                        currentInfo = process
                        break
                    }
                }
            }
        }
        var pkgName: String? = null
        if (currentInfo != null) {
            pkgName = currentInfo.processName
        }
        return pkgName
    }

    /**
     * 
     * @param context
     * @param bitmap bitmap
     * @param blurRadius 
     */
    fun blurBitmap(context: Context, bitmap: Bitmap, blurRadius: Float): Bitmap {

        //Let's create an empty bitmap with the same size of the bitmap we want to blur
        val outBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        //Instantiate a new Renderscript
        val rs = RenderScript.create(context)

        //Create an Intrinsic Blur Script using the Renderscript
        val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
        val allIn = Allocation.createFromBitmap(rs, bitmap)
        val allOut = Allocation.createFromBitmap(rs, outBitmap)

        //Set the radius of the blur: 0 < radius <= 25
        blurScript.setRadius(blurRadius)

        //Perform the Renderscript
        blurScript.setInput(allIn)
        blurScript.forEach(allOut)

        //Copy the final bitmap created by the out Allocation to the outBitmap
        allOut.copyTo(outBitmap)

        //recycle the original bitmap
        bitmap.recycle()

        //After finishing everything, we destroy the Renderscript.
        rs.destroy()

        return outBitmap
    }


    /**
     * 
     * @param view
     * @param url 
     * @param blurRadius 
     */
    fun blurBitmap(view: ImageView?, url: String?, blurRadius: Float) {
        if (null == view || null == url || null == view.context) {
            return
        }

        if (url == view.tag) {
            return
        }

        view.tag = url
        try {
            val weakView = WeakReference<ImageView>(view)
            val weakContext = WeakReference<Context>(view.context)
            Glide.with(view.context.applicationContext)
                    .asBitmap()
                    .load(url)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            Logger.e("blurImage", "onResourceReady")
                            applyBitmap(resource)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            Logger.e("blurImage", "onLoadFailed")
                        }

                        private fun applyBitmap(bitmap: Bitmap) {
                            val newBitmap = Bitmap.createBitmap(bitmap)
                            val v: ImageView? = weakView.get()
                            val context: Context? = weakContext.get()
                            if (null != v && null != context && view.tag == url) {
                                v.setImageBitmap(blurBitmap(context, newBitmap, blurRadius))
                            }
                        }

                    })
        } catch (ex: Exception) {
            Logger.e(ex, "ChatRtcCallScreen ")
        }
    }


    /**
     *  app
     */
    fun getApplicationName(context: Context): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes

        if (stringId == 0) {
            return applicationInfo.nonLocalizedLabel.toString()
        } else {
            return context.getString(stringId)
        }
    }

    /**
     *  appversion code
     */
    fun getVersionCode(context: Context): Int {
        return getPackageInfo(context).versionCode
    }

    /**
     *  appversion name
     */
    fun getVersionName(context: Context): String {
        return getPackageInfo(context).versionName
    }


    /**
     * true 
     */
    fun isDevBuild(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.isDevBuild() ?: false
    }

    /**
     * true 
     */
    fun isBetaBuild(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.isBetaBuild() ?: false
    }

    /**
     * true 
     */
    fun isReleaseBuild(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.isReleaseBuild() ?: true
    }

    /**
     * true (google play )
     */
    fun isSupportGooglePlay(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.isSupportGooglePlay() ?: true
    }

    /**
     * true lbs
     */
    fun isLbsEnable(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.lbsEnable() ?: true
    }

    /**
     * 
     */
    fun lastBuildTime(): Long {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.lastBuildTime() ?: 0
    }

    /**
     * true ，false 
     */
    fun isTestEnvEnable(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.testEnvEnable() ?: false
    }

    /**
     * 
     */
    fun useDevBlockChain(): Boolean {
        val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
        return provider?.useDevBlockChain() ?: false

    }

    fun is24HourFormat(context: Context?): Boolean {
        return android.text.format.DateFormat.is24HourFormat(context)
    }

    @SuppressLint("NewApi")
    fun isDefaultSmsProvider(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }

    fun getSimCountryIso(): Optional<String> {
        val simCountryIso = (AppContextHolder.APP_CONTEXT.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simCountryIso
        return Optional.fromNullable(simCountryIso?.toUpperCase())
    }

    /**
     * 
     * @param addressString（ipv4）
     * @return
     */
    fun checkInvalidAddressV4(addressString: String): Boolean {
        try {

            val addressStrings = addressString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val address = ByteArray(addressStrings.size)
            for (i in address.indices) {
                address[i] = java.lang.Byte.valueOf(addressStrings[i])
            }
            if (address.size == 4) {
                val b0 = address[0]
                val b1 = address[1]
                //127.x.x.x
                val SECTION_0 = 0x7F.toByte()
                //10.x.x.x/8
                val SECTION_1 = 0x0A.toByte()
                //172.16.x.x/12--172.31.x.x
                val SECTION_2 = 0xAC.toByte()
                val SECTION_3 = 0x10.toByte()
                val SECTION_4 = 0x1F.toByte()
                //192.168.x.x/16
                val SECTION_5 = 0xC0.toByte()
                val SECTION_6 = 0xA8.toByte()
                when (b0) {
                    SECTION_0 -> return true
                    SECTION_1 -> return true
                    SECTION_2 -> if (b1 >= SECTION_3 && b1 <= SECTION_4) {
                        return true
                    }
                    SECTION_5 -> if (b1 == SECTION_6) {
                        return true
                    }
                    else -> return false
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "checkInvalidAddressV4 error", ex)
        }

        return false
    }

    /**
     * return true 
     */
    fun isMainProcess(): Boolean {
        val am = AppContextHolder.APP_CONTEXT.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processInfos = am?.getRunningAppProcesses()?.toList()
        val mainProcessName = AppContextHolder.APP_CONTEXT.packageName;
        val myPid = Process.myPid()
        if (null != processInfos) {
            for (info in processInfos) {
                if (info.pid == myPid && mainProcessName.equals(info.processName)) {
                    return true
                }
            }
        }
        return false
    }

    fun exitApp(){
        try {
            val activityManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.appTasks.forEach {
                it.finishAndRemoveTask()
            }
            System.exit(0)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Activity
     */
    @Deprecated(replaceWith = ReplaceWith("Activity.createScreenShot()"), message = "Use Activity.createScreenShot() instead")
    fun createScreenShot(activity: Activity): Bitmap {
        val view = activity.window.decorView
        view.isDrawingCacheEnabled = true
        view.buildDrawingCache()
        val bitmap = Bitmap.createBitmap(view.drawingCache, 0, 0, view.measuredWidth, view.measuredHeight)
        view.isDrawingCacheEnabled = false
        view.destroyDrawingCache()
        return bitmap
    }

    /**
     * View
     */
    @Deprecated(replaceWith = ReplaceWith("View.createScreenShot()"), message = "Use View.createScreenShot() instead")
    fun createScreenShot(view: View): Bitmap {
        view.isDrawingCacheEnabled = true
        view.buildDrawingCache()
        view.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
        view.layout(view.x.toInt(), view.y.toInt(), view.x.toInt() + view.measuredWidth, view.y.toInt() + view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.drawingCache, 0, 0, view.measuredWidth, view.measuredHeight)
        view.isDrawingCacheEnabled = false
        view.destroyDrawingCache()
        return bitmap
    }

    fun isMobileNetwork(context: Context): Boolean {
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            if (info != null && info.isConnected) {
                return info.type == ConnectivityManager.TYPE_MOBILE
            }
        }catch (ex: Exception) {

        }
        return false
    }

    fun isWiFiNetwork(context: Context): Boolean {
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            if (info != null && info.isConnected) {
                return info.type == ConnectivityManager.TYPE_WIFI
            }
        }catch (ex: Exception) {

        }
        return false
    }

    fun isUsingNetwork(context: Context): Boolean {
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            return info != null && info.isConnectedOrConnecting
        }catch (ex: Exception) {

        }
        return true
    }

    fun checkNetwork():Boolean {
        if (NetworkUtil.isConnected()) {
            return true
        }

        AmeAppLifecycle.failure(getString(R.string.common_network_connection_error), true)
        return false
    }

    fun getLowCriteria(): Criteria {
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_COARSE//，，location。
        criteria.isAltitudeRequired = false//
        criteria.isBearingRequired = false//
        criteria.isCostAllowed = true//
        criteria.powerRequirement = Criteria.POWER_LOW//
        return criteria
    }


    fun getHighCriteria(): Criteria {
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_HIGH//，，location。
        criteria.isAltitudeRequired = false//
        criteria.isBearingRequired = false//
        criteria.isCostAllowed = true//
        criteria.powerRequirement = Criteria.POWER_LOW//
        return criteria
    }

    //GPS
    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context
                .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * 
     */
    fun getBestLocation(context: Context, criteriaParam: Criteria?): Location? {

        /**
         * network
         */
        fun getNetWorkLocation(context: Context): Location? {
            var location: Location? = null
            try {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                //
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {//Network
                    //network
                    location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }catch (ex: Exception) {
                ALog.e(TAG, "getNetWorkLocation error", ex)
            }
            return location
        }

        var criteria = criteriaParam
        var location: Location? = null
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (criteria == null) {
                criteria = Criteria()
            }
            val provider = manager.getBestProvider(criteria, true)
            location = if (provider.isNullOrEmpty()) {
                //，network
                getNetWorkLocation(context)
            } else {
                //
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
                //
                manager.getLastKnownLocation(provider)
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "getBestLocation error", ex)
        }
        return location
    }

    
}
