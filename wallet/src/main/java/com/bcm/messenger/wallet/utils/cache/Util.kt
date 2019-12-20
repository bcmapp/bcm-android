package com.bcm.messenger.wallet.utils.cache

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import androidx.core.content.ContextCompat
import android.util.Base64
import android.util.Log
import androidx.media.VolumeProviderCompat
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.and

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 16/11/2017 9:09 PM
 * @description
 */
object Util {

    private val COLOR_PATTEN = Pattern.compile("([0-9A-Fa-f]{8})|([0-9A-Fa-f]{6})")
    private val PHONE_NUM_PATTERN = Pattern.compile("^((\\+?86)?\\s?-?)1[0-9]{10}")
    private val PHONE_NUM_FORMAT_PATTERN = Pattern.compile("^((\\+?86)?)\\s?-?")
    private val EMAIL_PATTERN = Pattern.compile("^\\w+([-.]\\w+)*@\\w+([-]\\w+)*\\.(\\w+([-]\\w+)*\\.)*[a-z]+$", Pattern.CASE_INSENSITIVE)

    @JvmStatic
    fun getCurrentTimeSecond(): Long {
        return System.currentTimeMillis() / 1000
    }

    @JvmStatic
    fun getCurrentTimeSecondStr(): String {
        return (System.currentTimeMillis() / 1000).toString()
    }

    @JvmStatic
    fun getAttrColor(context: Context, @VolumeProviderCompat.ControlType attrRes: Int): Int {
        return if (Build.VERSION.SDK_INT >= 23) {
            ContextCompat.getColor(context, attrRes)
        } else {
            context.resources.getColor(attrRes)
        }
    }
    //color end

    private fun filterColor(colorString: String): String {
        //        Pattern p = Pattern.compile("([0-9A-Fa-f]{8})|([0-9A-Fa-f]{6})");
        val m = COLOR_PATTEN.matcher(colorString)

        while (m.find()) {
            return m.group()
        }
        return ""
    }

    @JvmStatic
    fun parseColor(colorString: String): Int {
        val filterColor = filterColor(colorString)
        val parsedColorString: String
        if (!"#".equals(filterColor[0].toString(), ignoreCase = true)) {
            parsedColorString = "#" + filterColor
        } else {
            parsedColorString = filterColor
        }
        return Color.parseColor(parsedColorString)
    }

    @JvmStatic
    fun isColor(colorString: String): Boolean {
        val m = COLOR_PATTEN.matcher(colorString)

        while (m.find()) {
            return true
        }
        return false
    }

    /**
     * @param
     */
    @JvmStatic
    fun createRoundCornerShapeDrawable(radius: Float, borderColor: Int): ShapeDrawable {
        val outerR = floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
        val roundRectShape = RoundRectShape(outerR, null, null)  //  构造一个圆角矩形,可以使用其他形状，这样ShapeDrawable 就会根据形状来绘制。
        //RectShape rectShape = new RectShape();  // 如果要构造直角矩形可以
        val shapeDrawable = ShapeDrawable(roundRectShape) // 组合圆角矩形和ShapeDrawable
        shapeDrawable.paint.color = borderColor         // 设置形状的颜色
        shapeDrawable.paint.style = Paint.Style.FILL   //  设置绘制方式为填充
        return shapeDrawable
    }

    @JvmStatic
    fun bitmap2Base64String(bm: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.PNG, 0, bos)// 参数100表示不压缩
        val bytes = bos.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    /**
     * dp to px
     */
    @JvmStatic
    fun dip2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * px to dp
     */
    @JvmStatic
    fun px2dip(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    @JvmStatic
    fun checkPermissions(context: Context?, permission: String): Boolean {

        val localPackageManager = context!!.applicationContext.packageManager
        return localPackageManager.checkPermission(permission, context.applicationContext.packageName) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun addBackSplash(input: String): String {
        var i: Int
        val result = StringBuilder()
        val chars = input.toCharArray()
        i = 0
        while (i < chars.size) {
            result.append(chars[i])
            if (i > 0 && i < chars.size && "\\" == chars[i].toString() && "\\" != chars[i - 1].toString() && "\\" != chars[i + 1].toString()) {
                result.append("\\").append("\\")
            }
            i++
        }

        Log.d("addBackSplash:", result.toString())
        return result.toString()
    }

    @JvmStatic
    fun md5with16Byte(str: String): String {
        try {
            val localMessageDigest = MessageDigest.getInstance("MD5")
            localMessageDigest.update(str.toByteArray())
            val arrayOfByte = localMessageDigest.digest()
            val stringBuffer = StringBuilder()
            for (anArrayOfByte in arrayOfByte) {
                val j = 0xFF and anArrayOfByte.toInt()
                if (j < 16) {
                    stringBuffer.append("0")
                }
                stringBuffer.append(Integer.toHexString(j))
            }
            return stringBuffer.toString().toLowerCase().substring(8, 24)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return ""
    }

    @JvmStatic
    fun md5(str: String): String {
        var reStr = ""
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(str.toByteArray())
            val stringBuffer = StringBuilder()
            for (b in bytes) {
                val bt = b and 0xff.toByte()
                if (bt < 16) {
                    stringBuffer.append(0)
                }
                stringBuffer.append(Integer.toHexString(bt.toInt()))
            }
            reStr = stringBuffer.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return reStr
    }

    @JvmStatic
    fun formatPhoneNum(phoneNum: String): String {

        val m1 = PHONE_NUM_PATTERN.matcher(phoneNum)
        if (m1.matches()) {
            val m2 = PHONE_NUM_FORMAT_PATTERN.matcher(phoneNum)
            val sb = StringBuffer()
            while (m2.find()) {
                m2.appendReplacement(sb, "")
            }
            m2.appendTail(sb)
            return sb.toString()
        } else {
            return ""
        }
    }

    /**
     * user java reg to check phone number and replace 86 or +86
     * only check start with "+86" or "86" ex +8615911119999 13100009999 replace +86 or 86 with ""
     *
     * @param phoneNum
     * @return
     */
    @JvmStatic
    fun checkPhoneNum(phoneNum: String): Boolean {

        val m1 = PHONE_NUM_PATTERN.matcher(phoneNum)
        return m1.matches()
    }

    @JvmStatic
    fun checkEmail(email: String): Boolean {
        val matcher = EMAIL_PATTERN.matcher(email)
        return matcher.matches()
    }

    val regexCIp = "^192\\.168\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$"
    val regexAIp = "^10\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$"
    val regexBIp = "^172\\.(1[6-9]|2\\d|3[0-1])\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)\\.(\\d{1}|[1-9]\\d|1\\d{2}|2[0-4]\\d|25\\d)$"

    private fun isIPv4RealAddress(address: String): Boolean {
        val p = Pattern.compile("($regexAIp)|($regexBIp)|($regexCIp)")
        val m = p.matcher(address)
        return m.matches()
    }

    @JvmStatic
    fun getHostIp(): String? {
        var networkInterfaces: Enumeration<NetworkInterface>? = null
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces()
        } catch (e: SocketException) {
            e.printStackTrace()
        }

        var address: InetAddress
        while (networkInterfaces!!.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            val inetAddresses = networkInterface.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                address = inetAddresses.nextElement()
                val hostAddress = address.hostAddress
//                val matcher = ip.matcher(hostAddress)
                if (!address.isLoopbackAddress && isIPv4RealAddress(hostAddress)) {
                    return hostAddress
                }

            }
        }
        return null
    }
}