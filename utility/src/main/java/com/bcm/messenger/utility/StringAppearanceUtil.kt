package com.bcm.messenger.utility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import com.bcm.messenger.utility.logger.ALog
import java.util.*
import java.util.regex.Pattern


/**
 *
 * Created by wjh on 2018/3/8
 */
object StringAppearanceUtil {

    private fun getContainIgnoreCaseRegex(keyword: String): String {
        val builder = StringBuilder()
        builder.append("(")
        for(c in keyword) {
            builder.append("[")
            builder.append(c.toLowerCase())
            builder.append("")
            builder.append(c.toUpperCase())
            builder.append("]")
        }
        builder.append(")")
        builder.append("|")
        builder.append(keyword)
        return builder.toString()
    }

    fun containIgnore(source: String, keyword: String): Boolean {

        return try {
            source.toLowerCase(Locale.getDefault()).contains(keyword, true)

        }catch (ex: Exception) {
            ALog.e("StringAppearanceUtil", "containIgnore error", ex)
            false
        }
    }

    fun applyAppearance(content: CharSequence, size: Int = 0, color: Int = 0): CharSequence {
        val span = SpannableString(content)
        if (size != 0) {
            span.setSpan(AbsoluteSizeSpan(size, false), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (color != 0) {
            span.setSpan(ForegroundColorSpan(color), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return span
    }

    fun applyAppearance(context: Context, content: CharSequence, bold: Boolean): CharSequence {
        val span = SpannableString(content)
        if (bold) {
            span.setSpan(StyleSpan(Typeface.BOLD), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }else {
            span.setSpan(StyleSpan(Typeface.NORMAL), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return span
    }

    fun addImage(content: CharSequence, image: Drawable, index: Int): CharSequence {
        val textSpan = SpannableString(content)
        val imageSpan = CenterImageSpan(image)
        textSpan.setSpan(imageSpan, index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return textSpan
    }

    fun addImage(context: Context, content: CharSequence, imageResId: Int, size: Int, index: Int): CharSequence {
        val drawable = context.resources.getDrawable(imageResId, null)
        drawable.setBounds(0, 0, size, size)
        return addImage(content, drawable, index)
    }

    fun getFirstCharacter(content: String): String {
        try {
            if (content.isNotEmpty()) {
                val codePoints = intArrayOf(content.codePointAt(0))
                return String(codePoints, 0, 1)
            }
        } catch (ex: Exception) {
            ALog.e("StringAppearanceUtil", "", ex)
        }
        return ""
    }

    fun getFirstCharacterLetter(content: String): String {
        var character = getFirstCharacter(content)
        character = PinyinUtils.getInstance().getSpellingForUpper(character)
        return if (character.isEmpty()) {
            character
        } else {
            character.substring(0, 1)
        }
    }

    fun applyFilterAppearance(content: CharSequence, keyword: String, size: Int? = null, color: Int? = null): CharSequence {
        val pattern = Pattern.compile(makeQueryString(keyword))
        val matcher = pattern.matcher(content)
        val span = SpannableString(content)
        while (matcher.find()) {
            if (size != null) {
                span.setSpan(AbsoluteSizeSpan(size, false), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (color != null) {
                span.setSpan(ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return span
    }

    fun applyFilterAppearanceIgnoreCase(content: CharSequence, keyword: String, size: Int? = null, color: Int? = null): CharSequence {
        val kw = makeQueryString(keyword)
        val pattern = Pattern.compile(getContainIgnoreCaseRegex(kw))
        val matcher = pattern.matcher(content)
        val span = SpannableString(content)
        while (matcher.find()) {
            if (size != null) {
                span.setSpan(AbsoluteSizeSpan(size, false), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (color != null) {
                span.setSpan(ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return span
    }

    fun applySmall(sequence: CharSequence): CharSequence {
        val spannable = SpannableString(sequence)
        spannable.setSpan(RelativeSizeSpan(0.9f), 0, sequence.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    fun applyBold(sequence: CharSequence): CharSequence {
        val spannable = SpannableString(sequence)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, sequence.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    fun join(list: Array<String>, delimiter: String): String {
        return join(Arrays.asList(*list), delimiter)
    }

    fun join(list: Collection<String>, delimiter: String): String {
        val result = StringBuilder()
        var i = 0

        for (item in list) {
            result.append(item)

            if (++i < list.size)
                result.append(delimiter)
        }

        return result.toString()
    }

    fun join(list: LongArray, delimeter: String): String {
        val sb = StringBuilder()

        for (j in list.indices) {
            if (j != 0) sb.append(delimeter)
            sb.append(list[j])
        }

        return sb.toString()
    }

    fun formatByteSizeString(byteSize: Long):String {
        val g = 1024*1024*1024f
        val m = 1024*1024f
        val k = 1024f
        return when {
            byteSize > g -> String.format(Locale.US, "%.2f GB", byteSize/g)
            byteSize > m -> String.format(Locale.US, "%.2f MB", byteSize/m)
            byteSize > k -> String.format(Locale.US, "%.2f KB", byteSize/k)
            else -> String.format(Locale.US, "%d B", byteSize)
        }
    }

    private fun makeQueryString(query: String): String {
        var keyword = query
        if (query.isNotBlank()) {
            val fbsArr = arrayOf("\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|")
            for (key in fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\" + key)
                }
            }
        }
        return keyword
    }
}

class CenterImageSpan(d: Drawable?) : ImageSpan(d) {
    override fun draw(canvas: Canvas?, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint?) {
        val d = drawable
        val fm = paint?.fontMetricsInt
        var transY =  0
        fm?.let {
            transY = (y + it.descent + y + it.ascent) / 2 - d.bounds.bottom / 2
        }
        canvas?.save()
        canvas?.translate(x, transY.toFloat())
        d.draw(canvas)
        canvas?.restore()
    }
}