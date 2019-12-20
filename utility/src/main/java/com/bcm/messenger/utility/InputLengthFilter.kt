package com.bcm.messenger.utility

import android.text.InputFilter
import android.text.Spanned
import java.util.regex.Pattern

class InputLengthFilter(private val maxLength: Int = 30) : InputFilter {
    companion object {
        private const val REGEX = "[\\u4e00-\\u9fcb]"

        fun filterString(source: String, length: Int): String {
            val p = Pattern.compile(REGEX)
            var count = 0
            var end = false
            source.forEachIndexed { index, c ->
                if (p.matcher(c.toString()).find()) {
                    if (count + 2 <= length) {
                        count += 2
                    } else {
                        end = true
                    }
                } else {
                    if (count + 1 <= length) {
                        count ++
                    } else {
                        end = true
                    }
                }
                if (end) {
                    var s = source.substring(0, index)
                    if (index != source.length - 1) {
                        s += "…"
                    }
                    return s
                }
            }
            return source
        }

        fun filterSpliceName(userName: String, length: Int): String {
            val p = Pattern.compile(REGEX)
            var count = 0
            var end = false
            userName.forEachIndexed { index, c ->
                if (p.matcher(c.toString()).find()) {
                    if (count + 2 <= length) {
                        count += 2
                    } else {
                        end = true
                    }
                } else {
                    if (count + 1 <= length) {
                        count ++
                    } else {
                        end = true
                    }
                }
                if (end) {
                    var s = userName.substring(0, index)
                    if (index != userName.length) {
                        if (index == 10) {
                            s = s.substring(0, 9)
                        }
                        s += "…"
                    }
                    return s
                }
            }
            return userName
        }
    }

    override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence {
        val c1 = getChineseCount(dest?.toString() ?: "")
        val c2 = getChineseCount(source?.toString() ?: "")
        val destCount = (dest?.toString()?.length ?: 0) + c1
        val sourceCount = (source?.toString()?.length ?: 0) + c2
        var name: String
        var count = 0
        var i = 0
        if (destCount + sourceCount > maxLength) {
            if (destCount < maxLength) {
                while (destCount + count < maxLength) {
                    i++
                    name = source?.subSequence(0, i).toString()
                    count = name.length + getChineseCount(name)
                    if (destCount + count > maxLength) {
                        i--
                    }
                }
                return if (i == 0) "" else source?.subSequence(0, i) ?: ""
            }
            return ""
        } else {
            return source ?: ""
        }
    }

    private fun getChineseCount(str: String): Int {
        var count = 0
        val p = Pattern.compile(REGEX)
        val matcher = p.matcher(str)
        while (matcher.find()) {
            count++
        }
        return count
    }
}